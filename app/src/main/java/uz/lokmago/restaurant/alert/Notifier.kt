package uz.lokmago.restaurant.alert

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uz.lokmago.restaurant.MainActivity
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.local.SettingsStore
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.util.som

@Singleton
class Notifier @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val settings: SettingsStore,
) {
    private val nm = ctx.getSystemService(NotificationManager::class.java)

    fun ensureChannels() {
        val sound = Uri.parse("android.resource://${ctx.packageName}/${R.raw.order_alert}")
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        nm.createNotificationChannel(NotificationChannel(CH_ORDERS, ctx.getString(R.string.channel_orders_name), NotificationManager.IMPORTANCE_HIGH).apply {
            description = ctx.getString(R.string.channel_orders_desc)
            setSound(sound, attrs)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 300, 600, 300, 900)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setBypassDnd(false)
        })
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, ctx.getString(R.string.channel_alert_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = ctx.getString(R.string.channel_alert_desc)
            setSound(null, null); enableVibration(false)
        })
    }

    fun canPost(): Boolean =
        settings.state.value.notifications &&
            (android.os.Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    /** Per-order notification. Same id per order => duplicates (socket + FCM) collapse into one. */
    fun notifyNew(orderId: String, title: String, body: String, silent: Boolean) {
        if (!canPost()) return
        val n = NotificationCompat.Builder(ctx, CH_ORDERS)
            .setSmallIcon(R.drawable.ic_bell_ring)
            .setContentTitle(title)
            .setContentText(body.lineSequence().first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true).setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setSilent(silent)                       // the alarm service already rings; avoid double sound
            .setContentIntent(openOrder(orderId))
            .setFullScreenIntent(openOrder(orderId, fromAlert = true), true)
            .build()
        nm.notify(idFor(orderId), n)
    }

    fun newOrderText(o: Order) = "#${o.number} — ${o.total.som()}\n${o.itemsCount} ta taom • ${o.payment.label}"

    fun cancel(orderId: String) = nm.cancel(idFor(orderId))
    fun cancelAllOrders() = nm.activeNotifications.filter { it.id != SERVICE_ID }.forEach { nm.cancel(it.id) }

    /** Foreground-service notification: shows queue head + how many are waiting. */
    fun serviceNotification(head: Order?, waiting: Int, silenced: Boolean): Notification {
        val b = NotificationCompat.Builder(ctx, CH_ALERT)
            .setSmallIcon(R.drawable.ic_bell_ring)
            .setContentTitle(if (head != null) "Yangi buyurtma #${head.number}" else "Yangi buyurtma")
            .setContentText(head?.let { "${it.total.som()} • ${it.itemsCount} ta taom • ${it.payment.label}" } ?: "Ma'lumot yuklanmoqda…")
            .setSubText(if (waiting > 0) "Navbatda yana $waiting ta" else null)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true).setOnlyAlertOnce(true).setSilent(true)
            .setContentIntent(openOrder(head?.id, fromAlert = true))
        if (!silenced) {
            b.addAction(0, "Signalni to'xtatish", PendingIntent.getService(
                ctx, 1, Intent(ctx, OrderAlertService::class.java).setAction(OrderAlertService.ACTION_SILENCE),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        return b.build()
    }

    private fun openOrder(orderId: String?, fromAlert: Boolean = false): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_ORDER_ID, orderId)
            .putExtra(MainActivity.EXTRA_FROM_ALERT, fromAlert)
        return PendingIntent.getActivity(ctx, (orderId ?: "x").hashCode() + if (fromAlert) 7 else 0, i,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun idFor(orderId: String) = orderId.hashCode().let { if (it == SERVICE_ID) it + 1 else it }

    companion object {
        const val CH_ORDERS = "orders_new_v1"
        const val CH_ALERT = "alert_service_v1"
        const val SERVICE_ID = 4242
    }
}
