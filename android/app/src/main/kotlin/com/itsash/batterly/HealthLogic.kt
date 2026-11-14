package com.itsash.batterly

import kotlin.math.abs

/** One capacity estimate from a charge or discharge of at least [CapacityTracker.MIN_SPAN] percent. */
data class CapacityEstimate(
    val timestampMs: Long,
    val mah: Double,
    val fromLevel: Int,
    val toLevel: Int,
    val charging: Boolean,
)

/**
 * Estimates the battery's real full capacity from `CHARGE_COUNTER` (pure, tested in `HealthLogicTest`).
 *
 * While the phone charges (or runs on battery) without interruption, the tracker notes the charge
 * counter at each level change. When that stretch ends (plugged / unplugged / full), the first and the
 * last level change give `capacity = Δcounter / Δlevel × 100`. Measuring between level changes keeps
 * the 1% rounding of the level out of the result. Stretches shorter than [MIN_SPAN] percent and
 * absurd results are dropped.
 */
class CapacityTracker {
    enum class Direction { CHARGING, DISCHARGING }

    private data class Anchor(val ms: Long, val level: Int, val counterUah: Long)

    private var direction: Direction? = null
    private var prevLevel: Int? = null
    private var prevMs = 0L
    private var start: Anchor? = null
    private var last: Anchor? = null

    /**
     * Feeds one reading. [counterUah] is the raw charge counter (see [normalizeCounterUah]).
     * Returns an estimate when a stretch long enough just ended.
     */
    fun update(ms: Long, level: Int, counterUah: Long?, direction: Direction?): CapacityEstimate? {
        if (direction != this.direction) {
            val done = finish()
            this.direction = direction
            reset(ms, level)
            return done
        }
        val counter = normalizeCounterUah(counterUah)
        val prev = prevLevel
        val closelyWatched = ms - prevMs <= MAX_GAP_MS
        prevLevel = level
        prevMs = ms
        if (direction == null || counter == null || prev == null || level == prev) return null
        if (!closelyWatched) {
            // Missed the exact moment of the change: a later anchor would be off by up to 1%.
            return null
        }
        val a = Anchor(ms, level, counter)
        if (start == null) start = a else last = a
        return null
    }

    /** Ends the current stretch (for example when the service stops). */
    fun finish(): CapacityEstimate? {
        val s = start ?: return null
        val e = last ?: return null
        val dir = direction ?: return null
        start = null
        last = null
        return estimate(s.level, s.counterUah, e.level, e.counterUah, e.ms, dir)
    }

    private fun reset(ms: Long, level: Int) {
        prevLevel = level
        prevMs = ms
        start = null
        last = null
    }

    companion object {
        const val MIN_SPAN = 20
        const val MIN_MAH = 1000.0
        const val MAX_MAH = 25000.0
        const val MAX_GAP_MS = 3 * 60_000L

        /**
         * `CHARGE_COUNTER` is documented in µAh, but some phones report mAh. A value below 30 000 can only
         * be mAh on a real phone battery (30 mAh µAh-wise would be under 1% of any battery).
         */
        fun normalizeCounterUah(raw: Long?): Long? {
            if (raw == null || raw <= 0) return null
            return if (raw < 30_000) raw * 1000 else raw
        }

        fun estimate(
            fromLevel: Int,
            fromCounterUah: Long,
            toLevel: Int,
            toCounterUah: Long,
            atMs: Long,
            direction: Direction,
        ): CapacityEstimate? {
            val dl = toLevel - fromLevel
            val dc = toCounterUah - fromCounterUah
            if (abs(dl) < MIN_SPAN) return null
            val charging = direction == Direction.CHARGING
            if (charging && (dl <= 0 || dc <= 0)) return null
            if (!charging && (dl >= 0 || dc >= 0)) return null
            val mah = dc / 1000.0 / dl * 100
            if (mah < MIN_MAH || mah > MAX_MAH) return null
            return CapacityEstimate(atMs, mah, fromLevel, toLevel, charging)
        }

        fun directionOf(status: ChargeState, pluggedIn: Boolean): Direction? = when {
            status == ChargeState.CHARGING -> Direction.CHARGING
            !pluggedIn -> Direction.DISCHARGING
            else -> null
        }
    }
}

/** Estimated charge cycles for phones that do not report a cycle count (Android 13 and older). */
object CycleLogic {
    /** Percent charged between two readings: only rises count. */
    fun chargedDelta(previousLevel: Int?, level: Int): Int =
        if (previousLevel != null && previousLevel in 0..100 && level > previousLevel) level - previousLevel else 0
}
