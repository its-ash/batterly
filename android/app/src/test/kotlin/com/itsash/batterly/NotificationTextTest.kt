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

    @Test
    fun statusIconText() {
        val pct = NotificationText.IconText("63", "%")
        assertEquals(null, NotificationText.iconText("icon", snapshot()))
        assertEquals(pct, NotificationText.iconText("level", snapshot()))
        assertEquals(NotificationText.IconText("12", "W"), NotificationText.iconText("watts", snapshot()))
        assertEquals(NotificationText.IconText("4.9", "W"), NotificationText.iconText("watts", snapshot(currentMa = 980.0)))
        // Not charging or no current sensor: the level.
        assertEquals(pct, NotificationText.iconText("watts", snapshot(currentMa = -480.0, status = ChargeState.DISCHARGING, plugged = "none")))
        assertEquals(pct, NotificationText.iconText("watts", snapshot(currentMa = null)))
    }
}
