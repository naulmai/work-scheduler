package com.naul.workscheduler

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ActivityLog(val id: Int, val timestamp: Long, val message: String)

class LogDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "activity_logs.db"
        private const val DATABASE_VERSION = 1
        private const val TABLE_NAME = "logs"
        private const val COLUMN_ID = "id"
        private const val COLUMN_TIMESTAMP = "timestamp"
        private const val COLUMN_MESSAGE = "message"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = "CREATE TABLE $TABLE_NAME ($COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, $COLUMN_TIMESTAMP INTEGER, $COLUMN_MESSAGE TEXT)"
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun addLog(message: String) {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_MESSAGE, message)
        }
        db.insert(TABLE_NAME, null, values)
        // Keep only last 50 logs to save space
        db.execSQL("DELETE FROM $TABLE_NAME WHERE $COLUMN_ID NOT IN (SELECT $COLUMN_ID FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC LIMIT 50)")
        db.close()
    }

    fun getAllLogs(): List<ActivityLog> {
        val logList = mutableListOf<ActivityLog>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_TIMESTAMP DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val log = ActivityLog(
                    cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MESSAGE))
                )
                logList.add(log)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return logList
    }
    
    fun clearLogs() {
        val db = this.writableDatabase
        db.delete(TABLE_NAME, null, null)
        db.close()
    }
}
