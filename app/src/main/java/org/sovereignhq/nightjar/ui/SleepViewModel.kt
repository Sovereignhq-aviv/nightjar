package org.sovereignhq.nightjar.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.sovereignhq.nightjar.BuildConfig
import org.sovereignhq.nightjar.update.UpdateChecker
import java.io.File
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import org.sovereignhq.nightjar.data.EventKind
import org.sovereignhq.nightjar.data.SessionStats
import org.sovereignhq.nightjar.data.SessionStore
import org.sovereignhq.nightjar.data.Settings
import org.sovereignhq.nightjar.data.SettingsSnapshot
import org.sovereignhq.nightjar.data.SleepSession
import org.sovereignhq.nightjar.data.SoundClip
import org.sovereignhq.nightjar.sleep.SleepClassifier

/** How the recordings list is ordered. */
enum class ClipSort { TIME, LOUDEST }

/** Where the app has got to in checking for, fetching and handing over a new version. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: UpdateChecker.Release) : UpdateState
    data class Downloading(val progress: Float) : UpdateState
    data class Ready(val file: File, val version: String) : UpdateState
    data class Failed(val message: String) : UpdateState
}

class SleepViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SessionStore(app)
    private val settingsStore = Settings(app)

    val player = ClipPlayer(app) { clip -> store.clipFile(clip.fileName) }

    var sessions by mutableStateOf<List<SleepSession>>(emptyList())
        private set

    /**
     * One immutable settings object rather than a field per preference. An earlier version had a
     * `windowMinutes` property beside a `setWindowMinutes()` helper, and the two compiled to the
     * same JVM signature.
     */
    var settings by mutableStateOf(settingsStore.snapshot())
        private set

    /** Which night the Sounds and Sleep screens are showing. Null means the most recent. */
    var selectedSessionId by mutableStateOf<String?>(null)
        private set

    var sort by mutableStateOf(ClipSort.TIME)
        private set
    var kindFilter by mutableStateOf<EventKind?>(null)
        private set
    var starredOnly by mutableStateOf(false)
        private set

    var updateState by mutableStateOf<UpdateState>(UpdateState.Idle)
        private set

    val installedVersion: String get() = BuildConfig.VERSION_NAME

    init {
        refresh()
    }

    // ---- self update ----

    /**
     * Called when the app opens. Silent by design: it says nothing when there is no update and
     * nothing when the network is unavailable, because neither is news.
     */
    fun maybeAutoCheck() {
        if (!settings.autoUpdateCheck) return
        val elapsed = System.currentTimeMillis() - settingsStore.lastUpdateCheckMs
        if (elapsed < AUTO_CHECK_INTERVAL_MS) return
        checkForUpdate(announceUpToDate = false)
    }

    fun checkForUpdate(announceUpToDate: Boolean = true) {
        if (updateState is UpdateState.Checking || updateState is UpdateState.Downloading) return
        updateState = UpdateState.Checking
        viewModelScope.launch {
            settingsStore.lastUpdateCheckMs = System.currentTimeMillis()
            val release = UpdateChecker.findUpdate()
            updateState = when {
                release != null -> UpdateState.Available(release)
                announceUpToDate -> UpdateState.UpToDate
                else -> UpdateState.Idle
            }
        }
    }

    fun downloadUpdate() {
        val available = updateState as? UpdateState.Available ?: return
        updateState = UpdateState.Downloading(0f)
        viewModelScope.launch {
            val file = UpdateChecker.download(getApplication(), available.release) { progress ->
                // Only overwrite while still downloading, so a cancel or failure is not undone by a
                // late progress callback.
                if (updateState is UpdateState.Downloading) {
                    updateState = UpdateState.Downloading(progress)
                }
            }
            updateState = if (file == null) {
                UpdateState.Failed("The download did not finish. Check the connection and retry.")
            } else {
                UpdateState.Ready(file, available.release.version)
            }
        }
    }

    /** The system installer intent, or null if the file went missing between download and tap. */
    fun installIntent(): Intent? {
        val ready = updateState as? UpdateState.Ready ?: return null
        if (!ready.file.exists()) {
            updateState = UpdateState.Failed("The downloaded file is gone. Try downloading again.")
            return null
        }
        return runCatching { UpdateChecker.installIntent(getApplication(), ready.file) }.getOrNull()
    }

    fun dismissUpdate() {
        updateState = UpdateState.Idle
    }

    fun refresh() {
        sessions = store.loadAll()
        if (selectedSessionId != null && sessions.none { it.id == selectedSessionId }) {
            selectedSessionId = null
        }
    }

    // ---- nights ----

    val selectedSession: SleepSession?
        get() = selectedSessionId?.let { id -> sessions.firstOrNull { it.id == id } }
            ?: sessions.firstOrNull()

    fun selectSession(id: String?) {
        selectedSessionId = id
        player.stop()
    }

    fun session(id: String): SleepSession? =
        sessions.firstOrNull { it.id == id } ?: store.load(id)

    fun stats(session: SleepSession): SessionStats =
        SleepClassifier.stats(session, settings.sleepGoalMinutes)

    // ---- the recordings library ----

    /** The visible list, after filtering and sorting. */
    fun visibleClips(session: SleepSession?): List<SoundClip> {
        val all = session?.clips ?: return emptyList()
        return all
            .filter { kindFilter == null || it.eventKind == kindFilter }
            .filter { !starredOnly || it.starred }
            .let { list ->
                when (sort) {
                    ClipSort.TIME -> list.sortedBy { it.startedAtMs }
                    ClipSort.LOUDEST -> list.sortedByDescending { it.peakDb }
                }
            }
    }

    fun countsByKind(session: SleepSession?): Map<EventKind, Int> =
        session?.clips?.groupingBy { it.eventKind }?.eachCount() ?: emptyMap()

    // Named for what they do rather than as setX(), which would collide with the JVM setters
    // Compose generates for the properties above.
    fun chooseSort(value: ClipSort) { sort = value }

    /** Tapping the active filter clears it. */
    fun toggleKindFilter(value: EventKind) {
        kindFilter = if (kindFilter == value) null else value
    }

    fun toggleStarredOnly() { starredOnly = !starredOnly }

    fun playClip(clip: SoundClip, queue: List<SoundClip>) = player.toggle(clip, queue)

    /** The night's loudest moments, back to back. */
    fun playHighlights(session: SleepSession, limit: Int = 12) {
        val reel = session.loudestClips(limit).sortedBy { it.startedAtMs }
        if (reel.isEmpty()) return
        player.play(reel.first(), reel)
    }

    /** A correction. Also the only way a personalised model will ever get labelled examples. */
    fun correctLabel(session: SleepSession, clip: SoundClip, kind: EventKind) {
        replace(store.setUserLabel(session, clip.fileName, kind.name))
    }

    /**
     * Stops recording a specific sound the model can name. The most useful thing you can do with a
     * loose sensitivity setting: one tap on "Air conditioning" and the list stops filling with it,
     * without making the app deafer to everything else.
     */
    fun muteDetail(label: String) {
        if (label.isBlank()) return
        updateSettings { copy(mutedLabels = mutedLabels + label) }
    }

    fun unmuteDetail(label: String) {
        updateSettings { copy(mutedLabels = mutedLabels - label) }
    }

    /** How many corrections exist across every night - the size of the training set so far. */
    fun correctionCount(): Int = sessions.sumOf { night -> night.clips.count { it.wasCorrected } }

    fun toggleStar(session: SleepSession, clip: SoundClip) {
        val updated = store.setStarred(session, clip.fileName, !clip.starred)
        replace(updated)
    }

    fun deleteClip(session: SleepSession, clip: SoundClip) {
        if (player.current?.fileName == clip.fileName) player.stop()
        replace(store.deleteClip(session, clip.fileName))
    }

    /**
     * A share sheet for one recording. Goes through FileProvider because the clips live in the
     * app's private storage, which other apps cannot read directly.
     */
    fun shareIntent(clip: SoundClip): Intent? {
        val context = getApplication<Application>()
        val file = store.clipFile(clip.fileName)
        if (!file.exists()) return null
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.clips", file)
        }.getOrNull() ?: return null

        return Intent(Intent.ACTION_SEND).apply {
            type = "audio/wav"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---- editing a night ----

    fun toggleTag(session: SleepSession, tag: String) {
        val tags = if (tag in session.tags) session.tags - tag else session.tags + tag
        persist(session.copy(tags = tags))
    }

    fun setNote(session: SleepSession, note: String) = persist(session.copy(note = note))

    fun setRating(session: SleepSession, stars: Int) =
        persist(session.copy(ratingStars = if (session.ratingStars == stars) 0 else stars))

    fun deleteSession(session: SleepSession) {
        player.stop()
        store.delete(session)
        selectedSessionId = null
        refresh()
    }

    private fun persist(updated: SleepSession) {
        store.save(updated)
        replace(updated)
    }

    private fun replace(updated: SleepSession) {
        sessions = sessions.map { if (it.id == updated.id) updated else it }
    }

    // ---- settings ----

    fun updateSettings(transform: SettingsSnapshot.() -> SettingsSnapshot) {
        val next = settings.transform()
        next.writeTo(settingsStore)
        settings = next
    }

    fun clipBytes(): Long = store.clipBytesOnDisk()

    fun cleanUpNow() {
        store.pruneAudio(settings.clipRetentionDays)
        store.pruneSessions(settings.nightRetentionDays)
        refresh()
    }

    private companion object {
        /** Four times a day is plenty for an app opened twice. */
        const val AUTO_CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
