package com.omnibrain.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class OmniBrainDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "omnibrain.db"
        private const val DATABASE_VERSION = 2
        private const val TABLE_LOGS = "execution_logs"
        private const val TABLE_CONFIG = "sys_config"
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE VIRTUAL TABLE $TABLE_LOGS USING fts5(
                tool_name,
                arguments,
                result,
                timestamp UNINDEXED
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE $TABLE_CONFIG (
                key TEXT PRIMARY KEY,
                value TEXT
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE IF NOT EXISTS $TABLE_CONFIG (key TEXT PRIMARY KEY, value TEXT)")
        }
    }

    fun logExecution(toolName: String, args: String, result: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("tool_name", toolName)
            put("arguments", args)
            put("result", result)
            put("timestamp", System.currentTimeMillis().toString())
        }
        db.insert(TABLE_LOGS, null, values)
    }

    fun searchLogs(query: String): List<String> {
        val db = readableDatabase
        val results = mutableListOf<String>()
        val cursor = db.rawQuery(
            "SELECT tool_name, result FROM $TABLE_LOGS WHERE $TABLE_LOGS MATCH ? ORDER BY rank LIMIT 20",
            arrayOf(query)
        )
        cursor.use {
            while (it.moveToNext()) {
                val tool = it.getString(0)
                val res = it.getString(1)
                results.add("[$tool]: $res")
            }
        }
        return results
    }

    fun getTotalLogCount(): Long {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT COUNT(*) FROM $TABLE_LOGS", null)
        cursor.use {
            if (it.moveToFirst()) return it.getLong(0)
        }
        return 0
    }

    fun setConfig(key: String, value: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict(TABLE_CONFIG, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getConfig(key: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT value FROM $TABLE_CONFIG WHERE key = ?", arrayOf(key))
        cursor.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }
}
