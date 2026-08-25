package com.example.utils

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object ReminderScheduler {

    fun scheduleReminder(
        context: Context,
        reminderId: String,
        title: String,
        message: String,
        triggerTimeMillis: Long
    ): Boolean {
        return try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra(ReminderReceiver.EXTRA_TITLE, title)
                putExtra(ReminderReceiver.EXTRA_MESSAGE, message)
                putExtra(ReminderReceiver.EXTRA_NOTIFICATION_ID, reminderId.hashCode())
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminderId.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            } else {
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerTimeMillis,
                    pendingIntent
                )
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    fun scheduleMonthlyEmi(
        context: Context,
        emiId: String,
        title: String,
        message: String,
        dueDay: Int,
        hour: Int = 9,
        minute: Int = 0
    ): Boolean {
        return try {
            val cal = Calendar.getInstance().apply {
                val currentDay = get(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, minOf(dueDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.MONTH, 1)
                    set(Calendar.DAY_OF_MONTH, minOf(dueDay, getActualMaximum(Calendar.DAY_OF_MONTH)))
                }
            }
            scheduleReminder(context, emiId, title, message, cal.timeInMillis)
        } catch (e: Exception) {
            false
        }
    }
}
