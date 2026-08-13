package org.sovereignhq.sleepwave.ui

import android.app.Application
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.sovereignhq.sleepwave.data.SessionStats
import org.sovereignhq.sleepwave.data.SessionStore
import org.sovereignhq.sleepwave.data.Settings
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.sleep.SleepClassifier

/**
 * Holds what the screens need and keeps SharedPreferences in sync.
 *
 * Settings are mirrored into Compose state rather than read straight from preferences, because
 * SharedPreferences does not tell Compose when a value changes. Each setter writes through to
 * disk immediately, so a night that starts after the app is killed still uses the right values.
 */
class SleepViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    val settings = Settings(app)

    var sessions by mutableStateOf<List<SleepSession>>(emptyList())
        private set

    var alarmHour by mutableIntStateOf(settings.alarmHour)
        private set
    var alarmMinute by mutableIntStateOf(settings.alarmMinute)
        private set
    var alarmEnabled by mutableStateOf(settings.alarmEnabled)
        private set
    var windowMinutes by mutableIntStateOf(settings.windowMinutes)
        private set
    var snoozeMinutes by mutableIntStateOf(settings.snoozeMinutes)
        private set
    var sleepGoalMinutes by mutableIntStateOf(settings.sleepGoalMinutes)
        private set
    var recordSnoring by mutableStateOf(settings.recordSnoring)
        private set
    var motionSensing by mutableStateOf(settings.motionSensing)
        private set
    var vibrate by mutableStateOf(settings.vibrate)
        private set
    var rampSeconds by mutableIntStateOf(settings.rampSeconds)
        private set
    var autoDeleteDays by mutableIntStateOf(settings.autoDeleteDays)
        private set
    var alarmSoundUri by mutableStateOf(settings.alarmSoundUri)
        private set

    var playingClip by mutableStateOf<String?>(null)
        private set

    private var player: MediaPlayer? = null

    init {
        refresh()
    }

    fun refresh() {
        sessions = store.loadAll()
    }

    fun session(id: String): SleepSession? = sessions.firstOrNull { it.id == id } ?: store.load(id)

    fun stats(session: SleepSession): SessionStats =
        SleepClassifier.stats(session, sleepGoalMinutes)

    fun clipPath(fileName: String): String = store.clipFile(fileName).absolutePath

    fun clipBytes(): Long = store.clipBytesOnDisk()

    // ---- editing a night ----

    fun toggleTag(session: SleepSession, tag: String) {
        val tags = if (tag in session.tags) session.tags - tag else session.tags + tag
        persist(session.copy(tags = tags))
    }

    fun setNote(session: SleepSession, note: String) = persist(session.copy(note = note))

    fun setRating(session: SleepSession, stars: Int) =
        persist(session.copy(ratingStars = if (session.ratingStars == stars) 0 else stars))

    fun delete(session: SleepSession) {
        stopClip()
        store.delete(session)
        refresh()
    }

    private fun persist(updated: SleepSession) {
        store.save(updated)
        sessions = sessions.map { if (it.id == updated.id) updated else it }
    }

    // ---- settings ----

    fun setAlarmTime(hour: Int, minute: Int) {
        alarmHour = hour; settings.alarmHour = hour
        alarmMinute = minute; settings.alarmMinute = minute
    }

    fun setAlarmEnabled(v: Boolean) { alarmEnabled = v; settings.alarmEnabled = v }
    fun setWindowMinutes(v: Int) { windowMinutes = v; settings.windowMinutes = v }
    fun setSnoozeMinutes(v: Int) { snoozeMinutes = v; settings.snoozeMinutes = v }
    fun setSleepGoalMinutes(v: Int) { sleepGoalMinutes = v; settings.sleepGoalMinutes = v }
    fun setRecordSnoring(v: Boolean) { recordSnoring = v; settings.recordSnoring = v }
    fun setMotionSensing(v: Boolean) { motionSensing = v; settings.motionSensing = v }
    fun setVibrate(v: Boolean) { vibrate = v; settings.vibrate = v }
    fun setRampSeconds(v: Int) { rampSeconds = v; settings.rampSeconds = v }
    fun setAutoDeleteDays(v: Int) { autoDeleteDays = v; settings.autoDeleteDays = v }
    fun setAlarmSoundUri(v: String) { alarmSoundUri = v; settings.alarmSoundUri = v }

    fun pruneNow() {
        store.pruneOlderThan(autoDeleteDays)
        refresh()
    }

    // ---- clip playback ----

    fun playClip(fileName: String) {
        if (playingClip == fileName) {
            stopClip()
            return
        }
        stopClip()
        player = try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(clipPath(fileName))
                setOnCompletionListener { stopClip() }
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e("SleepViewModel", "Could not play $fileName", e)
            null
        }
        playingClip = if (player != null) fileName else null
    }

    fun stopClip() {
        player?.let {
            runCatching { if (it.isPlaying) it.stop() }
            runCatching { it.release() }
        }
        player = null
        playingClip = null
    }

    override fun onCleared() {
        stopClip()
        super.onCleared()
    }
}
