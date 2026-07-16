package com.naul.workscheduler

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryActivity : AppCompatActivity() {

    private lateinit var dbHelper: LogDatabaseHelper
    private lateinit var tvFullHistory: TextView
    private lateinit var btnClearHistory: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        val preferenceHelper = PreferenceHelper(this)
        if (preferenceHelper.isDarkMode) {
            setTheme(R.style.Theme_WorkScheduler)
        } else {
            setTheme(R.style.Theme_WorkScheduler_Light)
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        dbHelper = LogDatabaseHelper(this)
        tvFullHistory = findViewById(R.id.tvFullHistory)
        btnClearHistory = findViewById(R.id.btnClearHistory)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        updateHistory()

        btnClearHistory.setOnClickListener {
            dbHelper.clearLogs()
            updateHistory()
        }
    }

    private fun updateHistory() {
        val logs = dbHelper.getAllLogs()
        if (logs.isEmpty()) {
            tvFullHistory.text = "No history found."
            return
        }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val historyText = StringBuilder()
        
        logs.forEach { log ->
            val time = sdf.format(Date(log.timestamp))
            historyText.append("[$time] ${log.message}\n")
        }
        
        tvFullHistory.text = historyText.toString()
    }
}
