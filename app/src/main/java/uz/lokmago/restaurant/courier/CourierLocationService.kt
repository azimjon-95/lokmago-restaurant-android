package uz.lokmago.restaurant.courier

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.MainActivity
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.repo.AppError

/**
 * «Ishni boshladim» dan «Ishni yakunladim» gacha kuryerning joylashuvini HAR 5 SONIYADA serverga (Redis) yuboradi.
 * Foreground xizmat (turi: location) — ilova ekranda bo'lmasa, telefon bloklangan bo'lsa ham davom etadi; doimiy bildirishnoma ko'rinib turadi
 * (Android talabi va kuryer uchun shaffoflik). ACCESS_BACKGROUND_LOCATION KERAK EMAS: xizmat ilova ko'rinib turgan paytda boshlanadi.
 * Server «smena yopiq» (409) yoki «token yaroqsiz» (401) desa — xizmat o'zi to'xtaydi. Tarmoq yo'q bo'lsa — keyingi nuqta bilan qayta uriniladi.
 */
@AndroidEntryPoint
class CourierLocationService : Service() {
    @Inject lateinit var api: CourierApi
    @Inject lateinit var session: SessionStore
    @Inject lateinit var notifier: CourierNotifier

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var lm: LocationManager? = null
    @Volatile private var last: Location? = null
    private val listener = LocationListener { loc -> if (better(loc, last)) last = loc }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("MissingPermission")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifier.ensureChannels()
        val open = PendingIntent.getActivity(this, 8, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(this, CourierNotifier.CH_SHIFT).setSmallIcon(R.drawable.ic_delivery).setContentTitle("Ish smenasi faol")
            .setContentText("Joylashuvingiz har 5 soniyada yuboriladi").setOngoing(true).setContentIntent(open).setPriority(NotificationCompat.PRIORITY_LOW).build()
        try { ServiceCompat.startForeground(this, CourierNotifier.SHIFT_ID, n, if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0) }
        catch (e: Exception) { stopSelf(); return START_NOT_STICKY }          // masalan: joylashuv ruxsati yo'q
        if (job == null) {
            lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                try { if (lm?.isProviderEnabled(p) == true) lm?.requestLocationUpdates(p, 2_000L, 0f, listener, Looper.getMainLooper()) }
                catch (_: SecurityException) { stopSelf(); return START_NOT_STICKY }
                catch (_: Exception) { }
            }
            job = scope.launch { loop() }
        }
        return START_STICKY
    }

    private suspend fun loop() {
        while (scope.isActive) {
            val loc = last
            if (loc != null && System.currentTimeMillis() - loc.time < 30_000) {
                try {
                    courierCall { api.location(CLocationReq(loc.latitude, loc.longitude, if (loc.hasAccuracy()) loc.accuracy.toDouble() else null, if (loc.hasSpeed()) loc.speed.toDouble() else null, if (loc.hasBearing()) loc.bearing.toDouble() else null)) }
                } catch (e: AppError.Unauthorized) { stopSelf(); return }
                catch (e: AppError.Message) { if (e.message?.contains("Ishni boshladim") == true) { stopSelf(); return } }
                catch (_: AppError) { /* offline / server band: keyingi nuqta bilan */ }
            }
            delay(5_000)
        }
    }

    /** Yangi nuqta eskisidan yaxshimi (yangiroq yoki aniqroq) — GPS va tarmoq nuqtalari aralashib ketmasin. */
    private fun better(n: Location, o: Location?): Boolean = o == null || n.time - o.time > 8_000 || (n.hasAccuracy() && (!o.hasAccuracy() || n.accuracy <= o.accuracy + 15))

    override fun onDestroy() {
        runCatching { lm?.removeUpdates(listener) }
        scope.cancel(); super.onDestroy()
    }

    companion object {
        fun start(ctx: Context) { androidx.core.content.ContextCompat.startForegroundService(ctx, Intent(ctx, CourierLocationService::class.java)) }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, CourierLocationService::class.java)) }
    }
}
