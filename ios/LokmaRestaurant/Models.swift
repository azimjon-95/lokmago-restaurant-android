import Foundation

// lakmago-server javoblari (admin panel bilan bir xil) — UI ishlatadigan maydonlarga o'giriladi.

struct LoginRequest: Encodable { let login: String; let password: String }
struct LoginUser: Decodable { let role: String?; let restaurantId: String? }
struct LoginResponse: Decodable { let token: String; let user: LoginUser }
struct RefreshResponse: Decodable { let token: String }

/// GET /api/panel/me
struct Restaurant: Codable {
    let id: String; let name: String; let cuisine: String?
    enum CodingKeys: String, CodingKey { case id = "_id", name, cuisine }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = (try? c.decode(String.self, forKey: .id)) ?? ""
        name = (try? c.decode(String.self, forKey: .name)) ?? "Restoran"
        cuisine = try? c.decodeIfPresent(String.self, forKey: .cuisine)   // massiv bo'lsa — e'tiborsiz
    }
}

private struct SelectedOption: Decodable { let name: String? }

struct OrderItem: Decodable, Identifiable {
    var id: String { "\(name)-\(quantity)" }
    let name: String; let quantity: Int; let price: Int64; let imageUrl: String?
    enum CodingKeys: String, CodingKey { case name, quantity, unitPrice, selectedOptions }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let base = (try? c.decode(String.self, forKey: .name)) ?? ""
        let opts = ((try? c.decode([SelectedOption].self, forKey: .selectedOptions)) ?? []).compactMap { $0.name }.filter { !$0.isEmpty }
        // Oshxona aynan qaysi hajmni tayyorlashini bilsin: "Lorus Set (6 Kishilik Set)"
        name = opts.isEmpty ? base : "\(base) (\(opts.joined(separator: ", ")))"
        quantity = (try? c.decode(Int.self, forKey: .quantity)) ?? 1
        price = Int64(((try? c.decode(Double.self, forKey: .unitPrice)) ?? 0).rounded())
        imageUrl = nil
    }
}

private struct Customer: Decodable { let name: String?; let phone: String? }

/// /api/panel/orders... (services/panelOrderView.js → toPanelOrder)
struct RestaurantOrder: Decodable, Identifiable {
    let id: String; let number: String; var status: String; let createdAt: Int64
    let fulfillment: String; let paymentMethod: String; let itemsCount: Int
    let subtotal: Int64; let deliveryFee: Int64; let total: Int64
    let customerName: String?; let customerPhone: String?; let address: String?
    let lat: Double?; let lng: Double?; let items: [OrderItem]

    enum CodingKeys: String, CodingKey {
        case id = "_id", dailyNumber, status, createdAt, fulfillment, paymentMethod, subtotal, deliveryFee, total
        case customer, phone, address, addressLat, addressLng, items
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        let daily = try? c.decodeIfPresent(Int.self, forKey: .dailyNumber)
        number = daily.map(String.init) ?? String(id.suffix(4)).uppercased()      // admin panel va bot bilan bir xil
        status = (try? c.decode(String.self, forKey: .status)) ?? "unknown"
        let iso = (try? c.decode(String.self, forKey: .createdAt)) ?? ""
        let f = ISO8601DateFormatter(); f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let date = f.date(from: iso) ?? ISO8601DateFormatter().date(from: iso) ?? Date()
        createdAt = Int64(date.timeIntervalSince1970 * 1000)
        fulfillment = (try? c.decode(String.self, forKey: .fulfillment)) ?? "delivery"
        paymentMethod = (try? c.decode(String.self, forKey: .paymentMethod)) ?? "cash"
        func money(_ k: CodingKeys) -> Int64 { Int64(((try? c.decode(Double.self, forKey: k)) ?? 0).rounded()) }
        subtotal = money(.subtotal); deliveryFee = money(.deliveryFee); total = money(.total)
        let customer = try? c.decodeIfPresent(Customer.self, forKey: .customer)
        customerName = customer?.name.flatMap { $0.isEmpty ? nil : $0 }
        let phone = (try? c.decodeIfPresent(String.self, forKey: .phone)) ?? nil
        customerPhone = [phone, customer?.phone].compactMap { $0 }.first { !$0.isEmpty }
        address = ((try? c.decodeIfPresent(String.self, forKey: .address)) ?? nil).flatMap { $0.isEmpty ? nil : $0 }
        lat = (try? c.decodeIfPresent(Double.self, forKey: .addressLat)) ?? nil
        lng = (try? c.decodeIfPresent(Double.self, forKey: .addressLng)) ?? nil
        items = (try? c.decode([OrderItem].self, forKey: .items)) ?? []
        itemsCount = items.reduce(0) { $0 + max($1.quantity, 0) }
    }
    var paymentTitle: String { paymentMethod.lowercased() == "cash" ? "Naqd" : "Karta" }
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

/// GET /api/panel/stats?from=&to= (controllers/restaurantApp.js). Every field optional: a missing one must never break the screen.
struct StatsResponse: Decodable {
    struct Created: Decodable { let count: Int?; let byStatus: [String: Int]? }
    struct MoneyCount: Decodable { let count: Int?; let total: Double? }
    struct ByPayment: Decodable { let cash: MoneyCount?; let card: MoneyCount? }
    struct Delivered: Decodable { let count: Int?; let total: Double?; let byPayment: ByPayment? }
    let created: Created?; let delivered: Delivered?
}

/// What the Home and Report screens show (mirrors Android's DayStats).
struct TodayStats {
    var ordersCount = 0, ordersDelta = 0, waiting = 0
    var revenue: Int64 = 0, revenueDeltaPct = 0
    var cashCount = 0, cardCount = 0
    var cashRevenue: Int64 = 0, cardRevenue: Int64 = 0
    var hourly: [Int64] = Array(repeating: 0, count: 24)

    init() {}
    init(today t: StatsResponse, yesterday y: StatsResponse?, history: [RestaurantOrder]) {
        ordersCount = t.created?.count ?? 0
        ordersDelta = y.map { ordersCount - ($0.created?.count ?? 0) } ?? 0
        waiting = t.created?.byStatus?["pending"] ?? 0
        revenue = Int64((t.delivered?.total ?? 0).rounded())
        let prev = Int64((y?.delivered?.total ?? 0).rounded())
        revenueDeltaPct = prev > 0 ? Int((Double(revenue - prev) * 100 / Double(prev)).rounded()) : 0
        cashCount = t.delivered?.byPayment?.cash?.count ?? 0
        cardCount = t.delivered?.byPayment?.card?.count ?? 0
        cashRevenue = Int64((t.delivered?.byPayment?.cash?.total ?? 0).rounded())
        cardRevenue = Int64((t.delivered?.byPayment?.card?.total ?? 0).rounded())
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "Asia/Tashkent") ?? .current
        for o in history where o.status.lowercased() == "delivered" {
            let h = cal.component(.hour, from: Date(timeIntervalSince1970: Double(o.createdAt) / 1000))
            hourly[h] += o.total
        }
    }
}
/// GET /api/panel/orders/history
struct HistoryPage: Decodable {
    let items: [RestaurantOrder]
    enum CodingKeys: String, CodingKey { case items }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = (try? c.decode([RestaurantOrder].self, forKey: .items)) ?? []
    }
}
struct StatusRequest: Encodable { let status: String }
struct DeviceRequest: Encodable { let token: String; let platform: String }
