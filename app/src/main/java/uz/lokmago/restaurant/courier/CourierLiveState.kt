package uz.lokmago.restaurant.courier

import kotlinx.coroutines.flow.MutableStateFlow

/** CourierLocationService yozadi, Bosh sahifa o'qiydi: GPS aniqligi (metr) va serverga uzatish holati. */
object CourierLiveState {
    val accuracyM = MutableStateFlow<Int?>(null)
    val connected = MutableStateFlow<Boolean?>(null)
    fun reset() { accuracyM.value = null; connected.value = null }
}
