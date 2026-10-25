import Foundation
import SwiftUI
import Combine

@MainActor final class AppModel: ObservableObject {
    let api = APIClient()
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
    private var pollingTask: Task<Void, Never>?
    private let refreshedKey = "token-refreshed-at"

    init() {
        token = KeychainStore.read()
        api.token = token
        let savedProfile = UserDefaults.standard.data(forKey: "restaurant-profile")
            .flatMap { try? JSONDecoder().decode(Restaurant.self, from: $0) }
        restaurant = savedProfile
    }
    var isSignedIn: Bool { token != nil }

    /// Admin panelning restoran akkaunti (login + parol) bilan kirish.
    func signIn(login: String, password: String) async {
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let result = try await api.login(login: login.trimmingCharacters(in: .whitespaces), password: password)
            restaurant = result.restaurant; token = result.token
            UserDefaults.standard.set(Date(), forKey: refreshedKey)
            await refresh()
            startPolling()
        } catch { self.error = error.localizedDescription }
    }
    func signOut() { pollingTask?.cancel(); token = nil; restaurant = nil; orders = []; stats = nil }

    /// Sessiya tugagan (parol o'zgargan, akkaunt bloklangan, muddat o'tgan) — login ekraniga.
    private func handle(_ error: Error) {
        if let api = error as? APIError, api.isUnauthorized { signOut(); self.error = "Sessiya tugadi. Qaytadan kiring."; return }
        self.error = error.localizedDescription
    }

    /// Ishlatilayotgan ilova chiqib ketmasin: kuniga ko'pi bilan bir marta yangi token.
    func refreshSessionIfStale() async {
        guard isSignedIn else { return }
        let last = UserDefaults.standard.object(forKey: refreshedKey) as? Date ?? .distantPast
        guard Date().timeIntervalSince(last) > 24 * 3600 else { return }
        do { token = try await api.refreshToken(); UserDefaults.standard.set(Date(), forKey: refreshedKey) }
        catch { if let api = error as? APIError, api.isUnauthorized { handle(error) } }   // tarmoq xatosi — keyingi safar
    }

    func refresh() async {
        guard isSignedIn else { return }
        await refreshSessionIfStale()
        guard isSignedIn else { return }
        do {
            let f = DateFormatter(); f.calendar = Calendar(identifier: .gregorian)
            f.timeZone = TimeZone(identifier: "Asia/Tashkent"); f.dateFormat = "yyyy-MM-dd"
            async let all = api.orders(); async let daily = api.stats(day: f.string(from: Date()))
            orders = try await all; stats = try await daily; error = nil
        } catch { handle(error) }
    }

    /// Yetkazib berish buyurtmasini restoran "Yetkazildi" qila olishi uchun confirm-delivered (server vaqt shartini tekshiradi);
    /// qolgan holatlar — admin panel bilan bir xil PATCH status.
    func update(_ order: RestaurantOrder, to status: String) async {
        do {
            if status == "delivered" && order.fulfillment == "delivery" { _ = try await api.confirmDelivered(id: order.id) }
            else { _ = try await api.setStatus(id: order.id, status: status) }
            await refresh()
        } catch { handle(error) }
    }
    func startPolling() {
        pollingTask?.cancel()
        pollingTask = Task {
            while !Task.isCancelled {
                try? await Task.sleep(for: AppConfig.pollInterval)
                if !Task.isCancelled { await refresh() }
            }
        }
    }
}
