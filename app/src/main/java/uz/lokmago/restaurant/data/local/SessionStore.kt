package uz.lokmago.restaurant.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Session(val token: String, val restaurantName: String, val login: String)
data class CourierSession(val token: String, val name: String, val login: String)

/** JWT + identity live in EncryptedSharedPreferences (Android Keystore–backed). */
@Singleton
class SessionStore @Inject constructor(@ApplicationContext ctx: Context) {
    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx, "lokmago_session",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val _session = MutableStateFlow(read())
    val session: StateFlow<Session?> = _session.asStateFlow()
    val token: String? get() = prefs.getString(K_TOKEN, null)

    /** The login name (never the password) survives logout/expiry so the login form can be pre-filled. */
    val lastLogin: String get() = prefs.getString(K_LAST_LOGIN, "") ?: ""

    /**
     * The always-on connection service (a low-priority "Ulanish faol" notification that keeps the socket alive). FCM is the primary
     * background channel; this is the optional extra. Costs battery and needs a Play Console foreground-service declaration, so the user can
     * switch it off. Kept here (synchronous prefs) because BootReceiver must read it without waiting for DataStore.
     */
    private val _persistentListener = MutableStateFlow(prefs.getBoolean("persistent_listener", true))
    val persistentListener: StateFlow<Boolean> = _persistentListener.asStateFlow()
    fun setPersistentListener(on: Boolean) { prefs.edit().putBoolean("persistent_listener", on).apply(); _persistentListener.value = on }

    /** Admin-panel policy for the alarm (last known). Applied together with the user's own switches: either one off = off. */
    var serverSound: Boolean
        get() = prefs.getBoolean("server_sound", true)
        set(v) { prefs.edit().putBoolean("server_sound", v).apply() }
    var serverVibration: Boolean
        get() = prefs.getBoolean("server_vibration", true)
        set(v) { prefs.edit().putBoolean("server_vibration", v).apply() }

    // ---- Kuryer tomoni: restoran sessiyasidan ALOHIDA token (boshqa kalit bilan imzolangan, restoran API'siga kirmaydi) ----
    private val _courier = MutableStateFlow(readCourier())
    val courier: StateFlow<CourierSession?> = _courier.asStateFlow()
    val courierToken: String? get() = prefs.getString("c_token", null)
    fun saveCourier(s: CourierSession) {
        prefs.edit().putString("c_token", s.token).putString("c_name", s.name).putString("c_login", s.login).putString("c_last_login", s.login).apply()
        _courier.value = s
    }
    fun expireCourier() { prefs.edit().remove("c_token").remove("c_name").remove("c_login").apply(); _courier.value = null }
    val lastCourierLogin: String get() = prefs.getString("c_last_login", "") ?: ""
    private fun readCourier(): CourierSession? {
        val t = prefs.getString("c_token", null) ?: return null
        return CourierSession(t, prefs.getString("c_name", "") ?: "", prefs.getString("c_login", "") ?: "")
    }

    /** Bu telefon qaysi tomon uchun: "restaurant" | "courier" | null (birinchi ochilish → tanlash ekrani). Chiqishda saqlanadi. */
    private val _role = MutableStateFlow(prefs.getString("app_role", null))
    val role: StateFlow<String?> = _role.asStateFlow()
    fun setRole(r: String?) { prefs.edit().apply { if (r == null) remove("app_role") else putString("app_role", r) }.apply(); _role.value = r }

    /** Permission walk-through shown once per device (survives logout on purpose). */
    var permissionsOnboarded: Boolean
        get() = prefs.getBoolean("permissions_onboarded", false)
        set(v) { prefs.edit().putBoolean("permissions_onboarded", v).apply() }
    val tokenAgeMs: Long get() = System.currentTimeMillis() - prefs.getLong(K_ISSUED, 0L)

    /** Stable per-install id so the backend can de-duplicate FCM tokens per device. */
    val deviceId: String
        get() = prefs.getString(K_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(K_DEVICE, it).apply() }

    var fcmToken: String?
        get() = prefs.getString(K_FCM, null)
        set(v) = prefs.edit().putString(K_FCM, v).apply()

    fun save(s: Session) {
        prefs.edit().putString(K_TOKEN, s.token).putString(K_RNAME, s.restaurantName).putString(K_LOGIN, s.login)
            .putString(K_LAST_LOGIN, s.login).putLong(K_ISSUED, System.currentTimeMillis()).apply()
        _session.value = s
    }

    /** Sliding refresh: same restaurant, fresh expiry. */
    fun updateToken(token: String) {
        val cur = _session.value ?: return
        prefs.edit().putString(K_TOKEN, token).putLong(K_ISSUED, System.currentTimeMillis()).apply()
        _session.value = cur.copy(token = token)
    }

    /** Called on 401 or logout: token gone, UI returns to login. */
    fun expire() {
        prefs.edit().remove(K_TOKEN).remove(K_RNAME).remove(K_LOGIN).remove(K_ISSUED).apply() // K_LAST_LOGIN stays on purpose
        _session.value = null
    }

    private fun read(): Session? {
        val t = prefs.getString(K_TOKEN, null) ?: return null
        return Session(t, prefs.getString(K_RNAME, "") ?: "", prefs.getString(K_LOGIN, "") ?: "")
    }

    private companion object {
        const val K_TOKEN = "token"; const val K_RNAME = "rname"; const val K_LOGIN = "login"
        const val K_DEVICE = "device"; const val K_FCM = "fcm"
        const val K_LAST_LOGIN = "last_login"; const val K_ISSUED = "issued_at"
    }
}
