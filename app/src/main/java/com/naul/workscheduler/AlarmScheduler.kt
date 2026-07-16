package com.naul.workscheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import java.util.Calendar

class AlarmScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val preferenceHelper = PreferenceHelper(context)

    fun scheduleAlarms() {
        if (!preferenceHelper.isScheduleEnabled) {
            cancelAlarms()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Log.w("AlarmScheduler", "Cannot schedule exact alarms. Permission missing.")
                return
            }
        }

        scheduleAlarm(
            Constants.ACTION_MUTE,
            Constants.REQUEST_CODE_MUTE,
            preferenceHelper.muteHour,
            preferenceHelper.muteMinute
        )

        // Schedule Pre-mute notification 10 minutes before
        val muteCalendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, preferenceHelper.muteHour)
            set(Calendar.MINUTE, preferenceHelper.muteMinute)
            set(Calendar.SECOND, 0)
        }
        
        val preMuteCalendar = (muteCalendar.clone() as Calendar).apply {
            add(Calendar.MINUTE, -10)
        }
        
        scheduleAlarm(
            Constants.ACTION_PRE_MUTE,
            Constants.REQUEST_CODE_PRE_MUTE,
            preMuteCalendar.get(Calendar.HOUR_OF_DAY),
            preMuteCalendar.get(Calendar.MINUTE)
        )

        scheduleAlarm(
            Constants.ACTION_UNMUTE,
            Constants.REQUEST_CODE_UNMUTE,
            preferenceHelper.unmuteHour,
            preferenceHelper.unmuteMinute
        )
    }

    private fun scheduleAlarm(action: String, requestCode: Int, hour: Int, minute: Int) {
        val intent = Intent(context, ScheduleReceiver::class.java)
        intent.action = action

        // Cancel existing PendingIntent before creating a new one
        val existingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )

        if (existingIntent != null) {
            alarmManager.cancel(existingIntent)
            existingIntent.cancel()
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val calendar = Calendar.getInstance()
        calendar.timeInMillis = System.currentTimeMillis()
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // If the time has already passed today, schedule for tomorrow
        if (calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.i("AlarmScheduler", "Scheduled $action for ${calendar.time}")
        } catch (e: SecurityException) {
            Log.e("AlarmScheduler", "SecurityException when scheduling exact alarm", e)
            // Fallback
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    fun cancelAlarms() {
        cancelAlarm(Constants.ACTION_MUTE, Constants.REQUEST_CODE_MUTE)
        cancelAlarm(Constants.ACTION_UNMUTE, Constants.REQUEST_CODE_UNMUTE)
        Log.i("AlarmScheduler", "All alarms cancelled")
    }

    private fun cancelAlarm(action: String, requestCode: Int) {
        val intent = Intent(context, ScheduleReceiver::class.java)
        intent.action = action
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
        pendingIntent.cancel()
    }
}