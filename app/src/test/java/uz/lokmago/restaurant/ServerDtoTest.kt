package uz.lokmago.restaurant

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.data.remote.dto.*
import uz.lokmago.restaurant.domain.*

/**
 * lakmago-server javoblari (admin panel bilan bir xil endpointlar) ilova domeniga to'g'ri o'girilishi.
 * JSON — server `toPanelOrder` / `/panel/stats` / `/auth/login` dan olingan shakl.
 */
class ServerDtoTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private val orderJson = """
        {"_id":"6716f0a1b2c3d4e5f6a7b8c9","dailyNumber":12,"status":"pending","createdAt":"2026-10-25T09:15:30.123Z",
         "fulfillment":"delivery","paymentMethod":"click","subtotal":560000,"deliveryFee":15000,"total":575000,
         "restaurantId":"r1","restaurantName":"TOTLI","userId":"u1",
         "customer":{"name":"Ali Valiyev","username":"ali","telegramId":"777","phone":"+998901112233","photoUrl":""},
         "address":"Namangan, Tinchlik 6","addressLat":41.0,"addressLng":71.67,
         "items":[{"dishId":"d1","name":"Lorus Set","quantity":1,"unitPrice":560000,
                   "selectedOptions":[{"name":"6 Kishilik Set","price":200000,"group":"Hajmi","variant":true}]},
                  {"name":"Coca-Cola","quantity":2,"unitPrice":0}],
         "finance":{"restaurantPayout":500000},"restaurantConfirm":null,"changed":true}
    """.trimIndent()

    @Test fun `panel order maps to domain`() {
        val o = json.decodeFromString<OrderDto>(orderJson).toDomain()
        assertEquals("12", o.number)
        assertEquals(OrderStatus.NEW, o.status)
        assertEquals(java.time.Instant.parse("2026-10-25T09:15:30.123Z").toEpochMilli(), o.createdAtMillis)
        assertEquals(Payment.CARD, o.payment)                 // Click — karta, NAQD emas
        assertEquals("Lorus Set (6 Kishilik Set)", o.items[0].name)
        assertEquals(3, o.itemsCount)
        assertEquals(575000L, o.total)
        assertEquals("Ali Valiyev", o.customerName)
        assertEquals("+998901112233", o.customerPhone)
        assertEquals(71.67, o.lng!!, 0.0)
    }

    @Test fun `short socket payload is partial`() {
        val d = json.decodeFromString<OrderDto>("""{"_id":"abc","status":"accepted"}""")
        assertTrue(d.isPartial)
        assertFalse(json.decodeFromString<OrderDto>(orderJson).isPartial)
    }

    @Test fun `changed false means already handled elsewhere`() {
        assertEquals(false, json.decodeFromString<OrderDto>("""{"_id":"a","status":"accepted","createdAt":"2026-01-01T00:00:00Z","changed":false}""").changed)
    }

    @Test fun `unknown status never rings`() {
        assertEquals(OrderStatus.UNKNOWN, json.decodeFromString<OrderDto>("""{"_id":"a","status":"awaiting_payment","createdAt":"2026-01-01T00:00:00Z"}""").toDomain().status)
    }

    @Test fun `login response of admin panel`() {
        val r = json.decodeFromString<LoginResponse>("""{"token":"T","user":{"_id":"u1","login":"totli","role":"restaurant","restaurantId":"r1","firstName":"Ali","allowedPages":[]}}""")
        assertEquals("restaurant", r.user.role); assertEquals("r1", r.user.restaurantId)
    }

    @Test fun `panel stats`() {
        val s = json.decodeFromString<PanelStatsDto>("""
            {"period":{"from":"2026-10-25","to":"2026-10-25","timezone":"Asia/Tashkent"},
             "created":{"count":9,"byStatus":{"pending":2,"delivered":5},"byFulfillment":{"delivery":7,"pickup":2}},
             "delivered":{"count":5,"total":1250000.5,"byPayment":{"cash":{"count":2,"total":300000},"card":{"count":3,"total":950000.5}}},
             "cancelled":{"count":1},"truncated":false}
        """.trimIndent())
        assertEquals(9, s.created.count); assertEquals(2, s.created.byStatus["pending"])
        assertEquals(3, s.delivered.byPayment.card.count); assertEquals(1250000.5, s.delivered.total, 0.0)
    }
}
