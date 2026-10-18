package com.betteryou.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.betteryou.app.BetterYouApplication
import com.betteryou.app.domain.today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a scheduled reminder is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_REMINDER) return
        val kind = Reminders.Kind.entries.firstOrNull { it.name == intent.getStringExtra(Reminders.EXTRA_KIND) } ?: return
        if (kind != Reminders.Kind.DAILY) {
            Reminders.show(context, kind)
            return
        }

        // Alarms are one-shot, so queue up tomorrow's reminder, and skip today's if the mood is already logged.
        val hour = intent.getIntExtra(Reminders.EXTRA_HOUR, 20)
        val minute = intent.getIntExtra(Reminders.EXTRA_MINUTE, 0)
        Reminders.scheduleNextDaily(context, hour, minute)
        val repository = (context.applicationContext as BetterYouApplication).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!repository.hasMood(today())) Reminders.show(context, kind)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Alarms are cleared on reboot and app updates, and fire at the wrong local time after a time zone
 * or clock change; restore them from stored data.
 */
class RescheduleReceiver : BroadcastReceiver() {
    private val actions = setOf(
        Intent.ACTION_BOOT_COMPLETED,
        Intent.ACTION_MY_PACKAGE_REPLACED,
        Intent.ACTION_TIMEZONE_CHANGED,
        Intent.ACTION_TIME_CHANGED,
    )

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in actions) return
        val repository = (context.applicationContext as BetterYouApplication).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
