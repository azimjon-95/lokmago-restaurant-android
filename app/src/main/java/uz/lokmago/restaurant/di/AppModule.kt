package uz.lokmago.restaurant.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import uz.lokmago.restaurant.BuildConfig
import uz.lokmago.restaurant.data.local.SessionStore
import uz.lokmago.restaurant.data.remote.AuthInterceptor
import uz.lokmago.restaurant.data.remote.Endpoints
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.domain.OrderQueue

@Qualifier @Retention(AnnotationRetention.RUNTIME) annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun json(): Json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    @Provides @Singleton
    fun okHttp(session: SessionStore): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(session))
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
            redactHeader("Authorization"); redactHeader(BuildConfig.API_GATEWAY_HEADER)
        })
        .connectTimeout(15, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).writeTimeout(20, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun api(client: OkHttpClient, json: Json): LokmaApi = Retrofit.Builder()
        .baseUrl(Endpoints.apiBase)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build().create(LokmaApi::class.java)

    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun orderQueue(): OrderQueue = OrderQueue()
}
