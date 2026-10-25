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
    static let pollInterval: Duration = .seconds(15)
}
