package uz.lokmago.restaurant.alert

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.realtime.NetworkMonitor

/**
 * Telefonning haqiqiy holatini o'qiydi va har bir qadam uchun TO'G'RI tizim oynasini ochadi — har qanday Android (8–15) va
 * ishlab chiqaruvchi (Xiaomi, Samsung, Huawei, Oppo, Vivo, Realme, Tecno...) uchun. Telefonda yo'q sahifa ko'rsatilmaydi.
 */
@Singleton
class PhoneSetupProbe @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val readiness: AlertReadiness,
    private val net: NetworkMonitor,
    private val session: SessionStore,
) {
    private val pkg get() = ctx.packageName

    /** Mahalliy holat; ulanish maydonlarini ViewModel to'ldiradi. */
    fun local(): PhoneSetup.State {
        val r = readiness.check().associateBy { it.id }
        return PhoneSetup.State(
            notifications = r["notifications"]?.ok ?: true,
            sound = r["channel"]?.ok ?: true,
            background = r["battery"]?.ok ?: true,
            lockScreenNeeded = r.containsKey("fullscreen"), lockScreen = r["fullscreen"]?.ok ?: true,
            autostartNeeded = autostartIntent() != null, autostartConfirmed = session.autostartConfirmed,
            appHasPush = r["firebase"]?.ok ?: true,
            googleServices = hasGoogleServices(),
            online = net.online.value,
            connecting = false, connected = false, serviceDown = false,
        )
    }

    private fun hasGoogleServices(): Boolean = runCatching { ctx.packageManager.getPackageInfo("com.google.android.gms", 0); true }.getOrDefault(false)

    /** Ishlab chiqaruvchining «Avto-ishga tushirish» sahifasi — faqat telefonda haqiqatan bor bo'lsa. */
    fun autostartIntent(): Intent? = AUTOSTART.asSequence()
        .map { (p, c) -> Intent().setClassName(p, c) }
        .firstOrNull { i -> runCatching { ctx.packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY) != null }.getOrDefault(false) }

    /** Qadam uchun tizim oynasi (birinchisi ochilmasa, keyingisi sinab ko'riladi). */
    fun intents(id: PhoneSetup.Id): List<Intent> = when (id) {
        PhoneSetup.Id.NOTIFICATIONS -> listOf(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
            details(),
        )
        PhoneSetup.Id.SOUND -> listOf(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg).putExtra(Settings.EXTRA_CHANNEL_ID, Notifier.CH_ORDERS),
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, pkg),
        )
        PhoneSetup.Id.BACKGROUND -> listOf(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),     // bitta «Ruxsat berish» tugmali oyna
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            details(),
        )
        PhoneSetup.Id.LOCK_SCREEN -> if (Build.VERSION.SDK_INT >= 34) listOf(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$pkg")), details()) else listOf(details())
        PhoneSetup.Id.AUTOSTART -> listOfNotNull(autostartIntent(), details())
        PhoneSetup.Id.CONNECTION -> emptyList()
    }

    fun internetIntents(): List<Intent> = listOfNotNull(
        if (Build.VERSION.SDK_INT >= 29) Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY) else null,
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
    )

    fun updateIntents(): List<Intent> = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg")),
    )

    private fun details() = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))

    /** @return true — biror oyna ochildi */
    fun open(intents: List<Intent>, from: Context): Boolean =
        intents.any { i -> runCatching { from.startActivity(Intent(i).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess }

    companion object {
        /** Xiaomi/Redmi/POCO, Huawei, Honor, Oppo/Realme/OnePlus (ColorOS), Vivo/iQOO, Asus, Letv, Tecno/Infinix (HiOS/XOS). */
        private val AUTOSTART = listOf(
            "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.huawei.systemmanager" to "com.huawei.systemmanager.optimize.process.ProtectActivity",
            "com.hihonor.systemmanager" to "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
            "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
            "com.oplus.safecenter" to "com.oplus.safecenter.permission.startup.StartupAppListActivity",
            "com.oppo.safe" to "com.oppo.safe.permission.startup.StartupAppListActivity",
            "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
            "com.iqoo.secure" to "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity",
            "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
            "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
            "com.transsion.phonemaster" to "com.cyin.himgr.autostart.AutoStartActivity",
        )
    }
}
