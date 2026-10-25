package uz.lokmago.restaurant.ui.orders

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.OrderRepository
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderStatus
import uz.lokmago.restaurant.realtime.OrderCoordinator
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

private val TABS = listOf(
    OrderStatus.NEW to "Yangi", OrderStatus.ACCEPTED to "Qabul qilingan", OrderStatus.PREPARING to "Tayyorlanmoqda",
    OrderStatus.READY to "Tayyor", OrderStatus.DELIVERING to "Yetkazilmoqda", OrderStatus.DELIVERED to "Yakunlangan",
)

/** Count-badge colour per tab, exactly as in the mockup (Qabul qilingan = green, Tayyorlanmoqda = blue, ...). */
private fun badgeColor(s: OrderStatus): Color = when (s) {
    OrderStatus.NEW -> Dt.red
    OrderStatus.ACCEPTED -> Dt.green
    OrderStatus.PREPARING -> Dt.blueBox
    OrderStatus.READY -> Dt.teal
    OrderStatus.DELIVERING -> Dt.purple
    else -> Dt.green
}

/** One list (the server's latest 80) feeds both the tab counters and the tab content, so they can never disagree. */
data class OrdersUi(val tab: Int = 0, val all: List<Order> = emptyList(), val loading: Boolean = true, val error: String? = null) {
    val orders get() = all.filter { it.status == TABS[tab].first }
    fun count(s: OrderStatus) = all.count { it.status == s }
}

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: OrderRepository, coordinator: OrderCoordinator) : ViewModel() {
    val ui = MutableStateFlow(OrdersUi())
    init {
        load()
        viewModelScope.launch { coordinator.changes.collect { load(silent = true) } }
        viewModelScope.launch { coordinator.arrivals.collect { load(silent = true) } }
    }
    fun select(i: Int) { ui.value = ui.value.copy(tab = i) }
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) ui.value = ui.value.copy(loading = true)
            runCatching { repo.list(null) }
                .onSuccess { ui.value = ui.value.copy(all = it, loading = false, error = null) }
                .onFailure { ui.value = ui.value.copy(loading = false, error = "Buyurtmalarni yuklab bo'lmadi. Tortib yangilang.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOpen: (String) -> Unit, queueSize: Int = 0, onBell: () -> Unit = {}, vm: OrdersViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = Dt.spaceScreen, end = Dt.spaceScreen, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Buyurtmalar", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
            BellButton(queueSize, onBell)
        }
        LazyRow(contentPadding = PaddingValues(horizontal = Dt.spaceScreen, vertical = 14.dp)) {
            items(TABS.size) { i ->
                val (st, label) = TABS[i]
                TabChip(label, ui.count(st), i == ui.tab, statusColor(st), badgeColor(st)) { vm.select(i) }
            }
        }
        PullToRefreshBox(false, { vm.load() }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = Dt.spaceScreen, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(Dt.spaceGap)) {
                if (ui.loading && ui.all.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green) }
                else if (ui.orders.isEmpty()) item { EmptyState(R.drawable.ic_orders, "Bu bo'limda buyurtma yo'q", ui.error ?: "Yangi buyurtmalar shu yerda ko'rinadi") }
                items(ui.orders, key = { it.id }) { o -> OrderCard(o) { onOpen(o.id) } }
            }
        }
    }
}
