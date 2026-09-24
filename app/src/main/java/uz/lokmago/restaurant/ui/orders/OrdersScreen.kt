package uz.lokmago.restaurant.ui.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import uz.lokmago.restaurant.ui.theme.Lg

private val TABS = listOf(
    OrderStatus.NEW to "Yangi", OrderStatus.ACCEPTED to "Qabul qilingan", OrderStatus.PREPARING to "Tayyorlanmoqda",
    OrderStatus.READY to "Tayyor", OrderStatus.DELIVERING to "Yetkazilmoqda", OrderStatus.DELIVERED to "Yakunlangan",
)

data class OrdersUi(val tab: Int = 0, val orders: List<Order> = emptyList(), val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class OrdersViewModel @Inject constructor(private val repo: OrderRepository, coordinator: OrderCoordinator) : ViewModel() {
    val ui = MutableStateFlow(OrdersUi())
    init {
        load()
        viewModelScope.launch { coordinator.changes.collect { load(silent = true) } }
        viewModelScope.launch { coordinator.arrivals.collect { load(silent = true) } }
    }
    fun select(i: Int) { ui.value = ui.value.copy(tab = i, orders = emptyList(), loading = true); load() }
    fun load(silent: Boolean = false) {
        val tab = ui.value.tab
        viewModelScope.launch {
            if (!silent) ui.value = ui.value.copy(loading = true)
            runCatching { repo.list(TABS[tab].first) }
                .onSuccess { if (ui.value.tab == tab) ui.value = ui.value.copy(orders = it, loading = false, error = null) }
                .onFailure { ui.value = ui.value.copy(loading = false, error = "Buyurtmalarni yuklab bo'lmadi. Tortib yangilang.") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(onOpen: (String) -> Unit, vm: OrdersViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        Text("Buyurtmalar", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(16.dp))
        ScrollableTabRow(ui.tab, containerColor = Lg.Bg, edgePadding = 12.dp, divider = {}, indicator = {}) {
            TABS.forEachIndexed { i, (st, label) ->
                val sel = i == ui.tab
                Text(label, color = if (sel) Color(0xFF03130D) else Lg.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp).clip(RoundedCornerShape(50))
                        .background(if (sel) statusColor(st) else Lg.Card).clickableNoRipple { vm.select(i) }
                        .padding(horizontal = 14.dp, vertical = 9.dp))
            }
        }
        PullToRefreshBox(false, { vm.load() }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green) }
                else if (ui.orders.isEmpty()) item { EmptyState(R.drawable.ic_orders, "Bu bo'limda buyurtma yo'q", ui.error ?: "Yangi buyurtmalar shu yerda ko'rinadi") }
                items(ui.orders, key = { it.id }) { o -> OrderCard(o) { onOpen(o.id) } }
            }
        }
    }
}
