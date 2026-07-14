package com.example.ringerscheduler

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log

import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("ScheduleReceiver", "Received action: $action")

        val preferenceHelper = PreferenceHelper(context)
        if (!preferenceHelper.isScheduleEnabled) {
            Log.i("ScheduleReceiver", "Schedule is disabled. Ignoring.")
            return
        }

        val calendar = Calendar.getInstance()
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK).toString()
        if (!preferenceHelper.activeDays.contains(currentDay)) {
            Log.i("ScheduleReceiver", "Today ($currentDay) is not an active day. Skipping.")
            // Reschedule for next check
            AlarmScheduler(context).scheduleAlarms()
            return
        }

        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        when (action) {
            Constants.ACTION_MUTE -> handleMuteAction(context, audioManager, preferenceHelper)
            Constants.ACTION_UNMUTE -> handleUnmuteAction(audioManager, preferenceHelper)
        }

        // Reschedule alarms for the next day
        val alarmScheduler = AlarmScheduler(context)
        alarmScheduler.scheduleAlarms()
    }

    private fun handleMuteAction(context: Context, audioManager: AudioManager, preferenceHelper: PreferenceHelper) {
        val targetSsid = normalizeSsid(preferenceHelper.homeWifiSsid)
        Log.d("ScheduleReceiver", "Target SSID (normalized): '$targetSsid'")
        
        if (targetSsid.isNotEmpty()) {
            val currentSsid = normalizeSsid(getCurrentWifiSsid(context) ?: "")
            Log.d("ScheduleReceiver", "Current SSID (normalized): '$currentSsid'")
            
            if (currentSsid.isNotEmpty() && currentSsid.equals(targetSsid, ignoreCase = true)) {
                Log.i("ScheduleReceiver", "MATCH FOUND: At home (SSID: $currentSsid), skipping mute.")
                return
            }
        }

        // Xiaomi Fix: Check if phone is already silent or vibrate
        val currentRingerMode = audioManager.ringerMode
        Log.d("ScheduleReceiver", "Current ringer mode: $currentRingerMode")
        if (currentRingerMode == AudioManager.RINGER_MODE_SILENT || currentRingerMode == AudioManager.RINGER_MODE_VIBRATE) {
            Log.i("ScheduleReceiver", "Phone is already silent or vibrate. Skipping.")
            return
        }

        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
        Log.d("ScheduleReceiver", "Current ring volume: $currentVolume")
        
        preferenceHelper.prevRingVolume = currentVolume
        preferenceHelper.prevRingerMode = currentRingerMode

        try {
            audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            Log.i("ScheduleReceiver", "SUCCESS: Set ringer mode to SILENT")
        } catch (e: SecurityException) {
            Log.e("ScheduleReceiver", "ERROR: Not allowed to change ringer mode", e)
        }
    }

    private fun handleUnmuteAction(audioManager: AudioManager, preferenceHelper: PreferenceHelper) {
        val prevMode = preferenceHelper.prevRingerMode
        val prevVolume = preferenceHelper.prevRingVolume
        Log.d("ScheduleReceiver", "Restoring state - Mode: $prevMode, Volume: $prevVolume")

        if (prevMode != -1) {
            try {
                audioManager.ringerMode = prevMode
                Log.i("ScheduleReceiver", "Restored ringer mode to $prevMode")
            } catch (e: SecurityException) {
                Log.e("ScheduleReceiver", "Not allowed to change ringer mode", e)
            }
        } else {
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                Log.i("ScheduleReceiver", "Fallback: Set ringer mode to NORMAL")
            } catch (e: SecurityException) {
                 Log.e("ScheduleReceiver", "Not allowed to change ringer mode", e)
            }
        }

        if (prevVolume != -1) {
            try {
                audioManager.setStreamVolume(AudioManager.STREAM_RING, prevVolume, 0)
                Log.i("ScheduleReceiver", "Restored ring volume to $prevVolume")
            } catch (e: SecurityException) {
                Log.e("ScheduleReceiver", "Not allowed to change volume", e)
            }
        }
        
        preferenceHelper.prevRingerMode = -1
        preferenceHelper.prevRingVolume = -1
    }

    private fun getCurrentWifiSsid(context: Context): String? {
        val hasFine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        Log.d("ScheduleReceiver", "Permissions - Fine: $hasFine, Coarse: $hasCoarse")
        
        if (!hasFine && !hasCoarse) {
            Log.w("ScheduleReceiver", "Location permission missing.")
            return null
        }
        
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        }
        Log.d("ScheduleReceiver", "GPS Enabled: $isGpsEnabled")

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Strategy 1: ConnectivityManager (Modern)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            Log.d("ScheduleReceiver", "Network: $network, Capabilities: ${capabilities != null}")
            
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                val transportInfo = capabilities.transportInfo
                Log.d("ScheduleReceiver", "TransportInfo type: ${transportInfo?.javaClass?.simpleName}")
                if (transportInfo is WifiInfo) {
                    val ssid = stripQuotes(transportInfo.ssid)
                    if (ssid != null && ssid != "<unknown ssid>") {
                        Log.d("ScheduleReceiver", "SSID from ConnectivityManager: $ssid")
                        return ssid
                    }
                }
            }
        }

        // Strategy 2: WifiManager (Legacy/Fallback)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfo = wifiManager.connectionInfo
        Log.d("ScheduleReceiver", "WifiInfo from WifiManager: ${wifiInfo != null}, SSID: ${wifiInfo?.ssid}")
        
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val ssid = stripQuotes(wifiInfo.ssid)
            if (ssid != null && ssid != "<unknown ssid>") {
                Log.d("ScheduleReceiver", "SSID from WifiManager: $ssid")
                return ssid
            }
        }

        return null
    }

    private fun normalizeSsid(ssid: String): String {
        return ssid.replace("\"", "").trim()
    }

    private fun stripQuotes(ssid: String?): String? {
        if (ssid == null || ssid == "<unknown ssid>") return null
        return if (ssid.startsWith("\"") && ssid.endsWith("\"") && ssid.length >= 2) {
            ssid.substring(1, ssid.length - 1)
        } else {
            ssid
        }
    }
}
