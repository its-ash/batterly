package com.itsash.batterly

import kotlin.math.abs

/** Charging state as reported by `BatteryManager.EXTRA_STATUS`. */
enum class ChargeState {
    CHARGING,
    DISCHARGING,
    NOT_CHARGING,
    FULL,
    UNKNOWN;

    /** Wire name used on the channel and in notifications ("charging", "full", ...). */
    val key: String get() = name.lowercase()

    companion object {
        // Values of BatteryManager.BATTERY_STATUS_*; plain ints keep this file JVM-testable.
        fun fromAndroid(status: Int): ChargeState = when (status) {
            2 -> CHARGING
            3 -> DISCHARGING
            4 -> NOT_CHARGING
            5 -> FULL
            else -> UNKNOWN
        }
    }
}

/**
 * Pure battery math with no Android types, covered by `CurrentMathTest`.
 *
 * `BatteryManager.BATTERY_PROPERTY_CURRENT_NOW` is documented as microamperes, with
 * positive = charging. Vendors do not agree: many report milliamperes, and some report
 * the opposite sign (positive = discharging). We normalize in two steps:
 *
 * 1. **Unit.** Phone batteries never move more than about 20 A, so a magnitude above
 *    [MICRO_AMP_THRESHOLD] (20 000) cannot be milliamperes and is treated as microamperes
 *    (divided by 1000). Known limit: a device reporting microamperes while idling below
 *    20 mA (|value| < 20 000) is read as milliamperes and shows a too-large idle current.
 * 2. **Sign.** The charging status is reliable on every device, so it wins over the sign
 *    of the raw value: while CHARGING the result is positive, while DISCHARGING it is
 *    negative. In FULL / NOT_CHARGING / UNKNOWN the current is near zero and the raw sign
 *    is kept.
 */
object CurrentMath {
    const val MICRO_AMP_THRESHOLD = 20_000L

    /** Converts a raw reading to milliamperes (unit only, sign unchanged). */
    fun toMilliAmps(raw: Long): Double =
        if (abs(raw) > MICRO_AMP_THRESHOLD) raw / 1000.0 else raw.toDouble()

    /** Milliamperes with the sign fixed from the charging status: + charging, - discharging. */
    fun normalize(raw: Long, state: ChargeState): Double {
        val ma = toMilliAmps(raw)
        return when (state) {
            ChargeState.CHARGING -> abs(ma)
            ChargeState.DISCHARGING -> -abs(ma)
            else -> ma
        }
    }

    /** `EXTRA_VOLTAGE` is millivolts; a few devices report volts (3.8) or tenths. Returns mV. */
    fun normalizeVoltageMv(raw: Int): Int = when {
        raw <= 0 -> 0
        raw < 20 -> raw * 1000
        else -> raw
    }

    /** Power in watts (signed like the current). */
    fun watts(currentMa: Double, voltageMv: Int): Double = currentMa * voltageMv / 1_000_000.0

    /** `EXTRA_PLUGGED` bitmask to a single name. */
    fun pluggedName(plugged: Int): String = when {
        plugged and 4 != 0 -> "wireless"
        plugged and 1 != 0 -> "ac"
        plugged and 2 != 0 -> "usb"
        plugged and 8 != 0 -> "dock"
        else -> "none"
    }

    fun healthName(health: Int): String = when (health) {
        2 -> "good"
        3 -> "overheat"
        4 -> "dead"
        5 -> "overVoltage"
        6 -> "failure"
        7 -> "cold"
        else -> "unknown"
    }
}
