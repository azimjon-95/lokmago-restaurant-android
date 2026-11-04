package uz.lokmago.restaurant.courier

import kotlinx.serialization.Serializable
import retrofit2.http.*
import uz.lokmago.restaurant.data.remote.dto.OkDto

/** lakmago-server `/api/courier-app/*` (docs/COURIER-APP.md). Kuryer JWT'si restoran tokenidan ALOHIDA (boshqa kalit). */
@Serializable data class CLoginReq(val login: String, val password: String)
@Serializable data class CCourierDto(val id: String = "", val login: String = "", val name: String = "", val phone: String = "", val vehicle: String = "")
@Serializable data class CLoginRes(val token: String, val courier: CCourierDto = CCourierDto())
@Serializable data class CShiftDto(val id: String = "", val startedAt: String? = null, val deliveries: Int = 0, val distanceM: Int = 0)
@Serializable data class CShiftState(val onShift: Boolean = false, val shift: CShiftDto? = null, val activeAssignmentId: String? = null)
@Serializable data class COfferDto(
    val id: String, val orderCode: String = "", val createdAt: String? = null,
    val restaurantName: String = "", val restaurantAddress: String = "", val restaurantLat: Double? = null, val restaurantLng: Double? = null,
    val addressLabel: String = "", val itemsSummary: String = "", val deliveryFee: Long = 0, val isPaid: Boolean = false,
    val collectAmount: Long = 0, val paymentMethod: String = "", val distanceM: Int? = null,
    // LokmaGo qutisi: deliveryFee = KURYER haqi. Eski server maydonlarni yubormaydi — standart qiymatlar xavfsiz.
    val kind: String = "", val earningSom: Long = 0, val freeDelivery: Boolean = false, val itemsCount: Int = 0,
)
@Serializable data class CItem(val name: String = "", val quantity: Int = 0)
@Serializable data class CActiveDto(
    val id: String, val orderId: String = "", val orderCode: String = "",
    val restaurantName: String = "", val restaurantAddress: String = "", val restaurantPhone: String = "", val restaurantLat: Double? = null, val restaurantLng: Double? = null,
    val customerName: String = "", val customerPhone: String = "", val addressLabel: String = "", val addressNote: String = "", val lat: Double? = null, val lng: Double? = null,
    val items: List<CItem> = emptyList(), val itemsSummary: String = "", val note: String = "",
    val deliveryFee: Long = 0, val isPaid: Boolean = false, val collectAmount: Long = 0, val paymentMethod: String = "",
    // Bosqichlar (LokmaGo qutisi): to_restaurant → «Taomni oldim» → to_customer → «Topshirdim». Havola oqimida: darhol to_customer.
    val kind: String = "", val stage: String = "", val pickedUp: Boolean = false, val canPickup: Boolean = false, val canDeliver: Boolean = true,
    val earningSom: Long = 0, val freeDelivery: Boolean = false,
)
@Serializable data class CPickupRes(val ok: Boolean = true, val already: Boolean = false, val active: CActiveDto? = null)
@Serializable data class CDistrictDto(val id: String = "", val uz: String = "", val group: String = "")
@Serializable data class CDistrictsRes(val districts: List<CDistrictDto> = emptyList())
@Serializable data class CMeCourier(val workDistricts: List<String> = emptyList())
@Serializable data class CWallet(val balanceSom: Double = 0.0, val openCashSom: Double = 0.0, val debtLimitSom: Double = 0.0, val cashRoomSom: Double = 0.0)
@Serializable data class CMeRes(val courier: CMeCourier = CMeCourier(), val wallet: CWallet? = null)
@Serializable data class CMeReq(val workDistricts: List<String> = emptyList())
@Serializable data class CAcceptRes(val ok: Boolean = true, val active: CActiveDto? = null)
@Serializable data class CActiveRes(val active: CActiveDto? = null)
@Serializable data class CHistoryDto(
    val id: String = "", val orderCode: String = "", val restaurantName: String = "", val addressLabel: String = "", val deliveryFee: Long = 0,
    val collectAmount: Long = 0, val isPaid: Boolean = false, val assignedAt: String? = null, val deliveredAt: String? = null, val minutes: Int? = null,
)
@Serializable data class CPeriod(val deliveries: Int = 0, val deliveryFees: Long = 0, val shifts: Int = 0, val workedMinutes: Int = 0, val distanceKm: Double = 0.0)
@Serializable data class CStats(val today: CPeriod = CPeriod(), val week: CPeriod = CPeriod(), val month: CPeriod = CPeriod())
@Serializable data class CLocationReq(val lat: Double, val lng: Double, val accuracy: Double? = null, val speed: Double? = null, val heading: Double? = null)
@Serializable data class CDeviceReq(val token: String, val platform: String = "android", val appVersion: String? = null)
@Serializable data class CDeviceDel(val token: String)
@Serializable data class CSent(val sent: Int = 0)

interface CourierApi {
    @POST("courier-app/login") suspend fun login(@Body b: CLoginReq): CLoginRes
    /** Tumanlar ro'yxati (ochiq) va kuryerning ish tumanlari: buyurtmalar shu tumanlardan (yoki biriktirilgan restoranlardan) ko'rinadi. */
    @GET("districts") suspend fun districts(): CDistrictsRes
    @GET("courier-app/me") suspend fun me(): CMeRes
    @PATCH("courier-app/me") suspend fun updateMe(@Body b: CMeReq): CMeReq
    @GET("courier-app/shift") suspend fun shift(): CShiftState
    @POST("courier-app/shift/start") suspend fun startShift(): CShiftState
    @POST("courier-app/shift/end") suspend fun endShift(): CShiftState
    /** Har ~5 soniyada (smena ochiq bo'lganda). 204. */
    @POST("courier-app/location") suspend fun location(@Body b: CLocationReq)
    @GET("courier-app/offers") suspend fun offers(): List<COfferDto>
    @POST("courier-app/offers/{id}/accept") suspend fun accept(@Path("id") id: String): CAcceptRes
    @GET("courier-app/active") suspend fun active(): CActiveRes
    /** «Taomni oldim»: serverda kuryerning joylashuvi restoran bilan solishtiriladi (uzoq bo'lsa adminga belgilanadi). */
    @POST("courier-app/active/pickup") suspend fun pickup(): CPickupRes
    @POST("courier-app/active/deliver") suspend fun deliver(): OkDto
    @GET("courier-app/history") suspend fun history(@Query("limit") limit: Int = 30): List<CHistoryDto>
    @GET("courier-app/stats") suspend fun stats(): CStats
    @POST("courier-app/devices") suspend fun registerDevice(@Body b: CDeviceReq)
    @HTTP(method = "DELETE", path = "courier-app/devices", hasBody = true) suspend fun unregisterDevice(@Body b: CDeviceDel)
    @POST("courier-app/devices/test") suspend fun testPush(): CSent
}
