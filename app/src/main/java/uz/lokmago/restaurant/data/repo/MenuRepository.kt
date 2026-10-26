package uz.lokmago.restaurant.data.repo

import android.net.Uri
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.JsonObject
import uz.lokmago.restaurant.data.remote.LokmaApi
import uz.lokmago.restaurant.data.remote.dto.StopRequest
import uz.lokmago.restaurant.domain.Dish

/** Menyu CRUD — admin panel (`panelApi.getDishes / createDish / updateDish / toggleStop / deleteDish`) bilan bir xil. */
@Singleton
class MenuRepository @Inject constructor(private val api: LokmaApi, private val uploader: ImageUploader) {
    suspend fun dishes(): List<Dish> = apiCall { api.dishes() }.map { it.toDomain() }
    suspend fun save(id: String?, body: JsonObject): Dish =
        apiCall { if (id == null) api.createDish(body) else api.updateDish(id, body) }.toDomain()
    suspend fun setStopped(id: String, stopped: Boolean): Dish = apiCall { api.stopDish(id, StopRequest(stopped)) }.toDomain()
    suspend fun delete(id: String) { apiCall { api.deleteDish(id) } }
    suspend fun stoppedCount(): Int = apiCall { api.stoppedCount() }.count
    suspend fun uploadImage(uri: Uri): String = uploader.upload(uri)
}
