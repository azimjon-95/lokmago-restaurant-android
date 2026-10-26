package uz.lokmago.restaurant.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.domain.DishCategories
import uz.lokmago.restaurant.ui.components.*
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

/**
 * Stop List — vaqtincha to'xtatilgan taomlar (admin panel StopListPage bilan bir xil): mijozlarga ko'rinmaydi,
 * «Qaytarish» bilan darhol sotuvga chiqadi. Ro'yxat soket orqali o'zi yangilanadi (MenuStore).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopListScreen(onOpenMenu: () -> Unit, vm: MenuViewModel = hiltViewModel()) {
    val all by vm.dishes.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val stopped = remember(all) { all.filter { !it.available } }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Dt.spaceScreen, end = Dt.spaceScreen, top = 8.dp, bottom = 12.dp)) {
            Text("Stop List", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
            Text("Vaqtincha to'xtatilgan taomlar — mijozlarga ko'rinmaydi", color = Lg.Muted, fontSize = 13.sp)
        }
        PullToRefreshBox(false, { vm.refresh() }, Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(horizontal = Dt.spaceScreen, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!loaded && all.isEmpty()) item { LinearProgressIndicator0() }
                else if (stopped.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(32.dp)).background(Lg.Green.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_check_circle, Lg.GreenText, 34.dp) }
                        Text("Stop List bo'sh", color = Lg.Text, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                        Text("Barcha taomlar sotuvda. Taom tugasa Menyudan STOP tugmasini bosing.", color = Lg.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp))
                        Text("Menyuga o'tish", color = Lg.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Lg.Line, RoundedCornerShape(12.dp)).clickable(onClick = onOpenMenu).padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                } else item { Text("${stopped.size} ta taom to'xtatilgan", color = Lg.Muted, fontSize = 12.sp) }
                items(stopped, key = { it.id }) { d ->
                    val shape = RoundedCornerShape(Dt.radiusCard)
                    Row(Modifier.fillMaxWidth().clip(shape).background(Lg.Card).border(1.dp, Lg.Line, shape).padding(10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DishThumb(d.imageUrl, 56.dp, grayscale = true)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(d.name, color = Lg.Text, fontWeight = FontWeight.Bold, fontSize = 15.sp, maxLines = 2)
                            Text("${DishCategories.label(d.category)} • ${d.price.som()}", color = Lg.Muted, fontSize = 12.sp)
                        }
                        Text("Qaytarish", color = Dt.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Lg.Green).clickable { vm.toggleStop(d) }.padding(horizontal = 14.dp, vertical = 10.dp))
                    }
                }
            }
        }
    }
}

@Composable private fun LinearProgressIndicator0() = androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth(), color = Lg.Green)
