package uz.lokmago.restaurant.push

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import uz.lokmago.restaurant.data.repo.apiCall
import uz.lokmago.restaurant.data.remote.dto.DeviceUnregisterRequest
import uz.lokmago.restaurant.data.remote.dto.DeviceRegisterRequest
import uz.lokmago.restaurant.data.remote.LokmaApi
import kotlinx.coroutines.CancellationException
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.lokmago.restaurant.data.local.SessionStore

/**
 * FCM tokenini serverga bog'laydi (`POST /api/panel/devices`), shunda server yangi buyurtmani ilova yopiq /
 * telefon qulflangan bo'lsa ham push bilan yuboradi (ildiz sababi: oldin token faqat telefonda saqlanardi).
 * Chaqiriladi: yangi token kelganda (LokmaFcmService), har soket ulanishida (OrderCoordinator), chiqishda — o'chirish.
 * Muvaffaqiyatsiz bo'lsa keyingi ulanishda o'zi qayta urinadi. restaurantId yuborilmaydi — server uni JWT'dan oladi.
 */
@Singleton
class DeviceRegistrar @Inject constructor(private val session: SessionStore, private val api: LokmaApi) {
    /** "jwt|fcmToken" — har qayta ulanishda behuda qayta yubormaslik uchun (jarayon qayta tug'ilsa bir marta yuboriladi). */
    @Volatile private var registeredFor: String? = null

    private suspend fun currentToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(if (it.isSuccessful) it.result else null) }
        }
    }.getOrNull()   // Firebase sozlanmagan (google-services.json yo'q) — push o'chiq, soket ishlayveradi

    suspend fun register(token: String? = null) {
        val t = token ?: session.fcmToken ?: currentToken() ?: return
        session.fcmToken = t
        val jwt = session.token ?: return                      // hali kirilmagan: kirgandan keyin (soket ulanganda) yuboriladi
        val key = "$jwt|$t"
        if (registeredFor == key) return
        try {
            apiCall { api.registerDevice(DeviceRegisterRequest(t, "android")) }
            registeredFor = key
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* keyingi ulanishda qayta urinadi */ }
    }

    /** Chiqishda (JWT hali bor paytda): bu telefon endi shu restoranning buyurtmalarini olmaydi. */
    suspend fun unregister() {
        val t = session.fcmToken ?: return
        try { apiCall { api.unregisterDevice(DeviceUnregisterRequest(t)) } }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { /* server keyinroq o'lik tokenni o'zi tozalaydi */ }
        registeredFor = null
    }
}
