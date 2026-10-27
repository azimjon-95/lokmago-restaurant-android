package uz.lokmago.restaurant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.alert.AlertController
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.local.SettingsStore
import uz.lokmago.restaurant.data.repo.AuthRepository
import uz.lokmago.restaurant.alert.AlertReadiness
import uz.lokmago.restaurant.alert.ReadinessItem
import uz.lokmago.restaurant.data.repo.MenuStore
import uz.lokmago.restaurant.ui.components.Conn
import uz.lokmago.restaurant.data.remote.dto.VersionDto
import uz.lokmago.restaurant.realtime.*

/** App-wide state: session, connectivity, the order queue and the alert overlay. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val coordinator: OrderCoordinator,
    private val sessionStore: SessionStore,
    private val readiness: AlertReadiness,
    net: NetworkMonitor,
    socket: SocketManager,
    private val alert: AlertController,
    private val auth: AuthRepository,
    private val settings: SettingsStore,
    private val menu: MenuStore,
) : ViewModel() {

    val session = sessionStore.session
    /** Stop List tab badge — live (socket `dish:stop`). */
    val stoppedCount = menu.stoppedCount
    val queue = coordinator.queue.state
    val silenced = alert.silenced
    /** Real INTERNET only (accepting an order is a REST call: it needs internet, not the socket). */
    val online: StateFlow<Boolean> = net.online
    /** ONLINE / CONNECTING / OFFLINE for the header label and the banner. */
    val connection: StateFlow<Conn> = combine(net.online, socket.connected) { n, s -> if (!n) Conn.OFFLINE else if (s) Conn.ONLINE else Conn.CONNECTING }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Conn.CONNECTING)

    // ---- permissions ----
    val readinessItems = MutableStateFlow<List<ReadinessItem>>(emptyList())
    val showPermissions = MutableStateFlow(false)
    val onboarded: Boolean get() = sessionStore.permissionsOnboarded
    fun refreshReadiness() { readinessItems.value = readiness.check() }
    fun permissionsFinished() { sessionStore.permissionsOnboarded = true; showPermissions.value = false; refreshReadiness() }

    private val hidden = MutableStateFlow(false)
    val alertVisible: StateFlow<Boolean> = combine(session, queue, hidden) { s, q, h -> s != null && !q.isEmpty && !h }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
        // "Avto yangilash": while the app is open, re-sync the pending orders every N seconds as a safety net behind the socket.
        viewModelScope.launch {
            settings.state.map { it.autoRefreshSec }.distinctUntilChanged().collectLatest { sec ->
                if (sec > 0) while (true) { delay(sec * 1_000L); if (session.value != null) { coordinator.syncPending(); menu.reconcile() } }
            }
        }
        viewModelScope.launch { coordinator.arrivals.collect { hidden.value = false } }   // new order ⇒ alert comes back
        viewModelScope.launch { session.collect { if (it != null) coordinator.start() } }
    }

    fun acceptHead() {
        val head = queue.value.head ?: return
        if (busy.value) return
        viewModelScope.launch {
            busy.value = true
            when (val r = coordinator.accept(head.id)) {
                is ActionResult.Done -> messages.emit("#${head.number} qabul qilindi")
                ActionResult.AlreadyHandled -> messages.emit("Buyurtma allaqachon qabul qilingan")
                is ActionResult.Failed -> messages.emit(r.message)
            }
            busy.value = false
        }
    }

    fun silence() = coordinator.silence()
    fun deferHead() = coordinator.deferHead()
    fun minimize() { hidden.value = true }
    fun showAlert() { hidden.value = false }
    fun sync() { viewModelScope.launch { coordinator.syncPending(); menu.reconcile() } }
    /** Sliding session: a restaurant that keeps using the app is never asked for its password again. */
    fun refreshSession() { viewModelScope.launch { auth.refreshIfStale() } }
    fun logout() { viewModelScope.launch { coordinator.logout() } }

    /** Serverda versiya siyosati endpointi yo'q — majburiy yangilanish faqat Play "update priority" (UpdateManager). */
    suspend fun updatePolicy(): VersionDto.AndroidVersion? = null
}
