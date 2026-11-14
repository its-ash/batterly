package com.itsash.batterly

import android.content.Context
import android.content.SharedPreferences

/** Settings shared by the app and [MonitorService] (SharedPreferences `batterly_prefs`). */
class BatteryPrefs(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var monitorEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONITOR, false)
        set(v) = prefs.edit().putBoolean(KEY_MONITOR, v).apply()

    var chargeAlarm: Boolean
        get() = prefs.getBoolean(KEY_CHARGE_ALARM, true)
        set(v) = prefs.edit().putBoolean(KEY_CHARGE_ALARM, v).apply()

    var chargeLimit: Int
        get() = prefs.getInt(KEY_CHARGE_LIMIT, 80).coerceIn(50, 100)
        set(v) = prefs.edit().putInt(KEY_CHARGE_LIMIT, v.coerceIn(50, 100)).apply()

    var lowReminder: Boolean
        get() = prefs.getBoolean(KEY_LOW_REMINDER, false)
        set(v) = prefs.edit().putBoolean(KEY_LOW_REMINDER, v).apply()

    var lowLevel: Int
        get() = prefs.getInt(KEY_LOW_LEVEL, 20).coerceIn(5, 50)
        set(v) = prefs.edit().putInt(KEY_LOW_LEVEL, v.coerceIn(5, 50)).apply()

    var vibrate: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(v) = prefs.edit().putBoolean(KEY_VIBRATE, v).apply()

    /** "c" or "f". Display only: the app converts. */
    var tempUnit: String
        get() = prefs.getString(KEY_TEMP_UNIT, "c") ?: "c"
        set(v) = prefs.edit().putString(KEY_TEMP_UNIT, if (v == "f") "f" else "c").apply()

    /** Peak charging watts of the current plug-in (written by the service). */
    var sessionPeakWatts: Double
        get() = prefs.getFloat(KEY_SESSION_PEAK, 0f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_SESSION_PEAK, v.toFloat()).apply()

    /** Peak watts of the last finished charge, shown by the widget. */
    var lastPeakWatts: Double
        get() = prefs.getFloat(KEY_LAST_PEAK, 0f).toDouble()
        set(v) = prefs.edit().putFloat(KEY_LAST_PEAK, v.toFloat()).apply()

    /** Small icon of the live notification: "icon" (battery glyph), "level" (the %), "watts" (W while charging, % otherwise). */
    var statusIcon: String
        get() = prefs.getString(KEY_STATUS_ICON, "icon")?.takeIf { it in STATUS_ICONS } ?: "icon"
        set(v) = prefs.edit().putString(KEY_STATUS_ICON, if (v in STATUS_ICONS) v else "icon").apply()

    /** Alert when a connected Bluetooth device drops to [btLowLevel]. */
    var btAlert: Boolean
        get() = prefs.getBoolean(KEY_BT_ALERT, false)
        set(v) = prefs.edit().putBoolean(KEY_BT_ALERT, v).apply()

    var btLowLevel: Int
        get() = prefs.getInt(KEY_BT_LOW_LEVEL, 20).coerceIn(5, 50)
        set(v) = prefs.edit().putInt(KEY_BT_LOW_LEVEL, v.coerceIn(5, 50)).apply()

    /** Sum of every percent charged while the monitor watched (written by the service). 100 = one cycle. */
    var chargedPercent: Long
        get() = prefs.getLong(KEY_CHARGED_PERCENT, 0)
        set(v) = prefs.edit().putLong(KEY_CHARGED_PERCENT, v).apply()

    /** When [chargedPercent] started counting, 0 = never. */
    var cycleSinceMs: Long
        get() = prefs.getLong(KEY_CYCLE_SINCE, 0)
        set(v) = prefs.edit().putLong(KEY_CYCLE_SINCE, v).apply()

    /** Last level the service saw, -1 = none. Kept across restarts so a charge while it was off still counts. */
    var lastLevel: Int
        get() = prefs.getInt(KEY_LAST_LEVEL, -1)
        set(v) = prefs.edit().putInt(KEY_LAST_LEVEL, v).apply()

    val alarmConfig: AlarmConfig
        get() = AlarmConfig(chargeAlarm, chargeLimit, lowReminder, lowLevel)

    fun toMap(): Map<String, Any?> = mapOf(
        "monitor" to monitorEnabled,
        "chargeAlarm" to chargeAlarm,
        "chargeLimit" to chargeLimit,
        "lowReminder" to lowReminder,
        "lowLevel" to lowLevel,
        "vibrate" to vibrate,
        "tempUnit" to tempUnit,
        "statusIcon" to statusIcon,
        "btAlert" to btAlert,
        "btLowLevel" to btLowLevel,
    )

    /** Applies one setting from the channel. Returns false for an unknown key. */
    fun set(key: String?, value: Any?): Boolean {
        when (key) {
            "monitor" -> monitorEnabled = value as? Boolean ?: return false
            "chargeAlarm" -> chargeAlarm = value as? Boolean ?: return false
            "chargeLimit" -> chargeLimit = (value as? Number)?.toInt() ?: return false
            "lowReminder" -> lowReminder = value as? Boolean ?: return false
            "lowLevel" -> lowLevel = (value as? Number)?.toInt() ?: return false
            "vibrate" -> vibrate = value as? Boolean ?: return false
            "tempUnit" -> tempUnit = value as? String ?: return false
            "statusIcon" -> statusIcon = (value as? String)?.takeIf { it in STATUS_ICONS } ?: return false
            "btAlert" -> btAlert = value as? Boolean ?: return false
            "btLowLevel" -> btLowLevel = (value as? Number)?.toInt() ?: return false
            else -> return false
        }
        return true
    }

    companion object {
        const val FILE = "batterly_prefs"
        private const val KEY_MONITOR = "monitor"
        private const val KEY_CHARGE_ALARM = "charge_alarm"
        private const val KEY_CHARGE_LIMIT = "charge_limit"
        private const val KEY_LOW_REMINDER = "low_reminder"
        private const val KEY_LOW_LEVEL = "low_level"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_TEMP_UNIT = "temp_unit"
        private const val KEY_SESSION_PEAK = "session_peak_w"
        private const val KEY_LAST_PEAK = "last_peak_w"
        private const val KEY_STATUS_ICON = "status_icon"
        private const val KEY_BT_ALERT = "bt_alert"
        private const val KEY_BT_LOW_LEVEL = "bt_low_level"
        private const val KEY_CHARGED_PERCENT = "charged_percent"
        private const val KEY_CYCLE_SINCE = "cycle_since_ms"
        private const val KEY_LAST_LEVEL = "last_level"
        val STATUS_ICONS = setOf("icon", "level", "watts")
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
