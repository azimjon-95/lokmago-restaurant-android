package uz.lokmago.restaurant.alert

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.di.AppScope
import uz.lokmago.restaurant.realtime.OrderCoordinator

/**
 * ZAXIRA "UYG'OTGICH" — ilova uxlab qolmasin.
 *
 * Asosiy kanal FCM push (yuqori ustuvorlik: telefon uxlab yotgan bo'lsa ham keladi). Lekin push yetib kelmagan holatlar bor:
 * Xiaomi/Huawei kabi telefonlar ilovani o'ldiradi, internet uzilib qaytadi, Doze rejimi socket'ni uzadi. Shuning uchun har ~15 daqiqada:
 *   1) socket ulanmagan bo'lsa — qayta ulanadi;
 *   2) serverdan kutilayotgan buyurtmalar o'qiladi — bor bo'lsa odatdagi signal (ovoz + bildirishnoma) chalinadi;
 *   3) "Fon ulanishi" yoqilgan bo'lsa — o'ldirilgan xizmat qayta ishga tushiriladi.
 *
 * AlarmManager.setAndAllowWhileIdle: aniq vaqt emas (tizim Doze'da ~9-15 daqiqada bir ruxsat beradi), maxsus ruxsat KERAK EMAS
 * (SCHEDULE_EXACT_ALARM yo'q). Har chaqiriqda o'zini qayta rejalashtiradi; qayta yuklanishda BootReceiver yoqadi; chiqishda o'chadi.
 */
@AndroidEntryPoint
class OrderWatchdog : BroadcastReceiver() {
    @Inject lateinit var session: SessionStore
    @Inject lateinit var coordinator: OrderCoordinator
    @Inject @AppScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (session.token == null) { cancel(context); return }
        schedule(context)                                                      // keyingi tekshiruv (zanjir uzilmasin)
        if (session.persistentListener.value) OrderListenerService.start(context)
        val pending = goAsync()                                                // ~10 s ichida tugashi kerak
        scope.launch {
            try { withTimeoutOrNull(9_000) { coordinator.wake() } } finally { pending.finish() }
        }
    }

    companion object {
        const val INTERVAL_MS = 15 * 60_000L
        private fun pi(ctx: Context) = PendingIntent.getBroadcast(
            ctx, 4410, Intent(ctx, OrderWatchdog::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        /** Idempotent: o'sha PendingIntent — eski rejani almashtiradi, ikkita parallel zanjir bo'lmaydi. */
        fun schedule(ctx: Context) {
            runCatching {
                ctx.getSystemService(AlarmManager::class.java)
                    .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + INTERVAL_MS, pi(ctx))
            }
        }
        fun cancel(ctx: Context) { runCatching { ctx.getSystemService(AlarmManager::class.java).cancel(pi(ctx)) } }
    }
}
