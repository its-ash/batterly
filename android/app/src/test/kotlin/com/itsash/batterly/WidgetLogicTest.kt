package com.itsash.batterly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetLogicTest {
    @Test
    fun throttlesWhileCharging() {
        assertFalse(WidgetLogic.pushDue(10_000, 8_000, charging = true, stateChanged = false))
        assertTrue(WidgetLogic.pushDue(11_000, 8_000, charging = true, stateChanged = false))
        assertTrue(WidgetLogic.pushDue(8_100, 8_000, charging = true, stateChanged = true))
    }

    @Test
    fun idleIsSlow() {
        assertFalse(WidgetLogic.pushDue(60_000, 0, charging = false, stateChanged = false))
        assertTrue(WidgetLogic.pushDue(300_000, 0, charging = false, stateChanged = false))
    }

    @Test
    fun peakMovesToLastOnUnplug() {
        var p = WidgetLogic.updatePeaks(true, true, 10.0, 0.0, 0.0)
        p = WidgetLogic.updatePeaks(true, true, 15.0, p.session, p.last)
        p = WidgetLogic.updatePeaks(true, true, 12.0, p.session, p.last)
        assertEquals(15.0, p.session, 0.001)
        p = WidgetLogic.updatePeaks(false, false, -1.0, p.session, p.last)
        assertEquals(0.0, p.session, 0.001)
        assertEquals(15.0, p.last, 0.001)
    }

    @Test
    fun timeToFull() {
        // 50% = 2000 mAh left, capacity 4000, 2000 mAh missing at 2000 mA = 60 min.
        assertEquals(60, WidgetLogic.minutesToFull(50, true, 2000.0, 2_000_000))
        assertNull(WidgetLogic.minutesToFull(100, true, 2000.0, 2_000_000))
        assertNull(WidgetLogic.minutesToFull(50, true, 10.0, 2_000_000))
        assertNull(WidgetLogic.minutesToFull(50, false, 2000.0, 2_000_000))
    }

    @Test
    fun durationText() {
        assertEquals("<1m", WidgetLogic.duration(0))
        assertEquals("35m", WidgetLogic.duration(35))
        assertEquals("1h 20m", WidgetLogic.duration(80))
    }
}
