package com.naul.workscheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return
        if (geofencingEvent.hasError()) {
            Log.e("GeofenceReceiver", "Geofencing error: ${geofencingEvent.errorCode}")
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val preferenceHelper = PreferenceHelper(context)

        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            Log.i("GeofenceReceiver", "Entered geofence. Muting.")
            // Reuse existing mute logic (simplified here)
            val currentRingerMode = audioManager.ringerMode
            if (currentRingerMode != AudioManager.RINGER_MODE_SILENT) {
                preferenceHelper.prevRingerMode = currentRingerMode
                preferenceHelper.prevRingVolume = audioManager.getStreamVolume(AudioManager.STREAM_RING)
                audioManager.ringerMode = AudioManager.RINGER_MODE_SILENT
            }
        } else if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_EXIT) {
            Log.i("GeofenceReceiver", "Exited geofence. Unmuting.")
            // Reuse existing unmute logic (simplified here)
            val prevMode = preferenceHelper.prevRingerMode
            if (prevMode != -1) {
                audioManager.ringerMode = prevMode
                val prevVolume = preferenceHelper.prevRingVolume
                if (prevVolume != -1) {
                    audioManager.setStreamVolume(AudioManager.STREAM_RING, prevVolume, 0)
                }
                preferenceHelper.prevRingerMode = -1
                preferenceHelper.prevRingVolume = -1
            }
        }
    }
}
