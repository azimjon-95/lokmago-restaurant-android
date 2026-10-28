package uz.lokmago.restaurant.push

import com.google.firebase.messaging.FirebaseMessaging
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
    private val session: SessionStore,
    private val api: LokmaApi,
    @AppScope scope: CoroutineScope,
) {
    private val core = DeviceRegistration(
        jwt = { session.token },
        storedToken = { session.fcmToken },
        saveToken = { session.fcmToken = it },
        fetchToken = { firebaseToken() },
        post = { apiCall { api.registerDevice(DeviceRegisterRequest(it, "android")) } },
        delete = { apiCall { api.unregisterDevice(DeviceUnregisterRequest(it)) } },
        scope = scope,
    )

    private suspend fun firebaseToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
        }
    }.getOrNull()

    suspend fun register(token: String? = null): Boolean = core.register(token)
    fun ensureRegistered(token: String? = null) = core.ensureRegistered(token)
    suspend fun unregister() = core.unregister()
}
