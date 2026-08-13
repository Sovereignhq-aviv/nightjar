package org.sovereignhq.sleepwave

import android.app.Application
import org.sovereignhq.sleepwave.service.Notifications

class SleepWaveApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Channels must exist before anything tries to post to them, including the alarm
        // backstop, which may run without the UI ever having been opened.
        Notifications.createChannels(this)
    }
}
