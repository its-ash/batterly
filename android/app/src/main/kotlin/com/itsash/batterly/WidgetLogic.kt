package com.itsash.batterly

import kotlin.math.max

/** Pure helpers for the home-screen widget (no Android types, tested in `WidgetLogicTest`). */
object WidgetLogic {
    const val CHARGING_PUSH_MS = 3_000L
    const val IDLE_PUSH_MS = 300_000L

    /**
     * Whether the service should push a widget update now. Charging: about every 3 s. Otherwise
     * only when the visible state changed (plug, status, level) or every 5 minutes.
     */
    fun pushDue(nowMs: Long, lastPushMs: Long, charging: Boolean, stateChanged: Boolean): Boolean {
        if (stateChanged) return true
        val interval = if (charging) CHARGING_PUSH_MS else IDLE_PUSH_MS
        return nowMs - lastPushMs >= interval
    }

    /** (session peak, last finished peak) after one more reading. */
    data class Peaks(val session: Double, val last: Double)

    /**
     * Tracks the peak charging watts of the current plug-in. While charging the session peak grows;
     * once unplugged a non-zero session peak becomes the "last charge" peak.
     */
    fun updatePeaks(charging: Boolean, pluggedIn: Boolean, watts: Double?, session: Double, last: Double): Peaks {
        if (charging && watts != null && watts > 0) return Peaks(max(session, watts), last)
        if (!pluggedIn && session > 0) return Peaks(0.0, session)
        return Peaks(session, last)
    }

    /**
     * Minutes until full from the charge counter and the current reading, or null when unknown or
     * absurd (> 48 h). Mirrors `BatteryMath.timeRemaining` in Dart (charging branch, unsmoothed).
     */
    fun minutesToFull(level: Int, charging: Boolean, currentMa: Double?, chargeCounterUah: Long?): Int? {
        if (!charging || currentMa == null || chargeCounterUah == null) return null
        if (level < 1 || level >= 100 || currentMa < 30.0) return null
        val leftMah = chargeCounterUah / 1000.0
        val capacityMah = leftMah * 100 / level
        val minutes = ((capacityMah - leftMah) / currentMa * 60).toInt()
        return if (minutes > 48 * 60) null else minutes
    }

    /** "1h 20m", "35m", "<1m". */
    fun duration(minutes: Int): String = when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
