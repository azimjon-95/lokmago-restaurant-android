package uz.lokmago.restaurant.alert

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.IBinder
import android.os.VibrationEffect
import android.os.PowerManager
import android.os.Vibrator
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.local.SettingsStore
import uz.lokmago.restaurant.domain.OrderQueue

/**
 * Keeps the process alive and rings until the queue is empty (or staff silences it).
 * Runs as a media-playback foreground service so it survives app-in-background / screen-off.
 */
@AndroidEntryPoint
class OrderAlertService : Service() {
    @Inject lateinit var queue: OrderQueue
    @Inject lateinit var alert: AlertController
    @Inject lateinit var notifier: Notifier
    @Inject lateinit var settings: SettingsStore

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var savedAlarmVolume: Int? = null
    private val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SILENCE) alert.silence()
        // Must call startForeground quickly after startForegroundService().
        val q = queue.state.value
        ServiceCompat.startForeground(this, Notifier.SERVICE_ID,
            notifier.serviceNotification(q.head, q.waiting.size, alert.silenced.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        if (job == null) {
            job = scope.launch {
                combine(queue.state, alert.silenced, alert.unresolved, settings.state) { s, silenced, unresolved, cfg ->
                    Snapshot(s, silenced, unresolved.isNotEmpty(), cfg.sound, cfg.vibration, cfg.volume)
                }.collect { render(it) }
            }
        }
        return START_NOT_STICKY
    }

    private data class Snapshot(val q: OrderQueue.State, val silenced: Boolean, val loading: Boolean, val sound: Boolean, val vibe: Boolean, val volume: Int)

    private fun render(s: Snapshot) {
        if (s.q.isEmpty && !s.loading) {          // nothing left to decide → stop ringing, go away
            stopSound(); stopVibration()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        getSystemService(NotificationManager::class.java).notify(
            Notifier.SERVICE_ID, notifier.serviceNotification(s.q.head, s.q.waiting.size, s.silenced))
        if (!s.silenced && s.sound) { applyVolume(s.volume); startSound() } else stopSound()
        if (!s.silenced && s.vibe) startVibration() else stopVibration()
    }

    private fun startSound() {
        if (player?.isPlaying == true) return
        stopSound()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attrs)
                setWakeMode(this@OrderAlertService, PowerManager.PARTIAL_WAKE_LOCK)   // CPU stays awake while ringing with the screen off
                resources.openRawResourceFd(R.raw.order_alert).use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = true
                prepare(); start()
            }
        }.getOrNull()
    }

    private fun stopSound() {
        player?.runCatching { stop(); release() }; player = null
        savedAlarmVolume?.let { v -> runCatching { getSystemService(AudioManager::class.java).setStreamVolume(AudioManager.STREAM_ALARM, v, 0) } }
        savedAlarmVolume = null
    }

    /**
     * The alarm stream is raised to the level chosen in Sozlamalar (Past / O'rta / Baland) and restored afterwards.
     * It is the alarm stream, so a silent / vibrate-only ringer does not mute it.
     */
    private fun applyVolume(level: Int) {
        runCatching {
            val am = getSystemService(AudioManager::class.java)
            if (savedAlarmVolume == null) savedAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            val frac = when (level) { 0 -> 0.4f; 1 -> 0.7f; else -> 1.0f }
            am.setStreamVolume(AudioManager.STREAM_ALARM, (am.getStreamMaxVolume(AudioManager.STREAM_ALARM) * frac).toInt().coerceAtLeast(1), 0)
        } // SecurityException under some Do-Not-Disturb policies: ring at the current volume instead
    }

    @Suppress("DEPRECATION")
    private fun startVibration() {
        if (vibrator != null) return
        vibrator = getSystemService(Vibrator::class.java)?.also {
            it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 600, 300, 600, 1500), 0), attrs)
        }
    }

    private fun stopVibration() { vibrator?.cancel(); vibrator = null }

    override fun onDestroy() {
        stopSound(); stopVibration(); scope.cancel(); super.onDestroy()
    }

    companion object { const val ACTION_SILENCE = "uz.lokmago.restaurant.SILENCE" }
}
