package com.itsash.batterly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AlarmTrackerTest {

    private val config = AlarmConfig(chargeAlarm = true, chargeLimit = 80, lowReminder = true, lowLevel = 20)

    @Test
    fun chargeAlarmFiresOnceAtTheLimit() {
        val t = AlarmTracker()
        assertNull(t.evaluate(79, true, config))
        assertEquals(Alert.CHARGE_LIMIT, t.evaluate(80, true, config))
        assertNull(t.evaluate(81, true, config))
        assertNull(t.evaluate(90, true, config))
    }

    @Test
    fun chargeAlarmRearmsAfterUnplugging() {
        val t = AlarmTracker()
        assertEquals(Alert.CHARGE_LIMIT, t.evaluate(85, true, config))
        assertNull(t.evaluate(84, false, config))
        assertEquals(Alert.CHARGE_LIMIT, t.evaluate(84, true, config))
    }

    @Test
    fun chargeAlarmCanBeDisabled() {
        val t = AlarmTracker()
        assertNull(t.evaluate(95, true, config.copy(chargeAlarm = false)))
    }

    @Test
    fun raisingTheLimitFiresAgainWhenReached() {
        val t = AlarmTracker()
        assertEquals(Alert.CHARGE_LIMIT, t.evaluate(80, true, config))
        val higher = config.copy(chargeLimit = 90)
        assertNull(t.evaluate(85, true, higher))
        assertEquals(Alert.CHARGE_LIMIT, t.evaluate(90, true, higher))
    }

    @Test
    fun lowReminderFiresOnceWhileDischarging() {
        val t = AlarmTracker()
        assertNull(t.evaluate(21, false, config))
        assertEquals(Alert.LOW_BATTERY, t.evaluate(20, false, config))
        assertNull(t.evaluate(19, false, config))
    }

    @Test
    fun lowReminderRearmsAfterChargingUp() {
        val t = AlarmTracker()
        assertEquals(Alert.LOW_BATTERY, t.evaluate(15, false, config))
        assertNull(t.evaluate(30, true, config))
        assertEquals(Alert.LOW_BATTERY, t.evaluate(18, false, config))
    }

    @Test
    fun lowReminderIgnoredWhilePlugged() {
        val t = AlarmTracker()
        assertNull(t.evaluate(10, true, config))
    }

    @Test
    fun lowReminderIsOptional() {
        val t = AlarmTracker()
        assertNull(t.evaluate(5, false, config.copy(lowReminder = false)))
    }
}
