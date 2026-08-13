package org.sovereignhq.sleepwave.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Sessions live as one JSON file each in filesDir/sessions, audio clips in filesDir/clips.
 * One file per night is a few hundred KB at most, so there is no database here on purpose:
 * fewer moving parts, and the whole history can be copied off the phone with a file manager.
 *
 * Retention is two-tier and deliberately lopsided. Audio is what fills a phone, so it is deleted
 * within days unless starred. The graphs and scores are tiny and Trends is worthless without
 * history, so nights are kept for about a year.
 */
class SessionStore(context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val sessionsDir = File(context.filesDir, "sessions").apply { mkdirs() }
    val clipsDir: File = File(context.filesDir, "clips").apply { mkdirs() }

    fun save(session: SleepSession) {
        try {
            File(sessionsDir, "${session.id}.json")
                .writeText(json.encodeToString(SleepSession.serializer(), session))
        } catch (e: Exception) {
            Log.e(TAG, "Could not save session ${session.id}", e)
        }
    }

    fun load(id: String): SleepSession? = readFile(File(sessionsDir, "$id.json"))

    /** Newest night first. */
    fun loadAll(): List<SleepSession> =
        (sessionsDir.listFiles { f -> f.extension == "json" } ?: emptyArray())
            .mapNotNull { readFile(it) }
            .sortedByDescending { it.startedAtMs }

    fun latest(): SleepSession? = loadAll().firstOrNull()

    fun clipFile(name: String): File = File(clipsDir, name)

    fun delete(session: SleepSession) {
        session.clips.forEach { runCatching { clipFile(it.fileName).delete() } }
        runCatching { File(sessionsDir, "${session.id}.json").delete() }
    }

    /** Returns the updated session so callers can refresh without a full reload. */
    fun setStarred(session: SleepSession, fileName: String, starred: Boolean): SleepSession {
        val updated = session.copy(
            clips = session.clips.map { if (it.fileName == fileName) it.copy(starred = starred) else it }
        )
        save(updated)
        return updated
    }

    /**
     * Records a human correction, and stars the clip in the same move. Starring is not a courtesy:
     * corrected clips are the training data for a personalised model, and unstarred audio is deleted
     * within days, so a correction that did not survive would be worthless.
     */
    fun setUserLabel(session: SleepSession, fileName: String, label: String): SleepSession {
        val updated = session.copy(
            clips = session.clips.map {
                if (it.fileName == fileName) it.copy(userLabel = label, starred = true) else it
            }
        )
        save(updated)
        return updated
    }

    fun deleteClip(session: SleepSession, fileName: String): SleepSession {
        runCatching { clipFile(fileName).delete() }
        val updated = session.copy(clips = session.clips.filterNot { it.fileName == fileName })
        save(updated)
        return updated
    }

    /**
     * Deletes clip audio older than [days], keeping anything starred, then removes the now-dangling
     * entries from their sessions so the library never offers a row that cannot play.
     * [days] <= 0 keeps everything.
     */
    fun pruneAudio(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * DAY_MS

        loadAll().forEach { session ->
            val expired = session.clips.filter { !it.starred && it.startedAtMs < cutoff }
            if (expired.isEmpty()) return@forEach
            expired.forEach { runCatching { clipFile(it.fileName).delete() } }
            save(session.copy(clips = session.clips - expired.toSet()))
        }

        // Sweep orphans: files whose session was deleted, or writes interrupted mid-night.
        val referenced = loadAll().flatMap { s -> s.clips.map { it.fileName } }.toSet()
        clipsDir.listFiles()?.forEach { f -> if (f.name !in referenced) f.delete() }
    }

    /** Drops whole nights older than [days], audio included. [days] <= 0 keeps everything. */
    fun pruneSessions(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * DAY_MS
        loadAll().filter { it.startedAtMs < cutoff }.forEach { delete(it) }
    }

    fun clipBytesOnDisk(): Long = clipsDir.listFiles()?.sumOf { it.length() } ?: 0L

    private fun readFile(file: File): SleepSession? = try {
        if (file.exists()) json.decodeFromString(SleepSession.serializer(), file.readText()) else null
    } catch (e: Exception) {
        Log.e(TAG, "Skipping unreadable session ${file.name}", e)
        null
    }

    private companion object {
        const val TAG = "SessionStore"
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
