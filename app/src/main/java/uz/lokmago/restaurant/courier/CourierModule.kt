package uz.lokmago.restaurant.courier

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.Endpoints
import uz.lokmago.restaurant.data.repo.AppError

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class CourierHttp

/** Kuryer tokeni FAQAT kuryer API'siga ketadi (alohida klient): restoran so'rovlariga aralashmaydi. 401 → kuryer sessiyasi tugaydi (login ekraniga). */
class CourierAuthInterceptor(private val session: SessionStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = session.courierToken
        val original = chain.request()
        val req = if (token != null && original.header("Authorization") == null) original.newBuilder().header("Authorization", "Bearer $token").build() else original
        val res = chain.proceed(req)
        if (res.code == 401 && token != null && !req.url.encodedPath.endsWith("courier-app/login")) session.expireCourier()
        return res
    }
}

@Module
@InstallIn(SingletonComponent::class)
object CourierModule {
    @Provides @Singleton @CourierHttp
    fun courierHttp(session: SessionStore): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(CourierAuthInterceptor(session))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization")
        })
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS).build()

    @Provides @Singleton
    fun courierApi(@CourierHttp client: OkHttpClient, json: Json): CourierApi = Retrofit.Builder()
        .baseUrl(Endpoints.apiBase).client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(CourierApi::class.java)
}

/** Kuryer so'rovlarida serverning O'Z xabari ko'rsatiladi («Avval olingan buyurtmani topshiring», «boshqa kuryer oldi» ...). */
suspend fun <T> courierCall(block: suspend () -> T): T = try {
    block()
} catch (e: HttpException) {
    val text = runCatching { org.json.JSONObject(e.response()?.errorBody()?.string().orEmpty()).optString("error").ifEmpty { null } }.getOrNull()
    throw when (e.code()) {
        401 -> AppError.Unauthorized()
        429 -> AppError.Busy()
        in 400..499 -> AppError.Message(text ?: "Xatolik (${e.code()})")
        else -> AppError.Server(e.code())
    }
} catch (e: IOException) {
    throw AppError.Offline()
} catch (e: kotlinx.serialization.SerializationException) {
    throw AppError.Server(502)
}
