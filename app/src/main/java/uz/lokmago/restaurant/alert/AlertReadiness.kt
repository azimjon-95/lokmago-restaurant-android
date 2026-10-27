package uz.lokmago.restaurant.alert

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class ReadinessItem(val id: String, val title: String, val hint: String, val ok: Boolean, val fix: Intent?)

/**
 * "Will a new order really wake this phone?" — the four things that decide it, each with a one-tap fix.
 * Android never lets an app grant these to itself; the user has to flip them in system settings.
 */
@Singleton
class AlertReadiness @Inject constructor(@ApplicationContext private val ctx: Context) {
    private val pkg get() = ctx.packageName

    fun check(): List<ReadinessItem> {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val list = mutableListOf<ReadinessItem>()

        list += ReadinessItem(
            "notifications", "Bildirishnomalar", "Yangi buyurtma xabari kelishi uchun yoqilgan bo'lishi shart",
            NotificationManagerCompat.from(ctx).areNotificationsEnabled(),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )

        val importance = nm.getNotificationChannel(Notifier.CH_ORDERS)?.importance ?: NotificationManager.IMPORTANCE_HIGH
        list += ReadinessItem(
            "channel", "Buyurtma kanali ovozi", "«Yangi buyurtma» kanali ovozli va yuqori muhimlikda bo'lsin",
            importance >= NotificationManager.IMPORTANCE_HIGH,
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_ORDERS),
        )

        if (Build.VERSION.SDK_INT >= 34) list += ReadinessItem(
            "fullscreen", "Bloklangan ekranda to'liq ekran", "Telefon bloklangan paytda buyurtma ekranni o'zi yoqishi uchun",
            nm.canUseFullScreenIntent(),
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")),
        )

        list += ReadinessItem(
            "battery", "Batareya cheklovi o'chirilgan", "Telefon uxlaganda tizim ilovani to'xtatib qo'ymasligi uchun «Cheklovsiz» qiling",
            ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(pkg),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
        )
        return list
    }
}
