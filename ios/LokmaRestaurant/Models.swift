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
    /// Order items carry only the dish id; the photo comes from the live menu (MenuStore).
    let dishId: String?
    enum CodingKeys: String, CodingKey { case name, quantity, unitPrice, selectedOptions, dishId }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        let base = (try? c.decode(String.self, forKey: .name)) ?? ""
        let opts = ((try? c.decode([SelectedOption].self, forKey: .selectedOptions)) ?? []).compactMap { $0.name }.filter { !$0.isEmpty }
        // Oshxona aynan qaysi hajmni tayyorlashini bilsin: "Lorus Set (6 Kishilik Set)"
        name = opts.isEmpty ? base : "\(base) (\(opts.joined(separator: ", ")))"
        quantity = (try? c.decode(Int.self, forKey: .quantity)) ?? 1
        price = Int64(((try? c.decode(Double.self, forKey: .unitPrice)) ?? 0).rounded())
        imageUrl = nil
        dishId = (try? c.decodeIfPresent(String.self, forKey: .dishId)) ?? nil
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
    /// Customer rating / comment (Order.rating, comment, ratedAt) — shown on the "Mijozlar izohlari" page.
    let rating: Int?; let comment: String?; let ratedAt: Int64?

    enum CodingKeys: String, CodingKey {
        case id = "_id", dailyNumber, status, createdAt, fulfillment, paymentMethod, subtotal, deliveryFee, total
        case customer, phone, address, addressLat, addressLng, items, rating, comment, ratedAt
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
        rating = (try? c.decodeIfPresent(Int.self, forKey: .rating)) ?? nil
        comment = ((try? c.decodeIfPresent(String.self, forKey: .comment)) ?? nil).flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : $0 }
        let ratedIso = ((try? c.decodeIfPresent(String.self, forKey: .ratedAt)) ?? nil) ?? ""
        ratedAt = ratedIso.isEmpty ? nil : ISO8601DateFormatter().date(from: ratedIso).map { Int64($0.timeIntervalSince1970 * 1000) } ?? f.date(from: ratedIso).map { Int64($0.timeIntervalSince1970 * 1000) }
    }
    /// Has the customer left a rating or a comment?
    var review: Review? {
        guard rating != nil || comment != nil else { return nil }
        return Review(orderId: id, number: number, stars: min(max(rating ?? 0, 0), 5), comment: comment ?? "", customer: customerName ?? "Mijoz", at: ratedAt ?? createdAt, total: total)
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
    let items: [RestaurantOrder]; let nextCursor: String?; let hasMore: Bool
    enum CodingKeys: String, CodingKey { case items, nextCursor, hasMore }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        items = (try? c.decode([RestaurantOrder].self, forKey: .items)) ?? []
        nextCursor = (try? c.decodeIfPresent(String.self, forKey: .nextCursor)) ?? nil
        hasMore = (try? c.decode(Bool.self, forKey: .hasMore)) ?? false
    }
}
struct StatusRequest: Encodable { let status: String }
struct DeviceRequest: Encodable { let token: String; let platform: String; let environment: String }

// ---- Menyu: /api/panel/dishes (controllers/restaurantPanel.js, models/Dish.js) ----
struct DishOption: Decodable { let name: String; let price: Int64; let mandatory: Bool }
struct DishGroup: Decodable {
    let title: String; let kind: String?; let options: [DishOption]
    enum CodingKeys: String, CodingKey { case title, kind, options }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        title = (try? c.decode(String.self, forKey: .title)) ?? ""
        kind = (try? c.decodeIfPresent(String.self, forKey: .kind)) ?? nil
        options = (try? c.decode([RawOption].self, forKey: .options))?.map { DishOption(name: $0.name, price: $0.price, mandatory: $0.mandatory) } ?? []
    }
    private struct RawOption: Decodable {
        let name: String; let price: Int64; let mandatory: Bool
        enum CodingKeys: String, CodingKey { case name, price, mandatory }
        init(from decoder: Decoder) throws {
            let c = try decoder.container(keyedBy: CodingKeys.self)
            name = (try? c.decode(String.self, forKey: .name)) ?? ""
            price = Int64(((try? c.decode(Double.self, forKey: .price)) ?? 0).rounded())
            mandatory = (try? c.decode(Bool.self, forKey: .mandatory)) ?? false
        }
    }
}

/// One dish. Every field is decoded leniently: a missing or odd value must never hide the whole menu.
struct Dish: Decodable, Identifiable {
    let id: String; var name: String; var description: String; var category: String; var section: String
    var price: Int64; var oldPrice: Int64?; var prepMinutes: Int?
    var weight: String; var volume: String; var drinkType: String
    var calories: Int?; var protein: Int?; var fat: Int?; var carbs: Int?
    var imageUrl: String?; var available: Bool
    var priceMode: String; var dineInPrice: Int64?; var groups: [DishGroup]

    enum CodingKeys: String, CodingKey {
        case id = "_id", name, description, category, section, price, oldPrice, prepMinutes, weight, volume, drinkType
        case calories, protein, fat, carbs, imageUrl, images, isAvailable, priceMode, dineInPrice, optionGroups
    }
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        func str(_ k: CodingKeys) -> String { ((try? c.decodeIfPresent(String.self, forKey: k)) ?? nil) ?? "" }
        func num(_ k: CodingKeys) -> Double? { (try? c.decodeIfPresent(Double.self, forKey: k)) ?? nil }
        id = try c.decode(String.self, forKey: .id)
        name = str(.name); description = str(.description); section = str(.section)
        category = str(.category).isEmpty ? "milliy" : str(.category)
        price = Int64((num(.price) ?? 0).rounded())
        oldPrice = num(.oldPrice).map { Int64($0.rounded()) }.flatMap { $0 > 0 ? $0 : nil }
        prepMinutes = num(.prepMinutes).map { Int($0) }
        weight = str(.weight); volume = str(.volume); drinkType = str(.drinkType)
        calories = num(.calories).map { Int($0.rounded()) }; protein = num(.protein).map { Int($0.rounded()) }
        fat = num(.fat).map { Int($0.rounded()) }; carbs = num(.carbs).map { Int($0.rounded()) }
        let images = (try? c.decode([String].self, forKey: .images)) ?? []
        let main = str(.imageUrl)
        imageUrl = main.isEmpty ? images.first { !$0.isEmpty } : main
        available = (try? c.decode(Bool.self, forKey: .isAvailable)) ?? true
        priceMode = str(.priceMode).isEmpty ? "sync" : str(.priceMode)
        dineInPrice = num(.dineInPrice).map { Int64($0.rounded()) }
        groups = (try? c.decode([DishGroup].self, forKey: .optionGroups)) ?? []
    }
}

/// Admin panel (MenuPage.jsx) bilan AYNAN bir xil kategoriyalar va qoidalar.
enum DishCategories {
    static let all: [(value: String, label: String)] = [
        ("milliy", "Milliy taom"), ("osh", "Osh"), ("shashlik", "Shashlik"), ("sup", "Sho'rva"), ("salat", "Salatlar"),
        ("choyxona", "Choyxona"), ("zavtroki", "Nonushta"), ("obed", "Issiq taomlar"), ("fastfood", "Fast food"),
        ("lavash", "Lavash"), ("burger", "Burger"), ("tovuq", "Tovuq"), ("pitsa", "Pitsa"), ("sushi", "Sushi"),
        ("evropa", "Yevropa"), ("turetskaya", "Turk taomlari"), ("koffe", "Qahva"), ("shirinlik", "Shirinlik"),
        ("salqin", "Ichimlik"), ("sous", "Souslar"), ("boks", "Bokslar"), ("non", "Nonlar"),
    ]
    static func label(_ value: String?) -> String { all.first { $0.value == value }?.label ?? "Boshqa" }
    /// lib/dishMeta.js: ichimlikda gramm emas hajm so'raladi va kaloriya so'ralmaydi.
    static func isDrink(_ category: String?) -> Bool { ["salqin", "ichimliklar", "ichimlik", "koffe"].contains((category ?? "").lowercased()) }
    static let drinkTypes: [(value: String, label: String)] = [
        ("salqin", "Salqin ichimliklar"), ("gazli", "Gazli ichimliklar"), ("sok", "Sharbat / sok"), ("choy", "Choy"),
        ("qahva", "Qahva"), ("suv", "Suv"), ("sut", "Sut mahsulotlari"), ("milliy", "Milliy ichimliklar"),
        ("energetik", "Energetik"), ("boshqa", "Boshqa"),
    ]
}

/// Mijoz bahosi/izohi. `stars` = 0 bo'lsa faqat matn qoldirilgan.
struct Review: Identifiable { var id: String { orderId }; let orderId: String; let number: String; let stars: Int; let comment: String; let customer: String; let at: Int64; let total: Int64 }

/// GET /api/upload/signature — Cloudinary'ga TO'G'RIDAN yuklash uchun imzo (secret telefonga tushmaydi).
struct UploadSignature: Decodable { let cloudName: String; let apiKey: String; let timestamp: Int64; let folder: String; let allowedFormats: String; let signature: String }

extension String {
    /// Cloudinary: kichik rasm (avto format/sifat, kesib to'ldirish) — admin panelning `optimizeUrl` bilan bir xil.
    func cdn(_ width: Int) -> String { contains("/upload/") ? replacingOccurrences(of: "/upload/", with: "/upload/f_auto,q_auto,w_\(width),c_fill/") : self }
    /// Katta ko'rinish: kesmasdan.
    func cdnFit(_ width: Int) -> String { contains("/upload/") ? replacingOccurrences(of: "/upload/", with: "/upload/f_auto,q_auto,w_\(width),c_limit/") : self }
}

struct CountResponse: Decodable { let count: Int }
