package com.itsash.batterly

import java.util.Locale
import kotlin.math.abs

/** One reading of the battery. [currentMa] is null when the phone exposes no current sensor. */
data class BatterySnapshot(
    val timestampMs: Long,
    val level: Int,
    val currentMa: Double?,
    val voltageMv: Int,
    val temperatureC: Double,
    val plugged: String,
    val status: ChargeState,
    val health: String,
    val cycleCount: Int?,
    val chargeCounterUah: Long?,
    val screenOn: Boolean,
) {
    val pluggedIn: Boolean get() = plugged != "none"

    val watts: Double? get() = currentMa?.let { CurrentMath.watts(it, voltageMv) }

    /** Wire format for the EventChannel and MethodChannel (read by `lib/battery/battery_snapshot.dart`). */
    fun toMap(): Map<String, Any?> = mapOf(
        "ts" to timestampMs,
        "level" to level,
        "currentMa" to currentMa,
        "voltageMv" to voltageMv,
        "tempC" to temperatureC,
        "plugged" to plugged,
        "status" to status.key,
        "health" to health,
        "cycleCount" to cycleCount,
        "chargeCounterUah" to chargeCounterUah,
        "screenOn" to screenOn,
    )
}

/** Text for the ongoing notification, pure so it can be unit tested. */
object NotificationText {
    /** "+12.4 W · 2,450 mA · 63%" while charging, "-1.8 W · 480 mA · 63%" while discharging. */
    fun summary(s: BatterySnapshot): String {
        val parts = mutableListOf<String>()
        val w = s.watts
        val ma = s.currentMa
        if (w != null && ma != null) {
            parts += String.format(Locale.US, "%s%.1f W", sign(w), abs(w))
            parts += String.format(Locale.US, "%,d mA", Math.round(abs(ma)))
        }
        parts += "${s.level}%"
        return parts.joinToString(" · ")
    }

    fun title(s: BatterySnapshot): String = when {
        s.status == ChargeState.CHARGING -> "Charging (${plugName(s.plugged)})"
        s.status == ChargeState.FULL -> "Battery full"
        s.pluggedIn -> "Plugged in, not charging"
        else -> "On battery"
    }

    /** Number and unit drawn as the status-bar icon, or null for the plain battery glyph. */
    data class IconText(val value: String, val unit: String)

    /**
     * [mode] "level": the battery %. "watts": charging watts while charging with a reading of at least
     * 0.5 W, otherwise the %. Anything else: null (glyph).
     */
    fun iconText(mode: String, s: BatterySnapshot): IconText? {
        val level = IconText("${s.level}", "%")
        return when (mode) {
            "level" -> level
            "watts" -> {
                val w = s.watts
                if (s.status == ChargeState.CHARGING && w != null && w >= 0.5) {
                    IconText(if (w < 10) String.format(Locale.US, "%.1f", w) else "${Math.round(w)}", "W")
                } else {
                    level
                }
            }
            else -> null
        }
    }

    private fun sign(v: Double) = if (v > 0.05) "+" else if (v < -0.05) "-" else ""

    private fun plugName(plugged: String) = when (plugged) {
        "ac" -> "AC"
        "usb" -> "USB"
        "wireless" -> "wireless"
        "dock" -> "dock"
        else -> plugged
    }
}
