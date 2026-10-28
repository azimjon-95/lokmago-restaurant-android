package uz.lokmago.restaurant.push

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The life cycle of this phone's push token on the server (POST / DELETE /api/panel/devices), as a pure class so it can be unit-tested
 * without Android. [DeviceRegistrar] wires it to SessionStore, Retrofit and Firebase.
 *
 *  • A token counts as registered ONLY after the server answered 2xx (`post` returned normally) — never on a guess.
 *  • The key is "jwt|token": a rotated JWT or a rotated FCM token registers again; nothing else does (no request storms).
 *  • One request at a time (Mutex): onNewToken, socket reconnect and login arriving together produce ONE POST.
 *  • [ensureRegistered] retries in the background with growing pauses, a bounded number of times, and stops on success,
 *    logout, or when a newer call supersedes it. The next socket connect / login / app start starts it again.
 *  • The server derives restaurantId from the JWT; nothing about the restaurant is sent.
 */
class DeviceRegistration(
    private val jwt: () -> String?,
    private val storedToken: () -> String?,
    private val saveToken: (String) -> Unit,
    private val fetchToken: suspend () -> String?,
    private val post: suspend (token: String) -> Unit,
    private val delete: suspend (token: String) -> Unit,
    private val scope: CoroutineScope,
    private val backoffMs: List<Long> = DEFAULT_BACKOFF_MS,
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
) {
    private val lock = Mutex()
    @Volatile private var registeredFor: String? = null
    private var retryJob: Job? = null

    /** One attempt. true = the server holds this (jwt, token) pair. Never throws (except cancellation). */
    suspend fun register(token: String? = null): Boolean = lock.withLock {
        val current = token ?: fetchToken() ?: storedToken() ?: return false
        saveToken(current)                                   // remember it even when logged out: registered right after login
        val session = jwt() ?: return false
        val key = "$session|$current"
        if (registeredFor == key) return true
        try {
            post(current)
            registeredFor = key
            true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { false }                       // offline / 5xx: NOT registered, will retry
    }

    @Synchronized
    fun ensureRegistered(token: String? = null) {
        retryJob?.cancel()
        retryJob = scope.launch {
            var attempt = 0
            while (isActive && jwt() != null && attempt < maxAttempts) {
                if (register(token)) return@launch
                delay(backoffMs[minOf(attempt, backoffMs.lastIndex)])
                attempt++
            }
        }
    }

    /** Logout: tell the server this phone no longer belongs to this account (needs the JWT, so call BEFORE it is dropped). */
    suspend fun unregister() {
        synchronized(this) { retryJob?.cancel(); retryJob = null }
        val token = storedToken() ?: return
        try { delete(token) } catch (e: CancellationException) { throw e } catch (_: Exception) { /* the app ignores pushes while logged out */ }
        registeredFor = null
    }

    companion object {
        /** 5 s, 15 s, 45 s, 2 min, then 5 min — at most [DEFAULT_MAX_ATTEMPTS] tries (~30 min), then wait for the next trigger. */
        val DEFAULT_BACKOFF_MS = listOf(5_000L, 15_000L, 45_000L, 120_000L, 300_000L)
        const val DEFAULT_MAX_ATTEMPTS = 8
    }
}
