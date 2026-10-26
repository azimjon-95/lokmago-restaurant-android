package uz.lokmago.restaurant.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

data class BarItem(@DrawableRes val icon: Int, val label: String, val badge: Int = 0)

/**
 * Instagram / iPhone uslubidagi suzuvchi menyu: ekran chetlaridan ajralib turgan kapsula, faqat ikonkalar,
 * tanlangani yumshoq "tabletka" bilan ajratiladi, sonlar qizil nishonda. iOS'dagi shisha ("Liquid Glass") bilan bir xil shakl.
 * Fon yarim shaffof (Android'da haqiqiy blur yo'q); kontent bar ostida emas, ustida tugaydi.
 */
@Composable
fun FloatingTabBar(items: List<BarItem>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    Box(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(
            Modifier.fillMaxWidth().height(66.dp)
                .shadow(18.dp, shape, clip = false, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(shape).background(Color(0xE60F1D36)).border(0.5.dp, Color.White.copy(alpha = 0.16f), shape).padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { i, item ->
                val on = i == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(shape)
                        .background(if (on) Color.White.copy(alpha = 0.14f) else Color.Transparent)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        }
                        .semantics { contentDescription = item.label + if (item.badge > 0) ", ${item.badge}" else "" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box {
                        LgIcon(item.icon, if (on) Lg.Green else Lg.TextSecondary, 26.dp)
                        if (item.badge > 0) Text(
                            if (item.badge > 99) "99+" else "${item.badge}", color = Dt.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.align(Alignment.TopEnd).offset(x = 12.dp, y = (-8).dp).clip(CircleShape).background(Lg.Red).padding(horizontal = 5.dp, vertical = 1.dp),
                        )
                    }
                }
            }
        }
    }
}
