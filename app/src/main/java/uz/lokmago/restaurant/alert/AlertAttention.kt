package uz.lokmago.restaurant.alert

import kotlin.math.exp

/**
 * ALARM OVOZINI FOYDALANUVCHI HARAKATIGA QARAB BOSHQARISH (toza mantiq, Android'ga bog'liq emas — testlangan).
 *
 *  Telefon bloklangan / boshqa ilovada  → TO'LIQ ovoz (xodim eshitishi shart).
 *  Ilovaga kirdi (ko'rib turibdi)        → JUDA PAST ovoz (bezovta qilmaydi, lekin buyurtma hali kutayotgani bilinadi).
 *  Buyurtma tafsilotini ochdi            → yanada past (o'qiyapti).
 *  Tafsilotdan hech narsa qilmay chiqdi  → ovoz yana ko'tariladi (buyurtma hali qabul qilinmagan).
 *  Ilovada 20 soniya tegmay turdi        → ovoz o'rtacha ko'tariladi (unutib qo'ygan bo'lishi mumkin).
 *  Ilovadan hech narsa qilmay chiqdi     → 3 soniyadan keyin (tez almashtirish bo'lishi mumkin) yana TO'LIQ.
 * Hamma o'tish YUMSHOQ (ramp), keskin sakrash yo'q. Qabul / rad etish — xizmat to'xtaydi, bu yerda hech narsa kerak emas.
 */
class AlertAttention(private val now: () -> Long) {
    data class Target(val level: Float, val rampMs: Long)

    @Volatile private var visible = false
    @Volatile private var details = false
    @Volatile private var leftAt = 0L              // ilovadan chiqqan payt
    @Volatile private var detailsLeftAt = 0L       // tafsilotdan chiqqan payt
    @Volatile private var lastTouch = 0L

    fun appVisible(v: Boolean) { if (v == visible) return; visible = v; if (!v) { leftAt = now(); details = false } else lastTouch = now() }
    fun detailsOpen(v: Boolean) { if (v == details) return; details = v; if (!v) detailsLeftAt = now() else lastTouch = now() }
    /** Ekranga har bir tegish: «foydalanuvchi shu yerda» (bo'sh turib qolish hisobi qayta boshlanadi). */
    fun touched() { lastTouch = now() }

    /** Hozirgi vaqtda ovoz nima bo'lishi kerak. Xizmat har ~0.4 s da so'raydi. */
    fun target(): Target {
        val t = now()
        if (!visible) {
            // chiqqandan keyin qisqa kutish: foydalanuvchi shunchaki boshqa ilovaga o'tib qaytishi mumkin
            return if (leftAt == 0L || t - leftAt >= AWAY_GRACE_MS) Target(FULL, UP_RAMP_MS) else Target(IN_APP, DOWN_RAMP_MS)
        }
        if (details) return Target(ON_DETAILS, DOWN_RAMP_MS)
        // tafsilotdan chiqdi va shundan beri ekranga tegmadi (hech narsa qilmadi) → bir necha soniyadan keyin ovoz yana ko'tariladi
        if (detailsLeftAt != 0L && lastTouch <= detailsLeftAt && t - detailsLeftAt >= LEFT_DETAILS_RISE_MS) return Target(RECOVER, UP_RAMP_MS)
        if (t - lastTouch >= IDLE_MS) return Target(RECOVER, UP_RAMP_MS)
        return Target(IN_APP, DOWN_RAMP_MS)
    }

    companion object {
        const val FULL = 1.0f
        const val RECOVER = 0.45f
        const val IN_APP = 0.18f
        const val ON_DETAILS = 0.08f
        const val AWAY_GRACE_MS = 3_000L
        const val IDLE_MS = 20_000L
        const val LEFT_DETAILS_RISE_MS = 6_000L
        const val DOWN_RAMP_MS = 600L
        const val UP_RAMP_MS = 1_800L

        /** Yumshoq o'tish: joriy qiymatni nishonga eksponensial yaqinlashtiradi (rampMs ≈ 95% ga yetish vaqti). */
        fun step(current: Float, target: Float, dtMs: Long, rampMs: Long): Float {
            if (current == target) return target
            val k = 1f - exp(-3f * dtMs / rampMs.coerceAtLeast(1))
            val next = current + (target - current) * k
            return if (kotlin.math.abs(next - target) < 0.004f) target else next
        }
    }
}
