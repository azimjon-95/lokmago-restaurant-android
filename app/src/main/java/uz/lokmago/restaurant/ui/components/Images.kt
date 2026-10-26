package uz.lokmago.restaurant.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.ui.theme.Lg
import uz.lokmago.restaurant.util.cdn
import uz.lokmago.restaurant.util.cdnFit

/** Small dish photo (or a plain icon when there is none). Tap = [onClick] (used to open the big picture). */
@Composable
fun DishThumb(url: String?, size: Dp, modifier: Modifier = Modifier, grayscale: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(12.dp)
    val base = modifier.size(size).clip(shape).background(Lg.CardHigh).let { if (onClick != null && url != null) it.clickable(onClick = onClick) else it }
    if (url.isNullOrBlank()) {
        Box(base, contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_utensils, Lg.Muted, size / 2.4f) }
    } else {
        AsyncImage(
            model = url.cdn((size.value * 3).toInt().coerceAtLeast(120)), contentDescription = null, modifier = base, contentScale = ContentScale.Crop,
            colorFilter = if (grayscale) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null,
        )
    }
}

/** Full-screen photo. Pinch to zoom, double-tap to zoom in/out, tap or ✕ to close. */
@Composable
fun ImageLightbox(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.94f)), contentAlignment = Alignment.Center) {
            AsyncImage(
                model = url.cdnFit(1400), contentDescription = null, contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }, onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f }) }
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
            )
            Box(
                Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(16.dp).size(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.16f)).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) { LgIcon(R.drawable.ic_close, Color.White, 22.dp) }
        }
    }
}
