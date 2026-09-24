package uz.lokmago.restaurant.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.OrderRepository
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderStatus
import uz.lokmago.restaurant.realtime.ActionResult
import uz.lokmago.restaurant.realtime.OrderCoordinator
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.hhmmDate
import uz.lokmago.restaurant.util.som

data class DetailUi(val order: Order? = null, val loading: Boolean = true, val error: String? = null, val busy: Boolean = false, val notice: String? = null)

@HiltViewModel
class OrderDetailViewModel @Inject constructor(
    saved: SavedStateHandle, private val repo: OrderRepository, private val coordinator: OrderCoordinator,
) : ViewModel() {
    private val id: String = checkNotNull(saved["id"])
    val ui = MutableStateFlow(DetailUi())

    init {
        load()
        // Another phone accepted / status changed → this screen updates in real time.
        viewModelScope.launch { coordinator.changes.collect { if (it.id == id) ui.value = ui.value.copy(order = it) } }
    }

    /** Always reload from the backend — notification payloads are never trusted as the source of truth. */
    fun load() = viewModelScope.launch {
        ui.value = ui.value.copy(loading = true, error = null)
        ui.value = try { DetailUi(order = repo.order(id), loading = false) }
        catch (e: AppError) { DetailUi(loading = false, error = e.message) }
    }

    fun act() {
        val o = ui.value.order ?: return
        viewModelScope.launch {
            ui.value = ui.value.copy(busy = true, notice = null)
            when (val r = coordinator.advance(o)) {
                is ActionResult.Done -> ui.value = ui.value.copy(order = r.order, busy = false)
                ActionResult.AlreadyHandled -> { load().join(); ui.value = ui.value.copy(busy = false, notice = "Buyurtma allaqachon qabul qilingan") }
                is ActionResult.Failed -> ui.value = ui.value.copy(busy = false, notice = r.message)
            }
        }
    }
}

@Composable
fun OrderDetailScreen(onBack: () -> Unit, vm: OrderDetailViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.clickableNoRipple(onBack)) { LgIcon(R.drawable.ic_arrow_left) }
            Text("Buyurtma #${ui.order?.number ?: ""}", color = Lg.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            ui.order?.let { StatusChip(it.status) }
        }
        val o = ui.order
        when {
            ui.loading -> LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green)
            o == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(ui.error ?: "Buyurtmani yuklab bo'lmadi", color = Lg.Text)
                PrimaryButton("Qayta urinish") { vm.load() }
            }
            else -> {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(o.createdAtMillis.hhmmDate(), color = Lg.Muted, fontSize = 13.sp)
                    ui.notice?.let { Text(it, color = Lg.Orange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lg.Orange.copy(alpha = 0.12f)).padding(12.dp)) }

                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            LgIcon(R.drawable.ic_user, Lg.Muted); Column { Text("Mijoz", color = Lg.Muted, fontSize = 12.sp); Text(o.customerName ?: "—", color = Lg.Text, fontWeight = FontWeight.SemiBold) }
                        }
                        o.customerPhone?.let { p ->
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.clickableNoRipple { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p"))) }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                LgIcon(R.drawable.ic_phone, Lg.Green); Text(p, color = Lg.Green, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            KV(R.drawable.ic_card.takeIf { o.payment.name == "CARD" } ?: R.drawable.ic_cash, "To'lov usuli", o.payment.label)
                            KV(R.drawable.ic_delivery, o.fulfillment.label, if (o.deliveryFee > 0) o.deliveryFee.som() else "—")
                        }
                    }

                    Text("Buyurtma tarkibi", color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    SectionCard {
                        o.items.forEachIndexed { i, it ->
                            if (i > 0) HorizontalDivider(color = Lg.Line, modifier = Modifier.padding(vertical = 10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AsyncImage(it.imageUrl, null, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Lg.Surface))
                                Column(Modifier.weight(1f)) { Text(it.name, color = Lg.Text, fontWeight = FontWeight.SemiBold); Text("× ${it.quantity}", color = Lg.Muted, fontSize = 12.sp) }
                                Text(it.lineTotal.som(), color = Lg.Text, fontSize = 14.sp)
                            }
                        }
                        HorizontalDivider(color = Lg.Line, modifier = Modifier.padding(vertical = 12.dp))
                        Sum("Taomlar summasi", o.subtotal.som()); Sum("Yetkazib berish", o.deliveryFee.som())
                        Spacer(Modifier.height(6.dp)); Sum("Jami", o.total.som(), big = true)
                    }

                    if (!o.address.isNullOrBlank()) SectionCard {
                        Text("Mijoz manzili", color = Lg.Muted, fontSize = 12.sp)
                        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LgIcon(R.drawable.ic_pin, Lg.Green); Text(o.address, color = Lg.Text) }
                        OutlinedButtonLike("Xaritada ko'rish") {
                            val q = if (o.lat != null && o.lng != null) "geo:${o.lat},${o.lng}?q=${o.lat},${o.lng}" else "geo:0,0?q=${Uri.encode(o.address)}"
                            runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(q))) }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                o.nextAction()?.let { a ->
                    Box(Modifier.padding(16.dp)) {
                        PrimaryButton(a.label, icon = R.drawable.ic_check, loading = ui.busy,
                            color = if (o.status == OrderStatus.ACCEPTED) Lg.Orange else Lg.Green) { vm.act() }
                    }
                }
            }
        }
    }
}

@Composable private fun KV(icon: Int, k: String, v: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        LgIcon(icon, Lg.Muted); Column { Text(k, color = Lg.Muted, fontSize = 12.sp); Text(v, color = Lg.Text, fontWeight = FontWeight.SemiBold) }
    }

@Composable private fun Sum(k: String, v: String, big: Boolean = false) =
    Row(Modifier.fillMaxWidth()) {
        Text(k, color = if (big) Lg.Text else Lg.Muted, fontSize = if (big) 17.sp else 13.sp, fontWeight = if (big) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text(v, color = Lg.Text, fontSize = if (big) 20.sp else 13.sp, fontWeight = if (big) FontWeight.ExtraBold else FontWeight.Normal)
    }

@Composable private fun OutlinedButtonLike(text: String, onClick: () -> Unit) =
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Lg.Green.copy(alpha = 0.10f)).clickableNoRipple(onClick).padding(12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        LgIcon(R.drawable.ic_map, Lg.Green, 18.dp); Spacer(Modifier.width(8.dp)); Text(text, color = Lg.Green, fontWeight = FontWeight.SemiBold)
    }
