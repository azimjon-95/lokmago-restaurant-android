package uz.lokmago.restaurant

import org.junit.Assert.*
import org.junit.Test
import uz.lokmago.restaurant.alert.AlertAttention
import uz.lokmago.restaurant.alert.AlertAttention.Companion.FULL
import uz.lokmago.restaurant.alert.AlertAttention.Companion.IN_APP
import uz.lokmago.restaurant.alert.AlertAttention.Companion.ON_DETAILS
import uz.lokmago.restaurant.alert.AlertAttention.Companion.RECOVER

/** Ovoz foydalanuvchining harakatiga qarab: o'tish mantig'i (vaqt qo'lda boshqariladi). */
class AlertAttentionTest {
    private var t = 100_000L
    private val a = AlertAttention { t }
    private fun level() = a.target().level

    @Test fun `phone locked or another app - full volume`() { assertEquals(FULL, level()) }

    @Test fun `opening the app ducks the alarm to a very low level at once`() {
        a.appVisible(true); assertEquals(IN_APP, level()); assertTrue(level() < 0.25f)
    }

    @Test fun `details page is even quieter than the list`() {
        a.appVisible(true); a.detailsOpen(true); assertEquals(ON_DETAILS, level()); assertTrue(ON_DETAILS < IN_APP)
    }

    @Test fun `leaving the app without acting - waits a few seconds then rises to full`() {
        a.appVisible(true); assertEquals(IN_APP, level())
        a.appVisible(false); assertEquals("short app switch must not blast", IN_APP, level())
        t += AlertAttention.AWAY_GRACE_MS - 1; assertEquals(IN_APP, level())
        t += 1; assertEquals(FULL, level()); assertEquals(AlertAttention.UP_RAMP_MS, a.target().rampMs)
    }

    @Test fun `going back into the app within the grace time never reaches full volume`() {
        a.appVisible(true); a.appVisible(false); t += 1_000; a.appVisible(true); t += 10_000
        assertEquals(IN_APP, level())
    }

    @Test fun `leaving the details page without acting raises the volume again`() {
        a.appVisible(true); a.detailsOpen(true); t += 5_000; a.detailsOpen(false)
        assertEquals("right after leaving: still gentle", IN_APP, level())
        t += AlertAttention.LEFT_DETAILS_RISE_MS; assertEquals(RECOVER, level())
    }

    @Test fun `leaving the details page and then touching the list keeps it quiet`() {
        a.appVisible(true); a.detailsOpen(true); t += 3_000; a.detailsOpen(false); t += 2_000; a.touched(); t += 8_000
        assertEquals(IN_APP, level())
    }

    @Test fun `touching the screen keeps it quiet, being idle in the app raises it to medium`() {
        a.appVisible(true)
        t += 15_000; a.touched(); t += 15_000; assertEquals("touched 15 s ago", IN_APP, level())
        t += AlertAttention.IDLE_MS; assertEquals(RECOVER, level()); assertTrue(RECOVER < FULL)
    }

    @Test fun `opening the app after being away resets idle`() {
        a.appVisible(true); a.appVisible(false); t += 60_000; assertEquals(FULL, level())
        a.appVisible(true); assertEquals(IN_APP, level())
    }

    @Test fun `ramps are smooth, never a jump, and converge`() {
        var g = FULL; var steps = 0
        while (g != IN_APP && steps < 200) { val n = AlertAttention.step(g, IN_APP, 100, AlertAttention.DOWN_RAMP_MS); assertTrue("monotonic down", n <= g); assertTrue("no jump bigger than half the distance", g - n <= (g - IN_APP) * 0.6f + 0.01f); g = n; steps++ }
        assertEquals(IN_APP, g); assertTrue("reached within ~1 s", steps <= 12)
        g = IN_APP; steps = 0
        while (g != FULL && steps < 200) { val n = AlertAttention.step(g, FULL, 100, AlertAttention.UP_RAMP_MS); assertTrue(n >= g); g = n; steps++ }
        assertEquals(FULL, g); assertTrue("rises slower than it falls", steps > 12)
    }
}
