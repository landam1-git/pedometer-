package com.example.pedometer.data

import java.time.LocalDate

/**
 * Snapshot of everything we persist about steps.
 *
 * Android's TYPE_STEP_COUNTER reports the total steps since the last reboot, so we
 * remember the previous reading and add the difference to today's total.
 *
 * @param lastRaw last value reported by the step counter, or -1 if none seen yet.
 * @param lastElapsedMs milliseconds since boot when [lastRaw] was read.
 * @param bootCount the device boot count when [lastRaw] was read, or -1 if unknown.
 * @param history steps per calendar day.
 */
data class StepData(
    val lastRaw: Long = -1L,
    val lastElapsedMs: Long = 0L,
    val bootCount: Int = UNKNOWN_BOOT_COUNT,
    val history: Map<LocalDate, Int> = emptyMap(),
) {
    fun stepsOn(day: LocalDate): Int = history[day] ?: 0

    companion object {
        const val UNKNOWN_BOOT_COUNT = -1
    }
}

/**
 * One reading from the step counter sensor.
 *
 * @param raw total steps since boot, as reported by the sensor.
 * @param elapsedMs milliseconds since boot when it was read.
 * @param bootCount the device boot count, or [StepData.UNKNOWN_BOOT_COUNT].
 */
data class SensorReading(
    val raw: Long,
    val elapsedMs: Long,
    val bootCount: Int = StepData.UNKNOWN_BOOT_COUNT,
)

/** Pure logic for turning raw step-counter readings into per-day totals. */
object StepAccumulator {

    /** How many days of history to keep. */
    const val MAX_HISTORY_DAYS = 90L

    /** Faster than a sprinter's cadence; anything above this is a sensor glitch. */
    const val MAX_STEPS_PER_SECOND = 5

    /** Extra allowance on top of the per-second limit, for sensor batching. */
    const val STEP_SLACK = 100

    /** Days above this are treated as corrupted data from older app versions. */
    const val MAX_PLAUSIBLE_STEPS_PER_DAY = 100_000

    /**
     * Applies a new step-counter reading.
     *
     * - The first reading ever only establishes a baseline.
     * - After a reboot the counter restarts from 0, so the whole reading counts as new steps.
     * - A reading lower than the previous one without a reboot is a glitch and is ignored.
     * - A jump of more steps than anyone could walk in the elapsed time (for example
     *   when a device first reports 0 and then its real total) is not counted; the
     *   reading just becomes the new baseline.
     * - New steps are credited to [today].
     */
    fun apply(data: StepData, reading: SensorReading, today: LocalDate): StepData {
        if (reading.raw < 0) return data

        val baseline = data.copy(
            lastRaw = reading.raw,
            lastElapsedMs = reading.elapsedMs,
            bootCount = reading.bootCount,
        )
        if (data.lastRaw < 0) return baseline

        val bootCountKnown = reading.bootCount != StepData.UNKNOWN_BOOT_COUNT &&
            data.bootCount != StepData.UNKNOWN_BOOT_COUNT
        val rebooted = (bootCountKnown && reading.bootCount != data.bootCount) ||
            reading.elapsedMs < data.lastElapsedMs ||
            (!bootCountKnown && reading.raw < data.lastRaw)

        val delta: Long
        val windowMs: Long
        if (rebooted) {
            delta = reading.raw
            windowMs = reading.elapsedMs
        } else {
            if (reading.raw < data.lastRaw) return data
            delta = reading.raw - data.lastRaw
            windowMs = reading.elapsedMs - data.lastElapsedMs
        }

        if (delta > maxPlausibleSteps(windowMs)) return baseline
        if (delta == 0L) return baseline

        val updated = data.history + (today to (data.stepsOn(today) + delta.toInt()))
        val cutoff = today.minusDays(MAX_HISTORY_DAYS)
        return baseline.copy(history = updated.filterKeys { it.isAfter(cutoff) })
    }

    fun maxPlausibleSteps(windowMs: Long): Long =
        windowMs.coerceAtLeast(0) / 1000 * MAX_STEPS_PER_SECOND + STEP_SLACK

    /** Drops days with impossible step counts left behind by older app versions. */
    fun removeImplausibleDays(history: Map<LocalDate, Int>): Map<LocalDate, Int> =
        history.filterValues { it in 0..MAX_PLAUSIBLE_STEPS_PER_DAY }

    /** Serializes history as "2026-11-09=1234;2026-11-08=5678". */
    fun encodeHistory(history: Map<LocalDate, Int>): String =
        history.entries
            .sortedBy { it.key }
            .joinToString(";") { "${it.key}=${it.value}" }

    fun decodeHistory(encoded: String?): Map<LocalDate, Int> {
        if (encoded.isNullOrBlank()) return emptyMap()
        return encoded.split(";").mapNotNull { entry ->
            val parts = entry.split("=")
            if (parts.size != 2) return@mapNotNull null
            val date = runCatching { LocalDate.parse(parts[0]) }.getOrNull()
            val steps = parts[1].toIntOrNull()
            if (date != null && steps != null) date to steps else null
        }.toMap()
    }
}
