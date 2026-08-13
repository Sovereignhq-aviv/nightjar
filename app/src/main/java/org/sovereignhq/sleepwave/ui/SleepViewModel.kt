package org.sovereignhq.sleepwave.ui

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import org.sovereignhq.sleepwave.data.EventKind
import org.sovereignhq.sleepwave.data.SessionStats
import org.sovereignhq.sleepwave.data.SessionStore
import org.sovereignhq.sleepwave.data.Settings
import org.sovereignhq.sleepwave.data.SettingsSnapshot
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.SoundClip
import org.sovereignhq.sleepwave.sleep.SleepClassifier

/** How the recordings list is ordered. */
enum class ClipSort { TIME, LOUDEST }

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

    init {
        refresh()
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

    fun setSort(value: ClipSort) { sort = value }

    fun setKindFilter(value: EventKind?) {
        kindFilter = if (kindFilter == value) null else value
    }

    fun setStarredOnly(value: Boolean) { starredOnly = value }

    fun playClip(clip: SoundClip, queue: List<SoundClip>) = player.toggle(clip, queue)

    /** The night's loudest moments, back to back. */
    fun playHighlights(session: SleepSession, limit: Int = 12) {
        val reel = session.loudestClips(limit).sortedBy { it.startedAtMs }
        if (reel.isEmpty()) return
        player.play(reel.first(), reel)
    }

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

    override fun onCleared() {
        player.stop()
        super.onCleared()
    }
}
