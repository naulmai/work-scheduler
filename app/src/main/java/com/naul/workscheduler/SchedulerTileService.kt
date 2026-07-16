package com.naul.workscheduler

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class SchedulerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val preferenceHelper = PreferenceHelper(this)
        updateTile(preferenceHelper.isScheduleEnabled)
    }

    override fun onClick() {
        super.onClick()
        val preferenceHelper = PreferenceHelper(this)
        val newState = !preferenceHelper.isScheduleEnabled
        preferenceHelper.isScheduleEnabled = newState
        
        val alarmScheduler = AlarmScheduler(this)
        if (newState) {
            alarmScheduler.scheduleAlarms()
        } else {
            alarmScheduler.cancelAlarms()
        }
        
        updateTile(newState)
    }

    private fun updateTile(isEnabled: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Work Scheduler"
        tile.updateTile()
    }
}
