import Foundation

/// lakmago-server `/api/courier-app/*` (docs/COURIER-APP.md). Hamma maydon ixtiyoriy: kutilmagan javob ekranni yiqitmasin.
struct CLoginReq: Encodable { let login: String; let password: String }
struct CCourier: Decodable { let id: String?; let login: String?; let name: String?; let phone: String? }
struct CLoginRes: Decodable { let token: String; let courier: CCourier? }
struct CShift: Decodable { let id: String?; let startedAt: String?; let deliveries: Int?; let distanceM: Int? }
struct CShiftState: Decodable { let onShift: Bool?; let shift: CShift?; let activeAssignmentId: String? }
struct COffer: Decodable, Identifiable {
    let id: String; let orderCode: String?; let restaurantName: String?; let restaurantAddress: String?; let addressLabel: String?; let itemsSummary: String?
    let deliveryFee: Int64?; let isPaid: Bool?; let collectAmount: Int64?; let distanceM: Int?
    // LokmaGo qutisi: deliveryFee = KURYER haqi (eski server bu maydonlarni yubormaydi — hammasi ixtiyoriy)
    let kind: String?; let freeDelivery: Bool?
}
struct CItem: Decodable { let name: String?; let quantity: Int? }
struct CActive: Decodable, Identifiable {
    let id: String; let orderCode: String?; let restaurantName: String?; let restaurantAddress: String?; let restaurantPhone: String?
    let customerName: String?; let customerPhone: String?; let addressLabel: String?; let addressNote: String?; let note: String?
    let restaurantLat: Double?; let restaurantLng: Double?; let lat: Double?; let lng: Double?
    let items: [CItem]?; let deliveryFee: Int64?; let isPaid: Bool?; let collectAmount: Int64?
    // Bosqichlar (LokmaGo qutisi): to_restaurant → «Taomni oldim» → to_customer → «Topshirdim». Havola oqimida canPickup yo'q (nil/false).
    let kind: String?; let stage: String?; let pickedUp: Bool?; let canPickup: Bool?; let canDeliver: Bool?; let freeDelivery: Bool?
}
struct CPickupRes: Decodable { let ok: Bool?; let already: Bool?; let active: CActive? }
struct CDistrict: Decodable, Identifiable { let id: String; let uz: String?; let group: String? }
struct CDistrictsRes: Decodable { let districts: [CDistrict]? }
struct CMeCourier: Decodable { let workDistricts: [String]? }
struct CMeRes: Decodable { let courier: CMeCourier? }
struct CMeUpd: Decodable { let workDistricts: [String]? }
struct CMeReq: Encodable { let workDistricts: [String] }
struct CAcceptRes: Decodable { let active: CActive? }
struct CActiveRes: Decodable { let active: CActive? }
struct CHistory: Decodable, Identifiable { let id: String; let orderCode: String?; let restaurantName: String?; let addressLabel: String?; let deliveryFee: Int64?; let deliveredAt: String?; let minutes: Int? }
struct CPeriod: Decodable { let deliveries: Int?; let deliveryFees: Int64?; let shifts: Int?; let workedMinutes: Int?; let distanceKm: Double? }
struct CStats: Decodable { let today: CPeriod?; let week: CPeriod?; let month: CPeriod? }
struct CSent: Decodable { let sent: Int? }
struct CLocationReq: Encodable { let lat: Double; let lng: Double; var accuracy: Double? = nil; var speed: Double? = nil; var heading: Double? = nil }
struct CDeviceReq: Encodable { let token: String; let platform: String; var environment: String? = nil; var appVersion: String? = nil }
struct CDeviceDel: Encodable { let token: String }
