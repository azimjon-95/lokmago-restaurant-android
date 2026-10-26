package uz.lokmago.restaurant.ui.reviews

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.OrderRepository
import uz.lokmago.restaurant.domain.Review
import uz.lokmago.restaurant.realtime.OrderCoordinator
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

data class ReviewsUi(val items: List<Review> = emptyList(), val loading: Boolean = true, val error: String? = null)

@HiltViewModel
class ReviewsViewModel @Inject constructor(private val repo: OrderRepository, coordinator: OrderCoordinator) : ViewModel() {
    val ui = MutableStateFlow(ReviewsUi())
    init {
        load()
        // yangi baho odatda buyurtma yakunlangach keladi — yakunlanganda ro'yxat yangilanadi
        viewModelScope.launch { coordinator.changes.collect { if (it.status.isFinal) load(silent = true) } }
    }
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) ui.value = ui.value.copy(loading = true, error = null)
            ui.value = try { ReviewsUi(items = repo.reviews(), loading = false) }
            catch (e: AppError) { ui.value.copy(loading = false, error = e.message) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewsScreen(onBack: () -> Unit, vm: ReviewsViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var filter by remember { mutableIntStateOf(0) }            // 0 = hammasi, 1..5 = shu baho
    var onlyText by remember { mutableStateOf(false) }

    val rated = remember(ui.items) { ui.items.filter { it.stars > 0 } }
    val avg = if (rated.isEmpty()) 0.0 else rated.sumOf { it.stars }.toDouble() / rated.size
    val shown = remember(ui.items, filter, onlyText) {
        ui.items.filter { (filter == 0 || it.stars == filter) && (!onlyText || it.comment.isNotBlank()) }
    }
    val dateFmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onBack), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_arrow_left) }
            Text("Mijozlar izohlari", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        PullToRefreshBox(false, { vm.load() }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = Dt.spaceScreen, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (ui.loading && ui.items.isEmpty()) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green) }
                if (rated.isNotEmpty()) item {
                    SectionCard(bg = Lg.CardStat) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(String.format(Locale.US, "%.1f", avg), color = Lg.Text, fontSize = 40.sp, fontWeight = FontWeight.ExtraBold)
                                Text(stars(Math.round(avg).toInt()), color = Lg.Orange, fontSize = 16.sp)
                                Text("${rated.size} ta baho", color = Lg.Muted, fontSize = 12.sp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (s in 5 downTo 1) {
                                    val n = rated.count { it.stars == s }
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text("$s", color = Lg.Muted, fontSize = 11.sp, modifier = Modifier.width(10.dp))
                                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(Lg.CardHigh)) {
                                            Box(Modifier.fillMaxHeight().fillMaxWidth(n.toFloat() / rated.size).background(Lg.Orange))
                                        }
                                        Text("$n", color = Lg.Muted, fontSize = 11.sp, modifier = Modifier.width(24.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(6) { i ->
                            Chip(if (i == 0) "Hammasi" else "$i ★", filter == i) { filter = i }
                        }
                        item { Chip("Faqat izohli", onlyText) { onlyText = !onlyText } }
                    }
                }
                if (!ui.loading && shown.isEmpty()) item {
                    EmptyState(R.drawable.ic_message, "Izoh topilmadi", ui.error ?: "Mijozlar buyurtmani baholaganda va izoh yozganda shu yerda ko'rinadi")
                }
                items(shown, key = { it.orderId }) { r ->
                    SectionCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (r.stars > 0) stars(r.stars) else "Izoh", color = Lg.Orange, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text("#${r.number}", color = Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        if (r.comment.isNotBlank()) Text(r.comment, color = Lg.Text, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(r.customer, color = Lg.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("${if (r.atMillis > 0) dateFmt.format(Date(r.atMillis)) else ""} • ${r.total.som()}", color = Lg.Muted, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private fun stars(n: Int) = "★".repeat(n.coerceIn(0, 5)) + "☆".repeat(5 - n.coerceIn(0, 5))

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) =
    Text(label, color = if (selected) Dt.onAccent else Lg.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (selected) Lg.Green else Lg.CardHigh).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 9.dp))
