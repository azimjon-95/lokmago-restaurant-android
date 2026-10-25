package uz.lokmago.restaurant.data.remote

import uz.lokmago.restaurant.BuildConfig

/**
 * Hammasi env.properties / CI env dan (README). Ilova lakmago-serverga TO'G'RIDAN-TO'G'RI ulanadi —
 * admin panel ishlatadigan o'sha API (`API_URL` + `API_PATH_PREFIX`, odatda `/api/`) va o'sha Socket.IO.
 */
object Endpoints {
    private val origin = BuildConfig.API_URL.trim().trimEnd('/')
    val apiBase: String = "$origin/${BuildConfig.API_PATH_PREFIX.trim('/')}/"
    val socketUrl: String = BuildConfig.SOCKET_URL.trim().trimEnd('/').ifEmpty { origin }
    val socketPath: String = BuildConfig.SOCKET_PATH.ifEmpty { "/socket.io/" }

    /** Gradle release tekshiruvidan keyingi ikkinchi himoya: release build hech qachon oddiy HTTP ishlatmaydi. */
    init {
        if (!BuildConfig.DEBUG) {
            check(apiBase.startsWith("https://") && socketUrl.startsWith("https://")) { "Release build requires HTTPS endpoints" }
        }
    }
}
