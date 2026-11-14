package com.itsash.batterly

import java.util.Locale
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

    /** "31.5 °C" or "88.7 °F". */
    fun temperature(celsius: Double, fahrenheit: Boolean): String =
        if (fahrenheit) String.format(Locale.US, "%.1f °F", celsius * 9 / 5 + 32) else String.format(Locale.US, "%.1f °C", celsius)

    /** A point of the graph widget: x and y in 0..1 (y = level / 100, 1 at the top is drawn by the caller). */
    data class GraphPoint(val x: Float, val y: Float)

    /**
     * Level samples between [fromMs] and [toMs] as polylines for the graph widget. A gap longer than
     * [gapMs] (monitor off, deep sleep) splits the line instead of drawing a false straight segment.
     * Lines with a single point are kept (the caller draws a dot).
     */
    fun graphLines(samples: List<Pair<Long, Int>>, fromMs: Long, toMs: Long, gapMs: Long = GRAPH_GAP_MS): List<List<GraphPoint>> {
        if (toMs <= fromMs) return emptyList()
        val span = (toMs - fromMs).toFloat()
        val lines = mutableListOf<MutableList<GraphPoint>>()
        var prevTs = Long.MIN_VALUE
        for ((ts, level) in samples) {
            if (ts < fromMs || ts > toMs) continue
            val p = GraphPoint((ts - fromMs) / span, level.coerceIn(0, 100) / 100f)
            if (lines.isEmpty() || ts - prevTs > gapMs) lines += mutableListOf(p) else lines.last() += p
            prevTs = ts
        }
        return lines
    }

    const val GRAPH_GAP_MS = 10 * 60_000L

    /** "1h 20m", "35m", "<1m". */
    fun duration(minutes: Int): String = when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }
}
