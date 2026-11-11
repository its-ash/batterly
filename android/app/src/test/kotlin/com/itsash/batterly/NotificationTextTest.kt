package com.itsash.batterly

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTextTest {

    private fun snapshot(
        level: Int = 63,
        currentMa: Double? = 2450.0,
        voltageMv: Int = 5000,
        status: ChargeState = ChargeState.CHARGING,
        plugged: String = "ac",
    ) = BatterySnapshot(
        timestampMs = 0,
        level = level,
        currentMa = currentMa,
        voltageMv = voltageMv,
        temperatureC = 30.0,
        plugged = plugged,
        status = status,
        health = "good",
        cycleCount = null,
        chargeCounterUah = null,
        screenOn = true,
    )

    @Test
    fun chargingSummaryShowsWattsCurrentAndLevel() {
        // 2450 mA * 5 V = 12.25 W
        assertEquals("+12.3 W · 2,450 mA · 63%", NotificationText.summary(snapshot()))
    }

    @Test
    fun dischargingSummaryUsesMinus() {
        val s = snapshot(currentMa = -480.0, voltageMv = 3900, status = ChargeState.DISCHARGING, plugged = "none")
        assertEquals("-1.9 W · 480 mA · 63%", NotificationText.summary(s))
    }

    @Test
    fun withoutCurrentSensorOnlyTheLevelShows() {
        assertEquals("63%", NotificationText.summary(snapshot(currentMa = null)))
    }

    @Test
    fun titles() {
        assertEquals("Charging (AC)", NotificationText.title(snapshot()))
        assertEquals("Battery full", NotificationText.title(snapshot(status = ChargeState.FULL)))
        assertEquals("On battery", NotificationText.title(snapshot(status = ChargeState.DISCHARGING, plugged = "none")))
        assertEquals("Plugged in, not charging", NotificationText.title(snapshot(status = ChargeState.NOT_CHARGING)))
    }
}
