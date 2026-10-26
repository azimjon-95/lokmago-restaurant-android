package uz.lokmago.restaurant

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.data.remote.dto.*
import uz.lokmago.restaurant.domain.*
import uz.lokmago.restaurant.ui.menu.*
import uz.lokmago.restaurant.util.cdn
import uz.lokmago.restaurant.util.cdnFit

/** Menyu formasi admin panel (MenuPage.jsx) bilan bir xil JSON yuborishi va server javoblari to'g'ri o'qilishi. */
class MenuLogicTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val dishJson = """
        {"_id":"d1","restaurantId":"r1","section":"Pitsa","name":"Margherita","description":"Pishloqli","price":89000,"oldPrice":99000,
         "category":"pitsa","prepMinutes":20,"imageUrl":"https://res.cloudinary.com/x/image/upload/v1/lokmago/dishes/a.jpg","images":[],
         "isAvailable":false,"priceMode":"custom","dineInPrice":79000,"calories":820,
         "optionGroups":[{"title":"Hajmi","kind":"variant","options":[{"name":"33 sm","price":89000},{"name":"40 sm","price":119000}]},
                         {"title":"Sous","options":[{"name":"Pishloq","price":5000,"mandatory":true}]}],"stoppedCount":3}
    """.trimIndent()

    @Test fun `dish dto maps to domain`() {
        val d = json.decodeFromString<DishDto>(dishJson).toDomain()
        assertEquals("Margherita", d.name); assertEquals(89000L, d.price); assertEquals(99000L, d.oldPrice)
        assertFalse(d.available); assertEquals("custom", d.priceMode); assertEquals(79000L, d.dineInPrice)
        assertEquals(2, d.groups.size); assertEquals("variant", d.groups[0].kind); assertNull(d.groups[1].kind)
        assertTrue(d.groups[1].options[0].mandatory)
    }

    @Test fun `missing optional fields do not break parsing`() {
        val d = json.decodeFromString<DishDto>("""{"_id":"x","name":"Choy","price":5000}""").toDomain()
        assertTrue(d.available); assertEquals("milliy", d.category); assertNull(d.imageUrl); assertTrue(d.groups.isEmpty())
    }

    @Test fun `image falls back to images array`() {
        val d = json.decodeFromString<DishDto>("""{"_id":"x","name":"A","price":1,"imageUrl":"","images":["u1"]}""").toDomain()
        assertEquals("u1", d.imageUrl)
    }

    @Test fun `validation mirrors the admin form`() {
        assertEquals("Taom nomini kiriting", DishDraft(price = "1000").validate())
        assertEquals("Narxni kiriting", DishDraft(name = "Osh").validate())
        assertNull(DishDraft(name = "Osh", price = "45 000").validate())
        val one = DishDraft(name = "Pitsa", price = "89000", groupsDirty = true,
            groups = listOf(GroupDraft("Hajmi", "variant", listOf(OptionDraft("33 sm", "89000")))))
        assertTrue(one.validate()!!.contains("kamida 2"))
        val noPrice = one.copy(groups = listOf(GroupDraft("Hajmi", "variant", listOf(OptionDraft("33 sm", "89000"), OptionDraft("40 sm", "")))))
        assertTrue(noPrice.validate()!!.contains("narx"))
        // guruhlar o'zgartirilmagan bo'lsa tekshirilmaydi (eski ma'lumotni buzmaslik)
        assertNull(one.copy(groupsDirty = false).validate())
    }

    @Test fun `create payload only carries filled fields and derives the section from the category`() {
        val j = DishDraft(name = " Osh ", price = "45 000", category = "osh", prepMinutes = "25", description = "Devzira").toJson(null)
        assertEquals("Osh", j["section"]!!.jsonPrimitive.content); assertEquals("osh", j["category"]!!.jsonPrimitive.content)
        assertEquals("Osh", j["name"]!!.jsonPrimitive.content); assertEquals(45000, j["price"]!!.jsonPrimitive.int)
        assertEquals(25, j["prepMinutes"]!!.jsonPrimitive.int)
        for (k in listOf("oldPrice", "weight", "volume", "calories", "imageUrl", "images", "optionGroups", "dineInPrice")) assertFalse("$k must be absent", j.containsKey(k))
    }

    @Test fun `price mode custom sends the dine-in price, sync does not`() {
        assertEquals(79000, DishDraft(name = "A", price = "1", priceMode = "custom", dineInPrice = "79000").toJson()["dineInPrice"]!!.jsonPrimitive.int)
        assertFalse(DishDraft(name = "A", price = "1", priceMode = "sync", dineInPrice = "79000").toJson().containsKey("dineInPrice"))
    }

    @Test fun `groups are sent only when edited and blanks are dropped, mandatory only for addons`() {
        val d = DishDraft(name = "A", price = "1", groupsDirty = true, groups = listOf(
            GroupDraft("Hajmi", "variant", listOf(OptionDraft("33 sm", "89000", mandatory = true), OptionDraft("", "5"), OptionDraft("40 sm", "119000"))),
            GroupDraft("Sous", "addon", listOf(OptionDraft("Pishloq", "5000", mandatory = true))),
        ))
        val groups = d.toJson()["optionGroups"]!!.jsonArray
        val variant = groups[0].jsonObject["options"]!!.jsonArray
        assertEquals(2, variant.size); assertFalse(variant[0].jsonObject.containsKey("mandatory"))
        assertTrue(groups[1].jsonObject["options"]!!.jsonArray[0].jsonObject["mandatory"]!!.jsonPrimitive.boolean)
        assertFalse(d.copy(groupsDirty = false).toJson().containsKey("optionGroups"))
    }

    @Test fun `removing the photo while editing clears it on the server`() {
        val existing = json.decodeFromString<DishDto>(dishJson).toDomain()
        val j = existing.toDraft().copy(imageUrl = "").toJson(existing)
        assertEquals("", j["imageUrl"]!!.jsonPrimitive.content); assertEquals(JsonArray(emptyList()), j["images"])
        assertFalse(DishDraft(name = "A", price = "1").toJson(null).containsKey("imageUrl"))
    }

    @Test fun `legacy group without kind is recognised as a size variant by title and prices`() {
        val legacy = DishGroup("Hajmi", null, listOf(DishOption("33 sm", 89000, false), DishOption("40 sm", 119000, false)))
        assertTrue(isVariantGroup(legacy, 89000)); assertFalse(isVariantGroup(DishGroup("Sous", null, listOf(DishOption("a", 5000, false), DishOption("b", 6000, false))), 89000))
        assertTrue(isVariantGroup(DishGroup("Sous", "variant", emptyList()), 1)); assertFalse(isVariantGroup(DishGroup("Hajmi", "addon", legacy.options), 89000))
    }

    @Test fun `drinks use volume and no nutrition`() {
        assertTrue(DishCategories.isDrink("salqin")); assertTrue(DishCategories.isDrink("koffe")); assertFalse(DishCategories.isDrink("osh"))
        assertEquals("Ichimlik", DishCategories.label("salqin")); assertEquals("Boshqa", DishCategories.label("nomalum"))
    }

    @Test fun `cloudinary urls are optimised, other urls untouched`() {
        val u = "https://res.cloudinary.com/x/image/upload/v1/a.jpg"
        assertEquals("https://res.cloudinary.com/x/image/upload/f_auto,q_auto,w_200,c_fill/v1/a.jpg", u.cdn(200))
        assertTrue(u.cdnFit(1400).contains("c_limit")); assertEquals("https://x.y/a.jpg", "https://x.y/a.jpg".cdn(200))
    }

    @Test fun `order item carries the dish id and ratings become reviews`() {
        val o = json.decodeFromString<OrderDto>("""{"_id":"6716f0a1b2c3d4e5f6a7b8c9","dailyNumber":7,"status":"delivered","createdAt":"2026-10-25T09:15:30Z","total":120000,
            "customer":{"name":"Ali"},"items":[{"dishId":"d1","name":"Osh","quantity":2,"unitPrice":45000}],"rating":5,"comment":"Juda mazali!","ratedAt":"2026-10-25T11:00:00Z"}""")
        assertEquals("d1", o.items[0].toDomain().dishId)
        val r = o.toReview()!!
        assertEquals(5, r.stars); assertEquals("Juda mazali!", r.comment); assertEquals("Ali", r.customer); assertEquals("7", r.number)
        assertNull(json.decodeFromString<OrderDto>("""{"_id":"x","status":"delivered"}""").toReview())
        assertEquals(0, json.decodeFromString<OrderDto>("""{"_id":"x","comment":"faqat matn"}""").toReview()!!.stars)
    }
}
