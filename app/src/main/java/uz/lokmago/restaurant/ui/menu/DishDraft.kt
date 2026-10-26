package uz.lokmago.restaurant.ui.menu

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import uz.lokmago.restaurant.domain.Dish
import uz.lokmago.restaurant.domain.DishCategories
import uz.lokmago.restaurant.domain.DishGroup

/*
 * Taom formasining holati va serverga yuboriladigan JSON — admin panelning DishForm (MenuPage.jsx) bilan AYNAN bir xil:
 * bo'lim kategoriya nomidan olinadi, faqat to'ldirilgan maydonlar yuboriladi, hajm/qo'shimcha guruhlari faqat
 * o'zgartirilgan bo'lsa yuboriladi (eski ma'lumot tasodifan buzilmasin).
 */
data class OptionDraft(val name: String = "", val price: String = "", val mandatory: Boolean = false)
data class GroupDraft(val title: String = "", val kind: String = "addon", val options: List<OptionDraft> = listOf(OptionDraft()))

data class DishDraft(
    val imageUrl: String = "", val name: String = "", val description: String = "", val category: String = "milliy",
    val price: String = "", val oldPrice: String = "", val prepMinutes: String = "15",
    val weight: String = "", val volume: String = "", val drinkType: String = "",
    val calories: String = "", val protein: String = "", val fat: String = "", val carbs: String = "",
    val priceMode: String = "sync", val dineInPrice: String = "",
    val groups: List<GroupDraft> = emptyList(), val groupsDirty: Boolean = false,
)

internal fun money(s: String): Long = s.filter { it.isDigit() }.toLongOrNull() ?: 0L
private fun decimal(s: String): Double? = s.replace(',', '.').trim().toDoubleOrNull()

private val SIZE_WORDS = Regex("(hajm|razmer|o['‘ʻ’`]?lcham|porsiya|portsiya|gramm?|\\bgr\\b|size|litr|объ[её]м|размер|порци|вес|литр)", RegexOption.IGNORE_CASE)

/** lib/dishPricing.js `isVariantGroup`: eski guruhlarda `kind` yo'q — nom va narxdan aniqlanadi. */
fun isVariantGroup(g: DishGroup, basePrice: Long): Boolean {
    if (g.kind == "variant") return true
    if (g.kind == "addon") return false
    if (g.options.size < 2) return false
    if (!SIZE_WORDS.containsMatchIn(g.title)) return false
    if (basePrice <= 0) return false
    return g.options.all { it.price >= basePrice * 0.5 }
}

fun Dish.toDraft() = DishDraft(
    imageUrl = imageUrl.orEmpty(), name = name, description = description, category = category,
    price = price.toString(), oldPrice = oldPrice?.toString().orEmpty(), prepMinutes = (prepMinutes ?: 15).toString(),
    weight = weight, volume = volume, drinkType = drinkType,
    calories = calories?.toString().orEmpty(), protein = protein?.toString().orEmpty(), fat = fat?.toString().orEmpty(), carbs = carbs?.toString().orEmpty(),
    priceMode = priceMode, dineInPrice = dineInPrice?.toString().orEmpty(),
    groups = groups.map { g ->
        GroupDraft(g.title, if (isVariantGroup(g, price)) "variant" else "addon",
            g.options.map { OptionDraft(it.name, it.price.takeIf { p -> p != 0L }?.toString().orEmpty(), it.mandatory) })
    },
)

fun validateGroups(groups: List<GroupDraft>): String? {
    for (g in groups) {
        if (g.title.isBlank()) return "Guruh nomini kiriting"
        val filled = g.options.filter { it.name.isNotBlank() }
        if (g.kind == "variant") {
            if (filled.size < 2) return "\"${g.title}\": kamida 2 ta hajm kiriting"
            if (filled.any { money(it.price) <= 0 }) return "\"${g.title}\": har bir hajmga narx yozing"
        } else if (filled.isEmpty()) return "\"${g.title}\": kamida bitta qo'shimcha kiriting"
    }
    return null
}

fun DishDraft.validate(): String? {
    if (name.isBlank()) return "Taom nomini kiriting"
    if (money(price) <= 0) return "Narxni kiriting"
    if (groupsDirty) validateGroups(groups)?.let { return it }
    return null
}

/** [editing] = taom tahrirlanayotgan bo'lsa (matn maydonlarini va rasmni tozalash mumkin bo'lishi uchun). */
fun DishDraft.toJson(editing: Dish? = null): JsonObject = buildJsonObject {
    val edit = editing != null
    put("section", DishCategories.label(category))
    put("category", category)
    put("name", name.trim())
    put("description", description.trim())
    put("price", money(price))
    if (money(oldPrice) > 0) put("oldPrice", money(oldPrice))
    put("prepMinutes", (prepMinutes.toIntOrNull() ?: 15).coerceIn(1, 240))
    if (edit || weight.isNotBlank()) put("weight", weight.trim())
    if (edit || volume.isNotBlank()) put("volume", volume.trim())
    if (edit || drinkType.isNotBlank()) put("drinkType", drinkType)
    decimal(calories)?.takeIf { it > 0 }?.let { put("calories", it) }
    decimal(protein)?.takeIf { it > 0 }?.let { put("protein", it) }
    decimal(fat)?.takeIf { it > 0 }?.let { put("fat", it) }
    decimal(carbs)?.takeIf { it > 0 }?.let { put("carbs", it) }
    put("priceMode", priceMode)
    if (priceMode == "custom" && money(dineInPrice) > 0) put("dineInPrice", money(dineInPrice))
    if (imageUrl.isNotBlank()) {
        put("imageUrl", imageUrl); put("images", JsonArray(listOf(JsonPrimitive(imageUrl))))
    } else if (editing?.imageUrl != null) {            // rasm olib tashlandi
        put("imageUrl", ""); put("images", JsonArray(emptyList()))
    }
    if (groupsDirty) put("optionGroups", JsonArray(groups.map { g ->
        buildJsonObject {
            put("title", g.title.trim()); put("kind", g.kind)
            put("options", JsonArray(g.options.filter { it.name.isNotBlank() }.map { o ->
                buildJsonObject {
                    put("name", o.name.trim()); put("price", money(o.price))
                    if (g.kind == "addon") put("mandatory", o.mandatory)   // faqat qo'shimchada ma'noli
                }
            }))
        }
    }))
}
