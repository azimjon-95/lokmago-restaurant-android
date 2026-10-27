package uz.lokmago.restaurant.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.hhmm
import uz.lokmago.restaurant.util.som

@Composable
fun LgIcon(@DrawableRes id: Int, tint: Color = Lg.Text, size: Dp = 22.dp, modifier: Modifier = Modifier) =
    Icon(painterResource(id), contentDescription = null, tint = tint, modifier = modifier.size(size))

// ---- status colours: every value comes from design/tokens.json (identical on iOS) ----
fun statusColor(s: OrderStatus): Color = when (s) {
    OrderStatus.NEW -> Dt.barNew
    OrderStatus.ACCEPTED -> Dt.barAccepted
    OrderStatus.PREPARING -> Dt.barPreparing
    OrderStatus.READY -> Dt.barReady
    OrderStatus.DELIVERING -> Dt.barDelivering
    OrderStatus.DELIVERED -> Dt.barDelivered
    OrderStatus.CANCELLED, OrderStatus.UNKNOWN -> Dt.barCancelled
}

fun statusPillBg(s: OrderStatus): Color = when (s) {
    OrderStatus.NEW -> Dt.pillNewBg
    OrderStatus.ACCEPTED -> Dt.pillAcceptedBg
    OrderStatus.PREPARING -> Dt.pillPreparingBg
    OrderStatus.READY -> Dt.pillReadyBg
    OrderStatus.DELIVERING -> Dt.pillDeliveringBg
    OrderStatus.DELIVERED -> Dt.pillDeliveredBg
    OrderStatus.CANCELLED, OrderStatus.UNKNOWN -> Dt.pillCancelledBg
}

fun statusPillFg(s: OrderStatus): Color = if (s == OrderStatus.CANCELLED || s == OrderStatus.UNKNOWN) Dt.pillCancelledFg else Dt.pillNewFg

@Composable
fun StatusChip(status: OrderStatus, modifier: Modifier = Modifier) =
    Text(
        status.label, color = statusPillFg(status), fontSize = Dt.fontPill, fontWeight = FontWeight.SemiBold,
        modifier = modifier.clip(RoundedCornerShape(50)).background(statusPillBg(status)).padding(horizontal = 14.dp, vertical = 6.dp),
    )

/**
 * ONLINE = server socket joined · CONNECTING = internet is there, socket (re)connecting · OFFLINE = no internet.
 * Showing "Internet yo'q" while only the socket reconnects (e.g. right after the app was frozen in the background) was the bug.
 */
enum class Conn { ONLINE, CONNECTING, OFFLINE }
private fun Conn.color() = when (this) { Conn.ONLINE -> Lg.GreenText; Conn.CONNECTING -> Lg.Orange; Conn.OFFLINE -> Lg.Red }
private fun Conn.title() = when (this) { Conn.ONLINE -> "Online"; Conn.CONNECTING -> "Ulanmoqda…"; Conn.OFFLINE -> "Offline" }

/** "● Online ⌄" exactly as in the mockup header (text, not a filled pill). */
@Composable
fun ConnectionPill(state: Conn) {
    val c = state.color()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(state.title(), color = c, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        LgIcon(R.drawable.ic_chevron_down, Lg.TextSecondary, 16.dp)
    }
}

/** Small "● Online" chip used inside the restaurant card. */
@Composable
fun OnlineChip(state: Conn) {
    val c = state.color()
    Row(
        Modifier.clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.14f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Text(state.title(), color = c, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bell in a dark circle with the red counter badge from the mockup. */
@Composable
fun BellButton(count: Int, onClick: () -> Unit) {
    Box(Modifier.size(Dt.sizeBell + 8.dp)) {
        Box(Modifier.align(Alignment.BottomStart).size(Dt.sizeBell).clip(CircleShape).background(Lg.CardHigh).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
            LgIcon(if (count > 0) R.drawable.ic_bell_ring else R.drawable.ic_bell, Lg.Text, 22.dp)
        }
        if (count > 0) Text(
            "$count", color = Dt.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).clip(CircleShape).background(Lg.Red).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** Order row: coloured bar on the left, 1dp hairline, status pill, trailing chevron. NEW orders get a red outline. */
@Composable
fun OrderCard(order: Order, modifier: Modifier = Modifier, highlight: Boolean = false, compact: Boolean = false, onClick: () -> Unit) {
    val accent = statusColor(order.status)
    val shape = RoundedCornerShape(Dt.radiusCard)
    Row(
        modifier.fillMaxWidth().clip(shape).background(Lg.Card)
            .border(1.dp, if (highlight || order.status == OrderStatus.NEW) Lg.Red.copy(alpha = 0.55f) else Lg.Line, shape)
            .clickable(onClick = onClick).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(Dt.spaceBar).fillMaxHeight().background(accent))
        Column(Modifier.padding(horizontal = Dt.spaceCardPad, vertical = 12.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${order.number}", fontSize = Dt.fontOrderNo, fontWeight = FontWeight.ExtraBold, color = Lg.Text, modifier = Modifier.weight(1f))
                StatusChip(order.status)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LgIcon(R.drawable.ic_clock, Lg.TextSecondary, 14.dp)
                Text("${order.createdAtMillis.hhmm()} • ${order.total.som()}", color = Lg.TextSecondary, fontSize = Dt.fontMeta)
            }
            if (!compact) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LgIcon(R.drawable.ic_utensils, Lg.TextSecondary, 14.dp)
                Text("${order.itemsCount} ta taom", color = Lg.TextSecondary, fontSize = Dt.fontMeta)
                Text("•", color = Lg.TextSecondary)
                LgIcon(if (order.payment.name == "CARD") R.drawable.ic_card else R.drawable.ic_cash, Lg.TextSecondary, 14.dp)
                Text(order.payment.label, color = Lg.TextSecondary, fontSize = Dt.fontMeta)
            }
        }
        if (!compact) Box(Modifier.fillMaxHeight().padding(end = 8.dp), contentAlignment = Alignment.CenterEnd) { LgIcon(R.drawable.ic_chevron_right, Lg.Blue.copy(alpha = 0.45f), 16.dp) }
    }
}

@Composable
fun PrimaryButton(text: String, modifier: Modifier = Modifier, icon: Int? = null, enabled: Boolean = true, loading: Boolean = false, color: Color = Lg.Green, onClick: () -> Unit) {
    Button(
        onClick = onClick, enabled = enabled && !loading, modifier = modifier.fillMaxWidth().height(Dt.sizeButton),
        shape = RoundedCornerShape(Dt.radiusButton),
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Dt.onAccent, disabledContainerColor = color.copy(alpha = 0.4f)),
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = Dt.onAccent, strokeWidth = 2.5.dp)
        else {
            icon?.let { LgIcon(it, Dt.onAccent, 20.dp); Spacer(Modifier.width(8.dp)) }
            Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, bg: Color = Lg.Card, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(Dt.radiusCard)).background(bg).padding(Dt.spaceCardPad), content = content)

/** Square icon holder from the mockup (Naqd / Karta / Bugungi tushum). */
@Composable
fun IconBox(@DrawableRes icon: Int, bg: Color, tint: Color, modifier: Modifier = Modifier) =
    Box(modifier.size(Dt.sizeIconBox).clip(RoundedCornerShape(Dt.radiusIconBox)).background(bg), contentAlignment = Alignment.Center) { LgIcon(icon, tint, 24.dp) }

/** Stat tile (home screen). [trailing] is the icon slot on the right. */
@Composable
fun StatCard(modifier: Modifier, label: String, value: String, delta: String? = null, trailing: (@Composable () -> Unit)? = null) =
    Row(modifier.clip(RoundedCornerShape(Dt.radiusCard)).background(Lg.CardStat).padding(Dt.spaceCardPad), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Lg.TextSecondary, fontSize = Dt.fontMeta)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(value, color = Lg.Text, fontSize = Dt.fontBig, fontWeight = FontWeight.ExtraBold)
                delta?.let { Text(it, color = Lg.GreenText, fontSize = Dt.fontLabel, modifier = Modifier.padding(bottom = 5.dp)) }
            }
        }
        trailing?.invoke()
    }

/** Orders-screen tab: card-coloured, count badge top-right; the selected tab is filled with the status colour. */
@Composable
fun TabChip(label: String, count: Int, selected: Boolean, color: Color, badgeColor: Color, onClick: () -> Unit) {
    Box(Modifier.padding(end = 10.dp)) {
        Box(
            Modifier.clip(RoundedCornerShape(Dt.radiusTab)).background(if (selected) color else Lg.CardHigh).clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
        ) { Text(label, color = Lg.Text, fontSize = Dt.fontTab, fontWeight = FontWeight.SemiBold) }
        if (count > 0) Text(
            "$count", color = if (selected) color else Dt.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-6).dp).clip(CircleShape)
                .background(if (selected) Color.White else badgeColor).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun EmptyState(icon: Int, title: String, hint: String) =
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LgIcon(icon, Lg.Muted, 40.dp)
        Text(title, color = Lg.Text, fontWeight = FontWeight.SemiBold)
        Text(hint, color = Lg.Muted, fontSize = Dt.fontMeta)
    }

@Composable
fun outlinedBorder() = BorderStroke(1.dp, Lg.Line)

/** Text-field colours shared by the login and the dish form. */
@Composable
fun lgFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lg.Green, unfocusedBorderColor = Lg.Line, focusedContainerColor = Lg.Card, unfocusedContainerColor = Lg.Card,
    focusedTextColor = Lg.Text, unfocusedTextColor = Lg.Text, cursorColor = Lg.Green,
    focusedLabelColor = Lg.Green, unfocusedLabelColor = Lg.Muted,
)
