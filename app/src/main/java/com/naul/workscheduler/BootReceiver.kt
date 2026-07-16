package com.naul.workscheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("BootReceiver", "Received intent action: $action")
        
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            action == Intent.ACTION_TIME_CHANGED || 
            action == Intent.ACTION_TIMEZONE_CHANGED) {
            
            val preferenceHelper = PreferenceHelper(context)
            if (preferenceHelper.isScheduleEnabled) {
                val alarmScheduler = AlarmScheduler(context)
                alarmScheduler.scheduleAlarms()
                Log.i("BootReceiver", "Alarms successfully rescheduled after $action")
            }
        }
    }
}
