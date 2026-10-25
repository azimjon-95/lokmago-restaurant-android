package uz.lokmago.restaurant.ui.alert

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.domain.OrderQueue
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.som

/**
 * Full-screen new-order alert. Shows ONE order (the head of the queue); the rest wait their turn.
 * Accepting the head immediately reveals the next one — the alarm keeps ringing until the queue is empty.
 */
@Composable
fun AlertScreen(
    state: OrderQueue.State,
    silenced: Boolean,
    busy: Boolean,
    online: Boolean,
    onAccept: () -> Unit,
    onSilence: () -> Unit,
    onDefer: () -> Unit,
    onOpen: (String) -> Unit,
    onMinimize: () -> Unit,
) {
    val head = state.head ?: return
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        1f, 0.55f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "a")

    Column(
        Modifier.fillMaxSize().background(Lg.Bg).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.size > 1)
                Text("Navbat: 1 / ${state.size}", color = Lg.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onMinimize), contentAlignment = Alignment.Center) {
                LgIcon(R.drawable.ic_close, Lg.Muted, 22.dp)
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LgIcon(R.drawable.ic_chef_hat, Lg.Orange, 30.dp)
            Column {
                Row { Text("Lokma", color = Lg.Text, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold); Text("Go", color = Lg.Orange, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold) }
                Text("Restoran", color = Lg.Muted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        LgIcon(R.drawable.ic_cloche, Lg.Orange, 84.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            "YANGI BUYURTMA", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp,
            modifier = Modifier.alpha(if (silenced) 1f else pulse).clip(RoundedCornerShape(50)).background(Lg.Red).padding(horizontal = 18.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("#${head.number}", color = Lg.Text, fontSize = 60.sp, fontWeight = FontWeight.ExtraBold)
        Text(head.total.som(), color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Meta(R.drawable.ic_utensils, "${head.itemsCount} ta taom")
            Meta(if (head.payment.name == "CARD") R.drawable.ic_card else R.drawable.ic_cash, "${head.payment.label} to'lov")
            Meta(if (head.fulfillment.name == "DELIVERY") R.drawable.ic_delivery else R.drawable.ic_bag, head.fulfillment.label)
        }

        if (state.waiting.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LgIcon(R.drawable.ic_queue, Lg.Orange, 18.dp)
                    Text("Navbatda yana ${state.waiting.size} ta", color = Lg.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.waiting, key = { it.id }) { o ->
                        Text("#${o.number} • ${o.total.som()}", color = Lg.Muted, fontSize = 12.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Lg.Surface).padding(horizontal = 12.dp, vertical = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))

        Box(Modifier.size(64.dp).clip(CircleShape).background(Lg.Red.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) {
            LgIcon(if (silenced) R.drawable.ic_volume_off else R.drawable.ic_volume, Lg.Red, 30.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                !online -> "Internet yo'q — qabul qilish uchun ulanish kerak"
                silenced -> "Signal o'chirilgan. Buyurtma hali qabul qilinmagan."
                else -> "Buyurtmani qabul qilmaguncha signal davom etadi…"
            },
            color = if (online) Lg.Muted else Lg.Orange, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            Column(
                Modifier.width(96.dp).clickable(enabled = !silenced, onClick = onSilence),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(Modifier.size(64.dp).clip(CircleShape).background(Lg.Red.copy(alpha = if (silenced) 0.35f else 1f)), contentAlignment = Alignment.Center) {
                    LgIcon(R.drawable.ic_close, Dt.onAccent, 30.dp)
                }
                Text("Signalni to'xtatish", color = Lg.Text, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
            Box(
                Modifier.weight(1f).height(104.dp).clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Dt.green, Dt.greenEnd)).let { if (online && !busy) it else Brush.verticalGradient(listOf(Dt.green.copy(alpha = 0.4f), Dt.greenEnd.copy(alpha = 0.4f))) })
                    .clickable(enabled = online && !busy, onClick = onAccept),
                contentAlignment = Alignment.Center,
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(28.dp), color = Dt.onAccent, strokeWidth = 3.dp)
                else Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LgIcon(R.drawable.ic_check_circle, Dt.onAccent, 36.dp)
                    Text("Qabul qilish", color = Dt.onAccent, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Text("Tarkibini ko'rish", color = Lg.Green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { onOpen(head.id) }.padding(12.dp))
            if (state.waiting.isNotEmpty())
                Text("Keyingisiga o'tkazish", color = Lg.Muted, fontSize = 14.sp,
                    modifier = Modifier.clickable(onClick = onDefer).padding(12.dp))
        }
    }
}

@Composable
private fun Meta(icon: Int, text: String) =
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LgIcon(icon, Lg.Muted, 18.dp); Text(text, color = Lg.Text, fontSize = 13.sp)
    }
