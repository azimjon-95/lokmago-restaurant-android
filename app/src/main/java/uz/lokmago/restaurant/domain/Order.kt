package uz.lokmago.restaurant.domain

enum class OrderStatus(val api: String, val label: String) {
    NEW("pending", "Yangi"),
    ACCEPTED("accepted", "Qabul qilingan"),
    PREPARING("preparing", "Tayyorlanmoqda"),
    READY("ready", "Tayyor"),
    DELIVERING("delivering", "Yetkazilmoqda"),
    DELIVERED("delivered", "Yetkazildi"),
    CANCELLED("cancelled", "Bekor qilingan"),
    UNKNOWN("unknown", "—");

    val isFinal get() = this == DELIVERED || this == CANCELLED

    companion object {
        // Unknown statuses must NEVER map to NEW — that would trigger the alarm.
        fun from(v: String?) = entries.firstOrNull { it.api.equals(v, ignoreCase = true) } ?: UNKNOWN
    }
}

enum class Fulfillment(val api: String, val label: String) {
    DELIVERY("delivery", "Yetkazib berish"),
    PICKUP("pickup", "Olib ketish"),
    DINE_IN("dine_in", "Zalda");

    companion object {
        fun from(v: String?) = entries.firstOrNull { it.api.equals(v, true) } ?: DELIVERY
    }
}

enum class Payment(val api: String, val label: String) {
    CASH("cash", "Naqd"), CARD("card", "Karta");

    companion object {
        fun from(v: String?) = entries.firstOrNull { it.api.equals(v, true) } ?: CASH
    }
}

data class OrderItem(
    val name: String,
    val quantity: Int,
    val price: Long,
    val imageUrl: String? = null,
) {
    val lineTotal get() = price * quantity
}

data class Order(
    val id: String,
    val number: String,
    val status: OrderStatus,
    val createdAtMillis: Long,
    val fulfillment: Fulfillment,
    val payment: Payment,
    val itemsCount: Int,
    val subtotal: Long,
    val deliveryFee: Long,
    val total: Long,
    val customerName: String? = null,
    val customerPhone: String? = null,
    val address: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
    val items: List<OrderItem> = emptyList(),
) {
    /** Next action the restaurant can take, or null when nothing is left to do. */
    fun nextAction(): NextAction? = when (status) {
        OrderStatus.NEW -> NextAction(OrderStatus.ACCEPTED, "Qabul qilish")
        OrderStatus.ACCEPTED -> NextAction(OrderStatus.PREPARING, "Tayyorlashni boshlash")
        OrderStatus.PREPARING -> NextAction(OrderStatus.READY, "Tayyor deb belgilash")
        OrderStatus.READY -> when (fulfillment) {
            Fulfillment.DELIVERY -> NextAction(OrderStatus.DELIVERING, "Yetkazishga berish")
            else -> NextAction(OrderStatus.DELIVERED, "Mijozga topshirildi")
        }
        OrderStatus.DELIVERING -> NextAction(OrderStatus.DELIVERED, "Yetkazildi")
        else -> null
    }
}

data class NextAction(val target: OrderStatus, val label: String)

data class DayStats(
    val ordersCount: Int, val ordersDelta: Int,
    val revenue: Long, val revenueDeltaPct: Int,
    val waiting: Int, val cashCount: Int, val cardCount: Int,
    val cashRevenue: Long, val cardRevenue: Long,
    val hourly: List<Long>, // 24 buckets
)
