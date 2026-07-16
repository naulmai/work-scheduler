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
        if (preferenceHelper.isPreMuteEnabled) {
            val muteCalendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, preferenceHelper.muteHour)
                set(Calendar.MINUTE, preferenceHelper.muteMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            
            val now = System.currentTimeMillis()
            val muteTime = muteCalendar.timeInMillis
            val preMuteTime = muteTime - (10 * 60 * 1000)
            
            val todayStr = "${Calendar.getInstance().get(Calendar.YEAR)}-${Calendar.getInstance().get(Calendar.DAY_OF_YEAR)}"

            // Battery Optimizer: If we are currently in the 10-minute window before muting, 
            // show notification immediately but ONLY IF not shown today already.
            if (now in preMuteTime until muteTime && preferenceHelper.lastPreMuteDate != todayStr) {
                context.sendBroadcast(Intent(context, ScheduleReceiver::class.java).apply {
                    action = Constants.ACTION_PRE_MUTE
                })
            }
            
            val preMuteCalendar = Calendar.getInstance().apply {
                timeInMillis = preMuteTime
            }
            
            scheduleAlarm(
                Constants.ACTION_PRE_MUTE,
                Constants.REQUEST_CODE_PRE_MUTE,
                preMuteCalendar.get(Calendar.HOUR_OF_DAY),
                preMuteCalendar.get(Calendar.MINUTE)
            )
        } else {
            cancelAlarm(Constants.ACTION_PRE_MUTE, Constants.REQUEST_CODE_PRE_MUTE)
        }

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

        val triggerTime = calendar.timeInMillis
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                }
            } else {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }
            Log.i("AlarmScheduler", "Scheduled $action for ${calendar.time} (High Accuracy)")
        } catch (e: Exception) {
            // Fallback for safety
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.e("AlarmScheduler", "Fallback used for $action", e)
        }
    }

    fun cancelAlarms() {
        cancelAlarm(Constants.ACTION_MUTE, Constants.REQUEST_CODE_MUTE)
        cancelAlarm(Constants.ACTION_UNMUTE, Constants.REQUEST_CODE_UNMUTE)
        cancelAlarm(Constants.ACTION_PRE_MUTE, Constants.REQUEST_CODE_PRE_MUTE)
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