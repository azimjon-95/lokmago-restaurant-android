package uz.lokmago.restaurant.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.ds by preferencesDataStore("lokmago_settings")

/** [volume]: 0 Past, 1 O'rta, 2 Baland (alarm stream). [autoRefreshSec]: safety-net sync while the app is open; 0 = off. */
data class AppSettings(
    val sound: Boolean = true, val vibration: Boolean = true, val notifications: Boolean = true,
    val volume: Int = 2, val autoRefreshSec: Int = 10,
)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val kSound = booleanPreferencesKey("sound")
    private val kVibe = booleanPreferencesKey("vibration")
    private val kNotif = booleanPreferencesKey("notifications")
    private val kVolume = intPreferencesKey("volume")
    private val kRefresh = intPreferencesKey("auto_refresh_sec")

    val state: StateFlow<AppSettings> = ctx.ds.data
        .map { AppSettings(it[kSound] ?: true, it[kVibe] ?: true, it[kNotif] ?: true, it[kVolume] ?: 2, it[kRefresh] ?: 10) }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, AppSettings())

    suspend fun setSound(v: Boolean) = ctx.ds.edit { it[kSound] = v }
    suspend fun setVibration(v: Boolean) = ctx.ds.edit { it[kVibe] = v }
    suspend fun setNotifications(v: Boolean) = ctx.ds.edit { it[kNotif] = v }
    suspend fun setVolume(v: Int) = ctx.ds.edit { it[kVolume] = v.coerceIn(0, 2) }
    suspend fun setAutoRefresh(sec: Int) = ctx.ds.edit { it[kRefresh] = sec }
}
