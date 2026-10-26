package uz.lokmago.restaurant.data.remote.dto

import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import uz.lokmago.restaurant.domain.*

/*
 * lakmago-server javoblari — admin panel (restoran roli) bilan AYNAN BIR XIL endpointlar va shakllar.
 * Server maydon nomlari (`_id`, `dailyNumber`, `unitPrice`, `customer.name` ...) shu yerda ilova
 * domeniga o'giriladi; UI va navbat mantiqi o'zgarmaydi. Noma'lum maydonlar e'tiborsiz (AppModule: ignoreUnknownKeys).
 */

// ---- Kirish: POST /api/auth/login (admin panel bilan bir xil) ----
@Serializable data class LoginRequest(val login: String, val password: String)
@Serializable data class UserDto(
    @SerialName("_id") val id: String = "",
    val login: String = "",
    val role: String? = null,
    val restaurantId: String? = null,
    val firstName: String? = null,
)
@Serializable data class LoginResponse(val token: String, val user: UserDto)
/** POST /api/panel/session/refresh */
@Serializable data class RefreshResponse(val token: String)
/** GET /api/panel/me — restoran profili (faqat kerakli maydonlar) */
@Serializable data class RestaurantDto(@SerialName("_id") val id: String = "", val name: String = "")

// ---- Buyurtma: /api/panel/orders... (services/panelOrderView.js → toPanelOrder) ----
@Serializable data class SelectedOptionDto(val name: String = "", val variant: Boolean? = null)
@Serializable data class OrderItemDto(
    val name: String = "",
    val quantity: Int = 1,
    val unitPrice: Double = 0.0,
    val selectedOptions: List<SelectedOptionDto> = emptyList(),
    val note: String? = null,
    /** Taom id — rasmni menyudan topish uchun (buyurtma elementida rasm yo'q). */
    val dishId: String? = null,
)
@Serializable data class CustomerDto(val name: String? = null, val phone: String? = null)

@Serializable data class OrderDto(
    @SerialName("_id") val id: String,
    val dailyNumber: Int? = null,
    val status: String = "unknown",
    val createdAt: String? = null,               // ISO-8601 (UTC)
    val fulfillment: String = "delivery",
    val paymentMethod: String = "cash",          // cash | click | payme | paynet | uzum
    val subtotal: Double = 0.0,
    val deliveryFee: Double = 0.0,
    val total: Double = 0.0,
    val customer: CustomerDto? = null,
    val phone: String? = null,
    val address: String? = null,
    val addressLat: Double? = null,
    val addressLng: Double? = null,
    val items: List<OrderItemDto> = emptyList(),
    /** PATCH status javobida: false = buyurtma allaqachon shu holatda edi (boshqa telefon ulgurgan). */
    val changed: Boolean? = null,
    /** Mijoz bahosi va izohi (buyurtma yakunlangach) — Order.rating / comment / ratedAt. */
    val rating: Int? = null,
    val comment: String? = null,
    val ratedAt: String? = null,
) {
    /** Soketdagi qisqa `{ _id, status }` (to'liq ko'rinish olinmaganda) — to'liq buyurtma emas. */
    val isPartial: Boolean get() = createdAt == null

    fun toDomain() = Order(
        id = id,
        // Admin panel va bot bilan bir xil raqam: kunlik raqam, bo'lmasa ID oxirgi 4 belgisi (services/orderNumber.js)
        number = dailyNumber?.toString() ?: id.takeLast(4).uppercase(),
        status = OrderStatus.from(status),
        createdAtMillis = createdAt?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: System.currentTimeMillis(),
        fulfillment = Fulfillment.from(fulfillment),
        // Karta orqali (Click/Payme/Paynet/Uzum) — "Karta". Faqat "cash" naqd.
        payment = if (paymentMethod.equals("cash", ignoreCase = true)) Payment.CASH else Payment.CARD,
        itemsCount = items.sumOf { it.quantity.coerceAtLeast(0) },
        subtotal = subtotal.roundToLong(),
        deliveryFee = deliveryFee.roundToLong(),
        total = total.roundToLong(),
        customerName = customer?.name?.takeIf { it.isNotBlank() },
        customerPhone = (phone?.takeIf { it.isNotBlank() } ?: customer?.phone)?.takeIf { it.isNotBlank() },
        address = address?.takeIf { it.isNotBlank() },
        lat = addressLat,
        lng = addressLng,
        items = items.map { it.toDomain() },
    )
}

/** Oshxona AYNAN qaysi hajmni tayyorlashini bilsin: "Lorus Set (6 Kishilik Set)". */
fun OrderItemDto.toDomain(): OrderItem {
    val opts = selectedOptions.map { it.name.trim() }.filter { it.isNotEmpty() }
    val title = if (opts.isEmpty()) name else "$name (${opts.joinToString(", ")})"
    return OrderItem(title, quantity, unitPrice.roundToLong(), null, dishId)
}

@Serializable data class StatusRequest(val status: String)
/** Soket `order:delivery-reminder` (services/restaurantReminders.js) — to'liq buyurtma emas. */
@Serializable data class ReminderEventDto(val orderId: String, val reminderCount: Int = 1)

// ---- Hisobot: GET /api/panel/stats?from=&to= (controllers/restaurantApp.js) ----
@Serializable data class MoneyCount(val count: Int = 0, val total: Double = 0.0)
@Serializable data class PaymentSplit(val cash: MoneyCount = MoneyCount(), val card: MoneyCount = MoneyCount())
@Serializable data class CreatedStats(val count: Int = 0, val byStatus: Map<String, Int> = emptyMap())
@Serializable data class DeliveredStats(val count: Int = 0, val total: Double = 0.0, val byPayment: PaymentSplit = PaymentSplit())
@Serializable data class PanelStatsDto(val created: CreatedStats = CreatedStats(), val delivered: DeliveredStats = DeliveredStats())

/** GET /api/panel/orders/history */
@Serializable data class HistoryPage(val items: List<OrderDto> = emptyList(), val nextCursor: String? = null, val hasMore: Boolean = false)

/**
 * Ilova versiyasi siyosati. Serverda alohida endpoint yo'q — majburiy yangilanish faqat Google Play
 * "update priority" orqali (UpdateManager). Tur UpdateManager imzosi uchun saqlangan.
 */
@Serializable data class VersionDto(val android: AndroidVersion = AndroidVersion()) {
    @Serializable data class AndroidVersion(
        val minimumVersion: String = "0.0.0",
        val latestVersion: String = "0.0.0",
        val forceUpdate: Boolean = false,
    )
}

/** Mijoz izohi bo'lgan buyurtma → Review. Baho ham, matn ham yo'q bo'lsa null. */
fun OrderDto.toReview(): Review? {
    val text = comment?.trim().orEmpty()
    val stars = rating?.coerceIn(1, 5)
    if (stars == null && text.isEmpty()) return null
    val at = (ratedAt ?: createdAt)?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L
    return Review(id, dailyNumber?.toString() ?: id.takeLast(4).uppercase(), stars ?: 0, text, customer?.name?.takeIf { it.isNotBlank() } ?: "Mijoz", at, total.roundToLong())
}

// ---- Menyu: /api/panel/dishes (controllers/restaurantPanel.js, models/Dish.js) ----
@Serializable data class OptionDto(val name: String = "", val price: Double? = null, val mandatory: Boolean? = null)
@Serializable data class OptionGroupDto(val title: String = "", val kind: String? = null, val options: List<OptionDto> = emptyList())
@Serializable data class DishDto(
    @SerialName("_id") val id: String,
    val name: String = "",
    val description: String? = null,
    val category: String? = null,
    val section: String? = null,
    val price: Double = 0.0,
    val oldPrice: Double? = null,
    val prepMinutes: Int? = null,
    val weight: String? = null,
    val volume: String? = null,
    val drinkType: String? = null,
    val calories: Double? = null,
    val protein: Double? = null,
    val fat: Double? = null,
    val carbs: Double? = null,
    val imageUrl: String? = null,
    val images: List<String> = emptyList(),
    val isAvailable: Boolean = true,
    val priceMode: String? = null,
    val dineInPrice: Double? = null,
    val optionGroups: List<OptionGroupDto> = emptyList(),
) {
    fun toDomain() = Dish(
        id = id, name = name, description = description.orEmpty(), category = category ?: "milliy", section = section.orEmpty(),
        price = price.roundToLong(), oldPrice = oldPrice?.roundToLong()?.takeIf { it > 0 }, prepMinutes = prepMinutes,
        weight = weight.orEmpty(), volume = volume.orEmpty(), drinkType = drinkType.orEmpty(),
        calories = calories?.roundToInt(), protein = protein?.roundToInt(), fat = fat?.roundToInt(), carbs = carbs?.roundToInt(),
        imageUrl = imageUrl?.takeIf { it.isNotBlank() } ?: images.firstOrNull { it.isNotBlank() },
        available = isAvailable, priceMode = priceMode ?: "sync", dineInPrice = dineInPrice?.roundToLong(),
        groups = optionGroups.map { g -> DishGroup(g.title, g.kind, g.options.map { DishOption(it.name, (it.price ?: 0.0).roundToLong(), it.mandatory == true) }) },
    )
}
@Serializable data class StopRequest(val stop: Boolean)
@Serializable data class OkDto(val ok: Boolean = true)
@Serializable data class CountDto(val count: Int = 0)
@Serializable data class DeviceRegisterRequest(val token: String, val platform: String = "android")
@Serializable data class DeviceUnregisterRequest(val token: String)

/** GET /api/upload/signature — Cloudinary'ga TO'G'RIDAN yuklash uchun imzo (secret telefonga tushmaydi). */
@Serializable data class UploadSignatureDto(
    val cloudName: String, val apiKey: String, val timestamp: Long, val folder: String,
    val allowedFormats: String, val signature: String,
)
