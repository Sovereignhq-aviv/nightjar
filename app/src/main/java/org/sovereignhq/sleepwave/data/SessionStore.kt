package org.sovereignhq.sleepwave.data

import android.content.Context
import android.util.Log
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Sessions live as one JSON file each in filesDir/sessions, audio clips in filesDir/clips.
 * One file per night is a few hundred KB at most, so there is no database here on purpose:
 * fewer moving parts, and the whole history can be copied off the phone with a file manager.
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

    /** Drops nights older than [days], audio included. [days] <= 0 keeps everything. */
    fun pruneOlderThan(days: Int) {
        if (days <= 0) return
        val cutoff = System.currentTimeMillis() - days * 24L * 60L * 60L * 1000L
        loadAll().filter { it.startedAtMs < cutoff }.forEach { delete(it) }
        // Sweep clips that lost their session for any reason.
        val referenced = loadAll().flatMap { s -> s.clips.map { it.fileName } }.toSet()
        clipsDir.listFiles()?.forEach { f -> if (f.name !in referenced) f.delete() }
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
    }
}
