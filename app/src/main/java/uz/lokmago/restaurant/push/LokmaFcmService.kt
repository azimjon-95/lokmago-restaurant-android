package uz.lokmago.restaurant.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.realtime.OrderCoordinator

/**
 * Backend sends DATA-ONLY high-priority messages so this runs even when the app is closed:
 *   { type: "order_new", orderId: "...", title: "...", body: "..." }
 * The payload is used only to show something instantly; the order itself is always reloaded from the backend.
 */
@AndroidEntryPoint
class LokmaFcmService : FirebaseMessagingService() {
    @Inject lateinit var coordinator: OrderCoordinator
    @Inject lateinit var registrar: DeviceRegistrar
    @Inject lateinit var session: SessionStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        session.fcmToken = token
        scope.launch { registrar.register(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (session.token == null) return                 // logged out: ignore
        val d = message.data
        val orderId = d["orderId"] ?: return
        when (d["type"]) {
            "order_new" -> coordinator.onPush(orderId, d["title"] ?: "🔔 Yangi buyurtma", d["body"] ?: "")
            "order_updated", "order_cancelled" -> coordinator.onRemoteChange(orderId)
            "order_delivery_reminder" -> coordinator.onDeliveryReminder(orderId, d["reminderCount"]?.toIntOrNull() ?: 1, d["title"], d["body"])
        }
    }
}
