import Foundation

struct LoginRequest: Encodable { let login: String; let password: String }
struct LoginResponse: Decodable { let token: String; let restaurant: Restaurant }
struct Restaurant: Codable { let id: String; let name: String; let cuisine: String? }
struct OrderItem: Decodable, Identifiable {
    var id: String { "\(name)-\(quantity)" }
    let name: String; let quantity: Int; let price: Int64; let imageUrl: String?
}
struct RestaurantOrder: Decodable, Identifiable {
    let id: String; let number: String; var status: String; let createdAt: Int64
    let fulfillment: String; let paymentMethod: String; let itemsCount: Int
    let subtotal: Int64; let deliveryFee: Int64; let total: Int64
    let customerName: String?; let customerPhone: String?; let address: String?
    let lat: Double?; let lng: Double?; let items: [OrderItem]
    var statusTitle: String {
        ["pending":"Yangi", "accepted":"Qabul qilingan", "preparing":"Tayyorlanmoqda",
         "ready":"Tayyor", "delivering":"Yetkazilmoqda", "delivered":"Yetkazildi",
         "cancelled":"Bekor qilingan"][status.lowercased()] ?? status
    }
    var nextAction: (status: String, title: String)? {
        switch status.lowercased() {
        case "pending": ("accepted", "Qabul qilish")
        case "accepted": ("preparing", "Tayyorlashni boshlash")
        case "preparing": ("ready", "Tayyor deb belgilash")
        case "ready" where fulfillment == "delivery": ("delivering", "Yetkazishga berish")
        case "ready", "delivering": ("delivered", fulfillment == "delivery" ? "Yetkazildi" : "Mijozga topshirildi")
        default: nil
        }
    }
}
struct TodayStats: Decodable {
    let ordersCount: Int; let ordersDelta: Int; let revenue: Int64; let revenueDeltaPct: Int
    let waiting: Int; let cashCount: Int; let cardCount: Int
    let cashRevenue: Int64; let cardRevenue: Int64; let hourly: [Int64]
    enum CodingKeys: String, CodingKey {
        case ordersCount, ordersDelta, revenue, revenueDeltaPct, waiting, cashCount, cardCount, cashRevenue, cardRevenue, hourly
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        ordersCount = try c.decodeIfPresent(Int.self, forKey: .ordersCount) ?? 0
        ordersDelta = try c.decodeIfPresent(Int.self, forKey: .ordersDelta) ?? 0
        revenue = try c.decodeIfPresent(Int64.self, forKey: .revenue) ?? 0
        revenueDeltaPct = try c.decodeIfPresent(Int.self, forKey: .revenueDeltaPct) ?? 0
        waiting = try c.decodeIfPresent(Int.self, forKey: .waiting) ?? 0
        cashCount = try c.decodeIfPresent(Int.self, forKey: .cashCount) ?? 0
        cardCount = try c.decodeIfPresent(Int.self, forKey: .cardCount) ?? 0
        cashRevenue = try c.decodeIfPresent(Int64.self, forKey: .cashRevenue) ?? 0
        cardRevenue = try c.decodeIfPresent(Int64.self, forKey: .cardRevenue) ?? 0
        hourly = try c.decodeIfPresent([Int64].self, forKey: .hourly) ?? []
    }
}
struct StatusRequest: Encodable { let status: String }
