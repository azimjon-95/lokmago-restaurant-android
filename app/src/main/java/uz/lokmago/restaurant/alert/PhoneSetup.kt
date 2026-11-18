package uz.lokmago.restaurant.alert

/**
 * "TELEFONNI SOZLASH" — qaysi qadamlar kerak va foydalanuvchiga nima deyiladi (toza mantiq, Android'ga bog'liq emas, testlangan).
 *
 * Qoida: ekranda dasturchi tili YO'Q (Firebase, FCM, token, server...). Faqat foydalanuvchi qila oladigan ish va bitta tugma.
 * Telefonda mavjud bo'lmagan qadam ko'rsatilmaydi (masalan Android 13 da "qulflangan ekran" ruxsati yo'q, Samsung'da "avto-ishga tushirish" sahifasi yo'q).
 * Ulanish (push) qadamini foydalanuvchi bajarmaydi — ilova o'zi uriniadi; faqat qo'lidan keladigan narsa bo'lsa (internet, yangilash) tugma chiqadi.
 */
object PhoneSetup {
    enum class Id { NOTIFICATIONS, SOUND, BACKGROUND, LOCK_SCREEN, AUTOSTART, CONNECTION }

    /** Tugma nima qiladi. FIX — tizim oynasini ochadi (ketma-ket sozlashga kiradi). */
    enum class Action { FIX, WAIT, RETRY, OPEN_INTERNET, UPDATE_APP, NONE }

    data class State(
        val notifications: Boolean,
        val sound: Boolean,
        val background: Boolean,
        val lockScreenNeeded: Boolean, val lockScreen: Boolean,
        val autostartNeeded: Boolean, val autostartConfirmed: Boolean,
        // ulanish (ilova o'zi hal qiladi)
        val appHasPush: Boolean,          // ilova push bilan yig'ilganmi
        val googleServices: Boolean,      // telefonda Google xizmatlari bormi (Huawei'ning yangi modellarida yo'q)
        val online: Boolean,
        val connecting: Boolean,          // hozir ulanishga urinyapti
        val connected: Boolean,           // telefon xabar olishga ro'yxatdan o'tgan
        val serviceDown: Boolean,         // LokmaGo tomonida nosozlik (foydalanuvchi tuzata olmaydi)
    )

    data class Step(val id: Id, val title: String, val hint: String, val done: Boolean, val action: Action, val button: String = "")

    fun plan(s: State): List<Step> = buildList {
        add(Step(Id.NOTIFICATIONS, "Bildirishnomalarga ruxsat", "Yangi buyurtma xabari ekranda chiqishi uchun", s.notifications, Action.FIX, "Ruxsat berish"))
        add(Step(Id.SOUND, "Buyurtma ovozi", "Yangi buyurtma ovoz bilan kelsin. Ochilgan oynada ovozni yoqing", s.sound, Action.FIX, "Yoqish"))
        add(Step(Id.BACKGROUND, "Ilova uxlab qolmasin", "Ochilgan oynada «Ruxsat berish»ni bosing", s.background, Action.FIX, "Ruxsat berish"))
        if (s.lockScreenNeeded) add(Step(Id.LOCK_SCREEN, "Qulflangan ekranda ko'rsatish", "Ochilgan oynada LGO Business'ni yoqing", s.lockScreen, Action.FIX, "Yoqish"))
        if (s.autostartNeeded) add(Step(Id.AUTOSTART, "Avtomatik ishga tushish", "Telefon o'chib-yonganda ham buyurtma kelsin. Ro'yxatda LGO Business'ni yoqing va orqaga qayting", s.autostartConfirmed, Action.FIX, "Ochish"))
        add(connection(s))
    }

    private fun connection(s: State): Step {
        val t = "Buyurtmalarni qabul qilish"
        return when {
            s.connected -> Step(Id.CONNECTION, t, "Telefon tayyor: ilova yopiq bo'lsa ham buyurtma keladi", true, Action.NONE)
            !s.appHasPush -> Step(Id.CONNECTION, t, "Ilovaning bu versiyasi eskirgan. Yangisini o'rnating", false, Action.UPDATE_APP, "Yangilash")
            !s.googleServices -> Step(Id.CONNECTION, t, "Bu telefonda Google xizmatlari yo'q. Buyurtmalar faqat ilova ochiq turganda keladi", false, Action.NONE)
            !s.online -> Step(Id.CONNECTION, t, "Internet yo'q. Wi-Fi yoki mobil internetni yoqing", false, Action.OPEN_INTERNET, "Internetni yoqish")
            s.connecting -> Step(Id.CONNECTION, t, "Ulanmoqda…", false, Action.WAIT)
            s.serviceDown -> Step(Id.CONNECTION, t, "LokmaGo xizmatida vaqtincha nosozlik. Biz bilan bog'laning", false, Action.RETRY, "Qayta urinish")
            else -> Step(Id.CONNECTION, t, "Ulanib bo'lmadi. Internetni tekshirib, qayta urining", false, Action.RETRY, "Qayta urinish")
        }
    }

    /** "Hammasini sozlash": navbatdagi tizim oynasi. [opened] — bu safar ochilganlar (rad etilganini qayta-qayta ochmaymiz). */
    fun next(plan: List<Step>, opened: Set<Id>): Step? = plan.firstOrNull { !it.done && it.action == Action.FIX && it.id !in opened }

    fun pendingFixes(plan: List<Step>): Int = plan.count { !it.done && it.action == Action.FIX }
    fun doneCount(plan: List<Step>): Int = plan.count { it.done }
    fun allDone(plan: List<Step>): Boolean = plan.all { it.done }

    /** Sinov xabari natijasi — oddiy tilda. */
    fun testMessage(sent: Int, failed: Int): String = when {
        sent > 0 -> "Sinov xabari yuborildi. Telefonni qulflang — 10 soniya ichida «Push ishlayapti ✓» kelishi kerak."
        failed > 0 -> "Xabar yetib bormadi. Qayta urining; takrorlansa, LokmaGo bilan bog'laning."
        else -> "Telefon hali ulanmagan. Biroz kuting va qayta urining."
    }
}
