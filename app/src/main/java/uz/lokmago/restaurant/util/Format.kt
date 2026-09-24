package uz.lokmago.restaurant.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Long.som(): String = "${this.toString().reversed().chunked(3).joinToString(" ").reversed()} so'm"
fun Long.hhmm(): String = SimpleDateFormat("HH:mm", Locale.US).format(Date(this))
fun Long.hhmmDate(): String = SimpleDateFormat("HH:mm • dd.MM.yyyy", Locale.US).format(Date(this))

/** Semantic version compare: "1.0.10" > "1.0.9". */
fun compareVersions(a: String, b: String): Int {
    val x = a.split('.').map { it.toIntOrNull() ?: 0 }
    val y = b.split('.').map { it.toIntOrNull() ?: 0 }
    for (i in 0 until maxOf(x.size, y.size)) {
        val c = (x.getOrElse(i) { 0 }).compareTo(y.getOrElse(i) { 0 })
        if (c != 0) return c
    }
    return 0
}
