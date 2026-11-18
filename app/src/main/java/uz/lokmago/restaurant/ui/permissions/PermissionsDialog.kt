package uz.lokmago.restaurant.ui.permissions

import androidx.compose.runtime.Composable
import uz.lokmago.restaurant.alert.ReadinessItem

/**
 * Birinchi kirishda (va Bosh sahifadagi «sozlanmagan» banneridan) ochiladi — endi yagona «Telefonni sozlash» ekrani.
 * [items] eski chaqiruvlar bilan moslik uchun qoldi; ekran holatni o'zi o'qiydi (PhoneSetupProbe).
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun PermissionsDialog(items: List<ReadinessItem>, onChanged: () -> Unit, onDismiss: () -> Unit) =
    PhoneSetupScreen(onDismiss = onDismiss, showTest = false, onChanged = onChanged)
