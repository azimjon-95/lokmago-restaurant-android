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

/// GET /api/panel/stats?from=&to= (controllers/restaurantApp.js)
struct TodayStats: Decodable {
    let ordersCount: Int; let waiting: Int; let revenue: Int64
    private struct Created: Decodable { let count: Int?; let byStatus: [String: Int]? }
    private struct Delivered: Decodable { let total: Double? }
    enum CodingKeys: String, CodingKey { case created, delivered }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let created = try? c.decode(Created.self, forKey: .created)
        let delivered = try? c.decode(Delivered.self, forKey: .delivered)
        ordersCount = created?.count ?? 0
        waiting = created?.byStatus?["pending"] ?? 0
        revenue = Int64((delivered?.total ?? 0).rounded())
    }
}
struct StatusRequest: Encodable { let status: String }
