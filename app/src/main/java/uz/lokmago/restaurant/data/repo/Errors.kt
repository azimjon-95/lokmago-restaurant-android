package uz.lokmago.restaurant.data.repo

import java.io.IOException
import retrofit2.HttpException

/** Foydalanuvchiga ko'rinadigan xatolar: nima bo'lganini va nima qilish kerakligini aytadi. */
sealed class AppError(message: String) : Exception(message) {
    class Offline : AppError("Internet yo'q. Ulanishni tekshiring — buyurtma holati o'zgarmadi.")
    class AlreadyHandled : AppError("Buyurtma allaqachon qabul qilingan")
    class NotFound : AppError("Buyurtma topilmadi")
    class Unauthorized : AppError("Login yoki parol noto'g'ri")
    class NotRestaurant : AppError("Bu ilova faqat restoran akkaunti uchun. Admin panelga kiradigan restoran login va parolini kiriting.")
    class TooEarly : AppError("Hali erta. Kuryer yetkazishi uchun biroz vaqt bering, keyin qayta urinib ko'ring.")
    class Cancelled : AppError("Buyurtma bekor qilingan")
    class Blocked : AppError("Juda ko'p urinish. Birozdan so'ng qayta urinib ko'ring.")
    class Busy : AppError("Server band. Birozdan so'ng qayta urinib ko'ring.")
    /** Serverning o'z (o'zbekcha) xabari — masalan "Akkaunt bloklangan. Administrator bilan bog'laning." */
    class Message(text: String) : AppError(text)
    class Server(code: Int) : AppError("Serverda xatolik ($code). Birozdan so'ng qayta urinib ko'ring.")
}

/** lakmago-server {error, code}; OkHttp xato tanasini faqat BIR MARTA o'qish mumkin. */
private fun HttpException.errorJson(): org.json.JSONObject? = runCatching {
    org.json.JSONObject(response()?.errorBody()?.string().orEmpty())
}.getOrNull()

suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val body = e.errorJson()
    val code = body?.optString("code")?.ifEmpty { null }
    val text = body?.optString("error")?.ifEmpty { null }
    val isLogin = e.response()?.raw()?.request?.url?.encodedPath?.endsWith("auth/login") == true
    throw when (e.code()) {
        401 -> AppError.Unauthorized()
        // 403: bloklangan akkaunt / rol mos emas — server matni aniqroq
        403 -> if (text != null) AppError.Message(text) else AppError.NotRestaurant()
        404 -> AppError.NotFound()
        409 -> when (code) {
            "CONFIRM_TOO_EARLY" -> AppError.TooEarly()
            "ORDER_CANCELLED" -> AppError.Cancelled()
            else -> AppError.AlreadyHandled()          // RACE_LOST: boshqa xodim/telefon ulgurdi
        }
        // WRONG_STATE: holat allaqachon o'zgargan (boshqa joyda bajarilgan) — "o'tib keting"
        400 -> if (code == "WRONG_STATE") AppError.AlreadyHandled() else if (text != null) AppError.Message(text) else AppError.Server(400)
        429 -> if (isLogin) AppError.Blocked() else AppError.Busy()
        else -> AppError.Server(e.code())
    }
} catch (e: IOException) {
    throw AppError.Offline()
} catch (e: kotlinx.serialization.SerializationException) {
    throw AppError.Server(502) // kutilmagan javob ekranni yiqitmasin, xabar ko'rsatsin
}
