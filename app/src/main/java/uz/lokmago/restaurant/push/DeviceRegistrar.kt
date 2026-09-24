package uz.lokmago.restaurant.push

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.DeviceRequest

/** Links / unlinks this phone's FCM token to the logged-in restaurant user (server-side). */
@Singleton
class DeviceRegistrar @Inject constructor(private val api: LokmaApi, private val session: SessionStore) {

    private suspend fun currentToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(it.result) }
        }
    }.getOrNull()   // Firebase not configured (no google-services.json) => push disabled, socket still works

    suspend fun register(token: String? = null) {
        val t = token ?: session.fcmToken ?: currentToken() ?: return
        session.fcmToken = t
        if (session.token == null) return
        runCatching { api.registerDevice(DeviceRequest(t, session.deviceId)) }
    }

    /** Best effort: must be called BEFORE the JWT is discarded. */
    suspend fun unregister() {
        val t = session.fcmToken ?: return
        runCatching { api.unregisterDevice(DeviceRequest(t, session.deviceId)) }
    }
}
