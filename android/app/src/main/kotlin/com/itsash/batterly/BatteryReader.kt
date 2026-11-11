package com.itsash.batterly

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

/** Reads a [BatterySnapshot] from the sticky battery intent and [BatteryManager] properties. */
class BatteryReader(context: Context) {
    private val app = context.applicationContext
    private val manager = app.getSystemService(BatteryManager::class.java)
    private val power = app.getSystemService(PowerManager::class.java)

    /** True when the phone reports a charging current at all. */
    fun hasCurrentSensor(): Boolean = readRawCurrent() != null

    fun read(): BatterySnapshot {
        val intent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100)?.takeIf { it > 0 } ?: 100
        val level = ((intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0) * 100 / scale).coerceIn(0, 100)
        val status = ChargeState.fromAndroid(intent?.getIntExtra(BatteryManager.EXTRA_STATUS, 1) ?: 1)
        val voltage = CurrentMath.normalizeVoltageMv(intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0)
        val temp = (intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
        val plugged = CurrentMath.pluggedName(intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0)
        val health = CurrentMath.healthName(intent?.getIntExtra(BatteryManager.EXTRA_HEALTH, 1) ?: 1)
        val cycles = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            intent?.getIntExtra(BatteryManager.EXTRA_CYCLE_COUNT, -1)?.takeIf { it > 0 }
        } else {
            null
        }
        // Without a plug the status can lag behind: trust "not plugged" for the sign.
        val effective = if (plugged == "none" && status == ChargeState.CHARGING) ChargeState.DISCHARGING else status

        return BatterySnapshot(
            timestampMs = System.currentTimeMillis(),
            level = level,
            currentMa = readRawCurrent()?.let { CurrentMath.normalize(it, effective) },
            voltageMv = voltage,
            temperatureC = temp,
            plugged = plugged,
            status = effective,
            health = health,
            cycleCount = cycles,
            chargeCounterUah = property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it > 0 },
            screenOn = power?.isInteractive ?: true,
        )
    }

    /** CURRENT_NOW, falling back to CURRENT_AVERAGE when the phone returns 0 or nothing. */
    private fun readRawCurrent(): Long? {
        val now = property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        if (now != null && now != 0L) return now
        val avg = property(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        return avg ?: now
    }

    /** Null when the property is unsupported (the platform returns Long.MIN_VALUE or 0 on some builds). */
    private fun property(id: Int): Long? {
        val v = runCatching { manager?.getLongProperty(id) }.getOrNull() ?: return null
        return if (v == Long.MIN_VALUE) null else v
    }
}
