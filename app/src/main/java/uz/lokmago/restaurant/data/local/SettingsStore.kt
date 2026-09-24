package uz.lokmago.restaurant.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
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

data class AppSettings(val sound: Boolean = true, val vibration: Boolean = true, val notifications: Boolean = true)

@Singleton
class SettingsStore @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val kSound = booleanPreferencesKey("sound")
    private val kVibe = booleanPreferencesKey("vibration")
    private val kNotif = booleanPreferencesKey("notifications")

    val state: StateFlow<AppSettings> = ctx.ds.data
        .map { AppSettings(it[kSound] ?: true, it[kVibe] ?: true, it[kNotif] ?: true) }
        .stateIn(CoroutineScope(SupervisorJob() + Dispatchers.IO), SharingStarted.Eagerly, AppSettings())

    suspend fun setSound(v: Boolean) = ctx.ds.edit { it[kSound] = v }
    suspend fun setVibration(v: Boolean) = ctx.ds.edit { it[kVibe] = v }
    suspend fun setNotifications(v: Boolean) = ctx.ds.edit { it[kNotif] = v }
}
