import Foundation

/// The menu, live. ONE list shared by the Menyu tab, the Stop List tab, the tab badge and the order screens (dish photos).
/// It reloads itself when the server says the menu changed (`dish:stop` / `dish:update`, the same events the admin panel
/// uses), after a socket reconnect and after login. Own edits update the list immediately and are then re-synced.
@MainActor final class MenuStore: ObservableObject {
    @Published private(set) var dishes: [Dish] = []
    @Published private(set) var loaded = false
    @Published var error: String?
    @Published var toast: String?
    private let api: APIClient
    private var pending: Task<Void, Never>?

    init(api: APIClient) { self.api = api }

    var stoppedCount: Int { dishes.filter { !$0.available }.count }
    func imageURL(forDish id: String?) -> String? { id.flatMap { id in dishes.first { $0.id == id }?.imageUrl } }

    func refresh() async {
        do { dishes = try await api.dishes(); loaded = true; error = nil }
        catch { self.error = error.localizedDescription }
    }

    /// Several socket events in a row → one reload.
    func scheduleRefresh() {
        pending?.cancel()
        pending = Task { [weak self] in
            try? await Task.sleep(nanoseconds: 800_000_000)
            if !Task.isCancelled { await self?.refresh() }
        }
    }

    func reset() { pending?.cancel(); dishes = []; loaded = false; error = nil }

    private func put(_ dish: Dish) {
        if let i = dishes.firstIndex(where: { $0.id == dish.id }) { dishes[i] = dish } else { dishes.append(dish) }
    }

    /// admin panel: `toggleStop(d._id, d.isAvailable)` — hozir sotuvda bo'lsa STOPga tushadi, aks holda qaytadi.
    func toggleStop(_ dish: Dish) async {
        var optimistic = dish; optimistic.available.toggle(); put(optimistic)
        do { put(try await api.stopDish(id: dish.id, stop: dish.available)) }
        catch { put(dish); flash(error.localizedDescription) }
    }

    func delete(_ dish: Dish) async {
        do { try await api.deleteDish(id: dish.id); dishes.removeAll { $0.id == dish.id } }
        catch { flash(error.localizedDescription) }
    }

    func save(id: String?, json: [String: Any]) async throws {
        put(try await api.saveDish(id: id, json: json))
    }

    private func flash(_ text: String) {
        toast = text
        Task { try? await Task.sleep(nanoseconds: 3_500_000_000); if toast == text { toast = nil } }
    }
}
