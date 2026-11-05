package uz.lokmago.restaurant.ui.courier

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.courier.*
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.util.som

private val OFFER_SECONDS = 30
private fun km(m: Int?) = when { m == null -> ""; m < 950 -> "${(m / 10) * 10} m"; else -> String.format(java.util.Locale.US, "%.1f km", m / 1000.0) }
private fun minutes(n: Int) = if (n >= 60) "${n / 60} soat ${n % 60} daq" else "$n daq"
private fun initials(name: String) = name.trim().split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }.ifBlank { "K" }

/** Kuryer tomonining ildizi (yorug' dizayn): Bosh sahifa · Buyurtmalar · Daromad · Profil + yangi buyurtma modali. */
@Composable
fun CourierRoot(vm: CourierViewModel = hiltViewModel()) {
    LightSystemBars()
    var tab by rememberSaveable { mutableStateOf(0) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val skipped: SnapshotStateList<String> = remember { mutableStateListOf() }        // «Rad etish» — faqat shu telefonda ko'rsatmaslik (boshqa kuryerlarga ta'sir qilmaydi)
    LaunchedEffect(tab) { if (tab == 1) vm.loadHistory(); if (tab == 2) { vm.loadStats(); vm.loadHistory() } }
    LaunchedEffect(Unit) { vm.loadStats() }

    Box(Modifier.fillMaxSize().background(Cr.Surface)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Box(Modifier.weight(1f)) {
                when (tab) { 0 -> HomeTab(vm, ui, onOpenEarnings = { tab = 2 }); 1 -> OrdersTab(vm, ui); 2 -> EarningsTab(ui); else -> ProfileTab(vm, ui) }
            }
            BottomBar(tab, ui.offers.size + (if (ui.active != null) 1 else 0)) { tab = it }
        }
        val offer = ui.offers.firstOrNull { it.id !in skipped }
        if (ui.onShift && ui.active == null && offer != null && tab != 3) {
            OfferModal(offer, ui.busy, onAccept = { vm.accept(offer.id); tab = 1 }, onReject = { skipped += offer.id }, onExpire = { skipped += offer.id })
        }
    }
}

@Composable
private fun BottomBar(selected: Int, badge: Int, onSelect: (Int) -> Unit) {
    val items = listOf(Triple(R.drawable.ic_home, "Bosh sahifa", 0), Triple(R.drawable.ic_orders, "Buyurtmalar", badge), Triple(R.drawable.ic_report, "Daromad", 0), Triple(R.drawable.ic_profile, "Profil", 0))
    Row(Modifier.fillMaxWidth().background(Color.White).border(1.dp, Cr.Line).navigationBarsPadding().padding(vertical = 8.dp)) {
        items.forEachIndexed { i, (icon, label, count) ->
            val on = i == selected
            Column(Modifier.weight(1f).clickable { onSelect(i) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box {
                    LgIcon(icon, if (on) Cr.Orange else Cr.Muted, 26.dp)
                    if (count > 0) Text("$count", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.TopEnd).offset(x = 10.dp, y = (-6).dp).clip(CircleShape).background(Cr.Red).padding(horizontal = 5.dp, vertical = 1.dp))
                }
                Text(label, color = if (on) Cr.Orange else Cr.Muted, fontSize = 11.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Yangi buyurtma — telefon qo'ng'irog'iga o'xshash to'liq ekran modal: 30 soniyalik hisoblagich, «Rad etish» / «Qabul qilish». */
@Composable
private fun OfferModal(o: COfferDto, busy: Boolean, onAccept: () -> Unit, onReject: () -> Unit, onExpire: () -> Unit) {
    val left by produceState(OFFER_SECONDS, o.id) {
        value = OFFER_SECONDS
        while (value > 0) { kotlinx.coroutines.delay(1000); value -= 1 }
        onExpire()
    }
    Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false, dismissOnClickOutside = false)) {
        Column(Modifier.fillMaxSize().background(Cr.Dark).statusBarsPadding().navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            Text("Yangi buyurtma!", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.height(20.dp))
            Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 8.dp.toPx()
                    drawArc(Color(0x33FFFFFF), 0f, 360f, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(Cr.Orange, -90f, 360f * left / OFFER_SECONDS, false, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Text("${left}s", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
            }
            Spacer(Modifier.height(20.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color.White).padding(18.dp)) {
                Text(o.restaurantName.ifBlank { "Restoran" }, color = Cr.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(12.dp))
                StopRow(Cr.Green, "Olish", o.restaurantAddress.ifBlank { o.restaurantName }, km(o.distanceM))
                Spacer(Modifier.height(10.dp))
                StopRow(Cr.Orange, "Yetkazish", o.addressLabel.ifBlank { "Mijoz manzili qabul qilgandan keyin ko'rinadi" }, "")
                if (o.itemsSummary.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(o.itemsSummary, color = Cr.Muted, fontSize = 13.sp) }
                Spacer(Modifier.height(14.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cr.OrangeSoft).padding(14.dp)) {
                    Text("Sizning daromadingiz", color = Cr.Muted, fontSize = 12.sp)
                    Text(o.deliveryFee.som(), color = Cr.Orange, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (o.isPaid) "To'langan — mijozdan pul olinmaydi" else "Mijozdan olinadi: ${o.collectAmount.som()}", color = Cr.Text, fontSize = 13.sp)
                }
            }
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f).heightIn(min = 56.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2F37)).clickable(onClick = onReject), contentAlignment = Alignment.Center) {
                    Text("Rad etish", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Box(Modifier.weight(1.4f)) { CrButton("Qabul qilish", loading = busy, onClick = onAccept) }
            }
        }
    }
}

@Composable
private fun StopRow(color: Color, title: String, address: String, trailing: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 4.dp).size(12.dp).clip(CircleShape).background(color))
        Column(Modifier.weight(1f)) { Text(title, color = Cr.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold); Text(address, color = Cr.Muted, fontSize = 13.sp) }
        if (trailing.isNotBlank()) Text(trailing, color = Cr.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HomeTab(vm: CourierViewModel, ui: CourierUi, onOpenEarnings: () -> Unit) {
    val me by vm.me.collectAsStateWithLifecycle()
    val accuracy by CourierLiveState.accuracyM.collectAsStateWithLifecycle()
    val linked by CourierLiveState.connected.collectAsStateWithLifecycle()
    var denied by remember { mutableStateOf(false) }
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.ACCESS_FINE_LOCATION] == true || r[Manifest.permission.ACCESS_COARSE_LOCATION] == true) { denied = false; vm.startShift() } else denied = true
    }
    fun begin() = perms.launch(buildList { add(Manifest.permission.ACCESS_FINE_LOCATION); add(Manifest.permission.ACCESS_COARSE_LOCATION); if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS) }.toTypedArray())
    ui.message?.let { msg -> LaunchedEffect(msg) { kotlinx.coroutines.delay(4000); vm.clearMessage() } }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Assalomu alaykum,", color = Cr.Muted, fontSize = 13.sp)
                    Text("${me?.name ?: "Kuryer"}!", color = Cr.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Cr.OrangeSoft), contentAlignment = Alignment.Center) { Text(initials(me?.name.orEmpty()), color = Cr.Orange, fontWeight = FontWeight.ExtraBold) }
            }
        }
        ui.message?.let { m -> item { Text(m, color = Cr.OrangeDark, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Cr.OrangeSoft).padding(12.dp)) } }
        // Onlayn / Oflayn = smena (har kuni «Ishni boshladim / yakunladim»)
        item {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (ui.onShift) Cr.GreenSoft else Color.White).border(1.dp, if (ui.onShift) Cr.Green.copy(alpha = 0.4f) else Cr.Line, RoundedCornerShape(20.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (ui.onShift) Cr.Green else Cr.Muted))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (ui.onShift) "Onlayn" else "Oflayn", color = Cr.Text, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                    Text(if (ui.onShift) "Buyurtmalar qabul qilinmoqda" else "Smena yopiq. Yoqing — buyurtma olasiz", color = Cr.Muted, fontSize = 12.sp)
                }
                Switch(ui.onShift, { on -> if (on) begin() else if (ui.active == null) vm.endShift() },
                    enabled = !ui.busy && !(ui.onShift && ui.active != null),
                    colors = SwitchDefaults.colors(checkedTrackColor = Cr.Green, checkedThumbColor = Color.White, uncheckedTrackColor = Color(0xFFD1D5DB), uncheckedThumbColor = Color.White, uncheckedBorderColor = Color.Transparent))
            }
            if (ui.onShift && ui.active != null) Text("Smenani yakunlash uchun avval buyurtmani topshiring", color = Cr.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
            if (denied) Text("Joylashuv ruxsatisiz smena boshlab bo'lmaydi: buyurtma sizga yaqinlik bo'yicha beriladi. Sozlamalar → Ilovalar → Joylashuv → «Ilova ishlatilayotganda».", color = Cr.Red, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp, top = 6.dp))
        }
        item {
            CrCard(Modifier.clickable(onClick = onOpenEarnings)) {
                Text("Bugungi daromad", color = Cr.Muted, fontSize = 13.sp)
                Text((ui.stats?.today?.deliveryFees ?: 0L).som(), color = Cr.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("${ui.stats?.today?.deliveries ?: 0} ta yetkazish • ${km(((ui.stats?.today?.distanceKm ?: 0.0) * 1000).toInt())}", color = Cr.Muted, fontSize = 12.sp)
            }
        }
        item {
            CrCard {
                val q = accuracy
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(if (!ui.onShift) "GPS: smena yopiq" else if (q == null) "GPS aniqligi: aniqlanmoqda…" else "GPS aniqligi: ±$q m", color = Cr.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (!ui.onShift || q == null) "Joylashuv smena davomida har 5 soniyada yuboriladi" else if (q <= 15) "Joylashuv aniq" else if (q <= 50) "Joylashuv yaxshi" else "Joylashuv past aniqlikda — ochiq joyga chiqing", color = if (q != null && q > 50) Cr.Red else Cr.Green, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (!ui.onShift) Cr.Muted else if (linked == false) Cr.Red else Cr.Green))
                    Spacer(Modifier.width(8.dp))
                    Text("Ulanish: ${if (!ui.onShift) "—" else if (linked == false) "Uzilgan (qayta uriniladi)" else "Barqaror"}", color = Cr.Muted, fontSize = 12.sp)
                }
            }
        }
        val a = ui.active
        if (a != null) item { ActiveCard(a, ui.busy, vm::deliver) }
        else if (ui.onShift) {
            item { Text("Yaqin atrofdagi buyurtmalar", color = Cr.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            if (ui.offers.isEmpty()) item { Empty("Hozircha buyurtma yo'q", "Yangi yetkazish chiqqanda bildirishnoma va ovoz bilan xabar beramiz") }
            items(ui.offers, key = { it.id }) { o -> OfferCard(o, ui.busy) { vm.accept(o.id) } }
        } else item { Empty("Ish boshlang", "«Onlayn»ni yoqing — yaqin atrofdagi buyurtmalar shu yerda chiqadi") }
    }
}

@Composable private fun Empty(title: String, hint: String) = Column(Modifier.fillMaxWidth().padding(vertical = 36.dp, horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
    LgIcon(R.drawable.ic_delivery, Cr.Muted, 40.dp); Spacer(Modifier.height(8.dp))
    Text(title, color = Cr.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold); Text(hint, color = Cr.Muted, fontSize = 13.sp, textAlign = TextAlign.Center)
}

@Composable
private fun OfferCard(o: COfferDto, busy: Boolean, onAccept: () -> Unit) = CrCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(o.restaurantName.ifBlank { "Restoran" }, color = Cr.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold); if (o.restaurantAddress.isNotBlank()) Text(o.restaurantAddress, color = Cr.Muted, fontSize = 12.sp) }
        o.distanceM?.let { Text(km(it), color = Cr.Orange, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold) }
    }
    Spacer(Modifier.height(8.dp))
    if (o.addressLabel.isNotBlank()) Text("Yetkazish: ${o.addressLabel}", color = Cr.Text, fontSize = 13.sp)
    if (o.itemsSummary.isNotBlank()) Text(o.itemsSummary, color = Cr.Muted, fontSize = 12.sp)
    Spacer(Modifier.height(8.dp))
    Text("Daromad: ${o.deliveryFee.som()}", color = Cr.Orange, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    Text(if (o.isPaid) "To'langan" else "Mijozdan olinadi: ${o.collectAmount.som()}", color = Cr.Muted, fontSize = 12.sp)
    Spacer(Modifier.height(12.dp))
    CrButton("Qabul qilish", loading = busy, onClick = onAccept)
}

@Composable
private fun ActiveCard(a: CActiveDto, busy: Boolean, onDeliver: () -> Unit) {
    val ctx = LocalContext.current
    fun nav(lat: Double?, lng: Double?, label: String) { if (lat != null && lng != null) runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})"))) } }
    fun call(phone: String) { if (phone.isNotBlank()) runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))) } }
    CrCard(border = true) {
        Text("FAOL BUYURTMA  #${a.orderCode}", color = Cr.Orange, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
        Spacer(Modifier.height(12.dp))
        StopRow(Cr.Green, a.restaurantName, a.restaurantAddress, "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp, top = 8.dp)) {
            Pill("Xaritada", R.drawable.ic_map) { nav(a.restaurantLat, a.restaurantLng, a.restaurantName) }
            if (a.restaurantPhone.isNotBlank()) Pill("Qo'ng'iroq", R.drawable.ic_phone) { call(a.restaurantPhone) }
        }
        Spacer(Modifier.height(14.dp))
        StopRow(Cr.Orange, a.customerName.ifBlank { "Mijoz" }, listOf(a.addressLabel, a.addressNote).filter { it.isNotBlank() }.joinToString(" • "), "")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 24.dp, top = 8.dp)) {
            Pill("Xaritada", R.drawable.ic_map) { nav(a.lat, a.lng, a.customerName.ifBlank { "Mijoz" }) }
            if (a.customerPhone.isNotBlank()) Pill("Qo'ng'iroq", R.drawable.ic_phone) { call(a.customerPhone) }
        }
        Spacer(Modifier.height(14.dp))
        a.items.forEach { Text("• ${it.quantity}× ${it.name}", color = Cr.Text, fontSize = 14.sp) }
        if (a.note.isNotBlank()) Text("Izoh: ${a.note}", color = Cr.OrangeDark, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Cr.OrangeSoft).padding(14.dp)) {
            Text(if (a.isPaid) "To'langan — mijozdan pul olinmaydi" else "Mijozdan olinadi: ${a.collectAmount.som()}", color = Cr.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text("Sizning daromadingiz: ${a.deliveryFee.som()}", color = Cr.Orange, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Spacer(Modifier.height(14.dp))
        CrButton("Yetkazildi", loading = busy, onClick = onDeliver)
    }
}

@Composable private fun Pill(text: String, icon: Int, onClick: () -> Unit) =
    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(Cr.OrangeSoft).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        LgIcon(icon, Cr.Orange, 18.dp); Spacer(Modifier.width(6.dp)); Text(text, color = Cr.Orange, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }

@Composable
private fun OrdersTab(vm: CourierViewModel, ui: CourierUi) {
    var seg by rememberSaveable { mutableStateOf(0) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Buyurtmalar", color = Cr.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item { CrSegment(listOf("Faol (${if (ui.active != null) 1 else 0})", "Tarix"), seg) { seg = it } }
        if (seg == 0) {
            val a = ui.active
            if (a != null) item { ActiveCard(a, ui.busy, vm::deliver) } else item { Empty("Faol buyurtma yo'q", "Qabul qilgan buyurtmangiz shu yerda ko'rinadi") }
        } else {
            if (ui.history.isEmpty()) item { Empty("Hali yetkazish yo'q", "Topshirgan buyurtmalaringiz shu yerda ko'rinadi") }
            items(ui.history, key = { it.id }) { h ->
                CrCard {
                    Row { Text("#${h.orderCode} • ${h.restaurantName}", color = Cr.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.weight(1f)); Text(h.deliveryFee.som(), color = Cr.Orange, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp) }
                    Text(listOf(h.addressLabel, h.minutes?.let { "$it daq" }, h.deliveredAt?.take(16)?.replace('T', ' ')).filterNotNull().filter { it.isNotBlank() }.joinToString(" • "), color = Cr.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun EarningsTab(ui: CourierUi) {
    var seg by rememberSaveable { mutableStateOf(0) }
    val s = ui.stats
    val p = when (seg) { 0 -> s?.today; 1 -> s?.week; else -> s?.month }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Daromad", color = Cr.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item { CrSegment(listOf("Bugun", "Hafta", "Oy"), seg) { seg = it } }
        item {
            CrCard(bg = Cr.OrangeSoft, border = false) {
                Text(listOf("Bugungi daromad", "7 kunlik daromad", "30 kunlik daromad")[seg], color = Cr.Muted, fontSize = 13.sp)
                Text((p?.deliveryFees ?: 0L).som(), color = Cr.Orange, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
        item {
            val n = p?.deliveries ?: 0
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Tile("Bajarilgan", "$n ta", Modifier.weight(1f)); Tile("O'rtacha", if (n > 0) ((p?.deliveryFees ?: 0L) / n).som() else "—", Modifier.weight(1f)); Tile("Yo'l", "${p?.distanceKm ?: 0.0} km", Modifier.weight(1f))
            }
            Text("Ish vaqti: ${minutes(p?.workedMinutes ?: 0)} • ${p?.shifts ?: 0} smena", color = Cr.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
        }
        item { Text("To'lovlar tarixi", color = Cr.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
        if (ui.history.isEmpty()) item { Text("Hali yetkazish yo'q", color = Cr.Muted, fontSize = 13.sp) }
        items(ui.history.take(20), key = { "p-" + it.id }) { h ->
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.White).border(1.dp, Cr.Line, RoundedCornerShape(14.dp)).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(h.deliveredAt?.take(16)?.replace('T', ' ').orEmpty(), color = Cr.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); Text("#${h.orderCode}", color = Cr.Muted, fontSize = 11.sp) }
                Text(h.deliveryFee.som(), color = Cr.Text, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
            }
        }
    }
}

@Composable private fun Tile(title: String, value: String, modifier: Modifier) = Column(modifier.clip(RoundedCornerShape(16.dp)).background(Color.White).border(1.dp, Cr.Line, RoundedCornerShape(16.dp)).padding(12.dp)) {
    Text(title, color = Cr.Muted, fontSize = 11.sp); Text(value, color = Cr.Text, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
}

@Composable
private fun ProfileTab(vm: CourierViewModel, ui: CourierUi) {
    val me by vm.me.collectAsStateWithLifecycle()
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Profil", color = Cr.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold) }
        item {
            CrCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(Cr.OrangeSoft), contentAlignment = Alignment.Center) { Text(initials(me?.name.orEmpty()), color = Cr.Orange, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                    Spacer(Modifier.width(14.dp))
                    Column { Text(me?.name.orEmpty(), color = Cr.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold); Text("Kuryer • ${me?.login.orEmpty()}", color = Cr.Muted, fontSize = 13.sp) }
                }
            }
        }
        ui.message?.let { m -> item { Text(m, color = Cr.OrangeDark, fontSize = 13.sp) } }
        item { CrButton("Push tekshiruvi (test xabar)", filled = false, loading = ui.busy) { vm.testPush() } }
        item { Text("Test bosgach ilovani yoping va telefonni bloklang — «Push ishlayapti ✓» kelishi kerak.", color = Cr.Muted, fontSize = 11.sp) }
        item { CrButton(if (ui.onShift) "Chiqishdan oldin ishni yakunlang" else "Restoran / Kuryer tanlash", filled = false, enabled = !ui.onShift) { vm.switchSide() } }
        item { CrButton("Chiqish", filled = false, danger = true, enabled = !ui.onShift) { vm.logout() } }
    }
}

/** Kuryer kirishi: FAQAT login va parol (telefon raqamni SMS bilan tasdiqlash YO'Q). Akkauntni administrator yaratadi. */
@Composable
fun CourierLoginScreen(lastLogin: String, onSwitchSide: () -> Unit, vm: CourierViewModel = hiltViewModel()) {
    LightSystemBars()
    var login by rememberSaveable { mutableStateOf(lastLogin) }
    var password by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Cr.Orange, unfocusedBorderColor = Cr.Line, focusedLabelColor = Cr.Orange, cursorColor = Cr.Orange, focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedTextColor = Cr.Text, unfocusedTextColor = Cr.Text)
    Column(Modifier.fillMaxSize().background(Color.White).statusBarsPadding().navigationBarsPadding().imePadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(R.drawable.logo_lgo), "LGO Business", Modifier.size(88.dp).clip(RoundedCornerShape(22.dp)))
        Spacer(Modifier.height(14.dp))
        Text("Kirish", color = Cr.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
        Text("Kuryer akkauntingiz login va parolini kiriting", color = Cr.Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(26.dp))
        OutlinedTextField(login, { login = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Login") }, singleLine = true, shape = RoundedCornerShape(16.dp), colors = colors)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it; error = null }, Modifier.fillMaxWidth(), label = { Text("Parol") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), shape = RoundedCornerShape(16.dp), colors = colors)
        error?.let { Text(it, color = Cr.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        CrButton("Davom etish", loading = loading) { loading = true; vm.login(login, password) { e -> loading = false; error = e } }
        Text("Restoran boshqaruviga o'tish", color = Cr.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onSwitchSide).padding(16.dp))
    }
}
