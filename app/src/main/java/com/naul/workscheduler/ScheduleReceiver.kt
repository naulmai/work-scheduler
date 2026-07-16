package com.naul.workscheduler

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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.os.Build
import android.util.Log
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit

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
            Constants.ACTION_MUTE -> {
                if (preferenceHelper.skipNextMute) {
                    Log.i("ScheduleReceiver", "SkipNextMute is enabled. Skipping this mute action.")
                    preferenceHelper.skipNextMute = false
                } else {
                    handleMuteAction(context, audioManager, preferenceHelper)
                }
            }
            Constants.ACTION_UNMUTE -> handleUnmuteAction(audioManager, preferenceHelper)
            Constants.ACTION_PRE_MUTE -> handlePreMuteAction(context, preferenceHelper)
            Constants.ACTION_SKIP_MUTE -> {
                preferenceHelper.skipNextMute = true
                Log.i("ScheduleReceiver", "User requested to skip next mute.")
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.cancel(200) // Cancel pre-mute notification
            }
        }

        // Reschedule alarms for the next day
        val alarmScheduler = AlarmScheduler(context)
        alarmScheduler.scheduleAlarms()
    }

    private fun handleMuteAction(context: Context, audioManager: AudioManager, preferenceHelper: PreferenceHelper) {
        val homeSsid = normalizeSsid(preferenceHelper.homeWifiSsid)
        val currentSsid = normalizeSsid(getCurrentWifiSsid(context) ?: "")

        // Check 1: Home Wi-Fi Exception (If at home, skip muting regardless of other conditions)
        if (homeSsid.isNotEmpty() && currentSsid.equals(homeSsid, ignoreCase = true)) {
            Log.i("ScheduleReceiver", "At home (SSID: $currentSsid), skipping mute.")
            return
        }

        // Check 2: Location Requirement (If "Require this location" is ON, we must be at work)
        if (preferenceHelper.isGeofencingEnabled) {
            val currentLoc = getCurrentLocation(context)
            val isAtWork = if (currentLoc != null) {
                val distance = calculateDistance(
                    currentLoc.first, currentLoc.second,
                    preferenceHelper.workLat.toDouble(), preferenceHelper.workLng.toDouble()
                )
                Log.d("ScheduleReceiver", "Distance to work: ${distance}m (Radius: ${preferenceHelper.geofenceRadius}m)")
                distance <= preferenceHelper.geofenceRadius
            } else {
                Log.w("ScheduleReceiver", "Could not determine location. Falling back to Mute for safety.")
                true // Fallback to muting if location can't be determined but requirement is ON
            }

            if (!isAtWork) {
                Log.i("ScheduleReceiver", "Not at work location. Skipping mute.")
                return
            }
        }

        // Check 3: Current Ringer Mode
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

    private fun handlePreMuteAction(context: Context, preferenceHelper: PreferenceHelper) {
        Log.i("ScheduleReceiver", "Sending pre-mute notification.")
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "ringer_scheduler_pre_mute"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Scheduler Alerts", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val skipIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = Constants.ACTION_SKIP_MUTE
        }
        val skipPendingIntent = PendingIntent.getBroadcast(
            context, 0, skipIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Work Scheduler")
            .setContentText("Silent mode starting in 10 mins. Skip for today?")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "Skip", skipPendingIntent)
            .build()

        notificationManager.notify(200, notification)
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

    private fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        return try {
            val task = fusedLocationClient.lastLocation
            val location = Tasks.await(task, 5, TimeUnit.SECONDS)
            if (location != null) {
                Pair(location.latitude, location.longitude)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("ScheduleReceiver", "Error getting location", e)
            null
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val results = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, results)
        return results[0]
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
