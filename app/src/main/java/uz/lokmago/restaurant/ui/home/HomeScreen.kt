package uz.lokmago.restaurant.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.repo.OrderRepository
import uz.lokmago.restaurant.domain.*
import uz.lokmago.restaurant.realtime.OrderCoordinator
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

data class HomeUi(val stats: DayStats? = null, val recent: List<Order> = emptyList(), val refreshing: Boolean = false, val error: String? = null)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: OrderRepository, private val coordinator: OrderCoordinator, session: SessionStore,
) : ViewModel() {
    val ui = MutableStateFlow(HomeUi())
    val restaurant = session.session

    init {
        refresh()
        viewModelScope.launch { coordinator.changes.collect { load() } }
        viewModelScope.launch { coordinator.arrivals.collect { load() } }
    }

    fun refresh() { viewModelScope.launch { ui.value = ui.value.copy(refreshing = true); coordinator.syncPending(); load(); ui.value = ui.value.copy(refreshing = false) } }

    private suspend fun load() {
        runCatching {
            val stats = repo.today()
            val recent = repo.list(null).filter { it.status != OrderStatus.NEW }.take(5)
            ui.value = ui.value.copy(stats = stats, recent = recent, error = null)
        }.onFailure { ui.value = ui.value.copy(error = "Ma'lumotlarni yuklab bo'lmadi") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    queue: OrderQueue.State, online: Boolean, notificationsOn: Boolean,
    onOpenOrder: (String) -> Unit, onOpenAll: () -> Unit, onFixNotifications: () -> Unit, onBell: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val session by vm.restaurant.collectAsStateWithLifecycle()

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LgIcon(R.drawable.ic_chef_hat, Lg.Orange, 34.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("LokmaGo", color = Lg.Orange, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Restoran", color = Lg.Muted, fontSize = 12.sp)
                    }
                    ConnectionPill(online)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Lg.Card).clickableNoRipple(onBell), contentAlignment = Alignment.Center) {
                        LgIcon(if (queue.isEmpty) R.drawable.ic_bell else R.drawable.ic_bell_ring, if (queue.isEmpty) Lg.Text else Lg.Red)
                    }
                    if (!queue.isEmpty) Text("${queue.size}", color = Lg.Text, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.offset(x = (-14).dp, y = (-14).dp).clip(CircleShape).background(Lg.Red).padding(horizontal = 6.dp, vertical = 1.dp))
                }
            }
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    LgIcon(R.drawable.ic_store, Lg.Green, 26.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(session?.restaurantName.orEmpty(), color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (!online) item { Banner(R.drawable.ic_wifi_off, "Internet yo'q. Ulanish tiklanganda buyurtmalar avtomatik yangilanadi.", Lg.Red) }
            if (!notificationsOn) item { Banner(R.drawable.ic_warning, "Bildirishnoma o'chirilgan — yangi buyurtmani o'tkazib yuborishingiz mumkin. Yoqish uchun bosing.", Lg.Orange, onFixNotifications) }

            // ---- The point of this screen: new orders, in queue order ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Yangi buyurtmalar", color = Lg.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (!queue.isEmpty) Text("${queue.size} ta kutmoqda", color = Lg.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (queue.isEmpty) item { EmptyState(R.drawable.ic_bell, "Yangi buyurtma yo'q", "Yangi buyurtma kelganda ovoz va vibratsiya bilan xabar beramiz") }
            items(queue.orders, key = { it.id }) { o -> OrderCard(o, highlight = true) { onOpenOrder(o.id) } }

            ui.stats?.let { s ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(Modifier.weight(1f), "Bugungi buyurtmalar", "${s.ordersCount}", if (s.ordersDelta != 0) "${if (s.ordersDelta > 0) "↑" else "↓"} ${kotlin.math.abs(s.ordersDelta)}" else null)
                        Stat(Modifier.weight(1f), "Kutilayotgan", "${s.waiting}", null)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Stat(Modifier.weight(1f), "Naqd", "${s.cashCount}", null)
                        Stat(Modifier.weight(1f), "Karta", "${s.cardCount}", null)
                    }
                }
                item {
                    SectionCard {
                        Text("Bugungi tushum", color = Lg.Muted, fontSize = 13.sp)
                        Text(s.revenue.som(), color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
            if (ui.recent.isNotEmpty()) {
                item { Row { Text("So'nggi buyurtmalar", color = Lg.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("Barchasi", color = Lg.Green, fontSize = 13.sp, modifier = Modifier.clickableNoRipple(onOpenAll)) } }
                items(ui.recent, key = { "r" + it.id }) { o -> OrderCard(o) { onOpenOrder(o.id) } }
            }
            ui.error?.let { item { Text(it, color = Lg.Muted, fontSize = 12.sp) } }
        }
    }
}

@Composable
private fun Stat(modifier: Modifier, label: String, value: String, delta: String?) =
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp)) {
        Text(label, color = Lg.Muted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(value, color = Lg.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
            delta?.let { Text(it, color = Lg.Green, fontSize = 12.sp, modifier = Modifier.padding(bottom = 5.dp)) }
        }
    }

@Composable
private fun Banner(icon: Int, text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) =
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f))
            .let { if (onClick != null) it.clickableNoRipple(onClick) else it }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { LgIcon(icon, color, 20.dp); Text(text, color = Lg.Text, fontSize = 13.sp) }
