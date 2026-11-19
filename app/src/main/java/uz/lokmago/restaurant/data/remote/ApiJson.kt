package uz.lokmago.restaurant.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json

/**
 * Server bilan JSON — BITTA joyda (Retrofit: restoran va kuryer API, test).
 *
 * encodeDefaults = true — MAJBURIY. Busiz kotlinx standart qiymatga teng maydonni so'rovga umuman QO'SHMAYDI: `platform: String = "android"`
 * yuborilmay qolardi va server push ro'yxatini 400 «Token yoki platforma noto'g'ri» bilan rad etardi (hech bir Android telefoni ro'yxatda yo'q,
 * buyurtma faqat ilova ochiq paytda kelardi). Kuryerning bo'sh tumanlar ro'yxati ham shu sababli yuborilmasdi.
 * explicitNulls = false — null maydonlar yuborilmaydi (server `.optional()` null'ni qabul qilmaydi).
 */
@OptIn(ExperimentalSerializationApi::class)
val ApiJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    encodeDefaults = true
}
