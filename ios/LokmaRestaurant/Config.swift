import Foundation

enum AppConfig {
    static let apiBaseURL = URL(string: "https://restoran-api.lokma.uz/restaurant/v1/")!
    static let gatewayHeader = Bundle.main.object(forInfoDictionaryKey: "API_GATEWAY_HEADER") as? String ?? "x-gateway-password"
    static let gatewayPassword = Bundle.main.object(forInfoDictionaryKey: "API_GATEWAY_PASSWORD") as? String ?? ""
    static let pollInterval: Duration = .seconds(15)
}
