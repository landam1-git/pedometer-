package com.example.pedometer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.time.LocalDate

class StepAccumulatorTest {

    private val day1 = LocalDate.of(2026, 11, 9)
    private val day2 = day1.plusDays(1)

    @Test
    fun firstReadingOnlySetsBaseline() {
        val result = StepAccumulator.apply(StepData(), raw = 5000, today = day1)
        assertEquals(5000L, result.lastRaw)
        assertEquals(0, result.stepsOn(day1))
    }

    @Test
    fun subsequentReadingsAddDelta() {
        var data = StepAccumulator.apply(StepData(), 5000, day1)
        data = StepAccumulator.apply(data, 5100, day1)
        data = StepAccumulator.apply(data, 5150, day1)
        assertEquals(150, data.stepsOn(day1))
        assertEquals(5150L, data.lastRaw)
    }

    @Test
    fun newDayStartsFromZero() {
        var data = StepAccumulator.apply(StepData(), 1000, day1)
        data = StepAccumulator.apply(data, 1300, day1)
        data = StepAccumulator.apply(data, 1350, day2)
        assertEquals(300, data.stepsOn(day1))
        assertEquals(50, data.stepsOn(day2))
    }

    @Test
    fun rebootResetIsHandled() {
        var data = StepAccumulator.apply(StepData(), 8000, day1)
        data = StepAccumulator.apply(data, 8200, day1)
        // Device rebooted: counter restarts from 0, now reads 40.
        data = StepAccumulator.apply(data, 40, day1)
        assertEquals(240, data.stepsOn(day1))
        assertEquals(40L, data.lastRaw)
    }

    @Test
    fun oldHistoryIsPruned() {
        val old = day1.minusDays(StepAccumulator.MAX_HISTORY_DAYS + 5)
        val data = StepData(lastRaw = 0, history = mapOf(old to 123))
        val result = StepAccumulator.apply(data, 10, day1)
        assertFalse(result.history.containsKey(old))
        assertEquals(10, result.stepsOn(day1))
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
