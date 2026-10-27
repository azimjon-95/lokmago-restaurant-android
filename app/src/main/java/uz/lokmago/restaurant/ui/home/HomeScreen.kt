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
import uz.lokmago.restaurant.ui.theme.Dt
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
    queue: OrderQueue.State, connection: Conn, notificationsOn: Boolean,
    onOpenOrder: (String) -> Unit, onOpenAll: () -> Unit, onFixNotifications: () -> Unit, onBell: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val session by vm.restaurant.collectAsStateWithLifecycle()
    // Flicker guard: the header label changes at once, the banner only if "connecting" lasts
    val connecting by produceState(false, connection) { value = false; if (connection == Conn.CONNECTING) { kotlinx.coroutines.delay(2_500); value = true } }

    PullToRefreshBox(isRefreshing = ui.refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Dt.spaceScreen), verticalArrangement = Arrangement.spacedBy(Dt.spaceGap)) {
            // ---- header: logo, "Lokma"+orange "Go", Restoran | ● Online ⌄ | bell ----
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LgIcon(R.drawable.ic_chef_hat, Lg.Orange, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text("Lokma", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Go", color = Lg.Orange, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Text("Restoran", color = Lg.Muted, fontSize = 13.sp)
                    }
                    ConnectionPill(connection)
                    Spacer(Modifier.width(12.dp))
                    BellButton(queue.size, onBell)
                }
            }
            // ---- restaurant card ----
            item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dt.radiusCard)).background(Lg.Card).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Lg.CardHigh), contentAlignment = Alignment.Center) {
                        LgIcon(R.drawable.ic_store, Lg.Orange, 34.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(session?.restaurantName.orEmpty(), color = Lg.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        OnlineChip(connection)
                    }
                    LgIcon(R.drawable.ic_chevron_right, Lg.TextSecondary, 20.dp)
                }
            }
            // Three different situations, three different messages: no internet / internet but server not joined yet / permissions
            if (connection == Conn.OFFLINE) item { Banner(R.drawable.ic_wifi_off, "Internet yo'q. Ulanish tiklanganda buyurtmalar avtomatik yangilanadi.", Lg.Red) }
            else if (connecting) item { Banner(R.drawable.ic_refresh, "Serverga ulanmoqda… Ulangach buyurtmalar darhol ko'rsatiladi.", Lg.Orange) }
            if (!notificationsOn) item { Banner(R.drawable.ic_warning, "Ogohlantirish to'liq sozlanmagan — yangi buyurtmani o'tkazib yuborishingiz mumkin. Sozlash uchun bosing.", Lg.Orange, onFixNotifications) }

            // ---- new orders waiting (queue order). Appears only while there is something to accept ----
            if (!queue.isEmpty) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Yangi buyurtmalar", color = Lg.Text, fontSize = Dt.fontSection, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("${queue.size} ta kutmoqda", color = Lg.Red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(queue.orders, key = { it.id }) { o -> OrderCard(o, highlight = true) { onOpenOrder(o.id) } }
            }

            // ---- stats ----
            ui.stats?.let { s ->
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dt.spaceGap)) {
                        StatCard(Modifier.weight(1f), "Bugungi buyurtmalar", "${s.ordersCount}", deltaText(s.ordersDelta))
                        StatCard(Modifier.weight(1f), "Kutilayotgan", "${s.waiting}", null) { LgIcon(R.drawable.ic_clock, Lg.Blue, 26.dp) }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(Dt.spaceGap)) {
                        StatCard(Modifier.weight(1f), "Naqd", "${s.cashCount}") { IconBox(R.drawable.ic_cash, Lg.CardHigh, Lg.GreenText) }
                        StatCard(Modifier.weight(1f), "Karta", "${s.cardCount}") { IconBox(R.drawable.ic_card, Dt.blueBox, Dt.onAccent) }
                    }
                }
                item {
                    val money = s.revenue.som()
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Dt.radiusCard)).background(Lg.CardStat).padding(Dt.spaceCardPad), verticalAlignment = Alignment.CenterVertically) {
                        IconBox(R.drawable.ic_wallet, Lg.Green, Dt.onAccent)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Bugungi tushum", color = Lg.TextSecondary, fontSize = Dt.fontMeta)
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(money.substringBeforeLast(" "), color = Lg.Text, fontSize = Dt.fontBig, fontWeight = FontWeight.ExtraBold)
                                Text(" " + money.substringAfterLast(" "), color = Lg.TextSecondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                        deltaText(s.revenueDeltaPct, "%")?.let { Text(it, color = Lg.GreenText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }

            // ---- recent orders ----
            if (ui.recent.isNotEmpty()) {
                item {
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("So'nggi buyurtmalar", color = Lg.Text, fontSize = Dt.fontSection, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("Barchasi", color = Lg.GreenText, fontSize = 14.sp, modifier = Modifier.clickableNoRipple(onOpenAll))
                    }
                }
                items(ui.recent, key = { "r" + it.id }) { o -> OrderCard(o, compact = true) { onOpenOrder(o.id) } }
            }
            ui.error?.let { item { Text(it, color = Lg.Muted, fontSize = 12.sp) } }
        }
    }
}

/** "↑ 3" / "↓ 2" / "↑ 12%"; null when there is no change worth showing. */
private fun deltaText(v: Int, suffix: String = ""): String? =
    if (v == 0) null else "${if (v > 0) "↑" else "↓"} ${kotlin.math.abs(v)}$suffix"

@Composable
private fun Banner(icon: Int, text: String, color: androidx.compose.ui.graphics.Color, onClick: (() -> Unit)? = null) =
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = 0.12f))
            .let { if (onClick != null) it.clickableNoRipple(onClick) else it }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { LgIcon(icon, color, 20.dp); Text(text, color = Lg.Text, fontSize = 13.sp) }
