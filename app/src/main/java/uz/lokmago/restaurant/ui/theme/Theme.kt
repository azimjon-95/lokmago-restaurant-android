package uz.lokmago.restaurant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Legacy names kept so every screen keeps compiling; the VALUES come from the shared design tokens (Dt). */
object Lg {
    val Bg = Dt.bg
    val Surface = Dt.header
    val Card = Dt.card
    val CardStat = Dt.cardStat
    val CardHigh = Dt.cardHigh
    val Line = Dt.line
    val Text = Dt.text
    val TextSecondary = Dt.textSecondary
    val Muted = Dt.textMuted
    val Green = Dt.green
    val GreenText = Dt.greenText
    val GreenDark = Dt.greenDeep
    val Orange = Dt.orange
    val Red = Dt.red
    val Blue = Dt.blue
    val Purple = Dt.purple
}

private val scheme = darkColorScheme(
    primary = Lg.Green, onPrimary = Dt.onAccent,
    background = Lg.Bg, onBackground = Lg.Text,
    surface = Lg.Surface, onSurface = Lg.Text,
    surfaceVariant = Lg.Card, onSurfaceVariant = Lg.Muted,
    error = Lg.Red, outline = Lg.Line,
)

private val type = Typography(
    headlineLarge = TextStyle(fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp),
    bodyMedium = TextStyle(fontSize = 13.sp),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LokmaGoTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
