package uz.lokmago.restaurant

import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.alert.PhoneSetup
import uz.lokmago.restaurant.alert.PhoneSetup.Action
import uz.lokmago.restaurant.alert.PhoneSetup.Id

class PhoneSetupTest {
    private val ok = PhoneSetup.State(
        notifications = true, sound = true, background = true, lockScreenNeeded = true, lockScreen = true, autostartNeeded = true, autostartConfirmed = true,
        appHasPush = true, googleServices = true, online = true, connecting = false, connected = true, serviceDown = false,
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
        assertEquals(Action.RETRY, c(ok.copy(connected = false, serviceDown = true)).action)
        assertEquals(Action.RETRY, c(ok.copy(connected = false)).action)
        assertTrue("an old build is reported before the network", c(ok.copy(connected = false, appHasPush = false, online = false)).action == Action.UPDATE_APP)
    }

    @Test fun `no developer words anywhere the user can see`() {
        val states = listOf(ok, ok.copy(connected = false), ok.copy(connected = false, appHasPush = false), ok.copy(connected = false, online = false),
            ok.copy(connected = false, googleServices = false), ok.copy(connected = false, serviceDown = true), ok.copy(connected = false, connecting = true),
            ok.copy(notifications = false, sound = false, background = false, lockScreen = false, autostartConfirmed = false))
        for (s in states) for (st in PhoneSetup.plan(s)) for (t in listOf(st.title, st.hint, st.button)) assertFalse("developer word in: $t", dev.containsMatchIn(t))
        for ((a, b) in listOf(1 to 0, 0 to 1, 0 to 0)) assertFalse(dev.containsMatchIn(PhoneSetup.testMessage(a, b)))
    }

    @Test fun `every fix step has a button`() {
        val p = PhoneSetup.plan(ok.copy(notifications = false, sound = false, background = false, lockScreen = false, autostartConfirmed = false))
        assertTrue(p.filter { it.action == Action.FIX }.all { it.button.isNotBlank() })
    }
}
