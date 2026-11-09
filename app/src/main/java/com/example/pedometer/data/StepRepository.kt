package com.example.pedometer.data

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class UiStepState(
    val data: StepData,
    val dailyGoal: Int,
    val strideLengthCm: Int,
    val today: LocalDate = LocalDate.now(),
) {
    val todaySteps: Int get() = data.stepsOn(today)
    val distanceKm: Double get() = todaySteps * strideLengthCm / 100_000.0
    /** Rough estimate: ~0.04 kcal per step for an average adult. */
    val calories: Int get() = (todaySteps * 0.04).toInt()
    val goalProgress: Float get() = if (dailyGoal <= 0) 0f else todaySteps.toFloat() / dailyGoal

    /** Steps for the last [days] days, oldest first, ending today. */
    fun lastDays(days: Int): List<Pair<LocalDate, Int>> =
        (days - 1 downTo 0).map { offset ->
            val day = today.minusDays(offset.toLong())
            day to data.stepsOn(day)
        }
}

/** Process-wide store for step data, backed by SharedPreferences. */
class StepRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("steps", Context.MODE_PRIVATE)

    /**
     * The last raw sensor reading only makes sense on this device and this boot, so it is
     * kept in its own file, which is excluded from backups (see res/xml/backup_rules.xml).
     */
    private val sensorPrefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(SENSOR_PREFS, Context.MODE_PRIVATE)

    /** The boot count cannot change while this process is alive. */
    private val bootCount: Int = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, StepData.UNKNOWN_BOOT_COUNT)
    }.getOrDefault(StepData.UNKNOWN_BOOT_COUNT)

    private val _state = MutableStateFlow(migrate().let { load() })
    val state: StateFlow<UiStepState> = _state.asStateFlow()

    @Synchronized
    fun onSensorReading(raw: Long) {
        val current = _state.value
        val reading = SensorReading(raw = raw, elapsedMs = SystemClock.elapsedRealtime(), bootCount = bootCount)
        val updated = StepAccumulator.apply(current.data, reading, LocalDate.now())
        if (updated == current.data) return
        sensorPrefs.edit()
            .putLong(KEY_LAST_RAW, updated.lastRaw)
            .putLong(KEY_LAST_ELAPSED, updated.lastElapsedMs)
            .putInt(KEY_BOOT_COUNT, updated.bootCount)
            .apply()
        if (updated.history != current.data.history) {
            prefs.edit().putString(KEY_HISTORY, StepAccumulator.encodeHistory(updated.history)).apply()
        }
        _state.value = current.copy(data = updated, today = LocalDate.now())
    }

    @Synchronized
    fun setDailyGoal(goal: Int) {
        prefs.edit().putInt(KEY_GOAL, goal).apply()
        _state.value = _state.value.copy(dailyGoal = goal)
    }

    @Synchronized
    fun setStrideLength(cm: Int) {
        prefs.edit().putInt(KEY_STRIDE, cm).apply()
        _state.value = _state.value.copy(strideLengthCm = cm)
    }

    /** Re-emits state so date-dependent values (e.g. "today") refresh after midnight. */
    fun refresh() {
        _state.value = _state.value.copy(today = LocalDate.now())
    }

    /**
     * Version 1 stored the last raw reading in the backed-up file and could record
     * impossible step counts after a bogus sensor reading; clean both up once.
     */
    private fun migrate() {
        if (prefs.getInt(KEY_DATA_VERSION, 1) >= DATA_VERSION) return
        val history = StepAccumulator.decodeHistory(prefs.getString(KEY_HISTORY, null))
        val cleaned = StepAccumulator.removeImplausibleDays(history)
        prefs.edit()
            .remove(KEY_LAST_RAW)
            .putString(KEY_HISTORY, StepAccumulator.encodeHistory(cleaned))
            .putInt(KEY_DATA_VERSION, DATA_VERSION)
            .commit()
    }

    private fun load() = UiStepState(
        data = StepData(
            lastRaw = sensorPrefs.getLong(KEY_LAST_RAW, -1L),
            lastElapsedMs = sensorPrefs.getLong(KEY_LAST_ELAPSED, 0L),
            bootCount = sensorPrefs.getInt(KEY_BOOT_COUNT, StepData.UNKNOWN_BOOT_COUNT),
            history = StepAccumulator.decodeHistory(prefs.getString(KEY_HISTORY, null)),
        ),
        dailyGoal = prefs.getInt(KEY_GOAL, DEFAULT_GOAL),
        strideLengthCm = prefs.getInt(KEY_STRIDE, DEFAULT_STRIDE_CM),
    )

    companion object {
        const val DEFAULT_GOAL = 10_000
        const val DEFAULT_STRIDE_CM = 76

        private const val SENSOR_PREFS = "sensor_state"
        private const val DATA_VERSION = 2

        private const val KEY_DATA_VERSION = "data_version"
        private const val KEY_LAST_RAW = "last_raw"
        private const val KEY_LAST_ELAPSED = "last_elapsed_ms"
        private const val KEY_BOOT_COUNT = "boot_count"
        private const val KEY_HISTORY = "history"
        private const val KEY_GOAL = "goal"
        private const val KEY_STRIDE = "stride_cm"

        @Volatile
        private var instance: StepRepository? = null

        fun get(context: Context): StepRepository =
            instance ?: synchronized(this) {
                instance ?: StepRepository(context).also { instance = it }
            }
    }
}
