package org.sovereignhq.nightjar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.sovereignhq.nightjar.audio.NightRecorder
import java.io.File

/**
 * A twelve-second microphone test, in the place you actually sleep.
 *
 * This exists because of the single worst failure this app can have: waking up to an empty list with
 * no way to tell whether the room was quiet, the microphone was blocked by a duvet, the permission
 * was revoked, or another app had the microphone locked. Waiting a whole night to find out is a
 * terrible feedback loop. Twelve seconds is a better one.
 *
 * It deliberately drives the real [NightRecorder] rather than a simplified probe, so the test
 * exercises the same input-source negotiation the night will use - including reporting which input
 * the phone actually granted.
 */
@Composable
fun MicCheckCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    var running by remember { mutableStateOf(false) }
    var level by remember { mutableFloatStateOf(0f) }
    var peak by remember { mutableFloatStateOf(0f) }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var source by remember { mutableStateOf("") }
    var floorDb by remember { mutableFloatStateOf(0f) }
    var frames by remember { mutableStateOf(0L) }
    var finished by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null)}

    val trace = remember { mutableStateOf(listOf<Float>()) }

    val recorder = remember {
        val dir = File(context.cacheDir, "miccheck").apply { mkdirs() }
        NightRecorder(
            clipsDir = dir,
            listener = object : NightRecorder.Listener {
                override fun onFrame(frame: NightRecorder.Frame) = Unit
                override fun onClipSaved(
                    fileName: String,
                    startedAtMs: Long,
                    durationMs: Long,
                    kind: String,
                    peakDb: Float,
                    envelope: List<Int>,
                    detail: String,
                    confidence: Float
                ) = Unit

                override fun onError(message: String) {
                    failure = message
                }
            }
        )
    }

    // Never leave the microphone open because someone scrolled away.
    DisposableEffect(Unit) {
        onDispose { runCatching { recorder.stop() } }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        val total = CHECK_SECONDS
        val startedAt = System.currentTimeMillis()
        while (running) {
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000
            secondsLeft = (total - elapsed).toInt().coerceAtLeast(0)

            level = recorder.lastLevel
            if (level > peak) peak = level
            source = recorder.audioSource
            floorDb = recorder.quietestFloorDb
            frames = recorder.framesRead
            trace.value = (trace.value + level).takeLast(120)

            if (elapsed >= total) {
                runCatching { recorder.stop() }
                running = false
                finished = true
            }
            delay(100)
        }
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "Put the phone exactly where it will sleep, then run this. Breathe normally, or just " +
                "stay still and let the room be the room.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(14.dp))

        if (running || finished) {
            LoudnessBar(
                peakAboveFloorDb = level * 40f,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
            )
            Spacer(Modifier.height(10.dp))
            LiveWave(trace.value)
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (running) "listening, ${secondsLeft}s left" else "done",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "input: ${source.ifBlank { "-" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (finished || failure != null) {
            Spacer(Modifier.height(12.dp))
            val verdict = verdictFor(failure, frames, peak, floorDb)
            Text(
                verdict.first,
                style = MaterialTheme.typography.titleMedium,
                color = if (verdict.second) {
                    MaterialTheme.colorScheme.secondary
                } else {
                    MaterialTheme.colorScheme.tertiary
                }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "loudest: ${(peak * 40).toInt()}dB above the room  ·  " +
                    "quietest floor: ${floorDb.toInt()}dB  ·  frames: $frames",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(14.dp))

        if (running) {
            OutlinedButton(
                onClick = {
                    runCatching { recorder.stop() }
                    running = false
                    finished = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text("Stop the check") }
        } else {
            Button(
                onClick = {
                    peak = 0f
                    trace.value = emptyList()
                    failure = null
                    finished = false
                    recorder.start()
                    running = true
                },
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) { Text(if (finished) "Check again" else "Check the microphone") }
        }
    }
}

/** Returns the verdict and whether it is good news. */
private fun verdictFor(
    failure: String?,
    frames: Long,
    peak: Float,
    floorDb: Float
): Pair<String, Boolean> = when {
    failure != null -> failure to false

    frames == 0L ->
        "No audio arrived at all. Another app may be holding the microphone - close any voice " +
            "recorder, call or assistant and try again." to false

    floorDb <= -80f && peak < 0.05f ->
        "The microphone is open but hearing almost nothing. That is a blocked microphone: move the " +
            "phone off soft bedding, or turn it face up." to false

    peak >= 0.15f ->
        "Good. The room is clearly audible from here - this position will work." to true

    else ->
        "Faint but usable. It will catch loud noises and probably miss quiet ones. Closer to the " +
            "bed, or off the soft surface, would be better." to false
}

private const val CHECK_SECONDS = 12L
