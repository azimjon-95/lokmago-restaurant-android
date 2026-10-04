package uz.lokmago.restaurant.data.repo

import java.io.IOException
import retrofit2.HttpException

/** User-facing errors. Messages are shown to staff as-is, so they say what happened and what to do. */
sealed class AppError(message: String) : Exception(message) {
    class Offline : AppError("Internet yo'q. Ulanishni tekshiring — buyurtma holati o'zgarmadi.")
    class AlreadyHandled : AppError("Buyurtma allaqachon qabul qilingan")
    class NotFound : AppError("Buyurtma topilmadi")
    class Unauthorized : AppError("Login yoki parol noto'g'ri")
    class TooEarly : AppError("Hali erta. Kuryer yetkazishi uchun biroz vaqt bering, keyin qayta urinib ko'ring.")
    class Blocked(seconds: Int?) : AppError("Kirish vaqtincha bloklandi. ${seconds ?: 30} soniyadan keyin urinib ko'ring.")
    class Busy : AppError("Server band. Birozdan so'ng qayta urinib ko'ring.")
    class Server(code: Int) : AppError("Serverda xatolik ($code). Birozdan so'ng qayta urinib ko'ring.")
}

/** The BFF sends {error, code, ...}; `code` tells apart outcomes that share an HTTP status.
 *  An OkHttp error body can be read only ONCE, so it is parsed a single time here. */
private fun HttpException.errorJson(): org.json.JSONObject? = runCatching {
    org.json.JSONObject(response()?.errorBody()?.string().orEmpty())
}.getOrNull()

suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val body = e.errorJson()
    val code = body?.optString("code")?.ifEmpty { null }
    throw when (e.code()) {
        401 -> AppError.Unauthorized()
        403, 404 -> AppError.NotFound()
        409 -> if (code == "confirm_too_early") AppError.TooEarly() else AppError.AlreadyHandled()
        429 -> if (code == "pin_blocked" || code == "login_blocked") AppError.Blocked(body?.optInt("retryAfter", -1)?.takeIf { it > 0 }) else AppError.Busy()
        else -> AppError.Server(e.code())
    }
} catch (e: IOException) {
    throw AppError.Offline()
} catch (e: kotlinx.serialization.SerializationException) {
    throw AppError.Server(502) // an unexpected reply must show a message, not crash the screen
}
