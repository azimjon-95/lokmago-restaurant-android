package uz.lokmago.restaurant.ui.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
    fun login(login: String, password: String) {
        if (login.isBlank() || password.isBlank()) { ui.value = LoginUi(error = "Restoran ID va PIN kodni kiriting"); return }
        viewModelScope.launch {
            ui.value = LoginUi(loading = true)
            ui.value = try { auth.login(login, password); LoginUi() } catch (e: AppError) { LoginUi(error = e.message) }
        }
    }
}

@Composable
fun LoginScreen(vm: LoginViewModel = hiltViewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    var login by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Lg.Green, unfocusedBorderColor = Lg.Line, focusedContainerColor = Lg.Card, unfocusedContainerColor = Lg.Card,
        focusedTextColor = Lg.Text, unfocusedTextColor = Lg.Text, cursorColor = Lg.Green,
        focusedLabelColor = Lg.Green, unfocusedLabelColor = Lg.Muted)

    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        LgIcon(R.drawable.ic_chef_hat, Lg.Orange, 72.dp)
        Spacer(Modifier.height(10.dp))
        Text("LokmaGo Restoran", color = Lg.Text, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text("Restoran ID va PIN kod bilan kiring", color = Lg.Muted, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(login, { login = it }, Modifier.fillMaxWidth(), label = { Text("Restoran ID") }, singleLine = true, colors = colors, shape = MaterialTheme.shapes.large)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(pass, { pass = it }, Modifier.fillMaxWidth(), label = { Text("PIN kod") }, singleLine = true, colors = colors, shape = MaterialTheme.shapes.large,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        ui.error?.let { Text(it, color = Lg.Red, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp)) }
        Spacer(Modifier.height(20.dp))
        PrimaryButton("Kirish", loading = ui.loading) { vm.login(login, pass) }
    }
}
