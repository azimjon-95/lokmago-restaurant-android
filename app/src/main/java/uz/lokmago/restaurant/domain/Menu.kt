package uz.lokmago.restaurant.domain

data class DishOption(val name: String, val price: Long, val mandatory: Boolean)
/** [kind]: "variant" (hajm — narxni ALMASHTIRADI) | "addon" (qo'shimcha — narxga QO'SHILADI) | null (eski yozuv). */
data class DishGroup(val title: String, val kind: String?, val options: List<DishOption>)

data class Dish(
    val id: String, val name: String, val description: String, val category: String, val section: String,
    val price: Long, val oldPrice: Long?, val prepMinutes: Int?,
    val weight: String, val volume: String, val drinkType: String,
    val calories: Int?, val protein: Int?, val fat: Int?, val carbs: Int?,
    val imageUrl: String?, val available: Boolean,
    val priceMode: String, val dineInPrice: Long?, val groups: List<DishGroup>,
)

/** Mijoz bahosi/izohi (Order.rating / comment). [stars] = 0 bo'lsa faqat matn qoldirilgan. */
data class Review(val orderId: String, val number: String, val stars: Int, val comment: String, val customer: String, val atMillis: Long, val total: Long)

/** Admin panel (MenuPage.jsx) bilan AYNAN bir xil kategoriyalar va qoidalar. */
object DishCategories {
    val all: List<Pair<String, String>> = listOf(
        "milliy" to "Milliy taom", "osh" to "Osh", "shashlik" to "Shashlik", "sup" to "Sho'rva", "salat" to "Salatlar",
        "choyxona" to "Choyxona", "zavtroki" to "Nonushta", "obed" to "Issiq taomlar", "fastfood" to "Fast food",
        "lavash" to "Lavash", "burger" to "Burger", "tovuq" to "Tovuq", "pitsa" to "Pitsa", "sushi" to "Sushi",
        "evropa" to "Yevropa", "turetskaya" to "Turk taomlari", "koffe" to "Qahva", "shirinlik" to "Shirinlik",
        "salqin" to "Ichimlik", "sous" to "Souslar", "boks" to "Bokslar", "non" to "Nonlar",
    )
    fun label(value: String?) = all.firstOrNull { it.first == value }?.second ?: "Boshqa"

    /** lib/dishMeta.js: ichimlikda gramm emas hajm so'raladi va kaloriya so'ralmaydi. */
    private val drinks = setOf("salqin", "ichimliklar", "ichimlik", "koffe")
    fun isDrink(category: String?) = category?.lowercase() in drinks

    val drinkTypes: List<Pair<String, String>> = listOf(
        "salqin" to "Salqin ichimliklar", "gazli" to "Gazli ichimliklar", "sok" to "Sharbat / sok", "choy" to "Choy",
        "qahva" to "Qahva", "suv" to "Suv", "sut" to "Sut mahsulotlari", "milliy" to "Milliy ichimliklar",
        "energetik" to "Energetik", "boshqa" to "Boshqa",
    )
}
