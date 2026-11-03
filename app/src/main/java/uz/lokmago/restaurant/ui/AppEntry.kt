package uz.lokmago.restaurant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.courier.CourierLoginScreen
import uz.lokmago.restaurant.ui.courier.CourierRoot
import uz.lokmago.restaurant.ui.theme.Lg

@HiltViewModel
class RoleViewModel @Inject constructor(private val session: SessionStore) : ViewModel() {
    val role = session.role
    val courier = session.courier
    val lastCourierLogin: String get() = session.lastCourierLogin
    fun pick(role: String?) = session.setRole(role)
}

/**
 * BITTA ilova, IKKI tomon: birinchi ochilganda «Restoran boshqaruvi» yoki «Kuryer» tanlanadi — tanlangani bilan login/parol so'raladi va
 * o'sha bo'limga kiriladi (alohida akkauntlar, alohida tokenlar, alohida ekranlar). Tanlov eslab qolinadi; login ekranidan o'zgartirish mumkin.
 */
@Composable
fun AppEntry(restaurantContent: @Composable (onSwitchSide: () -> Unit) -> Unit, vm: RoleViewModel = hiltViewModel()) {
    val role by vm.role.collectAsStateWithLifecycle()
    val courier by vm.courier.collectAsStateWithLifecycle()
    when (role) {
        null -> RoleChooserScreen(onPick = vm::pick)
        "courier" -> if (courier == null) CourierLoginScreen(vm.lastCourierLogin, onSwitchSide = { vm.pick(null) }) else CourierRoot()
        else -> restaurantContent { vm.pick(null) }
    }
}

@Composable
fun RoleChooserScreen(onPick: (String) -> Unit) {
    Column(Modifier.fillMaxSize().background(Lg.Bg).statusBarsPadding().navigationBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_lgo), "LGO Business", Modifier.size(110.dp).clip(RoundedCornerShape(26.dp)))
        Spacer(Modifier.height(14.dp))
        Text("LGO Business", color = Lg.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Qaysi tomon bilan ishlaysiz?", color = Lg.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(32.dp))
        RoleCard(R.drawable.ic_store, "Restoran boshqaruvi", "Buyurtmalar, menyu, stop list, hisobot", Lg.Green) { onPick("restaurant") }
        Spacer(Modifier.height(14.dp))
        RoleCard(R.drawable.ic_delivery, "Kuryer", "Smena, yetkazish takliflari, tarix", Lg.Orange) { onPick("courier") }
    }
}

@Composable
private fun RoleCard(icon: Int, title: String, hint: String, accent: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Lg.Card).border(1.5.dp, accent.copy(alpha = 0.6f), RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(20.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(accent.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { LgIcon(icon, accent, 30.dp) }
        Column(Modifier.weight(1f)) {
            Text(title, color = Lg.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(hint, color = Lg.Muted, fontSize = 13.sp)
        }
        LgIcon(R.drawable.ic_chevron_right, Lg.Muted, 22.dp)
    }
}
