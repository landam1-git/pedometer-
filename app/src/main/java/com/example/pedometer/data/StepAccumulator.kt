package com.example.pedometer.data

import java.time.LocalDate

/**
 * Snapshot of everything we persist about steps.
 *
 * @param lastRaw last value reported by the hardware step counter, or -1 if none seen yet.
 *   Android's TYPE_STEP_COUNTER reports the total steps since the last reboot, so we
 *   track the previous reading and add the difference to today's total.
 * @param history steps per calendar day.
 */
data class StepData(
    val lastRaw: Long = -1L,
    val history: Map<LocalDate, Int> = emptyMap(),
) {
    fun stepsOn(day: LocalDate): Int = history[day] ?: 0
}

/** Pure logic for turning raw step-counter readings into per-day totals. */
object StepAccumulator {

    /** How many days of history to keep. */
    const val MAX_HISTORY_DAYS = 90L

    /**
     * Applies a new raw step-counter reading.
     *
     * - The first reading ever only establishes a baseline.
     * - If the reading is lower than the previous one the device rebooted
     *   (the counter restarts at 0), so the whole reading counts as new steps.
     * - New steps are credited to [today].
     */
    fun apply(data: StepData, raw: Long, today: LocalDate): StepData {
        if (raw < 0) return data
        if (data.lastRaw < 0) return data.copy(lastRaw = raw)

        val delta = if (raw >= data.lastRaw) raw - data.lastRaw else raw
        if (delta == 0L) return data

        val updated = data.history + (today to (data.stepsOn(today) + delta.toInt()))
        val cutoff = today.minusDays(MAX_HISTORY_DAYS)
        return StepData(lastRaw = raw, history = updated.filterKeys { it.isAfter(cutoff) })
    }

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
