package com.naul.workscheduler

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class BackupManager(private val context: Context) {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val preferenceHelper = PreferenceHelper(context)

    fun backupData(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false)
            return
        }

        val data = hashMapOf(
            "muteHour" to preferenceHelper.muteHour,
            "muteMinute" to preferenceHelper.muteMinute,
            "unmuteHour" to preferenceHelper.unmuteHour,
            "unmuteMinute" to preferenceHelper.unmuteMinute,
            "homeWifiSsids" to preferenceHelper.homeWifiSsids.toList(),
            "activeDays" to preferenceHelper.activeDays.toList(),
            "workLat" to preferenceHelper.workLat,
            "workLng" to preferenceHelper.workLng,
            "geofencingEnabled" to preferenceHelper.isGeofencingEnabled,
            "lastBackup" to System.currentTimeMillis()
        )

        db.collection("users").document(user.uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Log.i("BackupManager", "Backup successful")
                onComplete(true)
            }
            .addOnFailureListener {
                Log.e("BackupManager", "Backup failed", it)
                onComplete(false)
            }
    }

    fun restoreData(onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser
        if (user == null) {
            onComplete(false)
            return
        }

        db.collection("users").document(user.uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    preferenceHelper.muteHour = (document.getLong("muteHour") ?: 8L).toInt()
                    preferenceHelper.muteMinute = (document.getLong("muteMinute") ?: 30L).toInt()
                    preferenceHelper.unmuteHour = (document.getLong("unmuteHour") ?: 18L).toInt()
                    preferenceHelper.unmuteMinute = (document.getLong("unmuteMinute") ?: 0L).toInt()
                    
                    val ssids = document.get("homeWifiSsids") as? List<String>
                    if (ssids != null) {
                        preferenceHelper.homeWifiSsids = ssids.toSet()
                    }
                    
                    val days = document.get("activeDays") as? List<String>
                    if (days != null) {
                        preferenceHelper.activeDays = days.toSet()
                    }
                    
                    preferenceHelper.workLat = (document.getDouble("workLat") ?: 0.0).toFloat()
                    preferenceHelper.workLng = (document.getDouble("workLng") ?: 0.0).toFloat()
                    preferenceHelper.isGeofencingEnabled = document.getBoolean("geofencingEnabled") ?: false
                    
                    Log.i("BackupManager", "Restore successful")
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
            .addOnFailureListener {
                Log.e("BackupManager", "Restore failed", it)
                onComplete(false)
            }
    }
}
