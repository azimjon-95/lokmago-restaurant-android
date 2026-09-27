package uz.lokmago.restaurant.realtime

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import uz.lokmago.restaurant.alert.AlertController
import uz.lokmago.restaurant.alert.Notifier
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.repo.AppError
import uz.lokmago.restaurant.data.repo.OrderRepository
import uz.lokmago.restaurant.di.AppScope
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderQueue
import uz.lokmago.restaurant.domain.OrderStatus
import uz.lokmago.restaurant.push.DeviceRegistrar

sealed interface ActionResult {
    data class Done(val order: Order) : ActionResult
    /** Another phone already handled it — not an error for the user, just "move on". */
    data object AlreadyHandled : ActionResult
    data class Failed(val message: String) : ActionResult
}

/**
 * The one place where the three order sources meet:
 *   Socket.IO (app open)  +  FCM (background/closed)  +  pending-orders sync (app start / reconnect)
 * All of them feed the same [OrderQueue]; de-duplication happens by order id, so the same order arriving
 * twice never rings twice.
 */
@Singleton
class OrderCoordinator @Inject constructor(
    private val repo: OrderRepository,
    val queue: OrderQueue,
    private val socket: SocketManager,
    private val alert: AlertController,
    private val notifier: Notifier,
    private val session: SessionStore,
    private val devices: DeviceRegistrar,
    @AppScope private val scope: CoroutineScope,
) {
    private val _arrivals = MutableSharedFlow<Order>(extraBufferCapacity = 16)
    /** Emits once per genuinely new order (UI un-minimizes the alert screen on this). */
    val arrivals: SharedFlow<Order> = _arrivals.asSharedFlow()

    private val _changes = MutableSharedFlow<Order>(extraBufferCapacity = 32)
    /** Any order changed (status etc.) — list/detail screens refresh on this. */
    val changes: SharedFlow<Order> = _changes.asSharedFlow()

    private val _reminders = MutableSharedFlow<Pair<String, Int>>(extraBufferCapacity = 16)
    /** orderId to reminderCount — a "buyurtma yetkazildimi?" nudge arrived (socket or push). */
    val reminders: SharedFlow<Pair<String, Int>> = _reminders.asSharedFlow()

    private val acceptLock = Mutex()
    private val inflight = ConcurrentHashMap.newKeySet<String>()
    private var started = false

    init {
        // When an order leaves the queue for ANY reason (accepted here/elsewhere, cancelled, sync) drop its notification.
        scope.launch {
            var prev = emptySet<String>()
            queue.state.map { s -> s.orders.map { it.id }.toSet() }.collect { now ->
                (prev - now).forEach(notifier::cancel)
                prev = now
            }
        }
    }

    @Synchronized
    fun start() {
        val token = session.token ?: return
        socket.connect(token)
        if (started) return
        started = true
        scope.launch {
            socket.events.collect { e ->
                when (e) {
                    SocketEvent.Connected -> { syncPending(); devices.register() }   // reconnect ⇒ re-sync, nothing is lost
                    is SocketEvent.OrderNew -> ingest(e.order)
                    is SocketEvent.OrderUpdated -> { queue.offer(e.order).also { if (it) arrived(e.order) }; _changes.emit(e.order) }
                    is SocketEvent.DeliveryReminder -> onDeliveryReminder(e.orderId, e.reminderCount)
                }
            }
        }
        scope.launch { session.session.collect { if (it == null) stop() } }
        scope.launch { syncPending() }
    }

    @Synchronized
    fun stop() {
        started = false
        socket.disconnect(); queue.clear(); alert.reset(); notifier.cancelAllOrders()
    }

    suspend fun logout() {
        devices.unregister()          // needs the JWT, so before expire()
        session.expire()              // collector above calls stop()
    }

    // ---- sources -------------------------------------------------------------------------------------

    private fun ingest(order: Order) {
        if (queue.offer(order)) arrived(order)
    }

    private fun arrived(o: Order) {
        alert.rearm()
        val ringing = alert.start()
        notifier.notifyNew(o.id, "🔔 Yangi buyurtma", notifier.newOrderText(o), silent = ringing)
        _arrivals.tryEmit(o)
    }

    /** FCM path. Payload text is shown immediately; the real order is reloaded from the backend. */
    fun onPush(orderId: String, title: String, body: String) {
        if (queue.contains(orderId) || !inflight.add(orderId)) return          // duplicate of socket/FCM
        alert.markUnresolved(orderId)
        alert.rearm()
        val ringing = alert.start()
        notifier.notifyNew(orderId, title, body, silent = ringing)
        scope.launch {
            try {
                var order: Order? = null
                repeat(4) { attempt ->
                    if (order == null) {
                        order = runCatching { repo.order(orderId) }.getOrNull()
                        if (order == null) delay(1_500L * (attempt + 1))
                    }
                }
                order?.let {
                    if (it.status == OrderStatus.NEW) { queue.offer(it); _arrivals.tryEmit(it) }
                    else { notifier.cancel(orderId); _changes.emit(it) }     // e.g. already accepted on another phone
                }
            } finally {
                alert.markResolved(orderId); inflight.remove(orderId)
            }
        }
    }

    /**
     * A delivery order hasn't been marked "Yetkazildi" in time. This is a NUDGE, not a new order —
     * it never touches [queue] or the loud alarm service; it's a plain notification with two
     * direct actions, same idea as the existing Telegram reminder (TZ §19).
     */
    fun onDeliveryReminder(orderId: String, reminderCount: Int, title: String? = null, body: String? = null) {
        notifier.notifyReminder(orderId, title ?: "🚴 Buyurtma yetkazildimi?", body ?: "Buyurtma hali yakunlanmagan.")
        _reminders.tryEmit(orderId to reminderCount)
    }

    /**
     * "Yetkazildi" (delivered = true) / "Jarayonda" (false) from a reminder — notification action,
     * Telegram-style in-app button, or the detail screen's secondary button all call this. Backend
     * is the source of truth: local state never marks the order complete on its own (TZ §18/§24).
     */
    suspend fun reminderAck(orderId: String, delivered: Boolean): ActionResult = try {
        val o = repo.reminderAck(orderId, delivered)
        notifier.cancel(orderId)
        _changes.emit(o)
        ActionResult.Done(o)
    } catch (e: AppError.AlreadyHandled) {
        notifier.cancel(orderId)
        runCatching { repo.order(orderId) }.getOrNull()?.let { _changes.emit(it) }
        ActionResult.AlreadyHandled
    } catch (e: AppError) {
        ActionResult.Failed(e.message ?: "Xatolik")
    }

    fun onRemoteChange(orderId: String) {
        scope.launch { runCatching { repo.order(orderId) }.getOrNull()?.let { queue.offer(it); _changes.emit(it) } }
    }

    /** App start / reconnect / pull-to-refresh: the backend is the source of truth. */
    suspend fun syncPending() {
        val requestedAt = System.currentTimeMillis()
        val list = runCatching { repo.pending() }.getOrNull() ?: return
        val added = queue.replaceAll(list, requestedAt)
        if (added.isNotEmpty()) {
            alert.rearm()
            val ringing = alert.start()
            added.forEach { notifier.notifyNew(it.id, "🔔 Yangi buyurtma", notifier.newOrderText(it), silent = ringing) }
            added.forEach { _arrivals.tryEmit(it) }
        }
        if (queue.state.value.isEmpty) notifier.cancelAllOrders()
    }

    // ---- actions -------------------------------------------------------------------------------------

    /**
     * Accepts one order. Serialized by a mutex, so rapid taps / a long queue are processed strictly
     * one at a time, in the order the staff acts on them. The queue is only touched AFTER the backend
     * confirms — offline never shows a false "accepted".
     */
    suspend fun accept(orderId: String): ActionResult = acceptLock.withLock {
        try {
            val o = repo.accept(orderId)
            queue.remove(orderId); _changes.emit(o)
            ActionResult.Done(o)
        } catch (e: AppError.AlreadyHandled) {
            queue.remove(orderId)
            runCatching { repo.order(orderId) }.getOrNull()?.let { _changes.emit(it) }
            ActionResult.AlreadyHandled
        } catch (e: AppError.NotFound) {
            queue.remove(orderId); ActionResult.Failed(e.message!!)
        } catch (e: AppError) {
            ActionResult.Failed(e.message ?: "Xatolik")
        }
    }

    suspend fun advance(order: Order): ActionResult {
        val next = order.nextAction() ?: return ActionResult.Failed("Keyingi amal yo'q")
        if (next.target == OrderStatus.ACCEPTED) return accept(order.id)
        return try { repo.setStatus(order.id, next.target).also { _changes.emit(it) }.let { ActionResult.Done(it) } }
        catch (e: AppError) { ActionResult.Failed(e.message ?: "Xatolik") }
    }

    fun silence() = alert.silence()
    fun deferHead() = queue.deferHead()
}
