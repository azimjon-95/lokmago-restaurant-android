package uz.lokmago.restaurant.courier

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uz.lokmago.restaurant.MainActivity
import uz.lokmago.restaurant.R

/**
 * Kuryer bildirishnomalari. Taklif — restoran signalidan ALOHIDA MELODIYA (res/raw/courier_alert) va alohida kanal.
 * Bosilsa kuryer ekrani ochiladi. Mijoz ma'lumoti push'da ham, bildirishnomada ham YO'Q.
 */
@Singleton
class CourierNotifier @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val sound = Uri.parse("android.resource://${ctx.packageName}/raw/courier_alert")      // NOM bilan: resurs almashsa kod o'zgarmaydi
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_OFFERS, "Yangi yetkazish takliflari", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Yaqin atrofdagi yangi yetkazish buyurtmasi (alohida melodiya)"
            setSound(sound, attrs); enableVibration(true); vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 700)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        })
        nm.createNotificationChannel(NotificationChannel(CH_SHIFT, "Ish smenasi", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Smena ochiq: joylashuvingiz har 5 soniyada yuboriladi"; setSound(null, null); enableVibration(false); setShowBadge(false)
        })
    }

    private fun open(): PendingIntent = PendingIntent.getActivity(ctx, 7, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun notifyOffer(assignmentId: String, title: String, body: String) {
        ensureChannels()
        val n = NotificationCompat.Builder(ctx, CH_OFFERS).setSmallIcon(R.drawable.ic_delivery).setContentTitle(title).setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body)).setPriority(NotificationCompat.PRIORITY_HIGH).setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setColor(0xFFFB8300.toInt()).setAutoCancel(true).setOnlyAlertOnce(false)
            .setLargeIcon(runCatching { BitmapFactory.decodeResource(ctx.resources, R.drawable.logo_lgo, BitmapFactory.Options().apply { inSampleSize = 2 }) }.getOrNull())
            .setContentIntent(open()).addAction(0, "🛵 Ko'rish", open()).setTimeoutAfter(120_000).build()
        runCatching { nm.notify(idFor(assignmentId), n) }                                    // bildirishnoma ruxsati yo'q bo'lsa jim
    }

    fun notifyTest(title: String, body: String) {
        ensureChannels()
        runCatching { nm.notify(TEST_ID, NotificationCompat.Builder(ctx, CH_OFFERS).setSmallIcon(R.drawable.ic_delivery).setContentTitle(title).setContentText(body).setAutoCancel(true).setContentIntent(open()).build()) }
    }

    fun cancel(assignmentId: String) = nm.cancel(idFor(assignmentId))
    private fun idFor(id: String) = 6000 + (id.hashCode() and 0x0FFF)

    companion object { const val CH_OFFERS = "courier_offers_v1"; const val CH_SHIFT = "courier_shift_v1"; const val SHIFT_ID = 4350; const val TEST_ID = 4351 }
}
