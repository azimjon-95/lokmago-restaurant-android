package uz.lokmago.restaurant.push

/**
 * What an FCM data message means for the app — parsed and VALIDATED in one pure place (no Android / Firebase types, unit-tested).
 * The server (services/mobilePush.js) is the only sender, but a push is still input: an `orderId` ends up in an API path
 * (`panel/orders/{id}`), so anything that is not a 24-hex Mongo id is dropped instead of being passed on.
 */
sealed interface PushMessage {
    data class NewOrder(val orderId: String, val title: String, val body: String) : PushMessage
    /** order_updated / order_cancelled: stop ringing for it (re-read the order from the API; the push carries no details). */
    data class OrderChanged(val orderId: String, val cancelled: Boolean) : PushMessage
    data class DeliveryReminder(val orderId: String, val count: Int, val title: String?, val body: String?) : PushMessage
    /** "Push tekshiruvi": no orderId, plain notification. */
    data class Test(val title: String?, val body: String?) : PushMessage
    data object Ignored : PushMessage
}

object PushPayload {
    private val OBJECT_ID = Regex("^[0-9a-fA-F]{24}$")
    private const val MAX_TITLE = 120
    private const val MAX_BODY = 300

    fun parse(data: Map<String, String>): PushMessage {
        val type = data["type"] ?: return PushMessage.Ignored
        if (type == "test") return PushMessage.Test(text(data["title"], MAX_TITLE), text(data["body"], MAX_BODY))
        val id = data["orderId"]?.takeIf { OBJECT_ID.matches(it) } ?: return PushMessage.Ignored
        return when (type) {
            "order_new" -> PushMessage.NewOrder(id, text(data["title"], MAX_TITLE) ?: "🔔 Yangi buyurtma", text(data["body"], MAX_BODY) ?: "")
            "order_updated" -> PushMessage.OrderChanged(id, cancelled = false)
            "order_cancelled" -> PushMessage.OrderChanged(id, cancelled = true)
            "order_delivery_reminder" -> PushMessage.DeliveryReminder(id, (data["reminderCount"]?.toIntOrNull() ?: 1).coerceIn(1, 20), text(data["title"], MAX_TITLE), text(data["body"], MAX_BODY))
            else -> PushMessage.Ignored                          // a newer server may send types this build does not know
        }
    }

    private fun text(v: String?, max: Int): String? = v?.trim()?.take(max)?.takeIf { it.isNotEmpty() }
}
