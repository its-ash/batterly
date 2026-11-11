package com.itsash.batterly

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Battery history: one row per sample (about one a minute while the monitor runs), kept for
 * [RETENTION_MS]. Written by [MonitorService], read by the app over the channel.
 */
class HistoryDb(context: Context) : SQLiteOpenHelper(context.applicationContext, "history.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE samples (" +
                "ts INTEGER PRIMARY KEY, level INTEGER NOT NULL, current_ma REAL, voltage_mv INTEGER NOT NULL, " +
                "temp_c REAL NOT NULL, plugged TEXT NOT NULL, screen_on INTEGER NOT NULL)",
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun insert(s: BatterySnapshot) {
        val values = ContentValues().apply {
            put("ts", s.timestampMs)
            put("level", s.level)
            if (s.currentMa != null) put("current_ma", s.currentMa) else putNull("current_ma")
            put("voltage_mv", s.voltageMv)
            put("temp_c", s.temperatureC)
            put("plugged", s.plugged)
            put("screen_on", if (s.screenOn) 1 else 0)
        }
        writableDatabase.insertWithOnConflict("samples", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Deletes samples older than the retention window. */
    fun prune(now: Long = System.currentTimeMillis()) {
        writableDatabase.delete("samples", "ts < ?", arrayOf((now - RETENTION_MS).toString()))
    }

    /** Samples at or after [sinceMs], oldest first, in the wire format of `HistorySample.fromMap`. */
    fun samples(sinceMs: Long): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        readableDatabase.rawQuery(
            "SELECT ts, level, current_ma, voltage_mv, temp_c, plugged, screen_on FROM samples WHERE ts >= ? ORDER BY ts",
            arrayOf(sinceMs.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                out += mapOf(
                    "ts" to c.getLong(0),
                    "level" to c.getInt(1),
                    "currentMa" to if (c.isNull(2)) null else c.getDouble(2),
                    "voltageMv" to c.getInt(3),
                    "tempC" to c.getDouble(4),
                    "plugged" to c.getString(5),
                    "screenOn" to (c.getInt(6) == 1),
                )
            }
        }
        return out
    }

    fun clear() {
        writableDatabase.delete("samples", null, null)
    }

    companion object {
        const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000
    }
}
