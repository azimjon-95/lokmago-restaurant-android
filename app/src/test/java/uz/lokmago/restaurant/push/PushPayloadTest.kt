package uz.lokmago.restaurant.push

import org.junit.Assert.*
import org.junit.Test

/** Server (services/mobilePush.js androidMessage) → app: what each data message means, and what is rejected. */
class PushPayloadTest {
    private val id = "6716f0a1b2c3d4e5f6a7b8c9"

    @Test fun `new order push is parsed with its text`() {
        val m = PushPayload.parse(mapOf("type" to "order_new", "orderId" to id, "title" to "🔔 Yangi buyurtma", "body" to "#125 • 128 000 so'm", "sendNo" to "2"))
        assertEquals(PushMessage.NewOrder(id, "🔔 Yangi buyurtma", "#125 • 128 000 so'm"), m)
    }

    @Test fun `missing title and body get a default, never null`() {
        val m = PushPayload.parse(mapOf("type" to "order_new", "orderId" to id)) as PushMessage.NewOrder
        assertEquals("🔔 Yangi buyurtma", m.title); assertEquals("", m.body)
    }

    @Test fun `accepted and cancelled map to OrderChanged`() {
        assertEquals(PushMessage.OrderChanged(id, cancelled = false), PushPayload.parse(mapOf("type" to "order_updated", "orderId" to id)))
        assertEquals(PushMessage.OrderChanged(id, cancelled = true), PushPayload.parse(mapOf("type" to "order_cancelled", "orderId" to id)))
    }

    @Test fun `an orderId that is not a mongo id is dropped before it can reach an API path`() {
        for (bad in listOf("../admin", "123", "6716f0a1b2c3d4e5f6a7b8c", "6716f0a1b2c3d4e5f6a7b8c9/../x", "zzzzzzzzzzzzzzzzzzzzzzzz", "", " $id", "$id\n"))
            assertEquals("orderId='$bad'", PushMessage.Ignored, PushPayload.parse(mapOf("type" to "order_new", "orderId" to bad)))
    }

    @Test fun `missing orderId, missing type, empty map and unknown type are ignored`() {
        assertEquals(PushMessage.Ignored, PushPayload.parse(mapOf("type" to "order_new")))
        assertEquals(PushMessage.Ignored, PushPayload.parse(mapOf("orderId" to id)))
        assertEquals(PushMessage.Ignored, PushPayload.parse(emptyMap()))
        assertEquals(PushMessage.Ignored, PushPayload.parse(mapOf("type" to "something_from_a_newer_server", "orderId" to id)))
    }

    @Test fun `test push needs no orderId`() {
        assertEquals(PushMessage.Test("Push ishlayapti ✓", "Ilova yopiq bo'lsa ham yangi buyurtma shunday keladi"),
            PushPayload.parse(mapOf("type" to "test", "title" to "Push ishlayapti ✓", "body" to "Ilova yopiq bo'lsa ham yangi buyurtma shunday keladi")))
        assertEquals(PushMessage.Test(null, null), PushPayload.parse(mapOf("type" to "test")))
    }

    @Test fun `texts are trimmed and length-capped so a huge push cannot flood the notification`() {
        val m = PushPayload.parse(mapOf("type" to "order_new", "orderId" to id, "title" to "  " + "T".repeat(500) + "  ", "body" to "B".repeat(2000))) as PushMessage.NewOrder
        assertEquals(120, m.title.length); assertEquals(300, m.body.length); assertFalse(m.title.startsWith(" "))
    }

    @Test fun `reminder count is clamped to 1 to 20 and defaults to 1`() {
        fun count(v: String?) = (PushPayload.parse(buildMap { put("type", "order_delivery_reminder"); put("orderId", id); if (v != null) put("reminderCount", v) }) as PushMessage.DeliveryReminder).count
        assertEquals(1, count(null)); assertEquals(1, count("abc")); assertEquals(1, count("-5")); assertEquals(3, count("3")); assertEquals(20, count("9999"))
    }
}
