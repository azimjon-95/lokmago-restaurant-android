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
    enum class Action { FIX, WAIT, RETRY, OPEN_INTERNET, UPDATE_APP, UPDATE_GOOGLE, RELOGIN, NONE }

    /** Nima uchun ulanmadi — texnik xato matnidan foydalanuvchi tushunadigan sababga. */
    enum class Problem { NONE, GOOGLE_SERVICES, SETUP, SIGN_IN, NETWORK, SERVICE, UNKNOWN }

    /**
     * [tokenError] — Google push tokeni olinmaganda uning xato matni (SERVICE_NOT_AVAILABLE, FIS_AUTH_ERROR...), olingan bo'lsa null.
     * [server] — server javobi: "401", "500", "offline" ...; muvaffaqiyat yoki so'rov bo'lmagan bo'lsa null.
     */
    fun classify(tokenOk: Boolean, tokenError: String?, server: String?, serverPushOn: Boolean?): Problem {
        if (!tokenOk) {
            val e = tokenError.orEmpty().uppercase()
            return when {
                listOf("FIS_AUTH_ERROR", "API KEY", "API_KEY", "PERMISSION_DENIED", "INVALID_ARGUMENT", "SENDER", "INSTALLATIONS SERVICE IS UNAVAILABLE", "BAD_REQUEST").any { it in e } -> Problem.SETUP
                listOf("SERVICE_NOT_AVAILABLE", "AUTHENTICATION_FAILED", "MISSING_INSTANCEID", "PHONE_REGISTRATION_ERROR", "TIMEOUT", "INTERNAL_SERVER_ERROR", "TOO_MANY_REGISTRATIONS").any { it in e } -> Problem.GOOGLE_SERVICES
                e.isBlank() -> Problem.GOOGLE_SERVICES
                else -> Problem.UNKNOWN
            }
        }
        return when {
            server == "401" || server == "403" -> Problem.SIGN_IN
            server == "offline" -> Problem.NETWORK
            server != null -> Problem.SERVICE                       // 4xx/5xx: LokmaGo tomonida
            serverPushOn == false -> Problem.SERVICE                // server push yuborishga sozlanmagan
            else -> Problem.NONE
        }
    }

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
        val problem: Problem = Problem.NONE,
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
            s.problem == Problem.GOOGLE_SERVICES -> Step(Id.CONNECTION, t, "Telefondagi Google xizmatlari javob bermayapti. Ularni yangilang, telefonni o'chirib-yoqing va qayta urining", false, Action.UPDATE_GOOGLE, "Yangilash")
            s.problem == Problem.SIGN_IN -> Step(Id.CONNECTION, t, "Kirish muddati tugagan. Chiqib, qayta kiring", false, Action.RELOGIN, "Qayta kirish")
            s.problem == Problem.NETWORK -> Step(Id.CONNECTION, t, "Internet sust yoki uzilib qoldi. Ulanishni tekshirib, qayta urining", false, Action.RETRY, "Qayta urinish")
            s.problem == Problem.SETUP -> Step(Id.CONNECTION, t, "LokmaGo tomonida sozlash kerak. Biz bilan bog'laning: «Texnik ma'lumot»ni rasmga olib yuboring", false, Action.RETRY, "Qayta urinish")
            s.problem == Problem.SERVICE -> Step(Id.CONNECTION, t, "LokmaGo xizmatida vaqtincha nosozlik. Birozdan so'ng qayta urining", false, Action.RETRY, "Qayta urinish")
            else -> Step(Id.CONNECTION, t, "Ulanib bo'lmadi. Qayta urining; takrorlansa, «Texnik ma'lumot»ni rasmga olib bizga yuboring", false, Action.RETRY, "Qayta urinish")
        }
    }

    /** "Hammasini sozlash": navbatdagi tizim oynasi. [opened] — bu safar ochilganlar (rad etilganini qayta-qayta ochmaymiz). */
    fun next(plan: List<Step>, opened: Set<Id>): Step? = plan.firstOrNull { !it.done && it.action == Action.FIX && it.id !in opened }

    fun pendingFixes(plan: List<Step>): Int = plan.count { !it.done && it.action == Action.FIX }
    fun doneCount(plan: List<Step>): Int = plan.count { it.done }
    fun allDone(plan: List<Step>): Boolean = plan.all { it.done }

    /** Sarlavha ostidagi bitta gap: «kuting» faqat haqiqatan kutilayotganda (ulanmagan bo'lsa noto'g'ri «kuting» demaymiz). */
    fun headline(plan: List<Step>): String {
        if (allDone(plan)) return "Buyurtmalar o'z vaqtida keladi"
        if (pendingFixes(plan) > 0) return "Pastdagi tugmani bosing — kerakli oynalar o'zi ochiladi"
        val c = plan.firstOrNull { it.id == Id.CONNECTION && !it.done } ?: return "Bir oz kuting…"
        return if (c.action == Action.WAIT) "Bir oz kuting…" else "Oxirgi qadam: «${c.title}»"
    }

    /** Sinov xabari natijasi — oddiy tilda. */
    fun testMessage(sent: Int, failed: Int): String = when {
        sent > 0 -> "Sinov xabari yuborildi. Telefonni qulflang — 10 soniya ichida «Push ishlayapti ✓» kelishi kerak."
        failed > 0 -> "Xabar yetib bormadi. Qayta urining; takrorlansa, LokmaGo bilan bog'laning."
        else -> "Telefon hali ulanmagan. Biroz kuting va qayta urining."
    }
}
