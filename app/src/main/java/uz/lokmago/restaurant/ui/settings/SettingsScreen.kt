package uz.lokmago.restaurant.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.local.SettingsStore
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Lg

@HiltViewModel
class SettingsViewModel @Inject constructor(private val store: SettingsStore, session: SessionStore) : ViewModel() {
    val settings = store.state
    val session = session.session
    fun sound(v: Boolean) = viewModelScope.launch { store.setSound(v) }
    fun vibration(v: Boolean) = viewModelScope.launch { store.setVibration(v) }
    fun notifications(v: Boolean) = viewModelScope.launch { store.setNotifications(v) }
}

@Composable
fun SettingsScreen(onLogout: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val cfg by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Sozlamalar", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Group("Buyurtmalar") {
            Toggle(R.drawable.ic_volume, "Buyurtma ovozi", cfg.sound, vm::sound)
            Toggle(R.drawable.ic_vibrate, "Vibratsiya", cfg.vibration, vm::vibration)
            Toggle(R.drawable.ic_bell, "Bildirishnoma", cfg.notifications, vm::notifications)
        }
        Group("Hisob") {
            Row2(R.drawable.ic_user, "Login", session?.login.orEmpty())
            Row2(R.drawable.ic_store, "Restoran nomi", session?.restaurantName.orEmpty())
            Row(Modifier.fillMaxWidth().clickableNoRipple(onLogout).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                LgIcon(R.drawable.ic_logout, Lg.Red); Text("Chiqish", color = Lg.Red, fontWeight = FontWeight.SemiBold)
            }
        }
        Group("Ilova") {
            Row2(R.drawable.ic_download, "Versiya", "v${BuildConfig.VERSION_NAME}")
            Row2(R.drawable.ic_help, "Yordam / Support", "")
            Row2(R.drawable.ic_shield, "Maxfiylik siyosati", "")
            Row2(R.drawable.ic_document, "Foydalanish shartlari", "")
        }
    }
}

@Composable private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, color = Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    SectionCard(content = content)
}

@Composable private fun Toggle(icon: Int, label: String, value: Boolean, onChange: (Boolean) -> Unit) =
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LgIcon(icon, Lg.Muted); Text(label, color = Lg.Text, modifier = Modifier.weight(1f))
        Switch(value, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Lg.Green, checkedThumbColor = Lg.Bg))
    }

@Composable private fun Row2(icon: Int, label: String, value: String) =
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LgIcon(icon, Lg.Muted); Text(label, color = Lg.Text, modifier = Modifier.weight(1f)); Text(value, color = Lg.Muted, fontSize = 14.sp)
    }
