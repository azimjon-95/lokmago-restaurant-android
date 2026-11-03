package uz.lokmago.restaurant.ui.courier

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.courier.*
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

/** Kuryer tomonining ildizi: 4 bo'lim (Ish · Tarix · Hisobot · Profil), pastda suzuvchi menyu (restoran bilan bir xil uslub). */
@Composable
fun CourierRoot(vm: CourierViewModel = hiltViewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    LaunchedEffect(tab) { if (tab == 1) vm.loadHistory(); if (tab == 2) vm.loadStats() }
    Column(Modifier.fillMaxSize().background(Lg.Bg).statusBarsPadding()) {
        Box(Modifier.weight(1f)) {
            when (tab) { 0 -> WorkTab(vm, ui); 1 -> HistoryTab(ui); 2 -> StatsTab(ui); else -> ProfileTab(vm, ui) }
        }
        FloatingTabBar(
            items = listOf(BarItem(R.drawable.ic_delivery, "Ish", ui.offers.size), BarItem(R.drawable.ic_orders, "Tarix"), BarItem(R.drawable.ic_report, "Hisobot"), BarItem(R.drawable.ic_profile, "Profil")),
            selected = tab, onSelect = { tab = it }, modifier = Modifier.navigationBarsPadding(),
        )
    }
}

private fun km(m: Int?) = when { m == null -> ""; m < 950 -> "${(m / 10) * 10} m"; else -> String.format(java.util.Locale.US, "%.1f km", m / 1000.0) }
private fun minutes(n: Int) = if (n >= 60) "${n / 60} soat ${n % 60} daq" else "$n daq"

@Composable
private fun WorkTab(vm: CourierViewModel, ui: CourierUi) {
    val ctx = LocalContext.current
    var denied by remember { mutableStateOf(false) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val ok = r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ok) vm.startShift() else denied = true
    }
    fun begin() {
        val need = buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }
        perms.launch(need.toTypedArray())
    }
    ui.message?.let { msg -> LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearMessage() } }

    LazyColumn(contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(vmName(vm), color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (ui.onShift) "Smena ochiq" else "Smena yopiq", color = if (ui.onShift) Lg.GreenText else Lg.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (ui.onShift) Lg.Green else Lg.Muted))
            }
        }
        ui.message?.let { m -> item { Text(m, color = Lg.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lg.Orange.copy(alpha = 0.12f)).padding(12.dp)) } }
        // «Ishni boshladim / yakunladim» — kuryer har kuni bosadi; ochiq smenada joylashuv har 5 s yuboriladi
        item {
            if (!ui.onShift) PrimaryButton("Ishni boshladim", icon = R.drawable.ic_check_circle, loading = ui.busy) { begin() }
            else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ui.shift?.let { s -> SectionCard(bg = Lg.CardStat) {
                    Row { Text("Bugungi smena", color = Lg.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f)); Text("${s.deliveries} ta yetkazish • ${km(s.distanceM)}", color = Lg.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                } }
                Text(
                    if (ui.active != null) "Avval buyurtmani topshiring" else "Ishni yakunladim", color = if (ui.active != null) Lg.Muted else Lg.Red, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.5.dp, if (ui.active != null) Lg.Line else Lg.Red, RoundedCornerShape(16.dp))
                        .let { if (ui.active == null && !ui.busy) it.clickable { vm.endShift() } else it }.padding(16.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        if (denied) item { Text("Joylashuv ruxsatisiz smena boshlab bo'lmaydi: buyurtma sizga yaqinlik bo'yicha beriladi. Sozlamalar → Ilovalar → Joylashuv → «Ilova ishlatilayotganda»ni yoqing.", color = Lg.Orange, fontSize = 12.sp) }

        val a = ui.active
        if (a != null) item { ActiveCard(a, ui.busy, onDeliver = vm::deliver) }
        else if (ui.onShift) {
            item { Text("Yaqin atrofdagi buyurtmalar", color = Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
            if (ui.offers.isEmpty()) item { EmptyState(R.drawable.ic_delivery, "Hozircha buyurtma yo'q", "Yangi yetkazish chiqqanda bildirishnoma va ovoz bilan xabar beramiz") }
            items(ui.offers, key = { it.id }) { o -> OfferCard(o, ui.busy) { vm.accept(o.id) } }
        } else item { EmptyState(R.drawable.ic_delivery, "Ish boshlang", "«Ishni boshladim»ni bosing — yaqin atrofdagi buyurtmalar shu yerda chiqadi") }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

@Composable private fun vmName(vm: CourierViewModel): String { val me by vm.me.collectAsStateWithLifecycle(); return me?.name ?: "Kuryer" }

@Composable
private fun OfferCard(o: COfferDto, busy: Boolean, onAccept: () -> Unit) = SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(o.restaurantName.ifBlank { "Restoran" }, color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            if (o.restaurantAddress.isNotBlank()) Text(o.restaurantAddress, color = Lg.Muted, fontSize = 12.sp)
        }
        o.distanceM?.let { Text(km(it), color = Lg.GreenText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
    }
    Spacer(Modifier.height(8.dp))
    if (o.addressLabel.isNotBlank()) Text("📍 ${o.addressLabel}", color = Lg.TextSecondary, fontSize = 13.sp)
    if (o.itemsSummary.isNotBlank()) Text(o.itemsSummary, color = Lg.Muted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Haq: ${o.deliveryFee.som()}", color = Lg.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Text(if (o.isPaid) "To'langan" else "Olinadi: ${o.collectAmount.som()}", color = if (o.isPaid) Lg.GreenText else Lg.Text, fontSize = 13.sp)
    }
    Spacer(Modifier.height(12.dp))
    PrimaryButton("Qabul qilish", icon = R.drawable.ic_check, loading = busy, onClick = onAccept)
}

@Composable
private fun ActiveCard(a: CActiveDto, busy: Boolean, onDeliver: () -> Unit) {
    val ctx = LocalContext.current
    fun nav(lat: Double?, lng: Double?, label: String) { if (lat != null && lng != null) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))) } }
    fun call(phone: String) { if (phone.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } }
    SectionCard(bg = Lg.CardStat) {
        Text("FAOL BUYURTMA  #${a.orderCode}", color = Lg.Orange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(10.dp))
        Text("1. Restoran", color = Lg.TextSecondary, fontSize = 12.sp)
        Text(a.restaurantName, color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold); Text(a.restaurantAddress, color = Lg.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Chip("Xaritada", R.drawable.ic_map) { nav(a.restaurantLat, a.restaurantLng, a.restaurantName) }
            if (a.restaurantPhone.isNotBlank()) Chip("Qo'ng'iroq", R.drawable.ic_phone) { call(a.restaurantPhone) }
        }
        Spacer(Modifier.height(14.dp))
        Text("2. Mijoz", color = Lg.TextSecondary, fontSize = 12.sp)
        Text(a.customerName.ifBlank { "Mijoz" }, color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(listOf(a.addressLabel, a.addressNote).filter { it.isNotBlank() }.joinToString(" • "), color = Lg.Muted, fontSize = 13.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            Chip("Xaritada", R.drawable.ic_map) { nav(a.lat, a.lng, a.customerName.ifBlank { "Mijoz" }) }
            if (a.customerPhone.isNotBlank()) Chip("Qo'ng'iroq", R.drawable.ic_phone) { call(a.customerPhone) }
        }
        Spacer(Modifier.height(14.dp))
        a.items.forEach { Text("• ${it.quantity}× ${it.name}", color = Lg.Text, fontSize = 14.sp) }
        if (a.note.isNotBlank()) Text("Izoh: ${a.note}", color = Lg.Orange, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Text(if (a.isPaid) "✅ To'langan — mijozdan pul olinmaydi" else "💵 Mijozdan olinadi: ${a.collectAmount.som()}", color = if (a.isPaid) Lg.GreenText else Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text("Yetkazish haqi: ${a.deliveryFee.som()}", color = Lg.Muted, fontSize = 12.sp)
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Topshirdim", icon = R.drawable.ic_check_circle, loading = busy, onClick = onDeliver)
    }
}

@Composable private fun Chip(text: String, icon: Int, onClick: () -> Unit) =
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Lg.Green.copy(alpha = 0.12f)).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LgIcon(icon, Lg.GreenText, 18.dp); Spacer(Modifier.width(6.dp)); Text(text, color = Lg.GreenText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
private fun HistoryTab(ui: CourierUi) = LazyColumn(contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    item { Text("Tarix", color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
    if (ui.history.isEmpty()) item { EmptyState(R.drawable.ic_orders, "Hali yetkazish yo'q", "Topshirgan buyurtmalaringiz shu yerda ko'rinadi") }
    items(ui.history, key = { it.id }) { h ->
        SectionCard {
            Row { Text("#${h.orderCode} • ${h.restaurantName}", color = Lg.Text, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(h.deliveryFee.som(), color = Lg.Orange, fontWeight = FontWeight.Bold) }
            Text(listOf(h.addressLabel, h.minutes?.let { "$it daq" }, h.deliveredAt?.take(16)?.replace('T', ' ')).filterNotNull().filter { it.isNotBlank() }.joinToString(" • "), color = Lg.Muted, fontSize = 12.sp)
        }
    }
    item { Spacer(Modifier.height(96.dp)) }
}

@Composable
private fun StatsTab(ui: CourierUi) = LazyColumn(contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    item { Text("Hisobot", color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
    val s = ui.stats
    if (s == null) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green) }
    else listOf("Bugun" to s.today, "Oxirgi 7 kun" to s.week, "Oxirgi 30 kun" to s.month).forEach { (title, p) ->
        item { SectionCard(bg = Lg.CardStat) {
            Text(title, color = Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row { Text("${p.deliveries}", color = Lg.Text, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f)); Text("yetkazish", color = Lg.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Bottom)) }
            Spacer(Modifier.height(6.dp))
            Text("Yetkazish haqi: ${p.deliveryFees.som()}", color = Lg.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text("Ish vaqti: ${minutes(p.workedMinutes)} • ${p.shifts} smena • ${p.distanceKm} km", color = Lg.Muted, fontSize = 12.sp)
        } }
    }
    item { Spacer(Modifier.height(96.dp)) }
}

@Composable
private fun ProfileTab(vm: CourierViewModel, ui: CourierUi) {
    val me by vm.me.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profil", color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item { SectionCard { Text(me?.name.orEmpty(), color = Lg.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold); Text("Login: ${me?.login.orEmpty()}", color = Lg.Muted, fontSize = 13.sp) } }
        ui.message?.let { m -> item { Text(m, color = Lg.Orange, fontSize = 13.sp) } }
        item { PrimaryButton("Push tekshiruvi (test xabar)", icon = R.drawable.ic_bell_ring, color = Lg.Orange, loading = ui.busy) { vm.testPush() } }
        item { Text("Test bosgach ilovani yoping va telefonni bloklang — «Push ishlayapti ✓» kelishi kerak.", color = Lg.Muted, fontSize = 11.sp) }
        item {
            Text(if (ui.onShift) "Chiqishdan oldin ishni yakunlang" else "Restoran / Kuryer tanlashga qaytish", color = Lg.TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lg.Card).let { if (!ui.onShift) it.clickable { vm.switchSide() } else it }.padding(16.dp))
        }
        item {
            Text("Chiqish", color = if (ui.onShift) Lg.Muted else Lg.Red, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).border(1.5.dp, if (ui.onShift) Lg.Line else Lg.Red, RoundedCornerShape(14.dp)).let { if (!ui.onShift) it.clickable { vm.logout() } else it }.padding(16.dp))
        }
        item { Spacer(Modifier.height(96.dp)) }
    }
}

/** Kuryer kirishi: login/parol (restoran akkauntidan ALOHIDA — kuryer akkauntini admin yaratadi). */
@Composable
fun CourierLoginScreen(lastLogin: String, onSwitchSide: () -> Unit, vm: CourierViewModel = hiltViewModel()) {
    var login by rememberSaveable { mutableStateOf(lastLogin) }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Lg.Bg).statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_lgo), "LGO Business", Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(12.dp))
        Text("Kuryer", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Kuryer akkaunti bilan kiring", color = Lg.Muted, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(login, { login = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Login") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = lgFieldColors())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Parol") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(16.dp), colors = lgFieldColors())
        error?.let { Text(it, color = Lg.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(18.dp))
        PrimaryButton("Kirish", loading = loading) { loading = true; vm.login(login, password) { e -> loading = false; error = e } }
        Text("Restoran boshqaruviga o'tish", color = Lg.GreenText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSwitchSide).padding(16.dp))
    }
}
