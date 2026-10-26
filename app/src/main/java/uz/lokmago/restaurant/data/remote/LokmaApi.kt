package uz.lokmago.restaurant.data.remote

import retrofit2.http.*
import uz.lokmago.restaurant.data.remote.dto.*

/**
 * lakmago-server (`/api/...`) — admin panelning restoran bo'limi bilan BIR XIL akkaunt, JWT va endpointlar.
 * restaurantId hech qachon clientdan yuborilmaydi: server uni tokendan oladi.
 */
interface LokmaApi {
    /** Admin panel bilan bir xil kirish (login + parol). */
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse
    /** Kirish paytida token hali sessiyada yo'q — shuning uchun sarlavha aniq beriladi. */
    @GET("panel/me") suspend fun me(@Header("Authorization") bearer: String): RestaurantDto
    /** Sliding session: hali yaroqli token evaziga yangi 30 kunlik token. */
    @POST("panel/session/refresh") suspend fun refresh(): RefreshResponse

    /** Oxirgi 80 ta (zal buyurtmalarisiz); status = null — hammasi. */
    @GET("panel/orders") suspend fun orders(@Query("status") status: String?): List<OrderDto>
    @GET("panel/orders/history") suspend fun history(
        @Query("from") from: String?, @Query("to") to: String?, @Query("limit") limit: Int = 200, @Query("cursor") cursor: String? = null,
    ): HistoryPage
    @GET("panel/orders/{id}") suspend fun order(@Path("id") id: String): OrderDto
    /** Qabul qilish ham, keyingi holatlar ham shu bitta yo'l (admin panel bilan bir xil qoidalar). */
    @PATCH("panel/orders/{id}/status") suspend fun setStatus(@Path("id") id: String, @Body body: StatusRequest): OrderDto
    /** Yetkazib berish buyurtmasini restoran yakunlaydi (server vaqt shartini tekshiradi). */
    @POST("panel/orders/{id}/confirm-delivered") suspend fun confirmDelivered(@Path("id") id: String): OrderDto

    @GET("panel/stats") suspend fun stats(@Query("from") from: String, @Query("to") to: String): PanelStatsDto

    // ---- Menyu va Stop List (admin panel bilan bir xil endpointlar) ----
    @GET("panel/dishes") suspend fun dishes(): List<DishDto>
    @POST("panel/dishes") suspend fun createDish(@Body body: kotlinx.serialization.json.JsonObject): DishDto
    @PATCH("panel/dishes/{id}") suspend fun updateDish(@Path("id") id: String, @Body body: kotlinx.serialization.json.JsonObject): DishDto
    /** stop = true → taom STOP-listga tushadi (isAvailable = false). */
    @PATCH("panel/dishes/{id}/stop") suspend fun stopDish(@Path("id") id: String, @Body body: StopRequest): DishDto
    @DELETE("panel/dishes/{id}") suspend fun deleteDish(@Path("id") id: String): OkDto
    /** Badge uchun arzon so'rov: faqat son. */
    @GET("panel/dishes/stopped/count") suspend fun stoppedCount(): CountDto
    @GET("upload/signature") suspend fun uploadSignature(@Query("folder") folder: String): UploadSignatureDto

    // ---- Push qurilmasi (lakmago-server: /api/panel/devices) — yangi buyurtma ilova yopiq bo'lsa ham keladi ----
    @POST("panel/devices") suspend fun registerDevice(@Body body: DeviceRegisterRequest)
    @HTTP(method = "DELETE", path = "panel/devices", hasBody = true) suspend fun unregisterDevice(@Body body: DeviceUnregisterRequest)
}
