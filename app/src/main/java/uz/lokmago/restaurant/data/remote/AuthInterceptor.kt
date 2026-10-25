package uz.lokmago.restaurant.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import uz.lokmago.restaurant.data.local.SessionStore

/**
 * Foydalanuvchi JWT'sini qo'shadi (admin panel bilan bir xil token). Token bilan kelgan 401 — sessiya tugagan
 * (parol o'zgargan, akkaunt bloklangan, muddat o'tgan): ilova login ekraniga qaytadi.
 * Kirish so'rovining o'zi (auth/login, panel/me — sarlavha aniq beriladi) bundan mustasno.
 */
class AuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.token
        val original = chain.request()
        val req = if (token != null && original.header("Authorization") == null)
            original.newBuilder().header("Authorization", "Bearer $token").build()
        else original
        val res = chain.proceed(req)
        if (res.code == 401 && token != null && !req.url.encodedPath.endsWith("auth/login")) session.expire()
        return res
    }
}
