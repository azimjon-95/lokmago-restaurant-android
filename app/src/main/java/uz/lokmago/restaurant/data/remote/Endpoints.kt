package uz.lokmago.restaurant.data.remote

import uz.lokmago.restaurant.BuildConfig

/** All hosts come from env.properties / CI env (see README). Nothing is hard-coded in source. */
object Endpoints {
    private val gateway = BuildConfig.API_GATEWAY_URL.trim().trimEnd('/')
    val apiBase: String = "$gateway/${BuildConfig.API_PATH_PREFIX.trim('/')}/"
    val socketUrl: String = BuildConfig.SOCKET_URL.trim().trimEnd('/').ifEmpty { gateway }
    val socketPath: String = BuildConfig.SOCKET_PATH.ifEmpty { "/socket.io/" }
}
