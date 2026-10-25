import Foundation

/// lakmago-server — admin panel ishlatadigan o'sha API (`/api/`). Gateway/BFF yo'q.
/// Boshqa server: Info.plist `API_BASE_URL` (xcconfig orqali), masalan https://apilokma.poppolizol.uz/api/
enum AppConfig {
    static let apiBaseURL: URL = {
        if let raw = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
           !raw.isEmpty, !raw.contains("$("), let url = URL(string: raw.hasSuffix("/") ? raw : raw + "/") {
            return url
        }
        return URL(string: "https://apilokma.poppolizol.uz/api/")!
    }()
    /// wss://<same host>/socket.io/?EIO=4&transport=websocket  (admin panel uses the same socket)
    static var socketURL: URL {
        var c = URLComponents(url: apiBaseURL, resolvingAgainstBaseURL: false)!
        c.scheme = (c.scheme == "http") ? "ws" : "wss"
        c.path = "/socket.io/"
        c.queryItems = [URLQueryItem(name: "EIO", value: "4"), URLQueryItem(name: "transport", value: "websocket")]
        return c.url!
    }
}
