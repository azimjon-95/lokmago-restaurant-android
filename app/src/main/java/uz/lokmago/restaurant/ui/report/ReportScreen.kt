package uz.lokmago.restaurant.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import uz.lokmago.restaurant.domain.DayStats
import uz.lokmago.restaurant.realtime.OrderCoordinator
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

@HiltViewModel
class ReportViewModel @Inject constructor(private val repo: OrderRepository, coordinator: OrderCoordinator) : ViewModel() {
    val stats = MutableStateFlow<DayStats?>(null)
    val error = MutableStateFlow<String?>(null)
    init {
        load(); viewModelScope.launch { coordinator.changes.collect { load() } }
    }
    fun load() { viewModelScope.launch { runCatching { repo.today() }.onSuccess { stats.value = it; error.value = null }.onFailure { error.value = "Hisobotni yuklab bo'lmadi" } } }
}

@Composable
fun ReportScreen(vm: ReportViewModel = hiltViewModel()) {
    val s by vm.stats.collectAsStateWithLifecycle()
    val err by vm.error.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Hisobot", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { LgIcon(R.drawable.ic_calendar, Lg.Muted, 18.dp); Spacer(Modifier.width(8.dp)); Text("Bugun", color = Lg.Text) }
        val d = s
        if (d == null) { Text(err ?: "Yuklanmoqda…", color = Lg.Muted); return@Column }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard(Modifier.weight(1f)) { Text("Buyurtmalar soni", color = Lg.Muted, fontSize = 12.sp); Text("${d.ordersCount}", color = Lg.Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
            SectionCard(Modifier.weight(1f)) { Text("Jami savdo", color = Lg.Muted, fontSize = 12.sp); Text(d.revenue.som(), color = Lg.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
        }
        val total = (d.cashRevenue + d.cardRevenue).coerceAtLeast(1)
        val cashPct = (d.cashRevenue * 100 / total).toInt()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionCard(Modifier.weight(1f)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LgIcon(R.drawable.ic_cash, Lg.Green, 16.dp); Text("Naqd usuli", color = Lg.Green, fontSize = 12.sp) }; Text(d.cashRevenue.som(), color = Lg.Text, fontWeight = FontWeight.Bold); Text("$cashPct%", color = Lg.Muted, fontSize = 12.sp) }
            SectionCard(Modifier.weight(1f)) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { LgIcon(R.drawable.ic_card, Lg.Blue, 16.dp); Text("Karta orqali", color = Lg.Blue, fontSize = 12.sp) }; Text(d.cardRevenue.som(), color = Lg.Text, fontWeight = FontWeight.Bold); Text("${100 - cashPct}%", color = Lg.Muted, fontSize = 12.sp) }
        }

        Text("Savdo grafigi", color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        SectionCard { HourlyChart(d.hourly) }

        Text("To'lov turlari", color = Lg.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        SectionCard {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Donut(cashPct / 100f, Modifier.size(110.dp))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Legend(Lg.Green, "Naqd", cashPct); Legend(Lg.Blue, "Karta", 100 - cashPct)
                }
            }
        }
    }
}

@Composable
private fun Legend(c: Color, label: String, pct: Int) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(10.dp)) { drawCircle(c) }; Text("$label   $pct%", color = Lg.Text, fontSize = 14.sp)
    }

@Composable
private fun Donut(cashShare: Float, modifier: Modifier) = Canvas(modifier) {
    val w = 16.dp.toPx(); val inset = w / 2
    val sz = Size(size.width - w, size.height - w)
    drawArc(Lg.Blue, -90f + 360f * cashShare, 360f * (1 - cashShare), false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Butt))
    drawArc(Lg.Green, -90f, 360f * cashShare, false, Offset(inset, inset), sz, style = Stroke(w, cap = StrokeCap.Butt))
}

/** Hourly revenue bars, 09:00–21:00 window (matches restaurant opening hours in the design). */
@Composable
private fun HourlyChart(hourly: List<Long>) {
    val hours = (9..21).toList()
    val values = hours.map { hourly.getOrElse(it) { 0L } }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / values.size
            values.forEachIndexed { i, v ->
                val h = size.height * (v.toFloat() / max)
                drawRoundRect(if (v == max && v > 0) Lg.Green else Lg.Green.copy(alpha = 0.55f),
                    Offset(i * slot + slot * 0.2f, size.height - h), Size(slot * 0.6f, h.coerceAtLeast(2f)), CornerRadius(4.dp.toPx()))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(9, 11, 13, 15, 17, 19, 21).forEach { Text("%02d:00".format(it), color = Lg.Muted, fontSize = 10.sp) }
        }
    }
}
