package tech.huangsh.onetap.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import tech.huangsh.onetap.service.TimeAnnouncementReceiver
import tech.huangsh.onetap.ui.activity.MainActivity
import java.util.Calendar

object TimeAnnouncementScheduler {
    private const val REQUEST_CODE = 2302

    fun reschedule(context: Context, enabled: Boolean, hours: Set<Int>) {
        cancel(context)
        if (!enabled || hours.isEmpty()) return
        scheduleNext(context, hours)
    }

    fun cancel(context: Context) {
        val existing = PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, TimeAnnouncementReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) alarmManager(context).cancel(existing)
    }

    fun scheduleNext(context: Context, hours: Set<Int>) {
        val next = nextTriggerAt(hours)
        val alarmManager = alarmManager(context)
        val pendingIntent = pendingIntent(context, PendingIntent.FLAG_UPDATE_CURRENT)
        alarmManager.setAlarmClock(
            AlarmManager.AlarmClockInfo(next, showIntent(context)),
            pendingIntent
        )
    }

    private fun nextTriggerAt(hours: Set<Int>): Long {
        val now = Calendar.getInstance()
        return hours
            .filter { it in 0..23 }
            .map { hour ->
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, hour)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                    if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
                }.timeInMillis
            }
            .minOrNull() ?: now.timeInMillis
    }

    private fun alarmManager(context: Context): AlarmManager {
        return context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun pendingIntent(context: Context, flag: Int): PendingIntent {
        val intent = Intent(context, TimeAnnouncementReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            flag or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun showIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
