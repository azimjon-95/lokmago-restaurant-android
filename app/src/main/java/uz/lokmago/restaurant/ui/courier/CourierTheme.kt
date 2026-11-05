package uz.lokmago.restaurant.ui.courier

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * KURYER TOMONI — yorug' to'q sariq dizayn (restoran tomoni qora; bu alohida mahsulot kabi ko'rinadi).
 * Tizim panellari (soat, antenna, batareya) yorug' fonda QORA ikonkalar bilan, chiqishda esa avvalgi holatga qaytadi.
 */
object Cr {
    val Orange = Color(0xFFFF6B00); val OrangeDark = Color(0xFFE85D00); val OrangeSoft = Color(0xFFFFF1E6)
    val Surface = Color(0xFFF6F6F8); val Card = Color.White; val Line = Color(0xFFE8E8EC)
    val Text = Color(0xFF16181D); val Muted = Color(0xFF6B7280)
    val Green = Color(0xFF16A34A); val GreenSoft = Color(0xFFE8F7EE); val Red = Color(0xFFE5484D)
    val Dark = Color(0xFF14171C)
}

@Composable
fun LightSystemBars() {
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val c = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = c?.isAppearanceLightStatusBars
        c?.isAppearanceLightStatusBars = true
        c?.isAppearanceLightNavigationBars = true
        onDispose { before?.let { c.isAppearanceLightStatusBars = it }; c?.isAppearanceLightNavigationBars = false }
    }
}

@Composable
fun CrCard(modifier: Modifier = Modifier, bg: Color = Cr.Card, border: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(modifier.fillMaxWidth().clip(shape).background(bg).let { if (border) it.border(1.dp, Cr.Line, shape) else it }.padding(16.dp), content = content)
}

@Composable
fun CrButton(text: String, modifier: Modifier = Modifier, filled: Boolean = true, danger: Boolean = false, loading: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val bg = if (!filled) Color.Transparent else if (danger) Cr.Red else Cr.Orange
    val fg = if (filled) Color.White else if (danger) Cr.Red else Cr.Orange
    Box(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape).background(bg.copy(alpha = if (enabled) 1f else 0.4f))
            .let { if (!filled) it.border(1.5.dp, fg.copy(alpha = if (enabled) 1f else 0.4f), shape) else it }
            .clickable(enabled = enabled && !loading, onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) CircularProgressIndicator(Modifier.size(22.dp), color = fg, strokeWidth = 2.5.dp)
        else Text(text, color = fg, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun CrSegment(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0xFFEDEDF1)).padding(4.dp)) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (i == selected) Cr.Orange else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (i == selected) Color.White else Cr.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
