package uz.lokmago.restaurant.courier

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.di.AppScope
import uz.lokmago.restaurant.push.DeviceRegistration

/**
 * Kuryer telefonining push tokeni (taklif ilova yopiq / telefon bloklangan bo'lsa ham keladi). Hayot sikli restoran tokeni bilan BIR XIL
 * (DeviceRegistration: faqat server 2xx'dan keyin «ro'yxatda», cheklangan backoff, bitta so'rov) — faqat boshqa API va boshqa JWT.
 */
@Singleton
class CourierDeviceRegistrar @Inject constructor(private val session: SessionStore, private val api: CourierApi, @AppScope scope: CoroutineScope) {
    private val core = DeviceRegistration(
        jwt = { session.courierToken },
        storedToken = { session.fcmToken },
        saveToken = { session.fcmToken = it },
        fetchToken = {
            runCatching { suspendCancellableCoroutine<String?> { c -> FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) } } }.getOrNull()
        },
        post = { courierCall { api.registerDevice(CDeviceReq(it, "android", BuildConfig.VERSION_NAME)) } },
        delete = { courierCall { api.unregisterDevice(CDeviceDel(it)) } },
        scope = scope,
    )
    suspend fun register(token: String? = null): Boolean = core.register(token)
    fun ensureRegistered(token: String? = null) = core.ensureRegistered(token)
    suspend fun unregister() = core.unregister()
}
