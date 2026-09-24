package uz.lokmago.restaurant.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.local.SessionStore

/** Adds the API-gateway password (from env) and the user's JWT. A 401 with a token means the session is dead. */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.token
        val req = chain.request().newBuilder().apply {
            if (BuildConfig.API_GATEWAY_PASSWORD.isNotEmpty()) header(BuildConfig.API_GATEWAY_HEADER, BuildConfig.API_GATEWAY_PASSWORD)
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        val res = chain.proceed(req)
        if (res.code == 401 && token != null && !req.url.encodedPath.endsWith("auth/login")) session.expire()
        return res
    }
}
