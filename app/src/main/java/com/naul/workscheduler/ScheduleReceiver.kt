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
import androidx.core.app.NotificationCompat
import android.os.Build
import android.util.Log
import android.location.Location
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import java.util.Calendar

class ScheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val action = intent.action
        Log.i("ScheduleReceiver", "Received action: $action")

        val preferenceHelper = PreferenceHelper(context)
        val dbHelper = LogDatabaseHelper(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Use a background thread for the entire flow to avoid IllegalStateException on main thread
        Thread {
            try {
                processBroadcast(context, action, preferenceHelper, dbHelper, audioManager)
            } finally {
                pendingResult.finish()
            }
        }.start()
    }

    private fun processBroadcast(context: Context, action: String?, preferenceHelper: PreferenceHelper, dbHelper: LogDatabaseHelper, audioManager: AudioManager) {
        // 1. Check Master Switch
        if (!preferenceHelper.isScheduleEnabled) {
            applyUnmute(audioManager, preferenceHelper, dbHelper, "Schedule Disabled")
            return
        }

        // 2. Check Active Days
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_WEEK).toString()
        if (!preferenceHelper.activeDays.contains(currentDay)) {
            if (action != Constants.ACTION_PRE_MUTE) {
                applyUnmute(audioManager, preferenceHelper, dbHelper, "Not a work day")
            }
            return
        }

        when (action) {
            Constants.ACTION_MUTE, Constants.ACTION_UNMUTE, Constants.ACTION_CHECK_STATUS -> {
                runEvaluationFlow(context, audioManager, preferenceHelper, dbHelper)
            }
            Constants.ACTION_PRE_MUTE -> handlePreMuteAction(context, preferenceHelper)
        }

        AlarmScheduler(context).scheduleAlarms()
    }

    private fun runEvaluationFlow(context: Context, audioManager: AudioManager, preferenceHelper: PreferenceHelper, dbHelper: LogDatabaseHelper) {
        // STEP 1: TIME WINDOW CHECK
        if (!isCurrentTimeInMuteWindow(preferenceHelper)) {
            applyUnmute(audioManager, preferenceHelper, dbHelper, "Outside time window")
            return
        }

        // STEP 2: HOME WIFI CHECK
        val currentSsid = normalizeSsid(getCurrentWifiSsid(context) ?: "")
        val homeSsids = preferenceHelper.homeWifiSsids.map { normalizeSsid(it) }
        
        if (homeSsids.isNotEmpty()) {
            if (homeSsids.any { it.equals(currentSsid, ignoreCase = true) }) {
                dbHelper.addLog("Check: Home Wi-Fi detected ($currentSsid)")
                applyUnmute(audioManager, preferenceHelper, dbHelper, "Home Override")
                return
            } else {
                val ssidDisplay = if (currentSsid.isEmpty()) "Disconnected" else currentSsid
                dbHelper.addLog("Check: Not on Home Wi-Fi ($ssidDisplay)")
            }
        }

        // STEP 3: WORK LOCATION CHECK
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isSystemGpsOn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) locationManager.isLocationEnabled else locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        val gpsStatus = if (isSystemGpsOn) "ON" else "OFF"

        if (!preferenceHelper.isGeofencingEnabled) {
            dbHelper.addLog("Check: Location Restriction is OFF (GPS is $gpsStatus)")
            applyMute(audioManager, preferenceHelper, dbHelper)
            return
        }

        // STEP 4: GEOFENCING ON -> CHECK LOCATION
        dbHelper.addLog("Check: Location Restriction is ON (GPS is $gpsStatus)")
        val currentLoc = getCurrentLocation(context)
        if (currentLoc == null) {
            dbHelper.addLog("Result: Location unavailable (Muting for safety)")
            applyMute(audioManager, preferenceHelper, dbHelper)
        } else {
            val dist = calculateDistance(
                currentLoc.first, currentLoc.second,
                preferenceHelper.workLat.toDouble(), preferenceHelper.workLng.toDouble()
            )
            val distInt = dist.toInt()
            if (dist <= preferenceHelper.geofenceRadius) {
                dbHelper.addLog("Result: At Work Area (${distInt}m) -> MUTE")
                applyMute(audioManager, preferenceHelper, dbHelper)
            } else {
                dbHelper.addLog("Result: Outside Work Area (${distInt}m) -> UNMUTE")
                applyUnmute(audioManager, preferenceHelper, dbHelper, "Not at Office")
            }
        }
    }

    private fun applyMute(audioManager: AudioManager, preferenceHelper: PreferenceHelper, dbHelper: LogDatabaseHelper) {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_SILENT) {
            preferenceHelper.prevRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
            preferenceHelper.prevRingerMode = audioManager.ringerMode
            try {
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
                dbHelper.addLog("Active: Muted by Schedule")
            } catch (e: Exception) {
                dbHelper.addLog("Error: Missing DND Permission")
            }
        }
    }

    private fun applyUnmute(audioManager: AudioManager, preferenceHelper: PreferenceHelper, dbHelper: LogDatabaseHelper, reason: String) {
        if (audioManager.ringerMode != AudioManager.RINGER_MODE_NORMAL) {
            val prevMode = preferenceHelper.prevRingerMode
            val prevVol = preferenceHelper.prevRingVolume
            try {
                if (prevMode != -1) {
                    audioManager.ringerMode = prevMode
                    if (prevVol != -1) audioManager.setStreamVolume(AudioManager.STREAM_RING, prevVol, 0)
                } else {
                    audioManager.ringerMode = AudioManager.RINGER_MODE_NORMAL
                }
                dbHelper.addLog("Active: Unmuted ($reason)")
            } catch (e: Exception) {
                dbHelper.addLog("Error: Sound restoration failed")
            }
            preferenceHelper.prevRingerMode = -1
            preferenceHelper.prevRingVolume = -1
        }
    }

    private fun isCurrentTimeInMuteWindow(prefs: PreferenceHelper): Boolean {
        val now = Calendar.getInstance()
        val currMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
        val startMin = prefs.muteHour * 60 + prefs.muteMinute
        val endMin = prefs.unmuteHour * 60 + prefs.unmuteMinute
        return if (startMin < endMin) currMin in startMin until endMin else currMin >= startMin || currMin < endMin
    }

    private fun handlePreMuteAction(context: Context, preferenceHelper: PreferenceHelper) {
        val todayStr = "${Calendar.getInstance().get(Calendar.YEAR)}-${Calendar.getInstance().get(Calendar.DAY_OF_YEAR)}"
        preferenceHelper.lastPreMuteDate = todayStr
        val dbHelper = LogDatabaseHelper(context)
        dbHelper.addLog("Notified: Silent mode in 10 mins")
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "ringer_scheduler_silent_notif"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(chId, "Scheduler Alerts", NotificationManager.IMPORTANCE_LOW))
        }
        val notif = NotificationCompat.Builder(context, chId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Work Scheduler")
            .setContentText("Silent mode will start in 10 minutes")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        nm.notify(200, notif)
    }

    private fun getCurrentWifiSsid(context: Context): String? {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        
        // Strategy 1: Modern API (API 29+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val network = connectivityManager.activeNetwork
            val capabilities = connectivityManager.getNetworkCapabilities(network)
            if (capabilities != null && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                // IMPORTANT: On Android 12+, we must try to get WifiInfo from transportInfo
                val transportInfo = capabilities.transportInfo
                if (transportInfo is WifiInfo) {
                    val ssid = stripQuotes(transportInfo.ssid)
                    if (ssid != null && ssid != "<unknown ssid>") return ssid
                }
            }
        }

        // Strategy 2: Legacy WifiManager (More reliable on many devices even with new APIs)
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        if (wifiInfo != null && wifiInfo.networkId != -1) {
            val ssid = stripQuotes(wifiInfo.ssid)
            if (ssid != null && ssid != "<unknown ssid>") return ssid
        }

        // Strategy 3: Check via ConnectivityManager NetworkInfo (Last resort fallback)
        @Suppress("DEPRECATION")
        val activeNetworkInfo = connectivityManager.activeNetworkInfo
        if (activeNetworkInfo != null && activeNetworkInfo.isConnected && activeNetworkInfo.type == ConnectivityManager.TYPE_WIFI) {
            val extraInfo = activeNetworkInfo.extraInfo
            if (extraInfo != null) {
                return stripQuotes(extraInfo)
            }
        }

        return null
    }

    private fun getCurrentLocation(context: Context): Pair<Double, Double>? {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return null
        }
        val flc = LocationServices.getFusedLocationProviderClient(context)
        return try {
            // 1. Try last known location first (Zero energy cost)
            val lastTask = flc.lastLocation
            val lastLoc = Tasks.await(lastTask, 3, TimeUnit.SECONDS)
            if (lastLoc != null && (System.currentTimeMillis() - lastLoc.time) < 300_000) {
                return Pair(lastLoc.latitude, lastLoc.longitude)
            }

            // 2. Request fresh location with HIGH ACCURACY
            val req = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .build()
            
            val freshTask = flc.getCurrentLocation(req, null)
            val freshLoc = Tasks.await(freshTask, 15, TimeUnit.SECONDS) // Wait up to 15s for GPS warm-up
            
            if (freshLoc != null) {
                Pair(freshLoc.latitude, freshLoc.longitude)
            } else {
                Log.w("ScheduleReceiver", "Location engine returned null")
                null
            }
        } catch (e: Exception) {
            val dbHelper = LogDatabaseHelper(context)
            dbHelper.addLog("Debug: GPS Error (${e.javaClass.simpleName})")
            null
        }
    }

    private fun calculateDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val res = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, res)
        return res[0]
    }

    private fun normalizeSsid(s: String): String = s.replace("\"", "").trim()
    private fun stripQuotes(s: String?): String? {
        if (s == null || s == "<unknown ssid>") return null
        return if (s.startsWith("\"") && s.endsWith("\"")) s.substring(1, s.length - 1) else s
    }
}
