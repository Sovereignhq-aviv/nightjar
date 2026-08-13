package org.sovereignhq.sleepwave.service

import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import org.sovereignhq.sleepwave.alarm.AlarmPlayer
import org.sovereignhq.sleepwave.audio.NightRecorder
import org.sovereignhq.sleepwave.audio.SnoreDetector
import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.SessionStore
import org.sovereignhq.sleepwave.data.Settings
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.SoundClip
import org.sovereignhq.sleepwave.sensor.MotionMonitor
import org.sovereignhq.sleepwave.sleep.ActivityAggregator
import org.sovereignhq.sleepwave.sleep.SleepClassifier
import org.sovereignhq.sleepwave.sleep.SmartAlarm
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the whole night.
 *
 * A foreground service with a partial wake lock is the only way Android will let an app keep the
 * microphone open with the screen off for eight hours. Everything else here hangs off that: the
 * recorder feeds frames in, frames become minutes, minutes become sleep stages, and once the wake
 * window opens each new minute is also a chance to ring the alarm.
 *
 * Minute boundaries are driven by audio frame timestamps rather than a timer, because a timer is
 * exactly the thing Doze mode likes to postpone, while audio frames keep arriving as long as the
 * microphone is open.
 */
class SleepService : Service(), NightRecorder.Listener, SnoreDetector.Listener {

    private lateinit var settings: Settings
    private lateinit var store: SessionStore
    private lateinit var recorder: NightRecorder
    private lateinit var detector: SnoreDetector
    private lateinit var motion: MotionMonitor
    private lateinit var player: AlarmPlayer

    private val aggregator = ActivityAggregator()
    private val samples = CopyOnWriteArrayList<Sample>()
    private val clips = CopyOnWriteArrayList<SoundClip>()
    private val main = Handler(Looper.getMainLooper())

    private var wakeLock: PowerManager.WakeLock? = null

    private var sessionId = ""
    private var startedAtMs = 0L
    private var alarmTargetMs = 0L
    private var windowMinutes = 30
    private var nextMinuteBoundaryMs = 0L
    private var snoreMinutes = 0
    private var snoreClipCount = 0
    private var noiseClipCount = 0
    private var lastSnoreClipMs = 0L
    private var lastNoiseClipMs = 0L
    private var lastNotificationMs = 0L

    private val tracking = AtomicBoolean(false)
    private val sessionSaved = AtomicBoolean(false)
    private var wokeAtMs: Long? = null
    private var wokeSmart = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = Settings(this)
        store = SessionStore(this)
        recorder = NightRecorder(store.clipsDir, this)
        detector = SnoreDetector(this)
        motion = MotionMonitor(this)
        player = AlarmPlayer(this)
        Notifications.createChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startTracking()
            ACTION_STOP -> stopTracking(userInitiated = true)
            ACTION_FIRE_ALARM -> fireAlarm(smart = false, reason = "Scheduled time")
            ACTION_SNOOZE -> snooze()
            ACTION_DISMISS -> dismiss()
            else -> if (!tracking.get() && !player.isPlaying) stopSelf()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- tracking

    private fun startTracking() {
        if (tracking.get()) return

        startedAtMs = System.currentTimeMillis()
        sessionId = "session_$startedAtMs"
        windowMinutes = settings.windowMinutes
        alarmTargetMs = if (settings.alarmEnabled) settings.nextAlarmTimeMs(startedAtMs) else 0L
        nextMinuteBoundaryMs = startedAtMs + 60_000
        samples.clear()
        clips.clear()
        snoreMinutes = 0
        snoreClipCount = 0
        noiseClipCount = 0
        wokeAtMs = null
        wokeSmart = false
        sessionSaved.set(false)
        tracking.set(true)

        settings.activeSessionId = sessionId
        settings.activeAlarmTargetMs = alarmTargetMs

        goForeground(alarmSummary())

        acquireWakeLock()
        if (settings.motionSensing && motion.available) motion.start()
        recorder.start()

        if (alarmTargetMs > 0) AlarmScheduler.schedule(this, alarmTargetMs)

        SleepState.beginNight(startedAtMs, alarmTargetMs)
    }

    private fun stopTracking(userInitiated: Boolean) {
        if (!tracking.getAndSet(false)) {
            if (!player.isPlaying) stopEverything()
            return
        }
        recorder.stop()
        motion.stop()
        AlarmScheduler.cancel(this)
        val saved = finaliseSession()
        releaseWakeLock()
        settings.activeSessionId = ""
        settings.activeAlarmTargetMs = 0L
        SleepState.endNight(saved?.id)
        if (userInitiated) store.pruneOlderThan(settings.autoDeleteDays)
        if (!player.isPlaying) stopEverything()
    }

    private fun stopEverything() {
        player.stop()
        SleepState.setAlarmRinging(false)
        Notifications.cancelAlarm(this)
        releaseWakeLock()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    // ---------------------------------------------------------- recorder input

    override fun onFrame(frame: NightRecorder.Frame) {
        if (!tracking.get()) return

        aggregator.onFrame(frame)
        detector.onFrame(frame)
        SleepState.setLevel(recorder.lastLevel)

        // Catch up if a boundary was somehow missed, but never spin: at most a few minutes.
        var guard = 0
        while (frame.timeMs >= nextMinuteBoundaryMs && guard++ < 5) {
            closeMinute()
            nextMinuteBoundaryMs += 60_000
        }
    }

    override fun onClipSaved(
        fileName: String,
        startedAtMs: Long,
        durationMs: Long,
        kind: String,
        peakDb: Float
    ) {
        clips.add(SoundClip(fileName, startedAtMs, durationMs, kind, peakDb))
        SleepState.setClipCount(clips.size)
    }

    override fun onError(message: String) {
        Log.w(TAG, "Recorder error: $message")
        SleepState.setError(message)
        // The alarm backstop is already registered with the system, so the wake-up still happens.
        main.post { updateNotification("Microphone unavailable - alarm still set", force = true) }
    }

    // ------------------------------------------------------------ snore input

    override fun onSnoreBurst(timeMs: Long, peakDb: Float) {
        aggregator.markSnoring()
    }

    override fun onSnoreEpisode(timeMs: Long, peakDb: Float) {
        if (!settings.recordSnoring) return
        if (snoreClipCount >= MAX_SNORE_CLIPS) return
        val now = System.currentTimeMillis()
        if (now - lastSnoreClipMs < SNORE_CLIP_COOLDOWN_MS) return
        lastSnoreClipMs = now
        snoreClipCount++
        recorder.requestClip("SNORE", peakDb)
    }

    override fun onNoiseEvent(timeMs: Long, peakDb: Float) {
        if (!settings.recordSnoring) return
        if (noiseClipCount >= MAX_NOISE_CLIPS) return
        val now = System.currentTimeMillis()
        if (now - lastNoiseClipMs < NOISE_CLIP_COOLDOWN_MS) return
        lastNoiseClipMs = now
        noiseClipCount++
        recorder.requestClip("NOISE", peakDb)
    }

    // ------------------------------------------------------------ the minute loop

    private fun closeMinute() {
        val (motionCount, motionPeak) =
            if (settings.motionSensing && motion.available) motion.drain() else 0 to 0f

        val result = aggregator.closeMinute(motionCount, motionPeak)
        samples.add(
            Sample(
                minute = samples.size,
                activity = result.activity,
                loudnessDb = result.peakDb,
                snoring = result.snoring
            )
        )
        if (result.snoring) snoreMinutes++

        SleepState.appendMinute(result.activity)
        SleepState.setSnoreMinutes(snoreMinutes)

        main.post { updateNotification(alarmSummary(), force = false) }
        considerWaking()
    }

    private fun considerWaking() {
        if (alarmTargetMs <= 0L || wokeAtMs != null) return

        val now = System.currentTimeMillis()
        val windowStart = alarmTargetMs - windowMinutes * 60_000L
        if (now < windowStart) return

        val activity = samples.map { it.activity }
        val (quiet, busy) = SleepClassifier.quietAndBusy(activity)
        val minutesIntoWindow = ((now - windowStart) / 60_000L).toInt()

        val decision = SmartAlarm.evaluate(
            minutesIntoWindow = minutesIntoWindow,
            windowMinutes = windowMinutes,
            recent = activity.takeLast(10),
            nightQuiet = quiet,
            nightBusy = busy
        )
        if (decision.wake) fireAlarm(smart = decision.smart, reason = decision.reason)
    }

    // ------------------------------------------------------------------- alarm

    private fun fireAlarm(smart: Boolean, reason: String) {
        if (player.isPlaying) return

        if (tracking.get()) {
            wokeAtMs = System.currentTimeMillis()
            wokeSmart = smart
            recorder.stop()
            motion.stop()
            tracking.set(false)
            AlarmScheduler.cancel(this)
            val saved = finaliseSession()
            settings.activeSessionId = ""
            settings.activeAlarmTargetMs = 0L
            SleepState.endNight(saved?.id)
        } else {
            // Woken by the system backstop after the service had been restarted: rescue whatever
            // session was left half-finished so the night is not lost.
            rescueOrphanedSession()
            goForeground("Alarm ringing")
        }

        acquireWakeLock()
        SleepState.setAlarmRinging(true)
        Log.i(TAG, "Alarm firing (smart=$smart): $reason")

        player.start(
            soundUri = settings.alarmSoundUri,
            vibrate = settings.vibrate,
            rampSeconds = settings.rampSeconds
        )

        getSystemService(NotificationManager::class.java)
            ?.notify(Notifications.ID_ALARM, Notifications.alarm(this))
        updateNotification("Alarm ringing", force = true)

        // Best effort: on most devices a running foreground service may still bring up the
        // full-screen alarm. When the OS refuses, the notification above is the way in.
        runCatching { Notifications.alarmScreenIntent(this).send() }
    }

    private fun snooze() {
        if (!player.isPlaying) return
        player.stop()
        SleepState.setAlarmRinging(false)
        Notifications.cancelAlarm(this)

        val next = System.currentTimeMillis() + settings.snoozeMinutes * 60_000L
        AlarmScheduler.schedule(this, next)
        alarmTargetMs = next
        windowMinutes = 0
        wokeAtMs = null
        updateNotification("Snoozed for ${settings.snoozeMinutes} minutes", force = true)
    }

    private fun dismiss() {
        AlarmScheduler.cancel(this)
        if (tracking.get()) {
            stopTracking(userInitiated = true)
        }
        stopEverything()
    }

    // -------------------------------------------------------------- persistence

    private fun finaliseSession(): SleepSession? {
        if (samples.size < MIN_SAVEABLE_MINUTES) {
            Log.i(TAG, "Night too short to be meaningful (${samples.size} min), discarding")
            clips.forEach { runCatching { store.clipFile(it.fileName).delete() } }
            return null
        }
        if (!sessionSaved.compareAndSet(false, true)) return store.load(sessionId)

        val raw = SleepSession(
            id = sessionId,
            startedAtMs = startedAtMs,
            endedAtMs = System.currentTimeMillis(),
            alarmTargetMs = alarmTargetMs,
            windowMinutes = windowMinutes,
            wokeAtMs = wokeAtMs,
            wokeSmart = wokeSmart,
            samples = samples.toList(),
            clips = clips.toList(),
            snoreMinutes = snoreMinutes
        )
        val classified = raw.copy(samples = SleepClassifier.classify(raw))
        store.save(classified)
        return classified
    }

    /** After a service restart the in-memory night is gone; keep whatever was already on disk. */
    private fun rescueOrphanedSession() {
        val orphan = settings.activeSessionId
        if (orphan.isNotBlank()) {
            settings.activeSessionId = ""
            settings.activeAlarmTargetMs = 0L
            store.load(orphan)?.let { SleepState.endNight(it.id) }
        }
    }

    // ------------------------------------------------------------- housekeeping

    private fun goForeground(text: String) {
        val notification = Notifications.tracking(this, text)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifications.ID_TRACKING,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(Notifications.ID_TRACKING, notification)
        }
        lastNotificationMs = System.currentTimeMillis()
    }

    /** Refreshed sparingly - redrawing a notification every minute all night costs battery. */
    private fun updateNotification(text: String, force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotificationMs < NOTIFICATION_INTERVAL_MS) return
        lastNotificationMs = now
        getSystemService(NotificationManager::class.java)
            ?.notify(Notifications.ID_TRACKING, Notifications.tracking(this, text))
    }

    private fun alarmSummary(): String {
        val hours = samples.size / 60
        val minutes = samples.size % 60
        val elapsed = when {
            samples.isEmpty() -> "starting"
            hours == 0 -> "${minutes}m so far"
            else -> "${hours}h ${minutes}m so far"
        }
        if (alarmTargetMs <= 0L) return "$elapsed - no alarm set"
        val target = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(alarmTargetMs))
        return "$elapsed - waking you by $target"
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(PowerManager::class.java) ?: return
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "sleepwave:night").apply {
            setReferenceCounted(false)
            acquire(MAX_NIGHT_MS)
        }
    }

    private fun releaseWakeLock() {
        runCatching { if (wakeLock?.isHeld == true) wakeLock?.release() }
        wakeLock = null
    }

    override fun onDestroy() {
        if (tracking.get()) {
            recorder.stop()
            motion.stop()
            finaliseSession()
        }
        player.stop()
        releaseWakeLock()
        SleepState.setAlarmRinging(false)
        isRunning = false
        super.onDestroy()
    }

    companion object {
        /**
         * Whether this service exists right now. The alarm backstop checks it to decide between
         * handing the wake-up to the service and ringing on its own.
         */
        @Volatile
        var isRunning: Boolean = false
            private set

        const val ACTION_START = "org.sovereignhq.sleepwave.START"
        const val ACTION_STOP = "org.sovereignhq.sleepwave.STOP"
        const val ACTION_FIRE_ALARM = "org.sovereignhq.sleepwave.FIRE_ALARM"
        const val ACTION_SNOOZE = "org.sovereignhq.sleepwave.SNOOZE"
        const val ACTION_DISMISS = "org.sovereignhq.sleepwave.DISMISS"

        private const val TAG = "SleepService"
        private const val MAX_NIGHT_MS = 13L * 60L * 60L * 1000L
        private const val NOTIFICATION_INTERVAL_MS = 5L * 60L * 1000L
        private const val MIN_SAVEABLE_MINUTES = 10
        private const val MAX_SNORE_CLIPS = 40
        private const val MAX_NOISE_CLIPS = 15
        private const val SNORE_CLIP_COOLDOWN_MS = 10L * 60L * 1000L
        private const val NOISE_CLIP_COOLDOWN_MS = 4L * 60L * 1000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, SleepService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun send(context: android.content.Context, action: String) {
            val intent = Intent(context, SleepService::class.java).setAction(action)
            runCatching { context.startService(intent) }
        }
    }
}
