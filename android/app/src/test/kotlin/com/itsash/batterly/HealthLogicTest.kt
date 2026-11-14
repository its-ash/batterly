package com.itsash.batterly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthLogicTest {
    private val charging = CapacityTracker.Direction.CHARGING
    private val discharging = CapacityTracker.Direction.DISCHARGING

    /** Simulates a charge of a [capacityMah] battery from [from]% to [to]%, a reading every 30 s, level steps every 2 min. */
    private fun charge(t: CapacityTracker, capacityMah: Double, from: Int, to: Int, startMs: Long = 0): Long {
        var ms = startMs
        // The first reading sits in the middle of a level step: its counter is off by half a percent.
        val perPercent = capacityMah * 10 // µAh per 1%
        var counter = (from * perPercent + perPercent / 2).toLong()
        assertNull(t.update(ms, from, counter, charging))
        for (level in from + 1..to) {
            for (i in 0 until 4) {
                ms += 30_000
                counter += (perPercent / 4).toLong()
                val shown = if (i == 3) level else level - 1
                assertNull(t.update(ms, shown, counter, charging))
            }
        }
        return ms
    }

    @Test
    fun estimatesCapacityFromALongCharge() {
        val t = CapacityTracker()
        val end = charge(t, 4500.0, 30, 80)
        val e = t.update(end + 30_000, 80, 999_999_999, null)
        assertNotNull(e)
        assertEquals(4500.0, e!!.mah, 50.0)
        assertEquals(31, e.fromLevel)
        assertEquals(80, e.toLevel)
        assertTrue(e.charging)
    }

    @Test
    fun shortChargeGivesNothing() {
        val t = CapacityTracker()
        val end = charge(t, 4500.0, 50, 65)
        assertNull(t.update(end + 30_000, 65, 1, null))
    }

    @Test
    fun dischargeWorksToo() {
        val t = CapacityTracker()
        var ms = 0L
        var counter = 3_000_000L
        t.update(ms, 75, counter, discharging)
        for (level in 74 downTo 40) {
            ms += 60_000
            counter -= 40_000 // 4000 mAh battery: 40 mAh per %
            t.update(ms, level, counter, discharging)
        }
        val e = t.finish()
        assertNotNull(e)
        assertEquals(4000.0, e!!.mah, 1.0)
        assertFalse(e.charging)
    }

    @Test
    fun missedLevelChangesDoNotAnchor() {
        val t = CapacityTracker()
        t.update(0, 30, 1_500_000, charging)
        // Next reading 30 min later (deep sleep): not an anchor.
        t.update(30 * 60_000, 40, 2_000_000, charging)
        t.update(31 * 60_000, 41, 2_050_000, charging) // anchor
        t.update(32 * 60_000, 50, 2_500_000, charging) // anchor, but only 9% span
        assertNull(t.finish())
    }

    @Test
    fun counterUnits() {
        assertEquals(3_000_000L, CapacityTracker.normalizeCounterUah(3_000_000))
        assertEquals(3_000_000L, CapacityTracker.normalizeCounterUah(3_000)) // reported in mAh
        assertNull(CapacityTracker.normalizeCounterUah(0))
        assertNull(CapacityTracker.normalizeCounterUah(null))
    }

    @Test
    fun absurdResultsAreDropped() {
        // Counter barely moved: 100 mAh battery.
        assertNull(CapacityTracker.estimate(20, 1_000_000, 60, 1_040_000, 0, charging))
        // Counter went the wrong way.
        assertNull(CapacityTracker.estimate(20, 2_000_000, 60, 1_000_000, 0, charging))
        assertEquals(5000.0, CapacityTracker.estimate(20, 1_000_000, 60, 3_000_000, 0, charging)!!.mah, 1e-9)
    }

    @Test
    fun direction() {
        assertEquals(charging, CapacityTracker.directionOf(ChargeState.CHARGING, true))
        assertEquals(discharging, CapacityTracker.directionOf(ChargeState.DISCHARGING, false))
        assertNull(CapacityTracker.directionOf(ChargeState.FULL, true))
        assertNull(CapacityTracker.directionOf(ChargeState.NOT_CHARGING, true))
    }

    @Test
    fun cyclesCountOnlyRises() {
        assertEquals(0, CycleLogic.chargedDelta(null, 50))
        assertEquals(5, CycleLogic.chargedDelta(45, 50))
        assertEquals(0, CycleLogic.chargedDelta(50, 45))
    }

    @Test
    fun androidDatesAcceptSecondsAndMillis() {
        assertEquals(1_700_000_000_000L, DeviceBattery.epochMs(1_700_000_000))
        assertEquals(1_700_000_000_000L, DeviceBattery.epochMs(1_700_000_000_000))
        assertNull(DeviceBattery.epochMs(1000))
    }

    @Test
    fun deviceAlarmFiresOncePerDrop() {
        val a = DeviceAlarmTracker()
        assertFalse(a.evaluate("x", 30, 20, true))
        assertTrue(a.evaluate("x", 20, 20, true))
        assertFalse(a.evaluate("x", 15, 20, true))
        assertTrue(a.evaluate("y", 10, 20, true)) // other device
        assertFalse(a.evaluate("x", 24, 20, true)) // not yet re-armed
        assertFalse(a.evaluate("x", 80, 20, true)) // charged: re-armed
        assertTrue(a.evaluate("x", 19, 20, true))
        assertFalse(a.evaluate("z", 5, 20, false)) // off
    }
}
