package uz.lokmago.restaurant.data.remote.dto

import kotlinx.serialization.Serializable
import uz.lokmago.restaurant.domain.*

@Serializable data class LoginRequest(val login: String, val password: String)
@Serializable data class RestaurantDto(val id: String, val name: String, val cuisine: String? = null, val logoUrl: String? = null)
@Serializable data class UserDto(val id: String, val login: String = "", val role: String? = null)
@Serializable data class RefreshResponse(val token: String)
@Serializable data class LoginResponse(val token: String, val restaurant: RestaurantDto, val user: UserDto)

@Serializable data class OrderItemDto(
    val name: String, val quantity: Int, val price: Long, val imageUrl: String? = null,
)

@Serializable data class OrderDto(
    val id: String,
    val number: String,
    val status: String,
    val createdAt: Long,                       // epoch millis (UTC)
    val fulfillment: String = "delivery",
    val paymentMethod: String = "cash",
    val itemsCount: Int = 0,
    val subtotal: Long = 0,
    val deliveryFee: Long = 0,
    val total: Long = 0,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val items: List<OrderItemDto> = emptyList(),
) {
    fun toDomain() = Order(
        id = id, number = number, status = OrderStatus.from(status), createdAtMillis = createdAt,
        fulfillment = Fulfillment.from(fulfillment), payment = Payment.from(paymentMethod),
        itemsCount = if (itemsCount > 0) itemsCount else items.size,
        subtotal = subtotal, deliveryFee = deliveryFee, total = total,
        customerName = customerName, customerPhone = customerPhone, address = address, lat = lat, lng = lng,
        items = items.map { OrderItem(it.name, it.quantity, it.price, it.imageUrl) },
    )
}

@Serializable data class StatusRequest(val status: String)
/** "delivered" | "in_progress" — see OrderRepository.reminderAck / backend §"Buyurtmani yakunlashni nazorat qilish". */
@Serializable data class ReminderAckRequest(val action: String)
/** Minimal Socket.IO payload for `order:delivery-reminder` — intentionally NOT a full order (TZ §22). */
@Serializable data class ReminderEventDto(val orderId: String, val reminderCount: Int)
@Serializable data class DeviceRequest(val token: String, val deviceId: String, val platform: String = "android")

@Serializable data class StatsDto(
    val ordersCount: Int = 0, val ordersDelta: Int = 0,
    val revenue: Long = 0, val revenueDeltaPct: Int = 0,
    val waiting: Int = 0,
    val cashCount: Int = 0, val cardCount: Int = 0,
    val cashRevenue: Long = 0, val cardRevenue: Long = 0,
    val hourly: List<Long> = List(24) { 0L },
) {
    fun toDomain() = DayStats(ordersCount, ordersDelta, revenue, revenueDeltaPct, waiting, cashCount, cardCount, cashRevenue, cardRevenue, hourly)
}

@Serializable data class VersionDto(val android: AndroidVersion = AndroidVersion()) {
    @Serializable data class AndroidVersion(
        val minimumVersion: String = "0.0.0",
        val latestVersion: String = "0.0.0",
        val forceUpdate: Boolean = false,
    )
}

@Serializable data class ErrorDto(val error: String? = null, val code: String? = null)
