package com.naul.workscheduler

import android.content.Context
import android.content.SharedPreferences

class PreferenceHelper(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    var muteHour: Int
        get() = prefs.getInt(Constants.KEY_MUTE_HOUR, 8)
        set(value) = prefs.edit().putInt(Constants.KEY_MUTE_HOUR, value).apply()

    var muteMinute: Int
        get() = prefs.getInt(Constants.KEY_MUTE_MINUTE, 30)
        set(value) = prefs.edit().putInt(Constants.KEY_MUTE_MINUTE, value).apply()

    var unmuteHour: Int
        get() = prefs.getInt(Constants.KEY_UNMUTE_HOUR, 18)
        set(value) = prefs.edit().putInt(Constants.KEY_UNMUTE_HOUR, value).apply()

    var unmuteMinute: Int
        get() = prefs.getInt(Constants.KEY_UNMUTE_MINUTE, 0)
        set(value) = prefs.edit().putInt(Constants.KEY_UNMUTE_MINUTE, value).apply()

    var homeWifiSsids: Set<String>
        get() = prefs.getStringSet(Constants.KEY_HOME_WIFI_SSIDS, emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet(Constants.KEY_HOME_WIFI_SSIDS, value).apply()

    var isScheduleEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_SCHEDULE_ENABLED, value).apply()

    var prevRingVolume: Int
        get() = prefs.getInt(Constants.KEY_PREV_RING_VOLUME, -1)
        set(value) = prefs.edit().putInt(Constants.KEY_PREV_RING_VOLUME, value).apply()

    var prevRingerMode: Int
        get() = prefs.getInt(Constants.KEY_PREV_RINGER_MODE, -1)
        set(value) = prefs.edit().putInt(Constants.KEY_PREV_RINGER_MODE, value).apply()

    var activeDays: Set<String>
        get() = prefs.getStringSet(Constants.KEY_ACTIVE_DAYS, setOf("2", "3", "4", "5", "6")) ?: setOf("2", "3", "4", "5", "6")
        set(value) = prefs.edit().putStringSet(Constants.KEY_ACTIVE_DAYS, value).apply()

    var workLat: Float
        get() = prefs.getFloat(Constants.KEY_WORK_LAT, 0f)
        set(value) = prefs.edit().putFloat(Constants.KEY_WORK_LAT, value).apply()

    var workLng: Float
        get() = prefs.getFloat(Constants.KEY_WORK_LNG, 0f)
        set(value) = prefs.edit().putFloat(Constants.KEY_WORK_LNG, value).apply()

    var geofenceRadius: Float
        get() = prefs.getFloat(Constants.KEY_GEOFENCE_RADIUS, 100f) // Default 100m
        set(value) = prefs.edit().putFloat(Constants.KEY_GEOFENCE_RADIUS, value).apply()

    var isGeofencingEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_GEOFENCING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(Constants.KEY_GEOFENCING_ENABLED, value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean(Constants.KEY_IS_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_IS_DARK_MODE, value).apply()

    var lastPreMuteDate: String
        get() = prefs.getString("last_pre_mute_date", "") ?: ""
        set(value) = prefs.edit().putString("last_pre_mute_date", value).apply()

    var isPreMuteEnabled: Boolean
        get() = prefs.getBoolean(Constants.KEY_PRE_MUTE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(Constants.KEY_PRE_MUTE_ENABLED, value).apply()
}
