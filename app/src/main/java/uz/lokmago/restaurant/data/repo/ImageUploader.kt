package uz.lokmago.restaurant.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import uz.lokmago.restaurant.data.remote.LokmaApi

/**
 * Taom rasmini admin panel bilan BIR XIL yo'l bilan yuklaydi (lib/cloudinary.js):
 *   1. serverdan imzo (secret telefonga tushmaydi)  2. rasm TO'G'RIDAN Cloudinary'ga  3. `secure_url` qaytadi.
 * Yuklashdan oldin rasm 4:3 ga kesiladi (admin ham 4/3 kesadi), 1600 px gacha kichraytiriladi va 5 MB dan oshmaydi.
 */
@Singleton
class ImageUploader @Inject constructor(@ApplicationContext private val ctx: Context, private val api: LokmaApi) {
    // ALOHIDA klient: ilovaning asosiy klienti har so'rovga login tokenini qo'shadi, u uchinchi tomonga HECH QACHON ketmasligi kerak.
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).writeTimeout(60, TimeUnit.SECONDS).build()

    suspend fun upload(uri: Uri): String {
        val bytes = withContext(Dispatchers.IO) { prepare(uri) }
        val sig = apiCall { api.uploadSignature("dishes") }
        return withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", "dish.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
                .addFormDataPart("api_key", sig.apiKey)
                .addFormDataPart("timestamp", sig.timestamp.toString())
                .addFormDataPart("signature", sig.signature)
                .addFormDataPart("folder", sig.folder)
                .addFormDataPart("allowed_formats", sig.allowedFormats)   // imzoga kiritilgan: AYNAN shu qiymat bo'lishi shart
                .build()
            val request = Request.Builder().url("https://api.cloudinary.com/v1_1/${sig.cloudName}/image/upload").post(body).build()
            try {
                client.newCall(request).execute().use { r ->
                    val text = r.body?.string().orEmpty()
                    if (!r.isSuccessful) throw AppError.Message("Rasm yuklanmadi (${r.code}). Qayta urinib ko'ring.")
                    JSONObject(text).optString("secure_url").ifBlank { throw AppError.Message("Rasm manzili qaytmadi") }
                }
            } catch (e: IOException) { throw AppError.Offline() }
        }
    }

    /** Pure function of the picked image → JPEG bytes (EXIF rotation applied, 4:3 centre crop, ≤1600 px, ≤5 MB). */
    private fun prepare(uri: Uri): ByteArray {
        val resolver = ctx.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2400) sample *= 2
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            ?: throw AppError.Message("Rasmni o'qib bo'lmadi")

        val rotation = resolver.openInputStream(uri)?.use {
            when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val upright = if (rotation == 0f) decoded else Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)

        val ratio = 4f / 3f
        val wide = upright.width.toFloat() / upright.height > ratio
        val cropW = if (wide) (upright.height * ratio).toInt() else upright.width
        val cropH = if (wide) upright.height else (upright.width / ratio).toInt()
        var out = Bitmap.createBitmap(upright, (upright.width - cropW) / 2, (upright.height - cropH) / 2, cropW, cropH)
        if (out.width > 1600) out = Bitmap.createScaledBitmap(out, 1600, (1600 / ratio).toInt(), true)

        var quality = 88
        var data: ByteArray
        do {
            val bos = ByteArrayOutputStream()
            out.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            data = bos.toByteArray()
            quality -= 8
        } while (data.size > 5 * 1024 * 1024 && quality > 30)
        return data
    }
}
