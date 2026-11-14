package com.itsash.batterly

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build

/**
 * Facts about the battery that do not change every second.
 *
 *  - [designCapacityMah]: the vendor's design capacity from the hidden `PowerProfile`
 *    (`power_profile.xml`). Read by reflection; null when the phone hides it or reports nonsense.
 *  - [advanced]: Android 14+ properties (state of health, manufacturing and first-use date, charging
 *    policy). They need `android.permission.BATTERY_STATS`, which only adb can grant:
 *    `adb shell pm grant com.itsash.batterly android.permission.BATTERY_STATS`.
 */
object DeviceBattery {
    // BatteryManager @SystemApi constants (API 34), not in the public SDK.
    private const val PROPERTY_MANUFACTURING_DATE = 7
    private const val PROPERTY_FIRST_USAGE_DATE = 8
    private const val PROPERTY_CHARGING_POLICY = 9
    private const val PROPERTY_STATE_OF_HEALTH = 10

    @SuppressLint("PrivateApi")
    fun designCapacityMah(context: Context): Double? = runCatching {
        val cls = Class.forName("com.android.internal.os.PowerProfile")
        val profile = cls.getConstructor(Context::class.java).newInstance(context.applicationContext)
        val mah = cls.getMethod("getBatteryCapacity").invoke(profile) as Double
        mah.takeIf { it in 1000.0..25000.0 }
    }.getOrNull()

    fun statsGranted(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.BATTERY_STATS) == PackageManager.PERMISSION_GRANTED

    /** Map for the `health` channel call. Values are null when unavailable. */
    fun advanced(context: Context): Map<String, Any?> {
        val granted = statsGranted(context)
        val supported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        if (!granted || !supported) {
            return mapOf("statsGranted" to granted)
        }
        val manager = context.getSystemService(BatteryManager::class.java)
        fun prop(id: Int): Long? = runCatching { manager?.getLongProperty(id) }.getOrNull()
            ?.takeIf { it != Long.MIN_VALUE && it > 0 }
        return mapOf(
            "statsGranted" to true,
            "stateOfHealth" to prop(PROPERTY_STATE_OF_HEALTH)?.takeIf { it in 1..100 }?.toInt(),
            "manufactureDateMs" to prop(PROPERTY_MANUFACTURING_DATE)?.let(::epochMs),
            "firstUseDateMs" to prop(PROPERTY_FIRST_USAGE_DATE)?.let(::epochMs),
            "chargingPolicy" to prop(PROPERTY_CHARGING_POLICY)?.toInt(),
        )
    }

    /** The dates come in seconds since the epoch; accept milliseconds too. Before 2010 is treated as unknown. */
    fun epochMs(v: Long): Long? {
        val ms = if (v > 100_000_000_000L) v else v * 1000
        return ms.takeIf { it >= 1_262_304_000_000L }
    }
}
