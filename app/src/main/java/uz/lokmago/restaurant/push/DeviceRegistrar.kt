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

    /** The last reason push setup failed: Google's token error text (e.g. SERVICE_NOT_AVAILABLE, FIS_AUTH_ERROR) or the server's answer. */
    @Volatile var lastTokenError: String? = null; private set
    @Volatile var lastServerError: Throwable? = null; private set
    private val core = DeviceRegistration(
        jwt = { session.token },
        storedToken = { session.fcmToken },
        saveToken = { session.fcmToken = it },
        fetchToken = { firebaseToken() },
        post = { apiCall { api.registerDevice(DeviceRegisterRequest(it, "android", session.deviceId, BuildConfig.VERSION_NAME, notificationsOn())) } },
        delete = { apiCall { api.unregisterDevice(DeviceUnregisterRequest(it)) } },
        scope = scope,
        metaKey = { "${notificationsOn()}|${BuildConfig.VERSION_NAME}" },
        onFailure = { lastServerError = it },
    )

    /** Google's push token. Never hangs (20 s cap) and remembers WHY it failed — the old code dropped the reason. */
    private suspend fun firebaseToken(): String? {
        val r = runCatching {
            kotlinx.coroutines.withTimeoutOrNull(20_000) {
                suspendCancellableCoroutine<Result<String>> { c ->
                    FirebaseMessaging.getInstance().token.addOnCompleteListener {
                        c.resume(if (it.isSuccessful && !it.result.isNullOrBlank()) Result.success(it.result) else Result.failure(it.exception ?: IllegalStateException("EMPTY_TOKEN")))
                    }
                }
            } ?: Result.failure(IllegalStateException("TIMEOUT"))
        }.getOrElse { Result.failure(it) }
        lastTokenError = r.exceptionOrNull()?.let { e -> generateSequence(e) { it.cause }.mapNotNull { it.message }.lastOrNull() ?: e.javaClass.simpleName }
        if (r.isSuccess) lastServerError = null
        return r.getOrNull()
    }

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
