import Foundation

enum APIError: LocalizedError {
    case http(Int, String)
    case notRestaurant
    var errorDescription: String? {
        switch self {
        case let .http(code, message): return message.isEmpty ? "Server xatosi (\(code))" : message
        case .notRestaurant: return "Bu ilova faqat restoran akkaunti uchun. Admin panelga kiradigan restoran login va parolini kiriting."
        }
    }
    var isUnauthorized: Bool { if case .http(401, _) = self { return true }; return false }
}

/// lakmago-server bilan TO'G'RIDAN-TO'G'RI — admin panelning restoran bo'limi bilan bir xil akkaunt, JWT va endpointlar.
/// restaurantId hech qachon yuborilmaydi: server uni tokendan oladi.
final class APIClient {
    var token: String?
    private let session: URLSession = {
        let config = URLSessionConfiguration.default
        config.waitsForConnectivity = true
        config.timeoutIntervalForRequest = 20
        return URLSession(configuration: config)
    }()
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    func request<T: Decodable>(_ path: String, method: String = "GET", body: Encodable? = nil, bearer: String? = nil) async throws -> T {
        guard let endpoint = URL(string: path, relativeTo: AppConfig.apiBaseURL)?.absoluteURL else {
            throw APIError.http(0, "API manzili noto'g'ri")
        }
        var request = URLRequest(url: endpoint)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        if let auth = bearer ?? token { request.setValue("Bearer \(auth)", forHTTPHeaderField: "Authorization") }
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try encoder.encode(AnyEncodable(body))
        }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw APIError.http(0, "Server javobi noto'g'ri") }
        guard (200..<300).contains(http.statusCode) else {
            let payload = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
            throw APIError.http(http.statusCode, (payload?["error"] as? String) ?? "")
        }
        return try decoder.decode(T.self, from: data)
    }

    /// Same as `request`, for endpoints whose reply body we do not need (it may be empty).
    @discardableResult
    func requestRaw(_ path: String, method: String, body: Encodable? = nil, json: [String: Any]? = nil) async throws -> Data {
        guard let endpoint = URL(string: path, relativeTo: AppConfig.apiBaseURL)?.absoluteURL else { throw APIError.http(0, "API manzili noto'g'ri") }
        var request = URLRequest(url: endpoint)
        request.httpMethod = method
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body { request.setValue("application/json", forHTTPHeaderField: "Content-Type"); request.httpBody = try encoder.encode(AnyEncodable(body)) }
        if let json { request.setValue("application/json", forHTTPHeaderField: "Content-Type"); request.httpBody = try JSONSerialization.data(withJSONObject: json) }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            // The server explains validation errors in its own words ("Narxni kiriting") — show them.
            let message = ((try? JSONSerialization.jsonObject(with: data)) as? [String: Any])?["error"] as? String ?? ""
            throw APIError.http((response as? HTTPURLResponse)?.statusCode ?? 0, message)
        }
        return data
    }

    /// Admin panel bilan bir xil kirish. Faqat restoran akkaunti; restoran nomi /panel/me dan.
    func login(login: String, password: String) async throws -> (token: String, restaurant: Restaurant) {
        let r: LoginResponse = try await request("auth/login", method: "POST", body: LoginRequest(login: login, password: password))
        guard r.user.role == "restaurant", let rid = r.user.restaurantId, !rid.isEmpty else { throw APIError.notRestaurant }
        let restaurant: Restaurant = try await request("panel/me", bearer: r.token)
        return (r.token, restaurant)
    }
    /// Sliding session: yaroqli token evaziga yangi 30 kunlik token.
    func refreshToken() async throws -> String { (try await request("panel/session/refresh", method: "POST") as RefreshResponse).token }

    /// status = "pending" → ALL new orders (the unfiltered list is only the latest 80, a long-waiting order could fall out of it).
    func orders(status: String? = nil) async throws -> [RestaurantOrder] {
        try await request(status == nil ? "panel/orders" : "panel/orders?status=\(status!)")
    }
    /// Badge uchun arzon so'rov: faqat son.
    func stoppedCount() async throws -> Int { (try await request("panel/dishes/stopped/count") as CountResponse).count }
    func stats(day: String) async throws -> StatsResponse { try await request("panel/stats?from=\(day)&to=\(day)") }
    /// Paged history (cursor) for the reviews page.
    func historyPage(from: String, to: String, cursor: String?) async throws -> HistoryPage {
        var path = "panel/orders/history?from=\(from)&to=\(to)&limit=200"
        if let cursor, let enc = cursor.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) { path += "&cursor=\(enc)" }
        return try await request(path)
    }

    // ---- Menyu (admin panel bilan bir xil endpointlar) ----
    func dishes() async throws -> [Dish] { try await request("panel/dishes") }
    func saveDish(id: String?, json: [String: Any]) async throws -> Dish {
        let data = try await requestRaw(id == nil ? "panel/dishes" : "panel/dishes/\(id!)", method: id == nil ? "POST" : "PATCH", json: json)
        return try JSONDecoder().decode(Dish.self, from: data)
    }
    /// stop = true → taom STOP-listga tushadi (isAvailable = false).
    func stopDish(id: String, stop: Bool) async throws -> Dish {
        let data = try await requestRaw("panel/dishes/\(id)/stop", method: "PATCH", json: ["stop": stop])
        return try JSONDecoder().decode(Dish.self, from: data)
    }
    func deleteDish(id: String) async throws { _ = try await requestRaw("panel/dishes/\(id)", method: "DELETE") }
    func uploadSignature(folder: String = "dishes") async throws -> UploadSignature { try await request("upload/signature?folder=\(folder)") }

    func history(day: String) async throws -> HistoryPage { try await request("panel/orders/history?from=\(day)&to=\(day)&limit=200") }
    /// Push token for background alerts. The server endpoint is described in docs/SERVER-PUSH.md; until it exists a 404 is expected and ignored.
    /// Chiqishda: bu iPhone endi shu restoranning buyurtmalarini olmaydi.
    func unregisterDevice(token: String) async throws { _ = try await requestRaw("panel/devices", method: "DELETE", json: ["token": token]) }
    func registerDevice(token: String) async throws {
        _ = try await requestRaw("panel/devices", method: "POST", body: DeviceRequest(token: token, platform: "ios"))
    }
    func setStatus(id: String, status: String) async throws -> RestaurantOrder {
        try await request("panel/orders/\(id)/status", method: "PATCH", body: StatusRequest(status: status))
    }
    /// Yetkazib berish buyurtmasini restoran yakunlaydi (server vaqt shartini tekshiradi).
    func confirmDelivered(id: String) async throws -> RestaurantOrder {
        try await request("panel/orders/\(id)/confirm-delivered", method: "POST")
    }
}
private struct AnyEncodable: Encodable {
    private let encodeValue: (Encoder) throws -> Void
    init(_ value: Encodable) { encodeValue = { encoder in try value.encode(to: encoder) } }
    func encode(to encoder: Encoder) throws { try encodeValue(encoder) }
}
