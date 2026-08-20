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
import org.sovereignhq.sleepwave.audio.EventDetector
import org.sovereignhq.sleepwave.audio.NightRecorder
import org.sovereignhq.sleepwave.audio.YamnetClassifier
import org.sovereignhq.sleepwave.data.EventKind
import org.sovereignhq.sleepwave.data.Sample
import org.sovereignhq.sleepwave.data.SessionStore
import org.sovereignhq.sleepwave.data.Settings
import org.sovereignhq.sleepwave.data.SleepSession
import org.sovereignhq.sleepwave.data.SoundClip
import org.sovereignhq.sleepwave.sensor.MotionMonitor
import org.sovereignhq.sleepwave.sleep.ActivityAggregator
import org.sovereignhq.sleepwave.sleep.BreathEstimator
import org.sovereignhq.sleepwave.sleep.SleepClassifier
import org.sovereignhq.sleepwave.sleep.SmartAlarm
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the whole night.
 *
 * A foreground service with a partial wake lock is the only way Android will let an app keep the
 * microphone open with the screen off for eight hours. Everything else hangs off that: the recorder
 * feeds frames in, frames become classified events and per-minute restlessness, minutes become
 * sleep stages, and once the wake window opens each new minute is also a chance to ring the alarm.
 *
 * Minute boundaries are driven by audio frame timestamps rather than a timer, because a timer is
 * exactly the thing Doze mode likes to postpone, while audio frames keep arriving as long as the
 * microphone is open.
 */
class SleepService : Service(), NightRecorder.Listener, EventDetector.Listener {

    private lateinit var settings: Settings
    private lateinit var store: SessionStore
    private lateinit var recorder: NightRecorder
    private lateinit var detector: EventDetector
    private lateinit var motion: MotionMonitor
    private lateinit var player: AlarmPlayer

    private var soundClassifier: YamnetClassifier? = null
    private val aggregator = ActivityAggregator()
    private var breath = BreathEstimator()
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
    private var maxClips = 120
    private var lastClipMs = 0L
    private var lastNotificationMs = 0L
    private var eventCount = 0
    private var resumed = false

    private val tracking = AtomicBoolean(false)
    private val sessionSaved = AtomicBoolean(false)
    private var wokeAtMs: Long? = null
    private var wokeSmart = false

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        settings = Settings(this)
        store = SessionStore(this)
        // Null when the model asset is absent; the recorder then keeps the heuristic's label.
        soundClassifier = YamnetClassifier.createOrNull(this)
        recorder = NightRecorder(store.clipsDir, this, soundClassifier)
        detector = EventDetector(settings.sensitivity.triggerDb, this)
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
            ACTION_QUIET -> quieten()
            ACTION_DISMISS -> dismiss()
            else -> {
                // START_STICKY hands us a null intent when Android restarts the service after
                // killing it. Treating that as "nothing to do" is how a night silently ended: the
                // process comes back, finds no reason to exist and stops. If a night was in flight,
                // pick it back up instead.
                val interrupted = settings.activeSessionId
                if (!tracking.get() && !player.isPlaying) {
                    if (interrupted.isNotBlank()) {
                        Log.w(TAG, "Restarted after being killed - resuming $interrupted")
                        startTracking(resumeSessionId = interrupted)
                    } else {
                        stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------------------------------------------------------- tracking

    private fun startTracking(resumeSessionId: String? = null) {
        if (tracking.get()) return

        val sensitivity = settings.sensitivity
        startedAtMs = System.currentTimeMillis()
        resumed = resumeSessionId != null
        // Reusing the id keeps one entry per night. The minutes before the kill are genuinely gone -
        // they only ever existed in memory - so the saved night covers from here, and says so.
        sessionId = resumeSessionId ?: "session_$startedAtMs"
        windowMinutes = settings.windowMinutes
        alarmTargetMs = when {
            resumeSessionId != null && settings.activeAlarmTargetMs > System.currentTimeMillis() ->
                settings.activeAlarmTargetMs
            settings.alarmEnabled -> settings.nextAlarmTimeMs(startedAtMs)
            else -> 0L
        }
        nextMinuteBoundaryMs = startedAtMs + 60_000
        maxClips = sensitivity.maxClipsPerNight
        detector = EventDetector(sensitivity.triggerDb, this)
        breath = BreathEstimator()
        // A fresh recorder every night: stopping one shuts down its disk-writer thread for good, so
        // reusing the instance after a resume would silently drop every clip.
        recorder = NightRecorder(store.clipsDir, this, soundClassifier)
        samples.clear()
        clips.clear()
        snoreMinutes = 0
        eventCount = 0
        lastClipMs = 0L
        wokeAtMs = null
        wokeSmart = false
        sessionSaved.set(false)
        tracking.set(true)

        settings.activeSessionId = sessionId
        settings.activeAlarmTargetMs = alarmTargetMs

        goForeground(statusLine())

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
        if (userInitiated) housekeep()
        if (!player.isPlaying) stopEverything()
    }

    private fun stopEverything() {
        player.stop()
        SleepState.setAlarmRinging(false)
        Notifications.cancelAlarm(this)
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun housekeep() {
        runCatching {
            store.pruneAudio(settings.clipRetentionDays)
            store.pruneSessions(settings.nightRetentionDays)
        }
    }

    // ---------------------------------------------------------- recorder input

    override fun onFrame(frame: NightRecorder.Frame) {
        if (!tracking.get()) return

        aggregator.onFrame(frame)
        detector.onFrame(frame)
        breath.onFrame(frame)
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
        peakDb: Float,
        envelope: List<Int>,
        detail: String,
        confidence: Float
    ) {
        // Muting happens here rather than at trigger time because the label only exists once the
        // model has seen the clip. The file is written and then removed, which costs a few hundred
        // KB of churn and saves scrolling past forty recordings of a fan.
        if (detail.isNotBlank() && detail in settings.mutedLabels) {
            runCatching { store.clipFile(fileName).delete() }
            Log.i(TAG, "Discarded a muted sound: $detail")
            return
        }

        clips.add(
            SoundClip(
                fileName = fileName,
                startedAtMs = startedAtMs,
                durationMs = durationMs,
                kind = kind,
                peakDb = peakDb,
                envelope = envelope,
                detail = detail,
                confidence = confidence
            )
        )
        SleepState.setClipCount(clips.size)
    }

    override fun onError(message: String) {
        Log.w(TAG, "Recorder error: $message")
        SleepState.setError(message)
        // The alarm backstop is already registered with the system, so the wake-up still happens.
        main.post { updateNotification("Microphone unavailable - alarm still set", force = true) }
    }

    // ------------------------------------------------------------ event input

    override fun onEvent(event: EventDetector.Event) {
        eventCount++
        if (event.kind == EventKind.SNORE || event.cadenced) aggregator.markSnoring()
        SleepState.setEventCount(eventCount)

        if (!settings.recordSounds) return
        if (clips.size >= maxClips) return
        if (recorder.clipPending) return

        // A short cooldown, not a long one: the point of this app is catching things, and clips
        // overlap by design anyway thanks to the lead-in.
        val now = System.currentTimeMillis()
        if (now - lastClipMs < CLIP_COOLDOWN_MS) return
        lastClipMs = now

        recorder.requestClip(event.kind.name, event.peakAboveFloorDb)
    }

    // ------------------------------------------------------------ the minute loop

    private fun closeMinute() {
        val (motionCount, motionPeak) =
            if (settings.motionSensing && motion.available) motion.drain() else 0 to 0f

        val result = aggregator.closeMinute(motionCount, motionPeak)
        val breathing = breath.estimate()
        samples.add(
            Sample(
                minute = samples.size,
                activity = result.activity,
                loudnessDb = result.peakDb,
                snoring = result.snoring,
                breathRate = breathing.ratePerMinute,
                breathRegularity = breathing.regularity
            )
        )
        if (result.snoring) snoreMinutes++

        SleepState.appendMinute(result.activity)
        SleepState.setSnoreMinutes(snoreMinutes)

        main.post { updateNotification(statusLine(), force = false) }
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

    /**
     * Silences a ringing alarm without dismissing it, then brings it back if nothing else happens.
     *
     * The restore is the important half. Without it "quiet" would be an undocumented way to switch
     * the alarm off by walking away, which is precisely what an alarm must not offer.
     */
    private fun quieten() {
        if (!player.isPlaying || player.isQuiet) return
        player.quieten()
        SleepState.setAlarmQuiet(true)
        main.removeCallbacks(restoreSound)
        main.postDelayed(restoreSound, QUIET_GRACE_MS)
        updateNotification("Alarm silenced - still waiting for you", force = true)
    }

    private val restoreSound = Runnable {
        if (player.isPlaying && player.isQuiet) {
            player.restoreVolume()
            SleepState.setAlarmQuiet(false)
            updateNotification("Alarm ringing", force = true)
        }
    }

    private fun snooze() {
        if (!player.isPlaying) return
        main.removeCallbacks(restoreSound)
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
        main.removeCallbacks(restoreSound)
        AlarmScheduler.cancel(this)
        if (tracking.get()) {
            stopTracking(userInitiated = true)
        } else {
            housekeep()
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
            snoreMinutes = snoreMinutes,
            diagnostics = buildDiagnostics()
        )
        val classified = raw.copy(samples = SleepClassifier.classify(raw))
        store.save(classified)
        return classified
    }

    /**
     * A one-line account of what the hardware actually did. Written every night and shown when the
     * morning comes back empty, because "no recordings" has several very different causes and
     * guessing between them from the sofa is hopeless.
     */
    private fun buildDiagnostics(): String = buildString {
        append("input=${recorder.audioSource}")
        append(" frames=${recorder.framesRead}")
        append(" quietest=${recorder.quietestFloorDb.toInt()}dB")
        append(" events=$eventCount")
        append(" clips=${clips.size}")
        append(" sensitivity=${settings.sensitivity.name}")
        if (!settings.recordSounds) append(" recording=off")
        if (resumed) append(" resumed-after-kill")
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
        startForeground(
            Notifications.ID_TRACKING,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        lastNotificationMs = System.currentTimeMillis()
    }

    /** Refreshed sparingly - redrawing a notification every minute all night costs battery. */
    private fun updateNotification(text: String, force: Boolean) {
        val now = System.currentTimeMillis()
        // Frequent for the first few minutes so that pulling down the shade right after starting
        // proves it is alive, then sparingly for the rest of the night to save battery.
        val interval = if (samples.size <= SETTLING_MINUTES) 60_000L else NOTIFICATION_INTERVAL_MS
        if (!force && now - lastNotificationMs < interval) return
        lastNotificationMs = now
        getSystemService(NotificationManager::class.java)
            ?.notify(Notifications.ID_TRACKING, Notifications.tracking(this, text))
    }

    private fun statusLine(): String {
        val hours = samples.size / 60
        val minutes = samples.size % 60
        val elapsed = when {
            samples.isEmpty() -> "Listening"
            hours == 0 -> "${minutes}m in"
            else -> "${hours}h ${minutes}m in"
        }
        val recorded = if (clips.isEmpty()) "" else " - ${clips.size} recorded"
        if (alarmTargetMs <= 0L) return "$elapsed$recorded - no alarm"
        val target = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            .format(java.util.Date(alarmTargetMs))
        return "$elapsed$recorded - waking you by $target"
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
        soundClassifier?.close()
        soundClassifier = null
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
        const val ACTION_QUIET = "org.sovereignhq.sleepwave.QUIET"

        private const val TAG = "SleepService"
        private const val MAX_NIGHT_MS = 13L * 60L * 60L * 1000L
        private const val NOTIFICATION_INTERVAL_MS = 5L * 60L * 1000L
        private const val SETTLING_MINUTES = 4

        /** How long a silenced alarm stays silent before it insists again. */
        private const val QUIET_GRACE_MS = 90_000L
        private const val MIN_SAVEABLE_MINUTES = 10

        /** Long enough that one snore does not become three clips, short enough to catch a lot. */
        private const val CLIP_COOLDOWN_MS = 15_000L

        fun start(context: android.content.Context) {
            val intent = Intent(context, SleepService::class.java).setAction(ACTION_START)
            context.startForegroundService(intent)
        }

        fun send(context: android.content.Context, action: String) {
            val intent = Intent(context, SleepService::class.java).setAction(action)
            runCatching { context.startService(intent) }
        }
    }
}
