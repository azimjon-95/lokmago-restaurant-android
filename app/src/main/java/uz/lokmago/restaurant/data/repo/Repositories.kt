package uz.lokmago.restaurant.data.repo

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import uz.lokmago.restaurant.data.local.Session
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.*
import uz.lokmago.restaurant.domain.DayStats
import uz.lokmago.restaurant.domain.Fulfillment
import uz.lokmago.restaurant.domain.Order
import uz.lokmago.restaurant.domain.OrderStatus
import uz.lokmago.restaurant.domain.Review

/** Restoran vaqti (server hisobotlari ham shu zonada). */
private val TASHKENT: ZoneId = ZoneId.of("Asia/Tashkent")

@Singleton
class OrderRepository @Inject constructor(private val api: LokmaApi) {
    suspend fun pending(): List<Order> = apiCall { api.orders("pending") }.map { it.toDomain() }
    suspend fun order(id: String): Order = apiCall { api.order(id) }.toDomain()
    suspend fun list(status: OrderStatus?): List<Order> =
        apiCall { api.orders(status?.api) }.map { it.toDomain() }.sortedByDescending { it.createdAtMillis }

    /**
     * Qabul qilish — admin panel bilan bir xil yo'l. `changed = false`: buyurtma allaqachon qabul qilingan
     * (boshqa telefon/panel ulgurgan) — foydalanuvchiga "allaqachon qabul qilingan" deyiladi.
     */
    suspend fun accept(id: String): Order {
        val r = apiCall { api.setStatus(id, StatusRequest(OrderStatus.ACCEPTED.api)) }
        if (r.changed == false) throw AppError.AlreadyHandled()
        return r.toDomain()
    }

    /**
     * Keyingi holat. YETKAZIB BERISH buyurtmasini restoran oddiy holat bilan "Yetkazildi" qila olmaydi
     * (serverda faqat olib ketish uchun) — u `confirm-delivered` orqali yakunlanadi (Telegram botdagi
     * "Yakunlandi" bilan bir xil yo'l, server vaqt shartini tekshiradi).
     */
    suspend fun advance(order: Order, target: OrderStatus): Order =
        if (target == OrderStatus.DELIVERED && order.fulfillment == Fulfillment.DELIVERY) confirmDelivered(order.id)
        else apiCall { api.setStatus(order.id, StatusRequest(target.api)) }.toDomain()

    suspend fun confirmDelivered(id: String): Order = apiCall { api.confirmDelivered(id) }.toDomain()

    /**
     * Eslatmaga javob. [delivered]=true — yakunlash (confirm-delivered). false = "Jarayonda": serverda hech
     * narsa o'zgarmaydi, keyingi eslatma jadval bo'yicha keladi (Telegram'dagi "⏳ Jarayonda" bilan bir xil).
     */
    suspend fun reminderAck(id: String, delivered: Boolean): Order =
        if (delivered) confirmDelivered(id) else order(id)

    /**
     * Bugungi hisobot: server kunlik statistikasi (bugun va kecha — farq uchun) + soatlik tushum grafigi
     * (bugungi buyurtmalar tarixidan, yetkazilganlar bo'yicha).
     */
    /**
     * Mijoz izohlari: server alohida endpoint bermaydi, baho/izoh buyurtmaning o'zida (Order.rating/comment).
     * Oxirgi [days] kun tarixi sahifalab o'qiladi va faqat bahosi yoki izohi borlari qoldiriladi.
     */
    suspend fun reviews(days: Long = 120, maxPages: Int = 12): List<Review> {
        val to = LocalDate.now(TASHKENT)
        val from = to.minusDays(days)
        val out = mutableListOf<Review>()
        var cursor: String? = null
        var pages = 0
        do {
            val page = apiCall { api.history(from.toString(), to.toString(), 200, cursor) }
            page.items.mapNotNullTo(out) { it.toReview() }
            cursor = page.nextCursor
            pages++
        } while (page.hasMore && cursor != null && pages < maxPages)
        return out.sortedByDescending { it.atMillis }
    }

    suspend fun today(): DayStats = coroutineScope {
        val today = LocalDate.now(TASHKENT)
        val yesterday = today.minusDays(1)
        val t = async { apiCall { api.stats(today.toString(), today.toString()) } }
        val y = async { runCatching { apiCall { api.stats(yesterday.toString(), yesterday.toString()) } }.getOrNull() }
        val h = async { runCatching { apiCall { api.history(today.toString(), today.toString()) } }.getOrNull() }
        val s = t.await()
        val prev = y.await()
        val hourly = MutableList(24) { 0L }
        h.await()?.items.orEmpty().filter { it.status == "delivered" }.forEach { o ->
            val at = o.createdAt?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return@forEach
            hourly[at.atZone(TASHKENT).hour] += o.total.roundToLong()
        }
        val revenue = s.delivered.total.roundToLong()
        val prevRevenue = prev?.delivered?.total?.roundToLong() ?: 0L
        DayStats(
            ordersCount = s.created.count,
            ordersDelta = if (prev != null) s.created.count - prev.created.count else 0,
            revenue = revenue,
            revenueDeltaPct = if (prevRevenue > 0) ((revenue - prevRevenue) * 100.0 / prevRevenue).roundToInt() else 0,
            waiting = s.created.byStatus["pending"] ?: 0,
            cashCount = s.delivered.byPayment.cash.count,
            cardCount = s.delivered.byPayment.card.count,
            cashRevenue = s.delivered.byPayment.cash.total.roundToLong(),
            cardRevenue = s.delivered.byPayment.card.total.roundToLong(),
            hourly = hourly,
        )
    }
}

@Singleton
class AuthRepository @Inject constructor(private val api: LokmaApi, private val session: SessionStore) {
    /**
     * Admin panelning restoran akkaunti bilan kirish. Admin/xodim akkaunti bu ilovaga kira olmaydi
     * (u restoranga bog'lanmagan). Restoran nomi /panel/me dan olinadi.
     */
    suspend fun login(login: String, password: String) {
        val r = apiCall { api.login(LoginRequest(login.trim(), password)) }
        if (r.user.role != "restaurant" || r.user.restaurantId.isNullOrBlank()) throw AppError.NotRestaurant()
        val name = runCatching { apiCall { api.me("Bearer ${r.token}") }.name }.getOrNull().orEmpty()
        // restaurantId saqlanmaydi: server uni har so'rovda tokendan oladi.
        session.save(Session(r.token, name.ifBlank { "Restoran" }, r.user.login.ifBlank { login.trim() }))
    }

    val lastLogin: String get() = session.lastLogin

    /**
     * Ishlatilayotgan ilova chiqib ketmasin: token bir kundan eski bo'lsa, yangisiga almashtiriladi.
     * Internet yo'q yoki server band — e'tiborsiz (eski token hali yaroqli); 401 sessiyani AuthInterceptor tugatadi.
     * Parol telefonda hech qachon saqlanmaydi.
     */
    suspend fun refreshIfStale() {
        if (session.token == null || session.tokenAgeMs < 24L * 60 * 60 * 1000) return
        try { session.updateToken(apiCall { api.refresh() }.token) }
        catch (e: kotlin.coroutines.cancellation.CancellationException) { throw e }
        catch (_: Exception) { /* keyingi safar */ }
    }
}
