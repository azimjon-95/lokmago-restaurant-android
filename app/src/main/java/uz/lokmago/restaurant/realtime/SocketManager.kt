package uz.lokmago.restaurant.realtime

import io.socket.client.IO
import io.socket.client.Socket
import java.net.URI
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import uz.lokmago.restaurant.data.remote.Endpoints
import uz.lokmago.restaurant.data.remote.dto.OrderDto
import uz.lokmago.restaurant.data.remote.dto.ReminderEventDto
import uz.lokmago.restaurant.domain.Order

sealed interface SocketEvent {
    data object Connected : SocketEvent
    /** Menyu o'zgardi (taom qo'shildi/tahrirlandi/STOP/o'chirildi) — admin panel bilan bir xil `dish:stop` / `dish:update`. */
    data class MenuChanged(val stoppedCount: Int? = null) : SocketEvent
    data class OrderNew(val order: Order) : SocketEvent
    data class OrderUpdated(val order: Order) : SocketEvent
    /** Server to'liq ko'rinishni bera olmagan (`{ _id, status }`) — buyurtma serverdan qayta o'qiladi. */
    data class OrderTouched(val orderId: String) : SocketEvent
    /** Delivery-completion reminder — payload is minimal (orderId + count), not a full order. */
    data class DeliveryReminder(val orderId: String, val reminderCount: Int) : SocketEvent
}

/**
 * Socket.IO — lakmago-server bilan TO'G'RIDAN-TO'G'RI (admin panel ishlatadigan o'sha soket).
 * Handshake'da JWT yuboriladi; server tokenni tekshirib, restoranni O'ZINING `restaurant:<id>` xonasiga
 * qo'shadi (sockets/restaurantSocketAuth.js) — client restaurantId yubormaydi. Avtomatik qayta ulanadi (1s → 10s).
 * Hodisalar (admin panel bilan bir xil): `order:new`, `order:update`, `order:delivery-reminder`.
 */
@Singleton
class SocketManager @Inject constructor(private val json: Json) {
    private var socket: Socket? = null
    private var tokenInUse: String? = null

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SocketEvent> = _events.asSharedFlow()
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    @Synchronized
    fun connect(token: String) {
        if (socket != null && tokenInUse == token) { if (socket?.connected() != true) socket?.connect(); return }
        disconnect()
        val opts = IO.Options().apply {
            path = Endpoints.socketPath
            transports = arrayOf("websocket")
            reconnection = true
            reconnectionDelay = 1_000
            reconnectionDelayMax = 10_000
            auth = mapOf("token" to token)
        }
        tokenInUse = token
        socket = IO.socket(URI.create(Endpoints.socketUrl), opts).apply {
            on(Socket.EVENT_CONNECT) { _connected.value = true; _events.tryEmit(SocketEvent.Connected) }
            on(Socket.EVENT_DISCONNECT) { _connected.value = false }
            on(Socket.EVENT_CONNECT_ERROR) { _connected.value = false }
            on("order:new") { a -> parse(a)?.let { _events.tryEmit(if (it.isPartial) SocketEvent.OrderTouched(it.id) else SocketEvent.OrderNew(it.toDomain())) } }
            on("order:update") { a -> parse(a)?.let { _events.tryEmit(if (it.isPartial) SocketEvent.OrderTouched(it.id) else SocketEvent.OrderUpdated(it.toDomain())) } }
            // dish:stop = { dishId, isAvailable, stoppedCount }: the server's own number, used for the badge at once
            on("dish:stop") { a -> _events.tryEmit(SocketEvent.MenuChanged((a.getOrNull(0) as? org.json.JSONObject)?.optInt("stoppedCount", -1)?.takeIf { it >= 0 })) }
            on("dish:update") { _events.tryEmit(SocketEvent.MenuChanged()) }
            on("order:delivery-reminder") { a -> parseReminder(a)?.let { _events.tryEmit(SocketEvent.DeliveryReminder(it.orderId, it.reminderCount)) } }
            connect()
        }
    }

    @Synchronized
    fun disconnect() {
        socket?.let { it.off(); it.disconnect(); it.close() }
        socket = null; tokenInUse = null; _connected.value = false
    }

    private fun parse(args: Array<Any>): OrderDto? = runCatching {
        json.decodeFromString<OrderDto>(args[0].toString())
    }.getOrNull()

    private fun parseReminder(args: Array<Any>): ReminderEventDto? = runCatching {
        json.decodeFromString<ReminderEventDto>(args[0].toString())
    }.getOrNull()
}
