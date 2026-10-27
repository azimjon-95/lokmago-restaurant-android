package uz.lokmago.restaurant.ui.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.alert.ReadinessItem
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

/**
 * Permission walk-through (shown once after the first login, and from the Home banner). Every row says WHY it is needed and has a
 * one-tap fix. Android never lets an app grant these to itself: notifications are the only one with a system dialog, the rest
 * are switched on by the user in system settings. Nothing is forced; "Keyinroq" is always there and the Home banner keeps reminding.
 */
@Composable
fun PermissionsDialog(items: List<ReadinessItem>, onChanged: () -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onChanged() }

    fun fix(item: ReadinessItem) {
        if (item.id == "notifications" && Build.VERSION.SDK_INT >= 33 && !asked) {      // the one real system dialog
            asked = true; notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        item.fix?.let { runCatching { ctx.startActivity(Intent(it).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
    }

    val missing = items.firstOrNull { !it.ok }
    val oem = Build.MANUFACTURER.lowercase()
    val aggressive = listOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus", "samsung", "tecno", "infinix").any { oem.contains(it) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Lg.Bg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    LgIcon(R.drawable.ic_bell_ring, Lg.Orange, 40.dp)
                    Text("Buyurtma o'tkazib yuborilmasin", color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Telefon bloklangan yoki ilova yopiq bo'lsa ham yangi buyurtma ovoz bilan xabar berishi uchun quyidagilar yoqilgan bo'lishi kerak.", color = Lg.TextSecondary, fontSize = 14.sp)

                    items.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp),
                            verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background((if (item.ok) Lg.Green else Lg.Orange).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                LgIcon(if (item.ok) R.drawable.ic_check else R.drawable.ic_warning, if (item.ok) Lg.GreenText else Lg.Orange, 16.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, color = Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text(item.hint, color = Lg.Muted, fontSize = 12.sp)
                            }
                            if (!item.ok) Text("Sozlash", color = Dt.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Lg.Orange).clickable { fix(item) }.padding(horizontal = 12.dp, vertical = 7.dp))
                        }
                    }

                    if (aggressive) Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.CardStat).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Telefoningizda (${Build.MANUFACTURER}) qo'shimcha", color = Lg.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Sozlamalar → Ilovalar → LGO Business → «Avto-ishga tushirish» va «Fonda ishlashga ruxsat»ni yoqing. Aks holda tizim ilovani to'xtatib qo'yishi va buyurtma kechikishi mumkin.", color = Lg.Muted, fontSize = 12.sp)
                        Text("Ilova sozlamalarini ochish", color = Lg.GreenText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable { runCatching { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } })
                    }

                    Text("Ruxsatlarni majburan olib bo'lmaydi — ularni faqat siz yoqasiz. Yoqmasangiz ilova ochiq paytda ishlayveradi, lekin yopiq yoki bloklangan telefonda yangi buyurtma haqida xabar kelmaydi.", color = Lg.Muted, fontSize = 12.sp)
                }
                Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PrimaryButton(
                        if (missing == null) "Tayyor" else "Sozlash: ${missing.title}", icon = if (missing == null) R.drawable.ic_check else null,
                        color = if (missing == null) Lg.Green else Lg.Orange,
                    ) { if (missing == null) onDismiss() else fix(missing) }
                    if (missing != null) Text("Keyinroq", color = Lg.Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(12.dp))
                }
            }
        }
    }
}
