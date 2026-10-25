package uz.lokmago.restaurant.push

import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import uz.lokmago.restaurant.data.local.SessionStore

/**
 * FCM tokeni faqat telefonda saqlanadi. lakmago-serverda hozircha push (FCM) yuborish yo'q — buyurtmalar
 * Socket.IO (`restaurant:<id>` xonasi) va ilova ochilganda/qayta ulanganda `pending` sinxronlashi orqali keladi.
 * Server FCM qo'llab-quvvatlaganda tokenni shu yerdan yuborish kifoya (endpoint qo'shilgach).
 */
@Singleton
class DeviceRegistrar @Inject constructor(private val session: SessionStore) {

    private suspend fun currentToken(): String? = runCatching {
        suspendCancellableCoroutine<String?> { c ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { c.resume(it.result) }
        }
    }.getOrNull()   // Firebase sozlanmagan (google-services.json yo'q) — push o'chiq, soket ishlayveradi

    suspend fun register(token: String? = null) {
        val t = token ?: session.fcmToken ?: currentToken() ?: return
        session.fcmToken = t
    }

    /** Chiqishda: serverda ro'yxat yo'q, faqat mahalliy holat. */
    suspend fun unregister() = Unit
}
