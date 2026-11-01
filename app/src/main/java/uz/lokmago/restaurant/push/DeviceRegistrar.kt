package uz.lokmago.restaurant.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessaging
import dagger.hilt.android.qualifiers.ApplicationContext
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.remote.dto.DeviceAckRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.DeviceRegisterRequest
import uz.lokmago.restaurant.data.remote.dto.DeviceUnregisterRequest
import uz.lokmago.restaurant.data.repo.apiCall
import uz.lokmago.restaurant.di.AppScope

/**
 * Hilt wiring of [DeviceRegistration]: current JWT and stored token from SessionStore, the token from Firebase, the calls through the
 * authenticated API. Triggers: login / JWT rotation and every socket connect (OrderCoordinator), a new FCM token (LokmaFcmService),
 * app start; logout unregisters. If Firebase is not configured (no google-services.json) the token is simply null and push stays off.
 */
@Singleton
class DeviceRegistrar @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val session: SessionStore,
    private val api: LokmaApi,
    @AppScope scope: CoroutineScope,
) {
    private fun notificationsOn() = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
    private val core = DeviceRegistration(
        jwt = { session.token },
        storedToken = { session.fcmToken },
        saveToken = { session.fcmToken = it },
        fetchToken = { firebaseToken() },
        post = { apiCall { api.registerDevice(DeviceRegisterRequest(it, "android", session.deviceId, BuildConfig.VERSION_NAME, notificationsOn())) } },
        delete = { apiCall { api.unregisterDevice(DeviceUnregisterRequest(it)) } },
        scope = scope,
        metaKey = { "${notificationsOn()}|${BuildConfig.VERSION_NAME}" },
    )

    private suspend fun firebaseToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
        }
    }.getOrNull()

    suspend fun register(token: String? = null): Boolean = core.register(token)
    fun ensureRegistered(token: String? = null) = core.ensureRegistered(token)
    suspend fun unregister() = core.unregister()

    /** Fire-and-forget delivery receipt ("received" / "opened"); never throws, never blocks the push callback. */
    suspend fun ack(orderId: String, attempt: Int, event: String) {
        val token = session.fcmToken ?: return
        runCatching { apiCall { api.ackDevice(DeviceAckRequest(token, orderId, attempt, event)) } }
    }

    /** Pull the admin's sound/vibration policy (socket path has no push payload to carry it). */
    suspend fun syncPolicy() {
        runCatching { apiCall { api.notifySettings() } }.getOrNull()?.let { session.serverSound = it.sound; session.serverVibration = it.vibration }
    }
}
