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
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.remote.Endpoints
import uz.lokmago.restaurant.data.remote.dto.OrderDto
import uz.lokmago.restaurant.data.remote.dto.ReminderEventDto
import uz.lokmago.restaurant.domain.Order

sealed interface SocketEvent {
    data object Connected : SocketEvent
    data class OrderNew(val order: Order) : SocketEvent
    data class OrderUpdated(val order: Order) : SocketEvent
    /** Delivery-completion reminder — payload is minimal (orderId + count), not a full order. */
    data class DeliveryReminder(val orderId: String, val reminderCount: Int) : SocketEvent
}

/**
 * Socket.IO client. Authenticates with the JWT in the handshake; the server derives the restaurant room
 * from the token — the client never sends a restaurantId. Reconnects automatically (backoff 1s → 10s).
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
            if (BuildConfig.API_GATEWAY_PASSWORD.isNotEmpty())
                extraHeaders = mapOf(BuildConfig.API_GATEWAY_HEADER to listOf(BuildConfig.API_GATEWAY_PASSWORD))
        }
        tokenInUse = token
        socket = IO.socket(URI.create(Endpoints.socketUrl), opts).apply {
            on(Socket.EVENT_CONNECT) { _connected.value = true; _events.tryEmit(SocketEvent.Connected) }
            on(Socket.EVENT_DISCONNECT) { _connected.value = false }
            on(Socket.EVENT_CONNECT_ERROR) { _connected.value = false }
            on("order:new") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.OrderNew(it)) } }
            on("order:updated") { a -> parse(a)?.let { _events.tryEmit(SocketEvent.OrderUpdated(it)) } }
            on("order:delivery-reminder") { a -> parseReminder(a)?.let { _events.tryEmit(SocketEvent.DeliveryReminder(it.orderId, it.reminderCount)) } }
            connect()
        }
    }

    @Synchronized
    fun disconnect() {
        socket?.let { it.off(); it.disconnect(); it.close() }
        socket = null; tokenInUse = null; _connected.value = false
    }

    private fun parse(args: Array<Any>): Order? = runCatching {
        json.decodeFromString<OrderDto>(args[0].toString()).toDomain()
    }.getOrNull()

    private fun parseReminder(args: Array<Any>): ReminderEventDto? = runCatching {
        json.decodeFromString<ReminderEventDto>(args[0].toString())
    }.getOrNull()
}
