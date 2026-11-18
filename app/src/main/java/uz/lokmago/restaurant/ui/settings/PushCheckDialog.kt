package uz.lokmago.restaurant.ui.settings

import androidx.compose.runtime.Composable
import uz.lokmago.restaurant.ui.permissions.PhoneSetupScreen

/** Profil → «Telefonni sozlash»: o'sha yagona ekran + «Sinov xabarini yuborish». (Eski texnik «Push tekshiruvi» o'rniga.) */
@Composable
fun PushCheckDialog(onDismiss: () -> Unit) = PhoneSetupScreen(onDismiss = onDismiss, showTest = true)
