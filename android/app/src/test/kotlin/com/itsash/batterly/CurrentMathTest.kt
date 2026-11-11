package com.itsash.batterly

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentMathTest {

    @Test
    fun microAmpsAreConvertedToMilliAmps() {
        assertEquals(2450.0, CurrentMath.toMilliAmps(2_450_000), 1e-9)
        assertEquals(-800.0, CurrentMath.toMilliAmps(-800_000), 1e-9)
    }

    @Test
    fun milliAmpsAreKept() {
        assertEquals(2450.0, CurrentMath.toMilliAmps(2450), 1e-9)
        assertEquals(-312.0, CurrentMath.toMilliAmps(-312), 1e-9)
    }

    @Test
    fun thresholdIsExclusive() {
        // 20 000 is still a plausible mA value (20 A); one above is read as microamps.
        assertEquals(20_000.0, CurrentMath.toMilliAmps(20_000), 1e-9)
        assertEquals(20.001, CurrentMath.toMilliAmps(20_001), 1e-9)
    }

    @Test
    fun chargingIsAlwaysPositive() {
        // Standard vendor (positive while charging) and inverted vendor (negative while charging).
        assertEquals(1500.0, CurrentMath.normalize(1_500_000, ChargeState.CHARGING), 1e-9)
        assertEquals(1500.0, CurrentMath.normalize(-1_500_000, ChargeState.CHARGING), 1e-9)
        assertEquals(900.0, CurrentMath.normalize(-900, ChargeState.CHARGING), 1e-9)
    }

    @Test
    fun dischargingIsAlwaysNegative() {
        assertEquals(-420.0, CurrentMath.normalize(-420_000, ChargeState.DISCHARGING), 1e-9)
        assertEquals(-420.0, CurrentMath.normalize(420_000, ChargeState.DISCHARGING), 1e-9)
        assertEquals(-300.0, CurrentMath.normalize(300, ChargeState.DISCHARGING), 1e-9)
    }

    @Test
    fun fullAndNotChargingKeepTheRawSign() {
        assertEquals(12.0, CurrentMath.normalize(12, ChargeState.FULL), 1e-9)
        assertEquals(-12.0, CurrentMath.normalize(-12, ChargeState.NOT_CHARGING), 1e-9)
        assertEquals(0.0, CurrentMath.normalize(0, ChargeState.UNKNOWN), 1e-9)
    }

    @Test
    fun voltageIsNormalizedToMillivolts() {
        assertEquals(4100, CurrentMath.normalizeVoltageMv(4100))
        assertEquals(4000, CurrentMath.normalizeVoltageMv(4))
        assertEquals(0, CurrentMath.normalizeVoltageMv(-1))
    }

    @Test
    fun powerIsCurrentTimesVoltage() {
        assertEquals(10.0, CurrentMath.watts(2500.0, 4000), 1e-9)
        assertEquals(-2.0, CurrentMath.watts(-500.0, 4000), 1e-9)
    }

    @Test
    fun chargerTypeFromBitmask() {
        assertEquals("none", CurrentMath.pluggedName(0))
        assertEquals("ac", CurrentMath.pluggedName(1))
        assertEquals("usb", CurrentMath.pluggedName(2))
        assertEquals("wireless", CurrentMath.pluggedName(4))
        assertEquals("wireless", CurrentMath.pluggedName(4 or 2))
    }

    @Test
    fun statusMapping() {
        assertEquals(ChargeState.CHARGING, ChargeState.fromAndroid(2))
        assertEquals(ChargeState.DISCHARGING, ChargeState.fromAndroid(3))
        assertEquals(ChargeState.FULL, ChargeState.fromAndroid(5))
        assertEquals(ChargeState.UNKNOWN, ChargeState.fromAndroid(99))
        assertTrue(ChargeState.NOT_CHARGING.key == "not_charging")
    }
}
