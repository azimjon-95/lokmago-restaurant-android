package uz.lokmago.restaurant.data.repo

import javax.inject.Inject
import javax.inject.Singleton
import uz.lokmago.restaurant.data.local.Session
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.*
import uz.lokmago.restaurant.domain.DayStats
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderStatus

@Singleton
class OrderRepository @Inject constructor(private val api: LokmaApi) {
    suspend fun pending(): List<Order> = apiCall { api.pending() }.map { it.toDomain() }
    suspend fun order(id: String): Order = apiCall { api.order(id) }.toDomain()
    suspend fun list(status: OrderStatus?): List<Order> =
        apiCall { api.orders(status?.api) }.map { it.toDomain() }.sortedByDescending { it.createdAtMillis }
    suspend fun accept(id: String): Order = apiCall { api.accept(id) }.toDomain()
    suspend fun setStatus(id: String, status: OrderStatus): Order =
        apiCall { api.setStatus(id, StatusRequest(status.api)) }.toDomain()
    /** [delivered]=true completes the order via the existing backend completion path;
     *  false = "Jarayonda" (in_progress) — just postpones the next reminder, no status change. */
    suspend fun reminderAck(id: String, delivered: Boolean): Order =
        apiCall { api.reminderAck(id, ReminderAckRequest(if (delivered) "delivered" else "in_progress")) }.toDomain()
    suspend fun today(): DayStats = apiCall { api.today() }.toDomain()
}

@Singleton
class AuthRepository @Inject constructor(private val api: LokmaApi, private val session: SessionStore) {
    suspend fun login(login: String, password: String) {
        val r = apiCall { api.login(LoginRequest(login.trim(), password)) }
        // restaurantId is intentionally NOT stored: the backend derives it from the JWT on every call.
        session.save(Session(r.token, r.restaurant.name, r.user.login.ifBlank { login.trim() }))
    }

    val lastLogin: String get() = session.lastLogin

    /**
     * Keeps a used app logged in: once the token is older than a day, trade it for a fresh one. Offline or a
     * server hiccup is ignored (the old token is still valid); a 401 expires the session via AuthInterceptor.
     * The password is never stored on the phone.
     */
    suspend fun refreshIfStale() {
        if (session.token == null || session.tokenAgeMs < 24L * 60 * 60 * 1000) return
        try { session.updateToken(apiCall { api.refresh() }.token) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { /* try again on the next foreground */ }
    }
}
