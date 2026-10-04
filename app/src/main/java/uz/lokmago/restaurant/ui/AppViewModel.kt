package uz.lokmago.restaurant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import uz.lokmago.restaurant.alert.AlertController
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.repo.AuthRepository
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.VersionDto
import uz.lokmago.restaurant.realtime.*

/** App-wide state: session, connectivity, the order queue and the alert overlay. */
@HiltViewModel
class AppViewModel @Inject constructor(
    private val coordinator: OrderCoordinator,
    sessionStore: SessionStore,
    net: NetworkMonitor,
    socket: SocketManager,
    private val alert: AlertController,
    private val api: LokmaApi,
    private val auth: AuthRepository,
) : ViewModel() {

    val session = sessionStore.session
    val queue = coordinator.queue.state
    val silenced = alert.silenced
    val online: StateFlow<Boolean> = combine(net.online, socket.connected) { n, s -> n && s }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val hidden = MutableStateFlow(false)
    val alertVisible: StateFlow<Boolean> = combine(session, queue, hidden) { s, q, h -> s != null && !q.isEmpty && !h }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val busy = MutableStateFlow(false)
    val messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    init {
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
    fun sync() { viewModelScope.launch { coordinator.syncPending() } }
    /** Sliding session: a restaurant that keeps using the app is never asked for its password again. */
    fun refreshSession() { viewModelScope.launch { auth.refreshIfStale() } }
    fun logout() { viewModelScope.launch { coordinator.logout() } }

    suspend fun updatePolicy(): VersionDto.AndroidVersion? = runCatching { api.version().android }.getOrNull()
}
