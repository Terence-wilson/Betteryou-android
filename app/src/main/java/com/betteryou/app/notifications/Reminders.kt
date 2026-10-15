package com.betteryou.app.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.betteryou.app.MainActivity
import com.betteryou.app.R
import com.betteryou.app.domain.AppSettings
import com.betteryou.app.domain.Cycle
import com.betteryou.app.domain.Schedule
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** Where a tapped notification should take the user. */
enum class NotificationRoute {
    CHECK_IN,
    DAILY_MOOD,
}

/** Schedules local reminders with [AlarmManager] and posts them via [ReminderReceiver]. */
object Reminders {
    private const val CHANNEL_ID = "reminders"
    const val ACTION_REMINDER = "com.betteryou.app.action.REMINDER"
    const val EXTRA_KIND = "com.betteryou.app.extra.KIND"
    const val EXTRA_HOUR = "com.betteryou.app.extra.HOUR"
    const val EXTRA_MINUTE = "com.betteryou.app.extra.MINUTE"
    const val EXTRA_ROUTE = "com.betteryou.app.extra.ROUTE"

    enum class Kind(val requestCode: Int, val title: String, val body: String, val route: NotificationRoute?) {
        CHECK_IN(
            1,
            "Time for your check-in",
            "Rate how your hobbies and habits went and choose your focus for the next cycle.",
            NotificationRoute.CHECK_IN,
        ),
        RESET_END(
            2,
            "Reset complete 🎉",
            "You finished your reset. Keep the momentum going until your check-in.",
            null,
        ),
        DAILY(
            3,
            "How are you feeling today?",
            "Take a few seconds to log your day.",
            NotificationRoute.DAILY_MOOD,
        ),
    }

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Check-in, reset and daily mood reminders"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Schedules the "check-in due" and "reset finished" notifications for a cycle. */
    fun scheduleCycleReminders(context: Context, cycle: Cycle?, enabled: Boolean) {
        cancel(context, Kind.CHECK_IN)
        cancel(context, Kind.RESET_END)
        if (!enabled || cycle == null || cycle.completedAt != null) return

        val now = Instant.now()
        if (cycle.checkInDate > now) schedule(context, Kind.CHECK_IN, cycle.checkInDate)
        if (cycle.hasPostResetPeriod && cycle.resetEndDate > now) {
            schedule(context, Kind.RESET_END, cycle.resetEndDate)
        }
    }

    fun scheduleDailyReminder(context: Context, settings: AppSettings) {
        cancel(context, Kind.DAILY)
        if (!settings.dailyCheckInEnabled || !settings.dailyReminderEnabled) return
        scheduleNextDaily(context, settings.dailyReminderHour, settings.dailyReminderMinute)
    }

    fun scheduleNextDaily(context: Context, hour: Int, minute: Int) {
        val next = Schedule.nextDailyOccurrence(ZonedDateTime.now(), LocalTime.of(hour, minute))
        schedule(context, Kind.DAILY, next.toInstant()) {
            putExtra(EXTRA_HOUR, hour)
            putExtra(EXTRA_MINUTE, minute)
        }
    }

    private fun schedule(context: Context, kind: Kind, at: Instant, extras: Intent.() -> Unit = {}) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = alarmIntent(context, kind, extras)
        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at.toEpochMilli(), operation)
        }
    }

    private fun cancel(context: Context, kind: Kind) {
        context.getSystemService(AlarmManager::class.java).cancel(alarmIntent(context, kind))
    }

    private fun alarmIntent(context: Context, kind: Kind, extras: Intent.() -> Unit = {}): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java)
            .setAction(ACTION_REMINDER)
            .putExtra(EXTRA_KIND, kind.name)
            .apply(extras)
        return PendingIntent.getBroadcast(
            context,
            kind.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun show(context: Context, kind: Kind) {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val open = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply { kind.route?.let { putExtra(EXTRA_ROUTE, it.name) } }
        val contentIntent = PendingIntent.getActivity(
            context,
            100 + kind.requestCode,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(kind.title)
            .setContentText(kind.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(kind.body))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(kind.requestCode, notification)
    }
}
