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

    init() {
        token = KeychainStore.read()
        api.token = token
        let savedProfile = UserDefaults.standard.data(forKey: "restaurant-profile")
            .flatMap { try? JSONDecoder().decode(Restaurant.self, from: $0) }
        restaurant = savedProfile
    }
    var isSignedIn: Bool { token != nil }
    func signIn(login: String, password: String) async {
        isLoading = true; error = nil
        defer { isLoading = false }
        do {
            let result = try await api.login(login: login, password: password)
            restaurant = result.restaurant; token = result.token
            await refresh()
            startPolling()
        } catch { self.error = error.localizedDescription }
    }
    func signOut() { pollingTask?.cancel(); token = nil; restaurant = nil; orders = []; stats = nil }
    func refresh() async {
        guard isSignedIn else { return }
        do { async let all = api.orders(); async let daily = api.today(); orders = try await all; stats = try await daily; error = nil }
        catch { self.error = error.localizedDescription }
    }
    func update(_ order: RestaurantOrder, to status: String) async {
        do {
            _ = try await api.setStatus(id: order.id, status: status)
            await refresh()
        } catch { self.error = error.localizedDescription }
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
