import SwiftUI
import CoreLocation

/// Kuryer tomonining BITTA modeli: kirish, smena, takliflar (har 5 s), faol buyurtma, tarix, hisobot, joylashuv, push tokeni.
/// Restoran sessiyasi (AppModel) bilan ARALASHMAYDI: alohida token (Keychain), alohida so'rovlar.
@MainActor final class CourierModel: ObservableObject {
    static weak var shared: CourierModel?
    private let api = APIClient()
    @Published private(set) var token: String? = KeychainStore.readCourier()
    @Published var name: String = UserDefaults.standard.string(forKey: "courier-name") ?? ""
    @Published var login: String = UserDefaults.standard.string(forKey: "courier-login") ?? ""
    @Published private(set) var loaded = false
    @Published private(set) var onShift = false
    @Published private(set) var shift: CShift?
    @Published private(set) var offers: [COffer] = []
    @Published private(set) var active: CActive?
    @Published private(set) var history: [CHistory] = []
    @Published private(set) var stats: CStats?
    @Published var busy = false
    @Published var message: String?
    @Published private(set) var locationDenied = false
    private var poll: Task<Void, Never>?

    var isSignedIn: Bool { token != nil }

    init() {
        CourierModel.shared = self
        CourierLocation.shared.onAuthChange = { [weak self] s in self?.locationDenied = (s == .denied || s == .restricted) }
    }

    // ---- helpers ----
    private func send<T: Decodable>(_ path: String, method: String = "GET", body: Encodable? = nil) async throws -> T {
        let data = try await api.requestRaw(path, method: method, body: body, idempotent: method == "GET", bearer: token)
        return try JSONDecoder().decode(T.self, from: data)
    }
    private func text(_ error: Error) -> String {
        if let e = error as? APIError, case .http(let code, let msg) = e {
            if code == 401 && token != nil { signOutLocally() }
            return msg.isEmpty ? "Xatolik (\(code))" : msg
        }
        return error is URLError ? "Internet yo'q. Ulanishni tekshiring." : error.localizedDescription
    }
    private func signOutLocally() { KeychainStore.saveCourier(nil); token = nil; poll?.cancel(); CourierLocation.shared.stop(); onShift = false; offers = []; active = nil }

    // ---- kirish / chiqish ----
    func signIn(login: String, password: String) async -> String? {
        guard !login.trimmingCharacters(in: .whitespaces).isEmpty, !password.isEmpty else { return "Login va parolni kiriting" }
        do {
            let r: CLoginRes = try await api.request("courier-app/login", method: "POST", body: CLoginReq(login: login.trimmingCharacters(in: .whitespaces), password: password))
            KeychainStore.saveCourier(r.token); token = r.token
            name = r.courier?.name ?? "Kuryer"; self.login = r.courier?.login ?? login
            UserDefaults.standard.set(name, forKey: "courier-name"); UserDefaults.standard.set(self.login, forKey: "courier-login")
            _ = await AlertCenter.shared.requestAuthorization()           // bildirishnoma ruxsati (taklif push'i uchun)
            if let hex = UserDefaults.standard.string(forKey: "apns-token") { registerPush(hex) }
            return nil
        } catch {
            if let e = error as? APIError, case .http(let code, let msg) = e { return code == 401 ? "Login yoki parol xato" : (msg.isEmpty ? "Xatolik (\(code))" : msg) }
            return error is URLError ? "Internet yo'q. Ulanishni tekshiring." : error.localizedDescription
        }
    }

    /// Chiqish: smena yopiq bo'lishi shart (UI tekshiradi). Push tokeni serverdan o'chiriladi (token hali amal qilayotganda).
    func signOut() async {
        if let hex = UserDefaults.standard.string(forKey: "apns-token"), let t = token { _ = try? await api.requestRaw("courier-app/devices", method: "DELETE", body: CDeviceDel(token: hex), bearer: t) }
        CourierLocation.shared.stop(); signOutLocally(); name = ""; message = nil
    }

    // ---- yangilash (har 5 s: takliflar va faol buyurtma) ----
    func start() {
        poll?.cancel()
        poll = Task { [weak self] in while !Task.isCancelled { await self?.refresh(); try? await Task.sleep(nanoseconds: 5_000_000_000) } }
    }
    func stopPolling() { poll?.cancel() }

    func refresh() async {
        guard token != nil else { return }
        do {
            let s: CShiftState = try await send("courier-app/shift")
            onShift = s.onShift ?? false; shift = s.shift
            if s.activeAssignmentId != nil { let a: CActiveRes = try await send("courier-app/active"); active = a.active } else { active = nil }
            offers = (onShift && active == nil) ? try await send("courier-app/offers") : []
            loaded = true
            if onShift { startLocation() } else { CourierLocation.shared.stop() }          // joylashuv SERVER holatiga ergashadi (ilova o'ldirilgan bo'lsa ham)
        } catch { if let e = error as? APIError, case .http(401, _) = e { signOutLocally() }; loaded = true }
    }

    private func act(_ block: () async throws -> Void) async {
        busy = true; message = nil
        do { try await block() } catch { message = text(error) }
        busy = false
    }

    func beginShift() async {
        CourierLocation.shared.requestPermission()
        await act {
            let _: CShiftState = try await send("courier-app/shift/start", method: "POST")
            await refresh()
        }
    }
    func endShift() async { await act { let _: CShiftState = try await send("courier-app/shift/end", method: "POST"); CourierLocation.shared.stop(); await refresh() } }
    func accept(_ id: String) async { await act { let r: CAcceptRes = try await send("courier-app/offers/\(id)/accept", method: "POST"); active = r.active; await refresh() } }
    func deliver() async { await act { let _: OkResponse = try await send("courier-app/active/deliver", method: "POST"); message = "Buyurtma topshirildi ✓"; await refresh(); await loadStats() } }
    func loadHistory() async { if let h: [CHistory] = try? await send("courier-app/history?limit=50") { history = h } }
    func loadStats() async { if let s: CStats = try? await send("courier-app/stats") { stats = s } }
    func testPush() async { await act { let r: CSent = try await send("courier-app/devices/test", method: "POST"); message = (r.sent ?? 0) > 0 ? "Test yuborildi: bildirishnoma kelishi kerak" : "Telefon serverda ro'yxatda emas — ilovani qayta oching" } }

    // ---- joylashuv: har 5 s ----
    private func startLocation() {
        CourierLocation.shared.start { [weak self] loc in
            guard let self else { return false }
            let body = CLocationReq(lat: loc.coordinate.latitude, lng: loc.coordinate.longitude, accuracy: loc.horizontalAccuracy >= 0 ? loc.horizontalAccuracy : nil,
                                    speed: loc.speed >= 0 ? loc.speed : nil, heading: loc.course >= 0 ? loc.course : nil)
            do { _ = try await self.api.requestRaw("courier-app/location", method: "POST", body: body, bearer: self.token); return true }
            catch let e as APIError { if case .http(let code, _) = e, code == 401 || code == 409 { return false }; return true }   // 401/409: smena yopiq yoki token yaroqsiz → to'xtaydi
            catch { return true }                                                                                                    // tarmoq yo'q: keyingi nuqta bilan qayta uriniladi
        }
    }

    // ---- push tokeni (AppModel.registerPushToken dan ham chaqiriladi) ----
    func registerPush(_ hex: String) {
        guard let t = token else { return }
        #if DEBUG
        let env = "development"
        #else
        let env = "production"
        #endif
        Task { _ = try? await api.requestRaw("courier-app/devices", method: "POST", body: CDeviceReq(token: hex, platform: "ios", environment: env, appVersion: Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String), idempotent: true, bearer: t) }
    }
}
struct OkResponse: Decodable { let ok: Bool? }
