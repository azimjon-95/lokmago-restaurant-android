package uz.lokmago.restaurant.ui.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.alert.PhoneSetup
import uz.lokmago.restaurant.alert.PhoneSetup.Action
import uz.lokmago.restaurant.alert.PhoneSetup.Id
import uz.lokmago.restaurant.alert.PhoneSetupProbe
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.apiCall
import uz.lokmago.restaurant.push.DeviceRegistrar
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

@HiltViewModel
class PhoneSetupViewModel @Inject constructor(
    private val probe: PhoneSetupProbe,
    private val registrar: DeviceRegistrar,
    private val api: LokmaApi,
    private val session: SessionStore,
) : ViewModel() {
    val state = MutableStateFlow(probe.local().copy(connecting = true))
    val testText = MutableStateFlow<String?>(null)
    val testing = MutableStateFlow(false)
    /** Faqat yordam xizmati uchun (yig'ilgan holda turadi): dasturchi tilidagi tafsilotlar shu yerda. */
    val tech = MutableStateFlow<List<String>>(emptyList())
    private var connectJob: Job? = null

    init { connect() }

    /** Tizim sozlamasidan qaytganda: mahalliy holatni qayta o'qish (ulanish natijasi saqlanadi). */
    fun refresh() {
        val s = state.value
        state.value = probe.local().copy(connecting = s.connecting, connected = s.connected, serviceDown = s.serviceDown)
        if (!s.connected && !s.connecting) connect()                       // masalan internet endi yoqildi
    }

    /** Ulanishni ilova O'ZI hal qiladi: bir necha marta urinadi, foydalanuvchidan hech narsa so'ramaydi. */
    fun connect() {
        if (connectJob?.isActive == true) return
        connectJob = viewModelScope.launch {
            state.value = probe.local().copy(connecting = true)
            val local = probe.local()
            var registered = false
            var serverPush: Boolean? = null
            val lines = mutableListOf<String>()
            if (local.appHasPush && local.googleServices && local.online) {
                for (wait in longArrayOf(0, 3_000, 7_000)) {
                    delay(wait)
                    registered = runCatching { registrar.register() }.getOrDefault(false)
                    if (registered) break
                }
                try {
                    val r = apiCall { api.devices() }
                    serverPush = r.providers.android
                    lines += "server android push: ${r.providers.android}; devices: ${r.devices.size}"
                } catch (e: AppError) { lines += "devices: ${e.message}" }
            }
            lines += "app push: ${local.appHasPush}; gms: ${local.googleServices}; online: ${local.online}; token: ${session.fcmToken?.takeLast(6)?.let { "…$it" } ?: "none"}; registered: $registered"
            tech.value = lines
            state.value = probe.local().copy(connecting = false, connected = registered && serverPush == true, serviceDown = registered && serverPush == false)
        }
    }

    fun confirmAutostart() { session.autostartConfirmed = true; refresh() }

    fun sendTest() = viewModelScope.launch {
        testing.value = true
        testText.value = try {
            val r = apiCall { api.testPush() }
            tech.value = tech.value + r.results.map { "test ${it.platform} ${it.token}: ${if (it.ok) "ok" else it.error.orEmpty()}" }
            PhoneSetup.testMessage(r.results.count { it.ok }, r.results.count { !it.ok })
        } catch (e: AppError) {
            tech.value = tech.value + "test: ${e.message}"
            if (e is AppError.Offline) "Internet yo'q. Ulanib, qayta urining." else PhoneSetup.testMessage(0, 1)
        }
        testing.value = false
    }

    fun intents(id: Id) = probe.intents(id)
    fun internet() = probe.internetIntents()
    fun update() = probe.updateIntents()
    fun open(intents: List<android.content.Intent>, ctx: Context) = probe.open(intents, ctx)
}

/**
 * «TELEFONNI SOZLASH» — bitta ekran, bitta asosiy tugma. Birinchi kirishda ham, Profil → «Telefonni sozlash»da ham shu.
 * «Hammasini sozlash» kerakli tizim oynalarini KETMA-KET o'zi ochadi: foydalanuvchi ruxsat berib orqaga qaytadi — keyingisi ochiladi.
 * Ekranda faqat oddiy til; texnik tafsilot pastda yig'ilgan holda (yordam xizmati uchun).
 */
@Composable
fun PhoneSetupScreen(onDismiss: () -> Unit, showTest: Boolean, onChanged: () -> Unit = {}, vm: PhoneSetupViewModel = hiltViewModel()) {
    val ctx = LocalContext.current
    val s by vm.state.collectAsStateWithLifecycle()
    val testText by vm.testText.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    val tech by vm.tech.collectAsStateWithLifecycle()
    val plan = remember(s) { PhoneSetup.plan(s) }

    var guided by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf(setOf<Id>()) }
    var resumes by remember { mutableIntStateOf(0) }
    var showTech by remember { mutableStateOf(false) }
    var askedAt by remember { mutableLongStateOf(0L) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(); onChanged(); resumes++ }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        // «Boshqa so'ramang» bosilgan bo'lsa tizim oynasi ko'rinmay darhol qaytadi — unda sozlamalarning o'zini ochamiz
        if (!granted && SystemClock.elapsedRealtime() - askedAt < 400) vm.open(vm.intents(Id.NOTIFICATIONS), ctx)
        vm.refresh(); onChanged()
    }

    fun launch(step: PhoneSetup.Step) {
        opened = opened + step.id
        when (step.action) {
            Action.FIX -> if (step.id == Id.NOTIFICATIONS && Build.VERSION.SDK_INT >= 33) {
                askedAt = SystemClock.elapsedRealtime(); notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else vm.open(vm.intents(step.id), ctx)
            Action.OPEN_INTERNET -> vm.open(vm.internet(), ctx)
            Action.UPDATE_APP -> vm.open(vm.update(), ctx)
            Action.RETRY -> vm.connect()
            Action.WAIT, Action.NONE -> Unit
        }
    }

    // Ketma-ket sozlash: har qaytishda navbatdagi kerakli oyna (rad etilgani qayta ochilmaydi)
    LaunchedEffect(resumes, guided) {
        if (!guided) return@LaunchedEffect
        delay(650)
        val next = PhoneSetup.next(PhoneSetup.plan(vm.state.value), opened)
        if (next == null) guided = false else launch(next)
    }

    val done = PhoneSetup.doneCount(plan)
    val fixes = PhoneSetup.pendingFixes(plan)
    val all = PhoneSetup.allDone(plan)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Lg.Bg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Telefonni sozlash", color = Lg.Text, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_close, Lg.Text) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Telefon qulflangan yoki ilova yopiq bo'lsa ham yangi buyurtma ovoz bilan kelishi uchun.", color = Lg.TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))

                    // Holat: hammasi tayyor yoki nechta qoldi
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (all) Lg.Green.copy(alpha = 0.14f) else Lg.Card).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LgIcon(if (all) R.drawable.ic_check_circle else R.drawable.ic_bell_ring, if (all) Lg.GreenText else Lg.Orange, 30.dp)
                            Column(Modifier.weight(1f)) {
                                Text(if (all) "Hammasi tayyor" else "$done / ${plan.size} tayyor", color = Lg.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text(if (all) "Buyurtmalar o'z vaqtida keladi" else if (fixes > 0) "Pastdagi tugmani bosing — kerakli oynalar o'zi ochiladi" else "Bir oz kuting…", color = Lg.Muted, fontSize = 13.sp)
                            }
                        }
                        LinearProgressIndicator(progress = { done.toFloat() / plan.size.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)), color = Lg.Green, trackColor = Lg.CardHigh)
                    }

                    plan.forEachIndexed { i, step -> StepRow(i + 1, step, opened = step.id in opened, onAction = { launch(step) }, onConfirm = vm::confirmAutostart) }

                    if (showTest && s.connected) {
                        testText?.let { Text(it, color = Lg.Text, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Lg.CardStat).padding(14.dp)) }
                    }

                    // Faqat yordam xizmati uchun — odatda yopiq
                    Text(if (showTech) "Texnik ma'lumotni yashirish" else "Texnik ma'lumot (yordam xizmati uchun)", color = Lg.Muted, fontSize = 12.sp,
                        modifier = Modifier.clickable { showTech = !showTech }.padding(vertical = 8.dp, horizontal = 4.dp))
                    if (showTech) Text((tech + "android ${Build.VERSION.SDK_INT}; ${Build.MANUFACTURER} ${Build.MODEL}").joinToString("\n"), color = Lg.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                }
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        fixes > 0 -> PrimaryButton(if (fixes == 1) "Sozlash" else "Hammasini sozlash ($fixes)", icon = R.drawable.ic_check_circle, color = Lg.Orange) {
                            opened = emptySet(); guided = true; resumes++
                        }
                        showTest && s.connected -> PrimaryButton("Sinov xabarini yuborish", icon = R.drawable.ic_bell_ring, loading = testing) { vm.sendTest() }
                        else -> PrimaryButton("Tayyor", icon = R.drawable.ic_check, color = Lg.Green, onClick = onDismiss)
                    }
                    if (!all) Text("Keyinroq", color = Lg.Muted, fontSize = 14.sp, modifier = Modifier.clickable(onClick = onDismiss).padding(10.dp))
                }
            }
        }
    }
}

@Composable
private fun StepRow(n: Int, step: PhoneSetup.Step, opened: Boolean, onAction: () -> Unit, onConfirm: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Lg.Card).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(if (step.done) Lg.Green.copy(alpha = 0.18f) else Lg.Orange.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            when {
                step.done -> LgIcon(R.drawable.ic_check, Lg.GreenText, 18.dp)
                step.action == Action.WAIT -> CircularProgressIndicator(Modifier.size(16.dp), color = Lg.Orange, strokeWidth = 2.dp)
                else -> Text("$n", color = Lg.Orange, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(step.title, color = Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(if (step.done && step.id != Id.CONNECTION) "Tayyor" else step.hint, color = if (step.done) Lg.GreenText else Lg.Muted, fontSize = 13.sp)
            // Avto-ishga tushirishni Android tekshira olmaydi: oynadan qaytgach foydalanuvchi tasdiqlaydi
            if (step.id == Id.AUTOSTART && !step.done && opened) Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ha, yoqdim", color = Dt.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Lg.Green).clickable(onClick = onConfirm).padding(horizontal = 12.dp, vertical = 7.dp))
                Text("Yana ochish", color = Lg.Orange, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 7.dp))
            }
        }
        val showBtn = !step.done && step.button.isNotBlank() && !(step.id == Id.AUTOSTART && opened)
        if (showBtn) Text(step.button, color = Dt.onAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Lg.Orange).clickable(onClick = onAction).padding(horizontal = 12.dp, vertical = 9.dp))
    }
}
