package com.betteryou.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.betteryou.app.BetterYouApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Fires when a scheduled reminder is due. */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Reminders.ACTION_REMINDER) return
        val kind = Reminders.Kind.entries.firstOrNull { it.name == intent.getStringExtra(Reminders.EXTRA_KIND) } ?: return
        Reminders.show(context, kind)

        if (kind == Reminders.Kind.DAILY) {
            // Alarms are one-shot, so queue up tomorrow's reminder.
            val hour = intent.getIntExtra(Reminders.EXTRA_HOUR, 20)
            val minute = intent.getIntExtra(Reminders.EXTRA_MINUTE, 0)
            Reminders.scheduleNextDaily(context, hour, minute)
        }
    }
}

/** Alarms are cleared on reboot and app updates; restore them from stored data. */
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
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
