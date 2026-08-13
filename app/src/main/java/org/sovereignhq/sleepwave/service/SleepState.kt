package org.sovereignhq.sleepwave.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state between the tracking service and the UI.
 *
 * The service and the screens live in the same process, so a plain observable singleton beats
 * binding to the service: no connection lifecycle to get wrong, and the UI reads correct values
 * the instant it comes back to the foreground.
 */
object SleepState {

    private val _tracking = MutableStateFlow(false)
    val tracking: StateFlow<Boolean> = _tracking.asStateFlow()

    private val _startedAtMs = MutableStateFlow(0L)
    val startedAtMs: StateFlow<Long> = _startedAtMs.asStateFlow()

    private val _alarmTargetMs = MutableStateFlow(0L)
    val alarmTargetMs: StateFlow<Long> = _alarmTargetMs.asStateFlow()

    /** Per-minute activity so far tonight, for the live graph. */
    private val _liveActivity = MutableStateFlow<List<Float>>(emptyList())
    val liveActivity: StateFlow<List<Float>> = _liveActivity.asStateFlow()

    /** Instantaneous mic level, 0..1, for the breathing dot on the night screen. */
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    private val _snoreMinutes = MutableStateFlow(0)
    val snoreMinutes: StateFlow<Int> = _snoreMinutes.asStateFlow()

    private val _clipCount = MutableStateFlow(0)
    val clipCount: StateFlow<Int> = _clipCount.asStateFlow()

    /** Every classified noise, including those that did not earn a recording. */
    private val _eventCount = MutableStateFlow(0)
    val eventCount: StateFlow<Int> = _eventCount.asStateFlow()

    private val _alarmRinging = MutableStateFlow(false)
    val alarmRinging: StateFlow<Boolean> = _alarmRinging.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when a night finishes, so the UI knows to open the morning report. */
    private val _finishedSessionId = MutableStateFlow<String?>(null)
    val finishedSessionId: StateFlow<String?> = _finishedSessionId.asStateFlow()

    fun beginNight(startedAtMs: Long, alarmTargetMs: Long) {
        _tracking.value = true
        _startedAtMs.value = startedAtMs
        _alarmTargetMs.value = alarmTargetMs
        _liveActivity.value = emptyList()
        _snoreMinutes.value = 0
        _clipCount.value = 0
        _eventCount.value = 0
        _level.value = 0f
        _error.value = null
        _finishedSessionId.value = null
    }

    fun endNight(sessionId: String?) {
        _tracking.value = false
        _level.value = 0f
        if (sessionId != null) _finishedSessionId.value = sessionId
    }

    fun appendMinute(activity: Float) {
        _liveActivity.value = _liveActivity.value + activity
    }

    fun setLevel(v: Float) { _level.value = v }
    fun setSnoreMinutes(v: Int) { _snoreMinutes.value = v }
    fun setClipCount(v: Int) { _clipCount.value = v }
    fun setEventCount(v: Int) { _eventCount.value = v }
    fun setAlarmRinging(v: Boolean) { _alarmRinging.value = v }
    fun setError(message: String?) { _error.value = message }
    fun consumeFinishedSession() { _finishedSessionId.value = null }
}
