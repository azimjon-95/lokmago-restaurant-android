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

    /** Stable per-install id so the backend can de-duplicate FCM tokens per device. */
    val deviceId: String
        get() = prefs.getString(K_DEVICE, null) ?: UUID.randomUUID().toString().also { prefs.edit().putString(K_DEVICE, it).apply() }

    var fcmToken: String?
        get() = prefs.getString(K_FCM, null)
        set(v) = prefs.edit().putString(K_FCM, v).apply()

    fun save(s: Session) {
        prefs.edit().putString(K_TOKEN, s.token).putString(K_RNAME, s.restaurantName).putString(K_LOGIN, s.login).apply()
        _session.value = s
    }

    /** Called on 401 or logout: token gone, UI returns to login. */
    fun expire() {
        prefs.edit().remove(K_TOKEN).remove(K_RNAME).remove(K_LOGIN).apply()
        _session.value = null
    }

    private fun read(): Session? {
        val t = prefs.getString(K_TOKEN, null) ?: return null
        return Session(t, prefs.getString(K_RNAME, "") ?: "", prefs.getString(K_LOGIN, "") ?: "")
    }

    private companion object {
        const val K_TOKEN = "token"; const val K_RNAME = "rname"; const val K_LOGIN = "login"
        const val K_DEVICE = "device"; const val K_FCM = "fcm"
    }
}
