package uz.lokmago.restaurant.data.remote

import retrofit2.http.*
import uz.lokmago.restaurant.data.remote.dto.*

interface LokmaApi {
    @POST("auth/login") suspend fun login(@Body body: LoginRequest): LoginResponse

    @GET("orders/pending") suspend fun pending(): List<OrderDto>
    @GET("orders") suspend fun orders(@Query("status") status: String?, @Query("limit") limit: Int = 50): List<OrderDto>
    @GET("orders/{id}") suspend fun order(@Path("id") id: String): OrderDto
    @POST("orders/{id}/accept") suspend fun accept(@Path("id") id: String): OrderDto
    @POST("orders/{id}/status") suspend fun setStatus(@Path("id") id: String, @Body body: StatusRequest): OrderDto
    /** "Yetkazildi" / "Jarayonda" from a delivery reminder. "delivered" goes through the SAME existing
     *  completion path as [setStatus]; "in_progress" only postpones the next reminder server-side. */
    @POST("orders/{id}/reminder/ack") suspend fun reminderAck(@Path("id") id: String, @Body body: ReminderAckRequest): OrderDto

    @GET("stats/today") suspend fun today(): StatsDto

    @POST("devices/fcm") suspend fun registerDevice(@Body body: DeviceRequest)
    @HTTP(method = "DELETE", path = "devices/fcm", hasBody = true) suspend fun unregisterDevice(@Body body: DeviceRequest)

    @GET("app/version") suspend fun version(): VersionDto
}
