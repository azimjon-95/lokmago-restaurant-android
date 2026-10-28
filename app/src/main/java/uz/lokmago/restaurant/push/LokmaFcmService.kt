package uz.lokmago.restaurant.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.realtime.OrderCoordinator

/**
 * Backend sends DATA-ONLY high-priority messages so this runs even when the app is closed:
 *   { type: "order_new", orderId: "<24 hex>", title: "...", body: "..." }
 * The payload is used only to show something instantly; the order itself is always reloaded from the authenticated API.
 *
 * Rules for this callback (FCM gives it ~10 s): no network and no waiting here — [OrderCoordinator.onPush] shows the notification and
 * starts the alarm at once and does the loading in its own scope; a malformed message is dropped, never allowed to crash the process;
 * while logged out every push is ignored.
 */
@AndroidEntryPoint
class LokmaFcmService : FirebaseMessagingService() {
    @Inject lateinit var coordinator: OrderCoordinator
    @Inject lateinit var registrar: DeviceRegistrar
    @Inject lateinit var session: SessionStore

    /** The token rotated (reinstall, data cleared, Google decided): remember it and tell the server (retries by itself). */
    override fun onNewToken(token: String) {
        session.fcmToken = token
        registrar.ensureRegistered(token)           // logged out: only remembered, registered right after the next login
    }

    override fun onMessageReceived(message: RemoteMessage) {
        try { handle(PushPayload.parse(message.data)) }
        catch (e: Exception) { Log.w(TAG, "push dropped: ${e.javaClass.simpleName}") }     // class name only: no payload, no token in logs
    }

    private fun handle(m: PushMessage) {
        if (session.token == null) return                       // logged out: ignore
        when (m) {
            is PushMessage.Test -> coordinator.onTestPush(m.title, m.body)
            is PushMessage.NewOrder -> coordinator.onPush(m.orderId, m.title, m.body)
            is PushMessage.OrderChanged -> coordinator.onRemoteChange(m.orderId)
            is PushMessage.DeliveryReminder -> coordinator.onDeliveryReminder(m.orderId, m.count, m.title, m.body)
            PushMessage.Ignored -> Unit
        }
    }

    private companion object { const val TAG = "LokmaFcm" }
}
