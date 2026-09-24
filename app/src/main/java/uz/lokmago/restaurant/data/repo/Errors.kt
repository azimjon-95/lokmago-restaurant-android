package uz.lokmago.restaurant.data.repo

import java.io.IOException
import retrofit2.HttpException

/** User-facing errors. Messages are shown to staff as-is, so they say what happened and what to do. */
sealed class AppError(message: String) : Exception(message) {
    class Offline : AppError("Internet yo'q. Ulanishni tekshiring — buyurtma holati o'zgarmadi.")
    class AlreadyHandled : AppError("Buyurtma allaqachon qabul qilingan")
    class NotFound : AppError("Buyurtma topilmadi")
    class Unauthorized : AppError("Login yoki parol noto'g'ri")
    class Server(code: Int) : AppError("Serverda xatolik ($code). Birozdan so'ng qayta urinib ko'ring.")
}

suspend fun <T> apiCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    throw when (e.code()) {
        401 -> AppError.Unauthorized()
        403, 404 -> AppError.NotFound()
        409 -> AppError.AlreadyHandled()
        else -> AppError.Server(e.code())
    }
} catch (e: IOException) {
    throw AppError.Offline()
}
