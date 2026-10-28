package uz.lokmago.restaurant.push

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Token life cycle: first registration, retries, rotation, logout, no request storms. Real coroutines, tiny delays. */
class DeviceRegistrationTest {
    private class Harness(
        var jwt: String? = "jwt1", var stored: String? = null, var firebase: String? = "tokA",
        var failPosts: Int = 0, var failDelete: Boolean = false, backoff: List<Long> = listOf(5L, 5L, 5L), maxAttempts: Int = 8,
    ) {
        val posts = mutableListOf<String>(); val deletes = mutableListOf<String>()
        val inFlight = AtomicInteger(); var maxInFlight = 0
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val reg = DeviceRegistration(
            jwt = { jwt }, storedToken = { stored }, saveToken = { stored = it }, fetchToken = { firebase },
            post = { t ->
                maxInFlight = maxOf(maxInFlight, inFlight.incrementAndGet()); delay(20)
                try { if (failPosts > 0) { failPosts--; throw IOException("offline") }; posts += t } finally { inFlight.decrementAndGet() }
            },
            delete = { t -> if (failDelete) throw IOException("offline"); deletes += t },
            scope = scope, backoffMs = backoff, maxAttempts = maxAttempts,
        )
        fun close() = scope.cancel()
    }

    private suspend fun until(ms: Long = 3_000, cond: () -> Boolean): Boolean { val t = System.currentTimeMillis(); while (System.currentTimeMillis() - t < ms) { if (cond()) return true; delay(5) }; return cond() }

    @Test fun `first registration posts the current firebase token once and remembers it`() = runBlocking {
        val h = Harness()
        assertTrue(h.reg.register()); assertEquals(listOf("tokA"), h.posts); assertEquals("tokA", h.stored)
        assertTrue(h.reg.register()); assertEquals("same jwt + same token: no second request", 1, h.posts.size); h.close()
    }

    @Test fun `not logged in - nothing is sent, the token is only remembered, login then registers`() = runBlocking {
        val h = Harness(jwt = null)
        assertFalse(h.reg.register()); assertTrue(h.posts.isEmpty()); assertEquals("tokA", h.stored)
        h.jwt = "jwt1"; assertTrue(h.reg.register()); assertEquals(listOf("tokA"), h.posts); h.close()
    }

    @Test fun `a failed request is NOT marked registered and the next call retries`() = runBlocking {
        val h = Harness(failPosts = 1)
        assertFalse(h.reg.register()); assertTrue(h.posts.isEmpty())
        assertTrue(h.reg.register()); assertEquals(listOf("tokA"), h.posts); h.close()
    }

    @Test fun `ensureRegistered keeps retrying in the background until the server accepts, then stops`() = runBlocking {
        val h = Harness(failPosts = 2)
        h.reg.ensureRegistered()
        assertTrue("registered after retries", until { h.posts == listOf("tokA") })
        delay(100); assertEquals("no more requests after success", 1, h.posts.size); h.close()
    }

    @Test fun `the retry loop is bounded - it gives up after maxAttempts and waits for the next trigger`() = runBlocking {
        val attempts = AtomicInteger()
        val h = Harness(failPosts = Int.MAX_VALUE, maxAttempts = 4)
        val counting = DeviceRegistration({ h.jwt }, { h.stored }, { h.stored = it }, { "tokA" }, { attempts.incrementAndGet(); throw IOException("down") }, { }, h.scope, listOf(2L), 4)
        counting.ensureRegistered()
        delay(400); assertEquals(4, attempts.get()); h.close()
    }

    @Test fun `logout while retrying stops the loop`() = runBlocking {
        val h = Harness(failPosts = Int.MAX_VALUE, backoff = listOf(10L))
        h.reg.ensureRegistered(); delay(60); h.jwt = null; delay(60)
        val frozen = h.posts.size + h.inFlight.get(); delay(150)
        assertEquals("nothing posted after logout", 0, h.posts.size); assertEquals(frozen, h.posts.size + h.inFlight.get()); h.close()
    }

    @Test fun `a new fcm token registers again and the old one is not re-sent`() = runBlocking {
        val h = Harness()
        h.reg.register(); h.reg.register("tokB")
        assertEquals(listOf("tokA", "tokB"), h.posts); assertEquals("tokB", h.stored)
        h.reg.register("tokB"); assertEquals(2, h.posts.size); h.close()
    }

    @Test fun `jwt rotation registers the same token again`() = runBlocking {
        val h = Harness()
        h.reg.register(); h.jwt = "jwt2"; h.reg.register()
        assertEquals(listOf("tokA", "tokA"), h.posts); h.close()
    }

    @Test fun `simultaneous triggers (login + reconnect + onNewToken) produce ONE request`() = runBlocking {
        val h = Harness()
        val jobs = (1..6).map { async(Dispatchers.Default) { h.reg.register() } }
        jobs.forEach { it.await() }
        assertEquals(1, h.posts.size); assertEquals("never two requests at once", 1, h.maxInFlight); h.close()
    }

    @Test fun `logout deletes this phone's token and the next login registers again`() = runBlocking {
        val h = Harness()
        h.reg.register(); h.reg.unregister()
        assertEquals(listOf("tokA"), h.deletes)
        h.reg.register(); assertEquals("registered again after logout/login", 2, h.posts.size); h.close()
    }

    @Test fun `logout with no token or an offline server never throws`() = runBlocking {
        val none = Harness(stored = null); none.reg.unregister(); assertTrue(none.deletes.isEmpty()); none.close()
        val off = Harness(stored = "tokA", failDelete = true); off.reg.unregister(); off.close()
    }

    @Test fun `no firebase (no google-services) falls back to the stored token, or does nothing`() = runBlocking {
        val none = Harness(firebase = null, stored = null)
        assertFalse(none.reg.register()); assertTrue(none.posts.isEmpty()); none.close()
        val stored = Harness(firebase = null, stored = "tokOld")
        assertTrue(stored.reg.register()); assertEquals(listOf("tokOld"), stored.posts); stored.close()
    }
}
