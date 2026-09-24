package uz.lokmago.restaurant.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderStatus
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.hhmm
import uz.lokmago.restaurant.util.som

@Composable
fun LgIcon(@DrawableRes id: Int, tint: Color = Lg.Text, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))

fun statusColor(s: OrderStatus): Color = when (s) {
    OrderStatus.NEW -> Lg.Red
    OrderStatus.ACCEPTED -> Lg.Orange
    OrderStatus.PREPARING -> Lg.Blue
    OrderStatus.READY -> Lg.Green
    OrderStatus.DELIVERING -> Lg.Purple
    OrderStatus.DELIVERED -> Lg.GreenDark
    OrderStatus.CANCELLED, OrderStatus.UNKNOWN -> Lg.Muted
}

@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val c = statusColor(status)
    Text(
        status.label, color = if (status == OrderStatus.NEW) Color.White else c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50))
            .background(if (status == OrderStatus.NEW) c else c.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
fun ConnectionPill(online: Boolean) {
    val c = if (online) Lg.Green else Lg.Red
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(if (online) "Online" else "Offline", color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun OrderCard(order: Order, modifier: Modifier = Modifier, highlight: Boolean = false, onClick: () -> Unit) {
    val accent = statusColor(order.status)
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card)
            .then(if (highlight) Modifier.background(Lg.Red.copy(alpha = 0.06f)) else Modifier)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.width(4.dp).height(IntrinsicSize.Max).fillMaxHeight().background(accent))
        Column(Modifier.padding(14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${order.number}", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Lg.Text, modifier = Modifier.weight(1f))
                StatusChip(order.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LgIcon(R.drawable.ic_clock, Lg.Muted, 14.dp)
                Text("${order.createdAtMillis.hhmm()} • ${order.total.som()}", color = Lg.Muted, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LgIcon(R.drawable.ic_utensils, Lg.Muted, 14.dp)
                Text("${order.itemsCount} ta taom", color = Lg.Muted, fontSize = 13.sp)
                Text("•", color = Lg.Muted)
                LgIcon(if (order.payment.name == "CARD") R.drawable.ic_card else R.drawable.ic_cash, Lg.Muted, 14.dp)
                Text(order.payment.label, color = Lg.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true, loading: Boolean = false, color: Color = Lg.Green, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color(0xFF03130D), disabledContainerColor = color.copy(alpha = 0.4f)),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Color(0xFF03130D), strokeWidth = 2.5.dp)
        else {
            icon?.let { LgIcon(it, Color(0xFF03130D), 20.dp); Spacer(Modifier.width(8.dp)) }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp), content = content)

@Composable
fun EmptyState(icon: Int, title: String, hint: String) =
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LgIcon(icon, Lg.Muted, 40.dp)
        Text(title, color = Lg.Text, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Lg.Muted, fontSize = 13.sp)
    }

@Composable
fun outlinedBorder() = BorderStroke(1.dp, Lg.Line)
