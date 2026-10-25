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

    /// Admin panel bilan bir xil kirish. Faqat restoran akkaunti; restoran nomi /panel/me dan.
    func login(login: String, password: String) async throws -> (token: String, restaurant: Restaurant) {
        let r: LoginResponse = try await request("auth/login", method: "POST", body: LoginRequest(login: login, password: password))
        guard r.user.role == "restaurant", let rid = r.user.restaurantId, !rid.isEmpty else { throw APIError.notRestaurant }
        let restaurant: Restaurant = try await request("panel/me", bearer: r.token)
        return (r.token, restaurant)
    }
    /// Sliding session: yaroqli token evaziga yangi 30 kunlik token.
    func refreshToken() async throws -> String { (try await request("panel/session/refresh", method: "POST") as RefreshResponse).token }

    func orders() async throws -> [RestaurantOrder] { try await request("panel/orders") }
    func stats(day: String) async throws -> TodayStats { try await request("panel/stats?from=\(day)&to=\(day)") }
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
