package uz.lokmago.restaurant.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.AuthRepository
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.theme.Lg

data class LoginUi(val loading: Boolean = false, val error: String? = null)

@HiltViewModel
class LoginViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    val ui = MutableStateFlow(LoginUi())

    /** Pre-fills the form after a logout / expired session. Only the login NAME is remembered, never the password. */
    val lastLogin: String = auth.lastLogin

    fun clearError() { if (ui.value.error != null) ui.value = ui.value.copy(error = null) }

    fun login(login: String, password: String) {
        if (ui.value.loading) return   // keyboard "Done" + button tap, or a double tap
        if (login.isBlank() || password.isBlank()) { ui.value = LoginUi(error = "Login va parolni kiriting"); return }
        viewModelScope.launch {
            ui.value = LoginUi(loading = true)
            ui.value = try { auth.login(login, password); LoginUi() } catch (e: AppError) { LoginUi(error = e.message) }
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    // rememberSaveable: survives rotation / process death while typing
    var login by rememberSaveable { mutableStateOf(vm.lastLogin) }
    var pass by rememberSaveable { mutableStateOf("") }
    var showPass by rememberSaveable { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Lg.Green, unfocusedBorderColor = Lg.Line, focusedContainerColor = Lg.Card, unfocusedContainerColor = Lg.Card,
        focusedTextColor = Lg.Text, unfocusedTextColor = Lg.Text, cursorColor = Lg.Green,
        focusedLabelColor = Lg.Green, unfocusedLabelColor = Lg.Muted)
    fun submit() { focus.clearFocus(); vm.login(login, pass) }

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LgIcon(R.drawable.ic_chef_hat, Lg.Orange, 72.dp)
        Spacer(Modifier.height(10.dp))
        Text("LokmaGo Restoran", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Restoran login va paroli bilan kiring", color = Lg.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(login, { login = it; vm.clearError() }, Modifier.fillMaxWidth(), label = { Text("Login") }, singleLine = true,
            colors = colors, shape = MaterialTheme.shapes.large,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }))
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pass, { pass = it; vm.clearError() }, Modifier.fillMaxWidth(), label = { Text("Parol") }, singleLine = true,
            colors = colors, shape = MaterialTheme.shapes.large,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = { TextButton(onClick = { showPass = !showPass }) { Text(if (showPass) "Yashirish" else "Ko'rsatish", color = Lg.Muted, fontSize = 12.sp) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }))
        ui.error?.let { Text(it, color = Lg.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Kirish", loading = ui.loading) { submit() }
        Spacer(Modifier.height(14.dp))
        Text("Bir marta kirsangiz, ilova sizni eslab qoladi.", color = Lg.Muted, fontSize = 12.sp)
    }
}
