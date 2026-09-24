package uz.lokmago.restaurant.domain

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FIFO queue of NEW orders that still need a human decision.
 *
 * Why it exists: when 5–6 orders arrive at once we must not show 6 overlapping alerts or play 6 sounds.
 * The head order is presented, everyone else waits their turn ("Navbatda yana N ta"),
 * and ONE alarm covers the whole queue.
 *
 * Rules
 *  - de-duplicates by order id (socket + FCM + pending sync may all deliver the same order)
 *  - only status NEW is queued; any other status removes the order
 *  - handled orders leave the queue; the next one becomes the head automatically
 *  - a deferred head goes to the back of the line
 *  - a backend sync never drops an order that arrived after the sync request started
 *
 * Pure Kotlin (no Android) — unit-tested on the JVM. All mutations are serialized by [lock].
 */
class OrderQueue(private val clock: () -> Long = System::currentTimeMillis) {

    data class State(val orders: List<Order> = emptyList()) {
        val head: Order? get() = orders.firstOrNull()
        val waiting: List<Order> get() = orders.drop(1)
        val size: Int get() = orders.size
        val isEmpty: Boolean get() = orders.isEmpty()
    }

    private val lock = Any()
    private val receivedAt = HashMap<String, Long>()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun contains(id: String): Boolean = _state.value.orders.any { it.id == id }

    /** @return true only when the order was newly added (drives "new order" side effects exactly once). */
    fun offer(order: Order): Boolean = synchronized(lock) {
        if (order.status != OrderStatus.NEW) {
            removeLocked(order.id)
            return false
        }
        val current = _state.value.orders
        if (current.any { it.id == order.id }) {
            _state.value = State(current.map { if (it.id == order.id) order else it })
            false
        } else {
            receivedAt[order.id] = clock()
            _state.value = State(current + order)
            true
        }
    }

    fun remove(id: String) = synchronized(lock) { removeLocked(id) }

    private fun removeLocked(id: String) {
        receivedAt.remove(id)
        val current = _state.value.orders
        if (current.any { it.id == id }) _state.value = State(current.filterNot { it.id == id })
    }

    /** Send the current head to the back of the line (staff can't deal with it right now). */
    fun deferHead() = synchronized(lock) {
        val o = _state.value.orders
        if (o.size >= 2) _state.value = State(o.drop(1) + o.first())
    }

    /**
     * Reconcile with the backend's authoritative pending list.
     * @param requestedAt when the backend request was *sent*; orders received after that are kept.
     * @return orders that were not known before (they need alarm / notification).
     */
    fun replaceAll(pending: List<Order>, requestedAt: Long): List<Order> = synchronized(lock) {
        val fresh = pending.filter { it.status == OrderStatus.NEW }.associateBy { it.id }
        val local = _state.value.orders
        val kept = local.mapNotNull { o ->
            fresh[o.id] ?: o.takeIf { (receivedAt[o.id] ?: 0L) > requestedAt }
        }
        val keptIds = kept.map { it.id }.toSet()
        val newcomers = fresh.values.filter { it.id !in keptIds }.sortedBy { it.createdAtMillis }
        newcomers.forEach { receivedAt[it.id] = clock() }
        receivedAt.keys.retainAll((keptIds + newcomers.map { it.id }).toSet())
        _state.value = State(kept + newcomers)
        newcomers.filter { n -> local.none { it.id == n.id } }
    }

    fun clear() = synchronized(lock) {
        receivedAt.clear()
        _state.value = State()
    }
}
