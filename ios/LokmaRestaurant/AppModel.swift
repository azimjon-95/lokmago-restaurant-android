import Foundation
import SwiftUI
import Combine

/// Settings keys shared with @AppStorage in SettingsScreen (same names, same defaults).
enum Prefs {
    static var sound: Bool { UserDefaults.standard.object(forKey: "sound") as? Bool ?? true }
    static var vibration: Bool { UserDefaults.standard.object(forKey: "vibration") as? Bool ?? true }
    static var volume: Int { UserDefaults.standard.object(forKey: "volume") as? Int ?? 2 }          // 0 Past, 1 O'rta, 2 Baland
    static var autoRefresh: Int { UserDefaults.standard.object(forKey: "autoRefresh") as? Int ?? 10 } // seconds, 0 = off
    static var volumeFraction: Float { [0.4, 0.7, 1.0][min(max(volume, 0), 2)] }
}

@MainActor final class AppModel: ObservableObject {
    static weak var shared: AppModel?

    let api: APIClient
    let menu: MenuStore
    private let socket = RealtimeSocket()
    @Published var token: String? { didSet { api.token = token; KeychainStore.save(token) } }
    @Published var restaurant: Restaurant? {
        didSet {
            if let restaurant, let data = try? JSONEncoder().encode(restaurant) {
                UserDefaults.standard.set(data, forKey: "restaurant-profile")
            } else {
                UserDefaults.standard.removeObject(forKey: "restaurant-profile")
            }
        }
    }
    @Published var orders: [RestaurantOrder] = []
    @Published var stats: TodayStats?
    @Published var isLoading = false
    @Published var error: String?
    @Published var online = false          // socket joined
    @Published var silenced = false        // "Signalni to'xtatish": mutes only, never accepts
    @Published var minimized = false       // alert screen closed for now
    @Published var busy = false
    private var knownPending = Set<String>()
    private var pollingTask: Task<Void, Never>?
    private let refreshedKey = "token-refreshed-at"

    init() {
        let client = APIClient()
        api = client
        menu = MenuStore(api: client)
        token = KeychainStore.read()
        api.token = token
        restaurant = UserDefaults.standard.data(forKey: "restaurant-profile").flatMap { try? JSONDecoder().decode(Restaurant.self, from: $0) }
        AppModel.shared = self
        socket.onConnected = { [weak self] in Task { @MainActor in self?.online = true; await self?.refresh(); await self?.menu.refresh() } }
        socket.onDisconnected = { [weak self] in Task { @MainActor in self?.online = false } }
        socket.onEvent = { [weak self] name in Task { @MainActor in
            if name.hasPrefix("order:") { await self?.refresh() }
            if name.hasPrefix("dish:") { self?.menu.scheduleRefresh() }           // menyu / STOP o'zgardi (admin panel bilan bir xil hodisalar)
        } }
    }

    var isSignedIn: Bool { token != nil }

    /// New orders waiting for a decision, oldest first (queue order, same as Android's OrderQueue).
    var queue: [RestaurantOrder] { orders.filter { $0.status.lowercased() == "pending" }.sorted { $0.createdAt < $1.createdAt } }

    // ---- lifecycle -----------------------------------------------------------------------------------
    func start() async {
        guard isSignedIn else { return }
        startRealtime()
        await refresh()
        await menu.refresh()
    }

    private func startRealtime() {
        guard let token else { return }
        socket.connect(token: token)
        startPolling()
        Task { await AlertCenter.shared.requestAuthorization() }
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .active:
            AlertCenter.shared.cancelBackgroundAlerts()
            Task { await start() }
        case .background:
            // The app is about to be suspended: if orders are still unaccepted, let the system keep nudging.
            if !queue.isEmpty && !silenced { AlertCenter.shared.scheduleBackgroundAlerts(for: queue) }
        default: break
        }
    }

    // ---- auth ----------------------------------------------------------------------------------------
    func signIn(login: String, password: String) async {
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let result = try await api.login(login: login.trimmingCharacters(in: .whitespaces), password: password)
            UserDefaults.standard.set(login.trimmingCharacters(in: .whitespaces), forKey: "last-login")   // login NAME only, never the password
            restaurant = result.restaurant; token = result.token
            UserDefaults.standard.set(Date(), forKey: refreshedKey)
            startRealtime()
            await refresh()
            registerStoredPushToken()
        } catch { self.error = error.localizedDescription }
    }

    func signOut() {
        pollingTask?.cancel(); socket.disconnect(); AlertCenter.shared.stopRinging(); AlertCenter.shared.cancelBackgroundAlerts()
        menu.reset()
        token = nil; restaurant = nil; orders = []; stats = nil; online = false; knownPending = []; silenced = false; minimized = false
    }

    private func handle(_ error: Error) {
        if let api = error as? APIError, api.isUnauthorized { signOut(); self.error = "Sessiya tugadi. Qaytadan kiring."; return }
        self.error = error.localizedDescription
    }

    /// A restaurant that keeps using the app is never asked for its password again: at most one fresh token per day.
    func refreshSessionIfStale() async {
        guard isSignedIn else { return }
        let last = UserDefaults.standard.object(forKey: refreshedKey) as? Date ?? .distantPast
        guard Date().timeIntervalSince(last) > 24 * 3600 else { return }
        do {
            let fresh = try await api.refreshToken()
            token = fresh; UserDefaults.standard.set(Date(), forKey: refreshedKey)
            socket.connect(token: fresh)
        } catch { if let api = error as? APIError, api.isUnauthorized { handle(error) } }
    }

    // ---- data ----------------------------------------------------------------------------------------
    func refresh() async {
        guard isSignedIn else { return }
        await refreshSessionIfStale()
        guard isSignedIn else { return }
        do {
            let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian)
            f.timeZone = TimeZone(identifier: "Asia/Tashkent"); f.dateFormat = "yyyy-MM-dd"
            let today = f.string(from: Date()), yesterday = f.string(from: Date().addingTimeInterval(-86_400))
            async let all = api.orders()
            async let t = api.stats(day: today)
            async let y = try? api.stats(day: yesterday)
            async let h = try? api.history(day: today)
            let (orderList, todayStats, prevStats, history) = try await (all, t, y, h)
            orders = orderList
            stats = TodayStats(today: todayStats, yesterday: prevStats, history: history?.items ?? [])
            error = nil
            reconcileAlert()
        } catch { handle(error) }
    }

    /// Accepting / next step. Delivery orders are closed through confirm-delivered (server checks the time rule).
    func update(_ order: RestaurantOrder, to status: String) async {
        busy = true; defer { busy = false }
        do {
            if status == "delivered" && order.fulfillment == "delivery" { _ = try await api.confirmDelivered(id: order.id) }
            else { _ = try await api.setStatus(id: order.id, status: status) }
            await refresh()
        } catch { handle(error); await refresh() }
    }

    /// Mijoz izohlari: oxirgi [days] kun tarixi sahifalab o'qiladi, faqat bahosi yoki izohi borlari qoladi.
    func loadReviews(days: Int = 120, maxPages: Int = 12) async throws -> [Review] {
        let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian); f.timeZone = TimeZone(identifier: "Asia/Tashkent"); f.dateFormat = "yyyy-MM-dd"
        let to = f.string(from: Date()), from = f.string(from: Date().addingTimeInterval(-Double(days) * 86_400))
        var out: [Review] = [], cursor: String? = nil, pages = 0
        repeat {
            let page = try await api.historyPage(from: from, to: to, cursor: cursor)
            out += page.items.compactMap(\.review)
            cursor = page.nextCursor; pages += 1
            if !page.hasMore { break }
        } while cursor != nil && pages < maxPages
        return out.sorted { $0.at > $1.at }
    }

    // ---- alarm ---------------------------------------------------------------------------------------
    func silence() { silenced = true; AlertCenter.shared.stopRinging() }
    func minimize() { minimized = true }

    /// Called after every reload: a genuinely new pending order re-arms the alarm; an empty queue stops it.
    private func reconcileAlert() {
        let ids = Set(queue.map(\.id))
        if !ids.subtracting(knownPending).isEmpty { silenced = false; minimized = false }
        knownPending = ids
        if ids.isEmpty {
            AlertCenter.shared.stopRinging(); AlertCenter.shared.cancelBackgroundAlerts(); silenced = false
        } else if !silenced {
            AlertCenter.shared.startRinging(volume: Prefs.volumeFraction, sound: Prefs.sound, vibrate: Prefs.vibration)
        }
    }

    // ---- push ----------------------------------------------------------------------------------------
    func registerPushToken(_ hex: String) {
        UserDefaults.standard.set(hex, forKey: "apns-token")
        registerStoredPushToken()
    }
    private func registerStoredPushToken() {
        guard isSignedIn, let hex = UserDefaults.standard.string(forKey: "apns-token") else { return }
        Task { try? await api.registerDevice(token: hex) }     // 404 until the server implements it: harmless
    }
    func pushArrived() { Task { await refresh() } }

    func startPolling() {
        pollingTask?.cancel()
        pollingTask = Task { [weak self] in
            while !Task.isCancelled {
                let sec = Prefs.autoRefresh
                try? await Task.sleep(for: .seconds(sec > 0 ? sec : 15))
                if !Task.isCancelled, sec > 0 { await self?.refresh() }
            }
        }
    }
}
