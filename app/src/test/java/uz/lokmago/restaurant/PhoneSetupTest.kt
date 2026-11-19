package uz.lokmago.restaurant

import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.alert.PhoneSetup
import uz.lokmago.restaurant.alert.PhoneSetup.Action
import uz.lokmago.restaurant.alert.PhoneSetup.Id
import uz.lokmago.restaurant.alert.PhoneSetup.Problem

class PhoneSetupTest {
    private val ok = PhoneSetup.State(
        notifications = true, sound = true, background = true, lockScreenNeeded = true, lockScreen = true, autostartNeeded = true, autostartConfirmed = true,
        appHasPush = true, googleServices = true, online = true, connecting = false, connected = true,
    )
    private val dev = Regex("(?i)firebase|fcm|token|server|google-services|\\.env|endpoint|api")

    @Test fun `fully set up phone - everything done, nothing to fix`() {
        val p = PhoneSetup.plan(ok)
        assertTrue(PhoneSetup.allDone(p)); assertEquals(0, PhoneSetup.pendingFixes(p)); assertNull(PhoneSetup.next(p, emptySet()))
    }

    @Test fun `steps that do not exist on this phone are not shown`() {
        val ids = PhoneSetup.plan(ok.copy(lockScreenNeeded = false, autostartNeeded = false)).map { it.id }
        assertFalse(Id.LOCK_SCREEN in ids); assertFalse(Id.AUTOSTART in ids)
        assertEquals(listOf(Id.NOTIFICATIONS, Id.SOUND, Id.BACKGROUND, Id.CONNECTION), ids)
    }

    @Test fun `guided flow opens missing system screens in order and skips the ones already opened`() {
        val p = PhoneSetup.plan(ok.copy(notifications = false, background = false, autostartConfirmed = false))
        assertEquals(3, PhoneSetup.pendingFixes(p))
        assertEquals(Id.NOTIFICATIONS, PhoneSetup.next(p, emptySet())?.id)
        assertEquals(Id.BACKGROUND, PhoneSetup.next(p, setOf(Id.NOTIFICATIONS))?.id)
        assertEquals(Id.AUTOSTART, PhoneSetup.next(p, setOf(Id.NOTIFICATIONS, Id.BACKGROUND))?.id)
        assertTrue("a declined step is not reopened again and again", PhoneSetup.next(p, setOf(Id.NOTIFICATIONS, Id.BACKGROUND, Id.AUTOSTART)) == null)
    }

    @Test fun `connection is never a guided step - the app handles it itself`() {
        val p = PhoneSetup.plan(ok.copy(connected = false, connecting = true))
        assertNull(PhoneSetup.next(p, emptySet()))
        assertEquals(Action.WAIT, p.last().action)
    }

    @Test fun `connection problems are explained by what the user can do`() {
        fun c(s: PhoneSetup.State) = PhoneSetup.plan(s).last()
        assertEquals(Action.UPDATE_APP, c(ok.copy(connected = false, appHasPush = false)).action)
        assertEquals(Action.OPEN_INTERNET, c(ok.copy(connected = false, online = false)).action)
        assertEquals(Action.NONE, c(ok.copy(connected = false, googleServices = false)).action)
        assertEquals(Action.RETRY, c(ok.copy(connected = false, problem = PhoneSetup.Problem.SERVICE)).action)
        assertEquals(Action.RETRY, c(ok.copy(connected = false)).action)
        assertTrue("an old build is reported before the network", c(ok.copy(connected = false, appHasPush = false, online = false)).action == Action.UPDATE_APP)
    }

    @Test fun `no developer words anywhere the user can see`() {
        val states = listOf(ok, ok.copy(connected = false), ok.copy(connected = false, appHasPush = false), ok.copy(connected = false, online = false),
            ok.copy(connected = false, googleServices = false), ok.copy(connected = false, problem = PhoneSetup.Problem.SERVICE), ok.copy(connected = false, connecting = true),
            ok.copy(notifications = false, sound = false, background = false, lockScreen = false, autostartConfirmed = false)) +
            Problem.values().map { ok.copy(connected = false, problem = it) }
        for (s in states) for (st in PhoneSetup.plan(s)) for (t in listOf(st.title, st.hint, st.button)) assertFalse("developer word in: $t", dev.containsMatchIn(t))
        for ((a, b) in listOf(1 to 0, 0 to 1, 0 to 0)) assertFalse(dev.containsMatchIn(PhoneSetup.testMessage(a, b)))
    }

    @Test fun `every fix step has a button`() {
        val p = PhoneSetup.plan(ok.copy(notifications = false, sound = false, background = false, lockScreen = false, autostartConfirmed = false))
        assertTrue(p.filter { it.action == Action.FIX }.all { it.button.isNotBlank() })
    }

    @Test fun `token errors are classified into something the user can act on`() {
        assertEquals(Problem.GOOGLE_SERVICES, PhoneSetup.classify(false, "java.io.IOException: SERVICE_NOT_AVAILABLE", null, null))
        assertEquals(Problem.GOOGLE_SERVICES, PhoneSetup.classify(false, "AUTHENTICATION_FAILED", null, null))
        assertEquals(Problem.GOOGLE_SERVICES, PhoneSetup.classify(false, "TIMEOUT", null, null))
        assertEquals(Problem.GOOGLE_SERVICES, PhoneSetup.classify(false, null, null, null))
        assertEquals(Problem.SETUP, PhoneSetup.classify(false, "java.io.IOException: FIS_AUTH_ERROR", null, null))
        assertEquals(Problem.SETUP, PhoneSetup.classify(false, "Firebase Installations Service is unavailable. Please try again later.", null, null))
        assertEquals(Problem.SETUP, PhoneSetup.classify(false, "API key not valid. Please pass a valid API key.", null, null))
        assertEquals(Problem.UNKNOWN, PhoneSetup.classify(false, "something new", null, null))
    }

    @Test fun `server answers are classified`() {
        assertEquals(Problem.SIGN_IN, PhoneSetup.classify(true, null, "401", true))
        assertEquals(Problem.NETWORK, PhoneSetup.classify(true, null, "offline", null))
        assertEquals(Problem.SERVICE, PhoneSetup.classify(true, null, "500", null))
        assertEquals(Problem.SERVICE, PhoneSetup.classify(true, null, "400", null))
        assertEquals(Problem.SERVICE, PhoneSetup.classify(true, null, null, false))
        assertEquals(Problem.NONE, PhoneSetup.classify(true, null, null, true))
    }

    @Test fun `each problem gives its own action and the header never says wait after a failure`() {
        fun c(p: Problem) = PhoneSetup.plan(ok.copy(connected = false, problem = p)).last()
        assertEquals(Action.UPDATE_GOOGLE, c(Problem.GOOGLE_SERVICES).action)
        assertEquals(Action.RELOGIN, c(Problem.SIGN_IN).action)
        assertEquals(Action.RETRY, c(Problem.NETWORK).action)
        assertEquals(Action.RETRY, c(Problem.SETUP).action)
        assertTrue(Problem.values().filter { it != Problem.NONE }.all { c(it).button.isNotBlank() })
        val failed = PhoneSetup.plan(ok.copy(connected = false, problem = Problem.UNKNOWN))
        assertFalse(PhoneSetup.headline(failed).contains("kuting"))
        assertTrue(PhoneSetup.headline(PhoneSetup.plan(ok.copy(connected = false, connecting = true))).contains("kuting"))
        assertEquals("Buyurtmalar o'z vaqtida keladi", PhoneSetup.headline(PhoneSetup.plan(ok)))
    }
}
