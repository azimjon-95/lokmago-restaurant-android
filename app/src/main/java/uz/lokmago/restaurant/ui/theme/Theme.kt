package uz.lokmago.restaurant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object Lg {
    val Bg = Color(0xFF0B1220)
    val Surface = Color(0xFF111A2B)
    val Card = Color(0xFF15203A)
    val Line = Color(0xFF223050)
    val Text = Color(0xFFF2F5FA)
    val Muted = Color(0xFF8A97AD)
    val Green = Color(0xFF12C98A)
    val GreenDark = Color(0xFF0B8F63)
    val Orange = Color(0xFFFF8A00)
    val Red = Color(0xFFFF3B4E)
    val Blue = Color(0xFF3B82F6)
    val Purple = Color(0xFF8B5CF6)
}

private val scheme = darkColorScheme(
    primary = Lg.Green, onPrimary = Color(0xFF03130D),
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
