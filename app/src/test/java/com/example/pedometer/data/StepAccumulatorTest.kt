package com.example.pedometer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class StepAccumulatorTest {

    private val day1 = LocalDate.of(2026, 11, 9)
    private val day2 = day1.plusDays(1)

    private val minute = 60_000L
    private val hour = 60 * minute

    private fun StepData.read(raw: Long, elapsedMs: Long, boot: Int = 1, day: LocalDate = day1) =
        StepAccumulator.apply(this, SensorReading(raw, elapsedMs, boot), day)

    @Test
    fun firstReadingOnlySetsBaseline() {
        val result = StepData().read(5000, hour)
        assertEquals(5000L, result.lastRaw)
        assertEquals(0, result.stepsOn(day1))
    }

    @Test
    fun subsequentReadingsAddDelta() {
        val data = StepData()
            .read(5000, hour)
            .read(5100, hour + minute)
            .read(5150, hour + 2 * minute)
        assertEquals(150, data.stepsOn(day1))
        assertEquals(5150L, data.lastRaw)
    }

    @Test
    fun newDayStartsFromZero() {
        val data = StepData()
            .read(1000, hour)
            .read(1300, 2 * hour)
            .read(1350, 3 * hour, day = day2)
        assertEquals(300, data.stepsOn(day1))
        assertEquals(50, data.stepsOn(day2))
    }

    @Test
    fun bogusZeroFollowedByRealTotalIsNotCounted() {
        // Some phones report 0 when the listener registers, then the real total since boot.
        val data = StepData()
            .read(0, 30 * 24 * hour)
            .read(318_000, 30 * 24 * hour + 2_000)
            .read(318_040, 30 * 24 * hour + 30_000)
        assertEquals(40, data.stepsOn(day1))
        assertEquals(318_040L, data.lastRaw)
    }

    @Test
    fun implausibleJumpIsDiscardedAndBecomesBaseline() {
        val data = StepData()
            .read(1000, hour)
            .read(50_000, hour + minute) // 49,000 steps in a minute: impossible
            .read(50_100, hour + 2 * minute)
        assertEquals(100, data.stepsOn(day1))
    }

    @Test
    fun stepsWhileServiceWasStoppedAreStillCounted() {
        // The hardware keeps counting; 6,000 steps over 3 hours is plausible.
        val data = StepData()
            .read(1000, hour)
            .read(7000, 4 * hour)
        assertEquals(6000, data.stepsOn(day1))
    }

    @Test
    fun rebootDetectedByBootCount() {
        val data = StepData()
            .read(8000, 10 * hour, boot = 1)
            .read(8200, 11 * hour, boot = 1)
            // Rebooted and already walked more than before.
            .read(9000, 3 * hour, boot = 2)
        assertEquals(200 + 9000, data.stepsOn(day1))
    }

    @Test
    fun rebootDetectedByUptimeGoingBackwards() {
        val unknown = StepData.UNKNOWN_BOOT_COUNT
        val data = StepData()
            .read(8000, 10 * hour, boot = unknown)
            .read(8200, 11 * hour, boot = unknown)
            .read(40, 5 * minute, boot = unknown)
        assertEquals(240, data.stepsOn(day1))
        assertEquals(40L, data.lastRaw)
    }

    @Test
    fun rebootWithImplausibleCountIsDiscarded() {
        val data = StepData()
            .read(8000, 10 * hour, boot = 1)
            .read(100_000, minute, boot = 2) // 100k steps one minute after boot
        assertEquals(0, data.stepsOn(day1))
        assertEquals(100_000L, data.lastRaw)
    }

    @Test
    fun lowerReadingWithoutRebootIsIgnored() {
        val data = StepData()
            .read(5000, hour)
            .read(0, hour + minute) // glitch
            .read(5100, hour + 2 * minute)
        assertEquals(100, data.stepsOn(day1))
        assertEquals(5100L, data.lastRaw)
    }

    @Test
    fun oldHistoryIsPruned() {
        val old = day1.minusDays(StepAccumulator.MAX_HISTORY_DAYS + 5)
        val data = StepData(lastRaw = 0, lastElapsedMs = hour, bootCount = 1, history = mapOf(old to 123))
        val result = data.read(10, 2 * hour)
        assertFalse(result.history.containsKey(old))
        assertEquals(10, result.stepsOn(day1))
    }

    @Test
    fun implausibleDaysAreRemoved() {
        val cleaned = StepAccumulator.removeImplausibleDays(mapOf(day1 to 318_000, day2 to 8_000))
        assertEquals(mapOf(day2 to 8_000), cleaned)
    }

    @Test
    fun historyRoundTrips() {
        val history = mapOf(day1 to 1234, day2 to 5678)
        val encoded = StepAccumulator.encodeHistory(history)
        assertEquals(history, StepAccumulator.decodeHistory(encoded))
    }

    @Test
    fun decodeIgnoresGarbage() {
        val decoded = StepAccumulator.decodeHistory("2026-11-09=10;nonsense;2026-13-40=5;2026-11-10=x")
        assertEquals(mapOf(day1 to 10), decoded)
    }
}
