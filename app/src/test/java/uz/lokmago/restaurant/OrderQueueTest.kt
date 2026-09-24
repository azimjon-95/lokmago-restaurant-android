package uz.lokmago.restaurant

import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.domain.*
import uz.lokmago.restaurant.util.compareVersions

class OrderQueueTest {
    private var now = 1_000L
    private val q = OrderQueue { now }

    private fun order(n: Int, status: OrderStatus = OrderStatus.NEW, at: Long = n * 10L) = Order(
        id = "id$n", number = "$n", status = status, createdAtMillis = at, fulfillment = Fulfillment.DELIVERY,
        payment = Payment.CARD, itemsCount = 2, subtotal = 100, deliveryFee = 20, total = 120,
    )

    @Test fun `six orders arrive - first is head, rest wait in arrival order`() {
        (1..6).forEach { assertTrue(q.offer(order(it))) }
        assertEquals("1", q.state.value.head?.number)
        assertEquals(listOf("2", "3", "4", "5", "6"), q.state.value.waiting.map { it.number })
    }

    @Test fun `accepting head promotes the next one`() {
        (1..3).forEach { q.offer(order(it)) }
        q.remove("id1")
        assertEquals("2", q.state.value.head?.number)
        assertEquals(2, q.state.value.size)
    }

    @Test fun `duplicate delivery of same order is ignored`() {
        assertTrue(q.offer(order(1)))
        assertFalse(q.offer(order(1)))     // socket + FCM both delivered it
        assertEquals(1, q.state.value.size)
    }

    @Test fun `non-new status removes the order`() {
        q.offer(order(1)); q.offer(order(2))
        q.offer(order(1, OrderStatus.ACCEPTED))       // accepted on another phone
        assertEquals(listOf("2"), q.state.value.orders.map { it.number })
    }

    @Test fun `defer sends head to the back`() {
        (1..3).forEach { q.offer(order(it)) }
        q.deferHead()
        assertEquals(listOf("2", "3", "1"), q.state.value.orders.map { it.number })
    }

    @Test fun `defer with a single order is a no-op`() {
        q.offer(order(1)); q.deferHead()
        assertEquals("1", q.state.value.head?.number)
    }

    @Test fun `sync adds missing orders sorted by creation time and reports them as new`() {
        val added = q.replaceAll(listOf(order(3, at = 30), order(1, at = 10), order(2, at = 20)), requestedAt = 500)
        assertEquals(listOf("1", "2", "3"), q.state.value.orders.map { it.number })
        assertEquals(3, added.size)
    }

    @Test fun `sync drops orders handled elsewhere`() {
        now = 100; q.offer(order(1)); q.offer(order(2))
        q.replaceAll(listOf(order(2)), requestedAt = 1_000)   // #1 no longer pending on backend
        assertEquals(listOf("2"), q.state.value.orders.map { it.number })
    }

    @Test fun `sync never drops an order that arrived after the request was sent`() {
        q.replaceAll(listOf(order(1)), requestedAt = 500)
        now = 2_000; q.offer(order(2))                         // socket delivered #2 while sync was in flight
        val added = q.replaceAll(listOf(order(1)), requestedAt = 1_500)   // stale response without #2
        assertEquals(listOf("1", "2"), q.state.value.orders.map { it.number })
        assertTrue(added.isEmpty())
    }

    @Test fun `sync keeps deferred order position for known orders`() {
        (1..3).forEach { q.offer(order(it)) }
        q.deferHead()                                          // 2,3,1
        q.replaceAll((1..3).map { order(it) }, requestedAt = 5_000)
        assertEquals(listOf("2", "3", "1"), q.state.value.orders.map { it.number })
    }

    @Test fun `unknown backend status never becomes NEW`() {
        assertEquals(OrderStatus.UNKNOWN, OrderStatus.from("weird"))
        assertEquals(OrderStatus.NEW, OrderStatus.from("pending"))
    }

    @Test fun `next action follows fulfillment`() {
        val d = order(1, OrderStatus.READY)
        assertEquals(OrderStatus.DELIVERING, d.nextAction()?.target)
        assertEquals(OrderStatus.DELIVERED, d.copy(fulfillment = Fulfillment.PICKUP).nextAction()?.target)
        assertNull(order(1, OrderStatus.DELIVERED).nextAction())
    }

    @Test fun `version compare is numeric not lexical`() {
        assertTrue(compareVersions("1.0.10", "1.0.9") > 0)
        assertTrue(compareVersions("1.0.3", "1.0.4") < 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
    }
}
