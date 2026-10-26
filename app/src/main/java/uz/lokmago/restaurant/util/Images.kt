package uz.lokmago.restaurant.util

/** Cloudinary: kichik rasm (avto format/sifat, kesib to'ldirish) — admin panelning `optimizeUrl` bilan bir xil. */
fun String.cdn(width: Int): String =
    if (!contains("/upload/")) this else replace("/upload/", "/upload/f_auto,q_auto,w_$width,c_fill/")

/** Katta ko'rinish: kesmasdan, kenglik bo'yicha cheklab. */
fun String.cdnFit(width: Int): String =
    if (!contains("/upload/")) this else replace("/upload/", "/upload/f_auto,q_auto,w_$width,c_limit/")
