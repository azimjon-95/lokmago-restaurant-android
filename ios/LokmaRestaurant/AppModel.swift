import Foundation
import SwiftUI
import Combine

/// Settings keys shared with @AppStorage in SettingsScreen (same names, same defaults).
enum Prefs {
    static var sound: Bool { UserDefaults.standard.object(forKey: "sound") as? Bool ?? true }
    static var vibration: Bool { UserDefaults.standard.object(forKey: "vibration") as? Bool ?? true }
    static var volume: Int { UserDefaults.standard.object(forKey: "volume") as? Int ?? 2 }          // 0 Past, 1 O'rta, 2 Baland
    static var autoRefresh: Int { UserDefaults.standard.object(forKey: "autoRefresh") as? Int ?? 10 } // seconds, 0 = off
    static var keepAwake: Bool { UserDefaults.standard.bool(forKey: "keepAwake") }
    static var volumeFraction: Float { [0.4, 0.7, 1.0][min(max(volume, 0), 2)] }
}

@MainActor final class AppModel: ObservableObject {
    static weak var shared: AppModel?

    let api: APIClient
    let menu: MenuStore
    private let socket = RealtimeSocket()
    private let network = NetworkMonitor()
    private var connectingTask: Task<Void, Never>?
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
    /// Real internet (Wi-Fi / LTE), from NWPathMonitor — NOT the same as "server socket joined".
    @Published private(set) var networkOnline = true
    @Published private(set) var socketJoined = false       // Socket.IO namespace joined
    @Published private(set) var showConnecting = false     // "Serverga ulanmoqda" banner, only if it takes longer than ~2.5 s
    @Published var showPermissions = false                 // first-run permission sheet (also reachable from Profil / Home banner)
    @Published private(set) var notificationsAllowed = true
    var hasInternet: Bool { networkOnline }
    /// Online = server socket joined · Ulanmoqda = internet is there, socket still (re)connecting · Offline = no internet.
    var connection: ConnectionState { !networkOnline ? .offline : (socketJoined ? .online : .connecting) }
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
        socket.onConnected = { [weak self] in Task { @MainActor in self?.setSocket(true); await self?.refresh(); await self?.menu.refresh() } }
        socket.onDisconnected = { [weak self] in Task { @MainActor in self?.setSocket(false) } }
        network.onChange = { [weak self] online in Task { @MainActor in self?.networkChanged(online) } }
        network.start()
        socket.onEvent = { [weak self] name, payload in
            let stopped = payload?["stoppedCount"] as? Int
            Task { @MainActor in
                if name.hasPrefix("order:") { await self?.refresh() }
                if name.hasPrefix("dish:") { self?.menu.menuEvent(stoppedCount: stopped) }   // menyu / STOP o'zgardi (admin panel bilan bir xil hodisalar)
            }
        }
    }

    var isSignedIn: Bool { token != nil }

    /// New orders waiting for a decision, oldest first (queue order, same as Android's OrderQueue).
    var queue: [RestaurantOrder] { orders.filter { $0.status.lowercased() == "pending" }.sorted { $0.createdAt < $1.createdAt } }

    // ---- lifecycle -----------------------------------------------------------------------------------
    func start() async {
        guard isSignedIn else { return }
        startRealtime()
        if !UserDefaults.standard.bool(forKey: "permissions-onboarded") { showPermissions = true }
        await refresh()
        await menu.refresh()
        await refreshPermissionState()
    }

    private func startRealtime() {
        guard let token else { return }
        socket.connect(token: token)
        startPolling()
        Task { await AlertCenter.shared.registerForPushIfAllowed() }      // fresh APNs token on every start
        applyKeepAwake()
    }

    /// The app just came back to the foreground. iOS FROZE it while it was in the background: the socket is dead and
    /// keep-alive HTTP connections are stale, so reconnect NOW (not after a backoff) and open fresh connections.
    private func wake() async {
        guard isSignedIn else { return }
        AlertCenter.shared.cancelBackgroundAlerts()
        socket.reconnectNow()
        startPolling()
        Task { await AlertCenter.shared.registerForPushIfAllowed() }
        applyKeepAwake()
        await api.flushConnections()
        await refresh()
        await menu.refresh()
        await refreshPermissionState()
    }

    private func setSocket(_ joined: Bool) { socketJoined = joined; connectionChanged() }

    private func networkChanged(_ online: Bool) {
        networkOnline = online
        if online, isSignedIn {
            socket.reconnectNow()                       // the old socket died with the old network
            Task { await api.flushConnections(); await refresh(); await menu.reconcile() }
        } else if !online {
            socketJoined = false
        }
        connectionChanged()
    }

    /// Flicker guard: "connecting" is shown as a banner only if it lasts, the small header label changes at once.
    private func connectionChanged() {
        connectingTask?.cancel(); showConnecting = false
        guard connection == .connecting else { return }
        connectingTask = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 2_500_000_000)
            guard !Task.isCancelled, let self, self.connection == .connecting else { return }
            self.showConnecting = true
        }
    }

    // ---- permissions ---------------------------------------------------------------------------------
    func refreshPermissionState() async {
        let rows = await AlertCenter.shared.readiness()
        notificationsAllowed = rows.first { $0.id == "auth" }?.ok ?? true
    }

    func permissionsFinished() {
        UserDefaults.standard.set(true, forKey: "permissions-onboarded")
        showPermissions = false
        Task { await refreshPermissionState() }
    }

    func scenePhaseChanged(_ phase: ScenePhase) {
        switch phase {
        case .active:
            Task { await wake() }
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
            showPermissions = !UserDefaults.standard.bool(forKey: "permissions-onboarded")
            await refresh()
            registerStoredPushToken()
            await refreshPermissionState()
        } catch { self.error = error.localizedDescription }
    }

    /// "Chiqish" tugmasi: avval push tokenini serverdan o'chiradi (JWT hali bor), keyin sessiyani yopadi.
    func logout() async {
        signOut()          // signOut() removes this iPhone's push token from the server (while the JWT is still valid)
    }

    func signOut() {
        if let auth = token, let hex = UserDefaults.standard.string(forKey: "apns-token") {
            Task { try? await api.unregisterDevice(token: hex, bearer: auth) }
        }
        pollingTask?.cancel(); socket.disconnect(); AlertCenter.shared.stopRinging(); AlertCenter.shared.cancelBackgroundAlerts()
        menu.reset()
        token = nil; restaurant = nil; orders = []; stats = nil; socketJoined = false; knownPending = []; silenced = false; minimized = false; showConnecting = false
    }

    private func handle(_ error: Error) {
        if let api = error as? APIError, api.isUnauthorized { signOut(); self.error = "Sessiya tugadi. Qaytadan kiring."; return }
        // A broken connection is not an "Xatolik" pop-up: the connection label/banner already says so and everything retries by itself.
        if error is URLError { return }
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
            async let p = try? api.orders(status: "pending")
            let (orderList, todayStats, prevStats, history, pending) = try await (all, t, y, h, p)
            // The unfiltered list is only the latest 80: merge the full pending list so the badge can never undercount.
            var byId = Dictionary(orderList.map { ($0.id, $0) }, uniquingKeysWith: { first, _ in first })
            for o in pending ?? [] { byId[o.id] = o }
            orders = byId.values.sorted { $0.createdAt > $1.createdAt }
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
        UserDefaults.standard.removeObject(forKey: "apns-error")
        registerStoredPushToken()
    }
    private func registerStoredPushToken() {
        guard isSignedIn, let hex = UserDefaults.standard.string(forKey: "apns-token") else { return }
        Task {
            do { try await api.registerDevice(token: hex); UserDefaults.standard.set("ok", forKey: "apns-server") }
            catch { UserDefaults.standard.set(error.localizedDescription, forKey: "apns-server") }   // shown by "Push tekshiruvi"
        }
    }
    func pushRegistrationFailed(_ reason: String) { UserDefaults.standard.set(reason, forKey: "apns-error") }

    /// "Kassa rejimi": while the app is open the screen does not lock (a restaurant phone on the counter, on a charger).
    func applyKeepAwake() { UIApplication.shared.isIdleTimerDisabled = Prefs.keepAwake }

    func pushArrived() { Task { await refresh() } }

    /// Lock-screen action; orderFlow on the server validates that it is still pending.
    func handleOrderNotificationAction(orderId: String, status: String) {
        guard status == "accepted" || status == "cancelled" else { return }
        Task {
            do { _ = try await api.setStatus(id: orderId, status: status); await refresh() }
            catch { handle(error); await refresh() }
        }
    }

    func startPolling() {
        pollingTask?.cancel()
        pollingTask = Task { [weak self] in
            while !Task.isCancelled {
                let sec = Prefs.autoRefresh
                try? await Task.sleep(for: .seconds(sec > 0 ? sec : 15))
                if !Task.isCancelled, sec > 0 { await self?.refresh(); await self?.menu.reconcile() }
            }
        }
    }
}

/// Online = server socket joined · connecting = internet is there, socket (re)connecting · offline = no internet.
enum ConnectionState { case online, connecting, offline }
