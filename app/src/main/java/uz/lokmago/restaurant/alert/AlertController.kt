package uz.lokmago.restaurant.alert

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Single owner of the "alarm" state. ONE alarm covers the whole order queue, no matter how many
 * orders are waiting. "Signalni to'xtatish" only silences it — it never accepts anything.
 */
@Singleton
class AlertController @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val _silenced = MutableStateFlow(false)
    val silenced: StateFlow<Boolean> = _silenced.asStateFlow()

    /** Push ids we know about but whose order data is still being loaded from the backend. */
    private val _unresolved = MutableStateFlow<Set<String>>(emptySet())
    val unresolved: StateFlow<Set<String>> = _unresolved.asStateFlow()

    fun markUnresolved(id: String) = _unresolved.update { it + id }
    fun markResolved(id: String) = _unresolved.update { it - id }

    fun silence() { _silenced.value = true }
    /** A brand-new order always re-arms the alarm, even if staff silenced the previous one. */
    fun rearm() { _silenced.value = false }

    /** @return true if the alarm service started (it then owns sound + vibration). */
    fun start(): Boolean = try {
        ContextCompat.startForegroundService(ctx, Intent(ctx, OrderAlertService::class.java))
        true
    } catch (e: Exception) { // e.g. ForegroundServiceStartNotAllowedException — fall back to channel sound
        false
    }

    fun reset() { _silenced.value = false; _unresolved.value = emptySet() }
}
