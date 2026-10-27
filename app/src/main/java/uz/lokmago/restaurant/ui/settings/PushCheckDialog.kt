package uz.lokmago.restaurant.ui.settings

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.R
import uz.lokmago.restaurant.alert.AlertReadiness
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.apiCall
import uz.lokmago.restaurant.push.DeviceRegistrar
import uz.lokmago.restaurant.ui.components.LgIcon
import uz.lokmago.restaurant.ui.components.PrimaryButton
import uz.lokmago.restaurant.ui.theme.Dt
import uz.lokmago.restaurant.ui.theme.Lg

data class PushStep(val ok: Boolean, val title: String, val advice: String = "")

/** Zanjir: ruxsat → Firebase → FCM tokeni → token serverda → serverda FCM sozlangan → test push haqiqatan yetadimi. */
@HiltViewModel
class PushCheckViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val api: LokmaApi, private val registrar: DeviceRegistrar, private val session: SessionStore, private val readiness: AlertReadiness,
) : ViewModel() {
    val steps = MutableStateFlow<List<PushStep>>(emptyList())
    val test = MutableStateFlow<List<PushStep>>(emptyList())
    val running = MutableStateFlow(true)
    val testing = MutableStateFlow(false)

    init { run() }

    private fun run() = viewModelScope.launch {
        val out = mutableListOf<PushStep>()
        val notif = readiness.check().firstOrNull { it.id == "notifications" }?.ok ?: true
        out += PushStep(notif, if (notif) "Bildirishnomalar yoqilgan" else "Bildirishnomalar O'CHIQ", if (notif) "" else "Sozlamalar → Ilovalar → LGO Business → Bildirishnomalar → yoqing.")
        val firebase = runCatching { FirebaseApp.getApps(ctx).isNotEmpty() }.getOrDefault(false)
        out += PushStep(firebase, if (firebase) "Firebase ulangan" else "Firebase sozlanmagan",
            if (firebase) "" else "Ilovaga app/google-services.json qo'shilmagan (yoki paket nomi mos emas). Firebase konsolidan yuklab, qayta yig'ing.")
        if (firebase) registrar.register()                                   // token olinadi va serverga yuboriladi
        val token = session.fcmToken
        out += PushStep(token != null, if (token != null) "Telefon push tokeni (FCM) olingan" else "Push tokeni hali yo'q",
            if (token != null) "" else "Internetni tekshiring, ilovani qayta oching. Google Play xizmatlari bo'lmagan telefonlarda push ishlamaydi.")
        try {
            val r = apiCall { api.devices() }
            out += PushStep(r.providers.android, if (r.providers.android) "Serverda Android push (FCM) sozlangan" else "Serverda FCM SOZLANMAGAN",
                if (r.providers.android) "" else "Server .env: FCM_SERVICE_ACCOUNT_BASE64 (ilovaning google-services.json i bilan BIR XIL Firebase loyihasidan). So'ng serverni qayta ishga tushiring.")
            val tail = token?.takeLast(6).orEmpty()
            val mine = tail.isNotEmpty() && r.devices.any { it.platform == "android" && it.token.endsWith(tail) }
            out += PushStep(mine, if (mine) "Bu telefon serverda ro'yxatda" else "Bu telefon serverda ro'yxatda YO'Q", if (mine) "" else "Chiqib qayta kiring. Serverda ${r.devices.size} ta qurilma bor.")
        } catch (e: AppError) {
            out += PushStep(false, if (e is AppError.NotFound) "Server eski: push endpointi yo'q" else "Server bilan bog'lanib bo'lmadi",
                if (e is AppError.NotFound) "lakmago-serverni yangilab (git pull, pm2 restart) qayta ishga tushiring." else e.message.orEmpty())
        }
        steps.value = out; running.value = false
    }

    fun sendTest() = viewModelScope.launch {
        testing.value = true
        test.value = try {
            val r = apiCall { api.testPush() }
            if (r.results.isEmpty()) listOf(PushStep(false, "Serverda hech qanday qurilma yo'q", "Avval yuqoridagi qadamlarni tuzating."))
            else r.results.map { PushStep(it.ok, if (it.ok) "Test yuborildi (${it.platform} ${it.token})" else "Test yetmadi (${it.platform} ${it.token})", if (it.ok) "Telefonga xabar kelishi kerak." else explain(it.error.orEmpty())) }
        } catch (e: AppError) { listOf(PushStep(false, "Test yuborilmadi", e.message.orEmpty())) }
        testing.value = false
    }

    /** Google/server xatosi → nima qilish kerak (oddiy tilda) */
    private fun explain(e: String) = when {
        "UNREGISTERED" in e || "NOT_FOUND" in e -> "$e. Token eskirgan: ilovani qayta oching."
        "SENDER_ID_MISMATCH" in e -> "$e. Token boshqa Firebase loyihasidan: ilovadagi google-services.json va serverdagi FCM kaliti BIR loyihadan bo'lsin."
        "401" in e || "403" in e || "THIRD_PARTY" in e || "OAuth" in e -> "$e. Serverdagi FCM service account kaliti noto'g'ri yoki bu loyihada Cloud Messaging API yoqilmagan."
        "sozlanmagan" in e -> "$e. Server .env to'ldirilmagan."
        else -> e
    }
}

@Composable
fun PushCheckDialog(onDismiss: () -> Unit, vm: PushCheckViewModel = hiltViewModel()) {
    val steps by vm.steps.collectAsStateWithLifecycle()
    val test by vm.test.collectAsStateWithLifecycle()
    val running by vm.running.collectAsStateWithLifecycle()
    val testing by vm.testing.collectAsStateWithLifecycle()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = Lg.Bg, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Push tekshiruvi", color = Lg.Text, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) { LgIcon(R.drawable.ic_close, Lg.Text) }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ilova yopiq yoki telefon bloklangan paytda yangi buyurtma xabari faqat shu zanjir to'liq bo'lsa keladi.", color = Lg.TextSecondary, fontSize = 13.sp)
                    if (running) Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Lg.Green) }
                    (steps + test).forEach { s ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Lg.Card).padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(Modifier.size(30.dp).clip(CircleShape).background((if (s.ok) Lg.Green else Lg.Orange).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                                LgIcon(if (s.ok) R.drawable.ic_check else R.drawable.ic_warning, if (s.ok) Lg.GreenText else Lg.Orange, 16.dp)
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(s.title, color = Lg.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                if (s.advice.isNotEmpty()) Text(s.advice, color = Lg.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Test push yuborish", icon = R.drawable.ic_bell_ring, loading = testing, enabled = !running, color = Lg.Orange) { vm.sendTest() }
                    Text("Bosgach ilovani yoping (yaqinlarni tozalang) va ekranni bloklang — 5-10 soniyada «Push ishlayapti ✓» xabari kelishi kerak.", color = Lg.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}
