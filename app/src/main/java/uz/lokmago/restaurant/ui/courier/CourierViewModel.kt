package uz.lokmago.restaurant.ui.courier

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.courier.*
import uz.lokmago.restaurant.data.local.CourierSession
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.repo.AppError

data class CourierUi(
    val loaded: Boolean = false, val onShift: Boolean = false, val shift: CShiftDto? = null,
    val offers: List<COfferDto> = emptyList(), val active: CActiveDto? = null,
    val history: List<CHistoryDto> = emptyList(), val stats: CStats? = null,
    val busy: Boolean = false, val message: String? = null,
)

/** Kuryer tomonining BITTA ko'rinish-modeli: smena, takliflar (har 5 s yangilanadi), faol buyurtma, tarix, hisobot. */
@HiltViewModel
class CourierViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val api: CourierApi, private val session: SessionStore, private val registrar: CourierDeviceRegistrar,
) : ViewModel() {
    val ui = MutableStateFlow(CourierUi())
    val me = session.courier
    private var poll: Job? = null

    init { startPolling() }

    /** Takliflar va faol buyurtma har 5 soniyada (push bilan birga: push — uyg'otadi, bu — ro'yxatni to'g'ri ushlaydi). */
    private fun startPolling() {
        poll?.cancel()
        poll = viewModelScope.launch { while (true) { if (session.courierToken != null) refresh(); delay(5_000) } }
    }

    fun refresh() = viewModelScope.launch { load() }

    private suspend fun load() {
        try {
            val shift = courierCall { api.shift() }
            val active = if (shift.activeAssignmentId != null) courierCall { api.active() }.active else null
            val offers = if (shift.onShift && active == null) courierCall { api.offers() } else emptyList()
            ui.value = ui.value.copy(loaded = true, onShift = shift.onShift, shift = shift.shift, active = active, offers = offers)
            // the service follows the SERVER's truth (also after the app was killed / the phone restarted)
            if (shift.onShift) CourierLocationService.start(ctx) else CourierLocationService.stop(ctx)
        } catch (e: AppError) { if (e !is AppError.Unauthorized) ui.value = ui.value.copy(loaded = true) }
    }

    private fun act(block: suspend () -> Unit) = viewModelScope.launch {
        ui.value = ui.value.copy(busy = true, message = null)
        try { block() } catch (e: AppError) { ui.value = ui.value.copy(message = e.message) }
        ui.value = ui.value.copy(busy = false)
    }

    fun startShift() = act { courierCall { api.startShift() }; CourierLocationService.start(ctx); registrar.ensureRegistered(); load() }
    fun endShift() = act { courierCall { api.endShift() }; CourierLocationService.stop(ctx); load() }
    fun accept(id: String) = act { val r = courierCall { api.accept(id) }; ui.value = ui.value.copy(active = r.active); load() }
    fun deliver() = act { courierCall { api.deliver() }; ui.value = ui.value.copy(message = "Buyurtma topshirildi ✓"); load(); loadStats() }
    fun loadHistory() = viewModelScope.launch { runCatching { courierCall { api.history(50) } }.onSuccess { ui.value = ui.value.copy(history = it) } }
    fun loadStats() = viewModelScope.launch { runCatching { courierCall { api.stats() } }.onSuccess { ui.value = ui.value.copy(stats = it) } }
    fun clearMessage() { ui.value = ui.value.copy(message = null) }

    fun login(login: String, password: String, onDone: (String?) -> Unit) = viewModelScope.launch {
        if (login.isBlank() || password.isBlank()) { onDone("Login va parolni kiriting"); return@launch }
        try {
            val r = courierCall { api.login(CLoginReq(login.trim(), password)) }
            session.saveCourier(CourierSession(r.token, r.courier.name.ifBlank { "Kuryer" }, r.courier.login.ifBlank { login.trim() }))
            registrar.ensureRegistered()
            onDone(null)
        } catch (e: AppError) { onDone(e.message) }
    }

    fun testPush() = act { val r = courierCall { api.testPush() }; ui.value = ui.value.copy(message = if (r.sent > 0) "Test yuborildi: bildirishnoma kelishi kerak" else "Telefon serverda ro'yxatda emas — ilovani qayta oching") }

    /** Chiqish: smena yopiq bo'lishi shart (UI buni tekshiradi). Push tokeni serverdan o'chiriladi (JWT hali bor paytda). */
    fun logout() = viewModelScope.launch {
        registrar.unregister()
        CourierLocationService.stop(ctx)
        session.expireCourier()
        ui.value = CourierUi()
    }

    /** Rolni almashtirish (Restoran ↔ Kuryer): tanlash ekraniga qaytadi; sessiyalar saqlanadi. */
    fun switchSide() = session.setRole(null)
}
