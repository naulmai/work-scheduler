package com.example.ringerscheduler

object Constants {
    const val PREFS_NAME = "ringer_scheduler_prefs"
    const val KEY_MUTE_HOUR = "mute_hour"
    const val KEY_MUTE_MINUTE = "mute_minute"
    const val KEY_UNMUTE_HOUR = "unmute_hour"
    const val KEY_UNMUTE_MINUTE = "unmute_minute"
    const val KEY_HOME_WIFI_SSID = "home_wifi_ssid"
    const val KEY_SCHEDULE_ENABLED = "schedule_enabled"
    const val KEY_PREV_RING_VOLUME = "prev_ring_volume"
    const val KEY_PREV_RINGER_MODE = "prev_ringer_mode"
    const val KEY_ACTIVE_DAYS = "active_days"

    const val ACTION_MUTE = "com.example.ringerscheduler.ACTION_MUTE"
    const val ACTION_UNMUTE = "com.example.ringerscheduler.ACTION_UNMUTE"

    const val REQUEST_CODE_MUTE = 100
    const val REQUEST_CODE_UNMUTE = 101
}
