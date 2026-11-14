package com.itsash.batterly

enum class Alert { CHARGE_LIMIT, LOW_BATTERY }

data class AlarmConfig(
    val chargeAlarm: Boolean,
    val chargeLimit: Int,
    val lowReminder: Boolean,
    val lowLevel: Int,
)

/**
 * Decides when to raise the charge-limit and low-battery alerts. Each alert fires once per
 * crossing and re-arms when the situation ends (unplugged / dropped below the limit, or
 * plugged in / recovered above the low level). Pure, covered by `AlarmTrackerTest`.
 *
 * State is in memory: restarting the service while the battery is still above the limit
 * can raise the alert once more.
 */
class AlarmTracker {
    private var chargeFired = false
    private var lowFired = false

    fun evaluate(level: Int, pluggedIn: Boolean, config: AlarmConfig): Alert? {
        if (!pluggedIn || level < config.chargeLimit) chargeFired = false
        if (pluggedIn || level >= config.lowLevel + LOW_HYSTERESIS) lowFired = false

        if (config.chargeAlarm && pluggedIn && level >= config.chargeLimit && !chargeFired) {
            chargeFired = true
            return Alert.CHARGE_LIMIT
        }
        if (config.lowReminder && !pluggedIn && level <= config.lowLevel && !lowFired) {
            lowFired = true
            return Alert.LOW_BATTERY
        }
        return null
    }

    private companion object {
        const val LOW_HYSTERESIS = 5
    }
}

/**
 * Low-battery alert for Bluetooth devices, keyed by address. Fires once when a device reaches
 * [threshold] or less, re-arms when it reports [threshold] + 5 or more (it was charged).
 */
class DeviceAlarmTracker {
    private val fired = HashSet<String>()

    /** True when an alert should be raised for this reading. */
    fun evaluate(address: String, level: Int, threshold: Int, enabled: Boolean): Boolean {
        if (level < 0) return false
        if (level >= threshold + 5) fired.remove(address)
        if (!enabled || level > threshold || address in fired) return false
        fired += address
        return true
    }
}
