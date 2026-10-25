import Foundation

enum APIError: LocalizedError {
    case http(Int, String)
    var errorDescription: String? {
        if case let .http(code, message) = self { return message.isEmpty ? "Server xatosi (\(code))" : message }
        return "Tarmoq xatosi"
    }
}

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

    func request<T: Decodable>(_ path: String, method: String = "GET", body: Encodable? = nil) async throws -> T {
        guard let endpoint = URL(string: path, relativeTo: AppConfig.apiBaseURL)?.absoluteURL else {
            throw APIError.http(0, "API manzili noto'g'ri")
        }
        var request = URLRequest(url: endpoint)
        request.httpMethod = method
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(AppConfig.gatewayPassword, forHTTPHeaderField: AppConfig.gatewayHeader)
        if let token { request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
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
        if T.self == EmptyResponse.self { return EmptyResponse() as! T }
        return try decoder.decode(T.self, from: data)
    }
    func login(login: String, password: String) async throws -> LoginResponse {
        try await request("auth/login", method: "POST", body: LoginRequest(login: login, password: password))
    }
    func pending() async throws -> [RestaurantOrder] { try await request("orders/pending") }
    func orders() async throws -> [RestaurantOrder] { try await request("orders?limit=50") }
    func today() async throws -> TodayStats { try await request("stats/today") }
    func setStatus(id: String, status: String) async throws -> RestaurantOrder {
        try await request("orders/\(id)/status", method: "POST", body: StatusRequest(status: status))
    }
}
private struct EmptyResponse: Decodable {}
private struct AnyEncodable: Encodable {
    private let encodeValue: (Encoder) throws -> Void
    init(_ value: Encodable) { encodeValue = { encoder in try value.encode(to: encoder) } }
    func encode(to encoder: Encoder) throws { try encodeValue(encoder) }
}
