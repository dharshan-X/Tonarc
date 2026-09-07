package com.quietrays.tonarc.data.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class SleepTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Timber.tag("SleepTimerReceiver").d("Sleep timer expired. Sending intent to MusicService")
        val serviceIntent = Intent(context, MusicService::class.java).apply {
            action = MusicService.ACTION_SLEEP_TIMER_EXPIRED
            putExtra(MusicService.EXTRA_FORCE_FOREGROUND_ON_START, true)
        }
        try {
            context.startService(serviceIntent)
        } catch (e: Exception) {
            Timber.tag("SleepTimerReceiver").w(e, "context.startService failed; falling back to startForegroundService")
            try {
                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
            } catch (fatal: Exception) {
                Timber.tag("SleepTimerReceiver").e(fatal, "Failed to start service for sleep timer")
            }
        }
    }
}
