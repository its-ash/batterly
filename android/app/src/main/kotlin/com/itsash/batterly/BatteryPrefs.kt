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
        const val KEY_ASKED_NOTIFICATIONS = "asked_notifications"
    }
}
