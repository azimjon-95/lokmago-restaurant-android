package uz.lokmago.restaurant.ui.settings

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.alert.AlertReadiness
import uz.lokmago.restaurant.alert.ReadinessItem
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.local.SettingsStore
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

private val VOLUME = listOf("Past", "O'rta", "Baland")
private val REFRESH = listOf(0, 5, 10, 30)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore, session: SessionStore, private val readiness: AlertReadiness) : ViewModel() {
    val settings = store.state
    val session = session.session
    val checks = MutableStateFlow(readiness.check())
    fun refreshChecks() { checks.value = readiness.check() }
    fun sound(v: Boolean) = viewModelScope.launch { store.setSound(v) }
    fun vibration(v: Boolean) = viewModelScope.launch { store.setVibration(v) }
    fun notifications(v: Boolean) = viewModelScope.launch { store.setNotifications(v) }
    fun cycleVolume(cur: Int) = viewModelScope.launch { store.setVolume((cur + 1) % VOLUME.size) }
    fun cycleRefresh(cur: Int) = viewModelScope.launch { store.setAutoRefresh(REFRESH[(REFRESH.indexOf(cur).coerceAtLeast(0) + 1) % REFRESH.size]) }
}

@Composable
fun SettingsScreen(onLogout: () -> Unit, onOpenReport: () -> Unit = {}, onOpenReviews: () -> Unit = {}, vm: SettingsViewModel = hiltViewModel()) {
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    val checks by vm.checks.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    // The user flips switches in system settings and comes back: re-check on every resume.
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) vm.refreshChecks() }
        owner.lifecycle.addObserver(obs); onDispose { owner.lifecycle.removeObserver(obs) }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(Dt.spaceGap)) {
        Text("Sozlamalar", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(top = 8.dp))

        Group("Biznes") {
            Row2(R.drawable.ic_report, "Hisobot", "Savdo va grafiklar") { onOpenReport() }
            Row2(R.drawable.ic_message, "Mijozlar izohlari", "Baho va fikrlar", last = true) { onOpenReviews() }
        }

        Group("Buyurtmalar") {
            Toggle(R.drawable.ic_volume, "Buyurtma ovozi", cfg.sound, vm::sound)
            Toggle(R.drawable.ic_vibrate, "Vibratsiya", cfg.vibration, vm::vibration)
            Toggle(R.drawable.ic_bell, "Bildirishnoma", cfg.notifications, vm::notifications)
            Row2(R.drawable.ic_refresh, "Avto yangilash", if (cfg.autoRefreshSec == 0) "O'chiq" else "${cfg.autoRefreshSec} soniya") { vm.cycleRefresh(cfg.autoRefreshSec) }
            Row2(R.drawable.ic_volume, "Ovozni sozlash", VOLUME[cfg.volume.coerceIn(0, 2)], last = true) { vm.cycleVolume(cfg.volume) }
        }

        // Does a new order really wake THIS phone? Each line says why it matters and fixes itself in one tap.
        val missing = checks.count { !it.ok }
        Group(if (missing == 0) "Ogohlantirish tayyorligi  ✓" else "Ogohlantirish tayyorligi  ($missing ta sozlanmagan)") {
            checks.forEachIndexed { i, c -> Readiness(c, ctx) { if (i < checks.lastIndex) Divider() } }
        }

        Group("Hisob") {
            Row2(R.drawable.ic_user, "Login", session?.login.orEmpty())
            Row2(R.drawable.ic_store, "Restoran nomi", session?.restaurantName.orEmpty())
            Row(Modifier.fillMaxWidth().clickableNoRipple(onLogout).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LgIcon(R.drawable.ic_logout, Lg.Red); Text("Chiqish", color = Lg.Red, fontWeight = FontWeight.SemiBold)
            }
        }
        Group("Ilova") {
            Row2(R.drawable.ic_download, "Versiya", "v${BuildConfig.VERSION_NAME}", chevron = false)
            Row2(R.drawable.ic_help, "Yordam / Support", "")
            Row2(R.drawable.ic_shield, "Maxfiylik siyosati", "")
            Row2(R.drawable.ic_document, "Foydalanish shartlari", "", last = true)
        }
    }
}

@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
    SectionCard(content = content)
}

@Composable private fun Divider() = Box(Modifier.fillMaxWidth().height(1.dp).background(Lg.Line))

@Composable private fun Toggle(icon: Int, label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LgIcon(icon, Lg.TextSecondary); Text(label, color = Lg.Text, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(
            checkedTrackColor = Lg.Green, checkedThumbColor = Dt.onAccent, checkedBorderColor = Lg.Green,
            uncheckedTrackColor = Lg.CardHigh, uncheckedThumbColor = Lg.Muted, uncheckedBorderColor = Lg.Line))
    }

@Composable private fun Row2(icon: Int, label: String, value: String, chevron: Boolean = true, last: Boolean = false, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().let { if (onClick != null) it.clickableNoRipple(onClick) else it }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LgIcon(icon, Lg.TextSecondary); Text(label, color = Lg.Text, modifier = Modifier.weight(1f))
        if (value.isNotEmpty()) Text(value, color = Lg.Muted, fontSize = 14.sp)
        if (chevron) LgIcon(R.drawable.ic_chevron_right, Lg.Muted, 16.dp)
    }
}

@Composable private fun Readiness(item: ReadinessItem, ctx: Context, divider: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (item.ok) Lg.Green.copy(alpha = 0.18f) else Lg.Orange.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            LgIcon(if (item.ok) R.drawable.ic_check else R.drawable.ic_warning, if (item.ok) Lg.GreenText else Lg.Orange, 16.dp)
        }
        Column(Modifier.weight(1f)) {
            Text(item.title, color = Lg.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(item.hint, color = Lg.Muted, fontSize = 12.sp)
        }
        if (!item.ok && item.fix != null) Text("Sozlash", color = Dt.onAccent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Lg.Orange)
                .clickableNoRipple { runCatching { ctx.startActivity(Intent(item.fix).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                .padding(horizontal = 12.dp, vertical = 7.dp))
    }
    divider()
}
