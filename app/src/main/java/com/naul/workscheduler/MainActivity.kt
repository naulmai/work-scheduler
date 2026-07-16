package com.naul.workscheduler

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var tvMuteTime: TextView
    private lateinit var tvUnmuteTime: TextView
    private lateinit var etWifiSsid: EditText
    private lateinit var switchSchedule: SwitchMaterial
    private lateinit var tvCurrentStatus: TextView
    private lateinit var tvLatestLog: TextView
    private lateinit var cardStatus: com.google.android.material.card.MaterialCardView
    private lateinit var ivStatusIcon: ImageView
    private lateinit var btnGrantDnd: MaterialButton
    private lateinit var btnGrantAlarm: MaterialButton
    private lateinit var btnGrantLocation: MaterialButton
    private lateinit var btnGrantBackgroundLocation: MaterialButton
    private lateinit var btnGrantNotifications: MaterialButton
    private lateinit var ivCheckDnd: ImageView
    private lateinit var ivCheckAlarm: ImageView
    private lateinit var ivCheckLocation: ImageView
    private lateinit var ivCheckBackgroundLocation: ImageView
    private lateinit var ivCheckNotifications: ImageView
    private lateinit var chipGroupDays: ChipGroup
    private lateinit var btnPickWifi: MaterialButton
    private lateinit var pbWifiScan: ProgressBar
    private lateinit var btnBatterySettings: MaterialButton
    private lateinit var btnThemeToggle: MaterialButton
    
    private lateinit var tvCloudStatus: TextView
    private lateinit var btnBackup: MaterialButton
    private lateinit var btnRestore: MaterialButton

    private lateinit var tvWorkLocation: TextView
    private lateinit var btnSetWorkLocation: MaterialButton
    private lateinit var switchGeofencing: SwitchMaterial

    private lateinit var preferenceHelper: PreferenceHelper
    private lateinit var alarmScheduler: AlarmScheduler
    private lateinit var backupManager: BackupManager
    private lateinit var dbHelper: LogDatabaseHelper
    
    private val auth = FirebaseAuth.getInstance()

    private val requestLocationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        updatePermissionStatus()
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        if (!fineLocationGranted) {
            Toast.makeText(this, "Location permission denied. Wi-Fi exception will be skipped.", Toast.LENGTH_LONG).show()
        }
    }

    private val requestNotificationPolicyLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    private val requestExactAlarmLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferenceHelper = PreferenceHelper(this)
        
        // Apply theme before super.onCreate
        if (preferenceHelper.isDarkMode) {
            setTheme(R.style.Theme_WorkScheduler)
        } else {
            setTheme(R.style.Theme_WorkScheduler_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        alarmScheduler = AlarmScheduler(this)
        backupManager = BackupManager(this)
        dbHelper = LogDatabaseHelper(this)

        initViews()
        setupUI()
        
        // Update theme toggle icon
        btnThemeToggle.setIconResource(if (preferenceHelper.isDarkMode) R.drawable.ic_sun else R.drawable.ic_moon)

        checkPermissions()
        updateCloudUI()
        updateLatestLog()
    }

    private fun initViews() {
        tvMuteTime = findViewById(R.id.tvMuteTime)
        tvUnmuteTime = findViewById(R.id.tvUnmuteTime)
        etWifiSsid = findViewById(R.id.etWifiSsid)
        switchSchedule = findViewById(R.id.switchSchedule)
        tvCurrentStatus = findViewById(R.id.tvCurrentStatus)
        tvLatestLog = findViewById(R.id.tvLatestLog)
        cardStatus = findViewById(R.id.cardStatus)
        ivStatusIcon = findViewById(R.id.ivStatusIcon)
        btnGrantDnd = findViewById(R.id.btnGrantDnd)
        btnGrantAlarm = findViewById(R.id.btnGrantAlarm)
        btnGrantLocation = findViewById(R.id.btnGrantLocation)
        btnGrantBackgroundLocation = findViewById(R.id.btnGrantBackgroundLocation)
        btnGrantNotifications = findViewById(R.id.btnGrantNotifications)
        ivCheckDnd = findViewById(R.id.ivCheckDnd)
        ivCheckAlarm = findViewById(R.id.ivCheckAlarm)
        ivCheckLocation = findViewById(R.id.ivCheckLocation)
        ivCheckBackgroundLocation = findViewById(R.id.ivCheckBackgroundLocation)
        ivCheckNotifications = findViewById(R.id.ivCheckNotifications)
        chipGroupDays = findViewById(R.id.chipGroupDays)
        btnPickWifi = findViewById(R.id.btnPickWifi)
        pbWifiScan = findViewById(R.id.pbWifiScan)
        btnBatterySettings = findViewById(R.id.btnBatterySettings)
        btnThemeToggle = findViewById(R.id.btnThemeToggle)
        tvCloudStatus = findViewById(R.id.tvCloudStatus)
        btnBackup = findViewById(R.id.btnBackup)
        btnRestore = findViewById(R.id.btnRestore)
        tvWorkLocation = findViewById(R.id.tvWorkLocation)
        btnSetWorkLocation = findViewById(R.id.btnSetWorkLocation)
        switchGeofencing = findViewById(R.id.switchGeofencing)
    }

    private fun setupUI() {
        updateTimeText(tvMuteTime, preferenceHelper.muteHour, preferenceHelper.muteMinute)
        updateTimeText(tvUnmuteTime, preferenceHelper.unmuteHour, preferenceHelper.unmuteMinute)
        
        etWifiSsid.setText(preferenceHelper.homeWifiSsid)
        switchSchedule.isChecked = preferenceHelper.isScheduleEnabled

        btnPickWifi.setOnClickListener {
            scanAndPickWifi()
        }

        findViewById<View>(R.id.btnMuteTime).setOnClickListener {
            showTimePicker("Select Start Time", preferenceHelper.muteHour, preferenceHelper.muteMinute) { hour, minute ->
                preferenceHelper.muteHour = hour
                preferenceHelper.muteMinute = minute
                updateTimeText(tvMuteTime, hour, minute)
                if (switchSchedule.isChecked) {
                    alarmScheduler.scheduleAlarms()
                }
            }
        }

        findViewById<View>(R.id.btnUnmuteTime).setOnClickListener {
            showTimePicker("Select End Time", preferenceHelper.unmuteHour, preferenceHelper.unmuteMinute) { hour, minute ->
                preferenceHelper.unmuteHour = hour
                preferenceHelper.unmuteMinute = minute
                updateTimeText(tvUnmuteTime, hour, minute)
                if (switchSchedule.isChecked) {
                    alarmScheduler.scheduleAlarms()
                }
            }
        }

        switchSchedule.setOnCheckedChangeListener { _, isChecked ->
            preferenceHelper.homeWifiSsid = etWifiSsid.text.toString().trim()
            preferenceHelper.isScheduleEnabled = isChecked
            
            if (isChecked) {
                if (hasSpecialPermissions()) {
                    alarmScheduler.scheduleAlarms()
                    Toast.makeText(this, "Schedule Enabled", Toast.LENGTH_SHORT).show()
                } else {
                    switchSchedule.isChecked = false
                    preferenceHelper.isScheduleEnabled = false
                    Toast.makeText(this, "Permissions required", Toast.LENGTH_SHORT).show()
                }
            } else {
                alarmScheduler.cancelAlarms()
                Toast.makeText(this, "Schedule Disabled", Toast.LENGTH_SHORT).show()
            }
            updateStatusCard()
        }

        btnThemeToggle.setOnClickListener {
            preferenceHelper.isDarkMode = !preferenceHelper.isDarkMode
            recreate() // Restart activity to apply new theme
        }

        setupDayChips()
        updateStatusCard()
        updatePermissionStatus()

        btnGrantDnd.setOnClickListener {
            val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            requestNotificationPolicyLauncher.launch(intent)
        }

        btnGrantAlarm.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                requestExactAlarmLauncher.launch(intent)
            }
        }

        btnGrantLocation.setOnClickListener {
            val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            
            if (!hasLocationPermission) {
                requestLocationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            } else {
                // If permission is already granted, it must be the GPS that is off
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }

        btnGrantBackgroundLocation.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                AlertDialog.Builder(this)
                    .setTitle("Allow Always Location Access")
                    .setMessage("To detect Wi-Fi in the background, please select 'Allow all the time' in the next screen.")
                    .setPositiveButton("Settings") { _, _ ->
                        requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            } else {
                Toast.makeText(this, "Not required for this Android version", Toast.LENGTH_SHORT).show()
            }
        }

        btnBatterySettings.setOnClickListener {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        }

        btnGrantNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS))
            }
        }

        btnBackup.setOnClickListener {
            if (auth.currentUser == null) {
                signInGoogle()
            } else {
                performBackup()
            }
        }

        btnRestore.setOnClickListener {
            if (auth.currentUser == null) {
                signInGoogle()
            } else {
                performRestore()
            }
        }

        setupGeofencingUI()
    }

    private fun updateCloudUI() {
        val user = auth.currentUser
        if (user != null) {
            tvCloudStatus.text = "Logged in as ${user.email}"
            btnBackup.text = "Backup Now"
        } else {
            tvCloudStatus.text = "Sign in to sync your settings"
            btnBackup.text = "Login to Sync"
        }
    }

    private fun signInGoogle() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private val signInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(Exception::class.java)!!
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: Exception) {
            Log.e("MainActivity", "Google Sign-In failed: ${e.message}", e)
            Toast.makeText(this, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential).addOnCompleteListener(this) { task ->
            if (task.isSuccessful) {
                updateCloudUI()
                Toast.makeText(this, "Cloud integration active!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Firebase Auth failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performBackup() {
        btnBackup.isEnabled = false
        btnBackup.text = "Backing up..."
        backupManager.backupData { success ->
            btnBackup.isEnabled = true
            updateCloudUI()
            if (success) {
                Toast.makeText(this, "Settings saved to cloud", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Backup failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun performRestore() {
        btnRestore.isEnabled = false
        backupManager.restoreData { success ->
            btnRestore.isEnabled = true
            if (success) {
                // UI Refresh
                setupUI()
                updateStatusCard()
                Toast.makeText(this, "Settings restored!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "No backup found or restore failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupGeofencingUI() {
        if (preferenceHelper.workLat != 0f && preferenceHelper.workLng != 0f) {
            tvWorkLocation.text = "Location: Set (Lat: ${String.format("%.4f", preferenceHelper.workLat)})"
        }
        
        switchGeofencing.isChecked = preferenceHelper.isGeofencingEnabled
        
        btnSetWorkLocation.setOnClickListener {
            updateCurrentWorkLocation()
        }

        switchGeofencing.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (preferenceHelper.workLat == 0f) {
                    Toast.makeText(this, "Please set work location first", Toast.LENGTH_SHORT).show()
                    switchGeofencing.isChecked = false
                    return@setOnCheckedChangeListener
                }
                if (!hasBackgroundLocationPermission()) {
                    Toast.makeText(this, "Background location required", Toast.LENGTH_SHORT).show()
                    switchGeofencing.isChecked = false
                    return@setOnCheckedChangeListener
                }
            }
            preferenceHelper.isGeofencingEnabled = isChecked
        }
    }

    private fun setupDayChips() {
        val daysMap = mapOf(
            R.id.chipMon to Calendar.MONDAY.toString(),
            R.id.chipTue to Calendar.TUESDAY.toString(),
            R.id.chipWed to Calendar.WEDNESDAY.toString(),
            R.id.chipThu to Calendar.THURSDAY.toString(),
            R.id.chipFri to Calendar.FRIDAY.toString(),
            R.id.chipSat to Calendar.SATURDAY.toString(),
            R.id.chipSun to Calendar.SUNDAY.toString()
        )

        val activeDays = preferenceHelper.activeDays
        daysMap.forEach { (id, dayValue) ->
            val chip = findViewById<Chip>(id)
            chip.isChecked = activeDays.contains(dayValue)
            chip.setOnCheckedChangeListener { _, isChecked ->
                val currentActive = preferenceHelper.activeDays.toMutableSet()
                if (isChecked) currentActive.add(dayValue) else currentActive.remove(dayValue)
                preferenceHelper.activeDays = currentActive
                if (switchSchedule.isChecked) {
                    alarmScheduler.scheduleAlarms()
                }
            }
        }
    }

    private fun updateStatusCard() {
        val isDark = preferenceHelper.isDarkMode
        val activeColor = ContextCompat.getColor(this, R.color.accent_teal)
        val disabledColor = if (isDark) ContextCompat.getColor(this, R.color.text_secondary_dark) 
                           else ContextCompat.getColor(this, R.color.text_secondary_light)
        
        if (preferenceHelper.isScheduleEnabled) {
            tvCurrentStatus.text = "Scheduler is Active"
            tvCurrentStatus.setTextColor(activeColor)
            ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            ivStatusIcon.setColorFilter(activeColor)
            ivStatusIcon.alpha = 1.0f
        } else {
            tvCurrentStatus.text = "Scheduler is Disabled"
            tvCurrentStatus.setTextColor(disabledColor)
            ivStatusIcon.setImageResource(R.drawable.ic_check_circle)
            ivStatusIcon.setColorFilter(disabledColor)
            ivStatusIcon.alpha = 0.5f
        }
    }

    private fun updatePermissionStatus() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val hasDnd = notificationManager.isNotificationPolicyAccessGranted
        
        val hasExactAlarm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }

        val hasLocationPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        val hasBackgroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        // Check if Location Services (GPS) are enabled
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isGpsEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        // Update DND UI
        if (hasDnd) {
            btnGrantDnd.visibility = View.GONE
            ivCheckDnd.visibility = View.VISIBLE
        } else {
            btnGrantDnd.visibility = View.VISIBLE
            ivCheckDnd.visibility = View.GONE
        }

        // Update Alarm UI
        if (hasExactAlarm) {
            btnGrantAlarm.visibility = View.GONE
            ivCheckAlarm.visibility = View.VISIBLE
        } else {
            btnGrantAlarm.visibility = View.VISIBLE
            ivCheckAlarm.visibility = View.GONE
        }

        // Update Location UI - Must have BOTH permission AND GPS enabled
        if (hasLocationPermission && isGpsEnabled) {
            btnGrantLocation.visibility = View.GONE
            ivCheckLocation.visibility = View.VISIBLE
        } else {
            btnGrantLocation.visibility = View.VISIBLE
            ivCheckLocation.visibility = View.GONE
            
            // Update button text to guide user
            if (!hasLocationPermission) {
                btnGrantLocation.text = "Grant"
            } else if (!isGpsEnabled) {
                btnGrantLocation.text = "Turn on GPS"
            }
        }

        // Update Background Location UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            findViewById<View>(R.id.layoutPermissionBackgroundLocation).visibility = View.VISIBLE
            if (hasBackgroundLocation) {
                btnGrantBackgroundLocation.visibility = View.GONE
                findViewById<View>(R.id.ivCheckBackgroundLocation).visibility = View.VISIBLE
            } else {
                btnGrantBackgroundLocation.visibility = View.VISIBLE
                findViewById<View>(R.id.ivCheckBackgroundLocation).visibility = View.GONE
            }
        } else {
            findViewById<View>(R.id.layoutPermissionBackgroundLocation).visibility = View.GONE
        }

        // Update Notification UI
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            findViewById<View>(R.id.layoutPermissionNotifications).visibility = View.VISIBLE
            val hasNotifications = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (hasNotifications) {
                btnGrantNotifications.visibility = View.GONE
                findViewById<View>(R.id.ivCheckNotifications).visibility = View.VISIBLE
            } else {
                btnGrantNotifications.visibility = View.VISIBLE
                findViewById<View>(R.id.ivCheckNotifications).visibility = View.GONE
            }
        } else {
            findViewById<View>(R.id.layoutPermissionNotifications).visibility = View.GONE
        }
    }

    private fun hasSpecialPermissions(): Boolean {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
            if (!alarmManager.canScheduleExactAlarms()) return false
        }
        return true
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun updateCurrentWorkLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        
        LocationServices.getFusedLocationProviderClient(this).lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                preferenceHelper.workLat = location.latitude.toFloat()
                preferenceHelper.workLng = location.longitude.toFloat()
                tvWorkLocation.text = "Location: Set (Lat: ${String.format("%.4f", preferenceHelper.workLat)})"
                Toast.makeText(this, "Work location updated!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Could not get current location", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTimeText(textView: TextView, hour: Int, minute: Int) {
        val amPm = if (hour < 12) "AM" else "PM"
        val hourIn12 = when {
            hour == 0 -> 12
            hour > 12 -> hour - 12
            else -> hour
        }
        textView.text = String.format("%02d:%02d %s", hourIn12, minute, amPm)
    }

    private fun showTimePicker(title: String, initialHour: Int, initialMinute: Int, onTimeSelected: (Int, Int) -> Unit) {
        val themeRes = if (preferenceHelper.isDarkMode) R.style.CustomTimePickerTheme else R.style.CustomTimePickerTheme_Light
        val picker = MaterialTimePicker.Builder()
            .setTimeFormat(TimeFormat.CLOCK_12H)
            .setHour(initialHour)
            .setMinute(initialMinute)
            .setTitleText(title)
            .setTheme(themeRes)
            .build()

        picker.addOnPositiveButtonClickListener {
            onTimeSelected(picker.hour, picker.minute)
        }
        
        picker.show(supportFragmentManager, "MATERIAL_TIME_PICKER")
    }

    private fun scanAndPickWifi() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        
        if (!wifiManager.isWifiEnabled) {
            Toast.makeText(this, "Please enable Wi-Fi first", Toast.LENGTH_SHORT).show()
            return
        }

        // Check Location Permission
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }

        // Check if Location Services are enabled (Required for Wi-Fi scanning)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        if (!isLocationEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Location Services Disabled")
                .setMessage("Location services must be enabled to scan for Wi-Fi networks. Please enable it in settings.")
                .setPositiveButton("Settings") { _, _ ->
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel", null)
                .show()
            return
        }

        btnPickWifi.visibility = View.GONE
        pbWifiScan.visibility = View.VISIBLE

        val wifiScanReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    showWifiSelectionDialog(wifiManager.scanResults)
                }
                try {
                    unregisterReceiver(this)
                } catch (e: Exception) {}
                btnPickWifi.visibility = View.VISIBLE
                pbWifiScan.visibility = View.GONE
            }
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        registerReceiver(wifiScanReceiver, intentFilter)

        @Suppress("DEPRECATION")
        val success = wifiManager.startScan()
        if (!success) {
            // scan failure handling - immediately show cached results if scan couldn't start
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                showWifiSelectionDialog(wifiManager.scanResults)
            }
            try {
                unregisterReceiver(wifiScanReceiver)
            } catch (e: Exception) {}
            btnPickWifi.visibility = View.VISIBLE
            pbWifiScan.visibility = View.GONE
        }
    }

    private fun showWifiSelectionDialog(scanResults: List<ScanResult>) {
        @Suppress("DEPRECATION")
        val ssids = scanResults
            .map { it.SSID }
            .filter { it.isNotBlank() }
            .distinct()
            .toTypedArray()

        if (ssids.isEmpty()) {
            Toast.makeText(this, "No Wi-Fi networks found", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Select Wi-Fi Network")
            .setItems(ssids) { _, which ->
                val selectedSsid = ssids[which]
                etWifiSsid.setText(selectedSsid)
                preferenceHelper.homeWifiSsid = selectedSsid
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun checkPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (permissionsToRequest.isNotEmpty()) {
            requestLocationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
        updateLatestLog()
    }

    private fun updateLatestLog() {
        val logs = dbHelper.getAllLogs()
        if (logs.isNotEmpty()) {
            val latest = logs[0]
            val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
            val time = sdf.format(java.util.Date(latest.timestamp))
            tvLatestLog.text = "[$time] ${latest.message}"
        }
    }

    override fun onPause() {
        super.onPause()
        preferenceHelper.homeWifiSsid = etWifiSsid.text.toString().trim()
    }
}
