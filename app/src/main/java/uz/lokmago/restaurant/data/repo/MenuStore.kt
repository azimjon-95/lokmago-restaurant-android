package uz.lokmago.restaurant.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.di.AppScope
import uz.lokmago.restaurant.domain.Dish
import uz.lokmago.restaurant.realtime.SocketEvent
import uz.lokmago.restaurant.realtime.SocketManager

/**
 * The menu, live. ONE list shared by the Menyu tab, the Stop List tab, the tab badge and the order screens (dish photos).
 * It reloads by itself when the server says the menu changed (`dish:stop` / `dish:update`, same events the admin panel uses),
 * after a socket reconnect, and after login. Own edits update the list immediately (optimistic) and then re-sync.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class MenuStore @Inject constructor(
    private val repo: MenuRepository,
    socket: SocketManager,
    session: SessionStore,
    @AppScope private val scope: CoroutineScope,
) {
    private val _dishes = MutableStateFlow<List<Dish>>(emptyList())
    val dishes: StateFlow<List<Dish>> = _dishes.asStateFlow()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    val stoppedCount: StateFlow<Int> = dishes.map { list -> list.count { !it.available } }.stateIn(scope, SharingStarted.Eagerly, 0)
    private val lock = Mutex()

    init {
        scope.launch {
            socket.events.filter { it is SocketEvent.MenuChanged || it is SocketEvent.Connected }.collectLatest {
                delay(800)                              // several changes in a row → one reload
                if (session.token != null) refresh()
            }
        }
        scope.launch {
            session.session.collect { s ->
                if (s == null) { _dishes.value = emptyList(); _loaded.value = false } else refresh()
            }
        }
    }

    suspend fun refresh() {
        lock.withLock {
            try { _dishes.value = repo.dishes(); _loaded.value = true; _error.value = null }
            catch (e: AppError) { _error.value = e.message }
        }
    }

    /** Instant UI update for an action we just did; the server reply (or the next refresh) is the final word. */
    fun put(dish: Dish) {
        val cur = _dishes.value
        _dishes.value = if (cur.any { it.id == dish.id }) cur.map { if (it.id == dish.id) dish else it } else cur + dish
    }
    fun drop(id: String) { _dishes.value = _dishes.value.filterNot { it.id == id } }

    /** dishId → photo, for the small pictures inside an order. */
    fun imageOf(dishId: String?): String? = dishId?.let { id -> _dishes.value.firstOrNull { it.id == id }?.imageUrl }
}
