package com.omnibrain.core.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

data class OmniBrainRecord(
    val id: Long = 0,
    val sessionId: String,
    val timestamp: String,
    val lastActiveModel: String,
    val currentTask: String,
    val decisionsJson: String,
    val markdownNotes: String
)

class OmniBrainDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "omnibrain.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_SNAPSHOTS = "snapshots"
        const val COLUMN_ID = "id"
        const val COLUMN_SESSION_ID = "session_id"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_LAST_ACTIVE_MODEL = "last_active_model"
        const val COLUMN_CURRENT_TASK = "current_task"
        const val COLUMN_DECISIONS = "decisions"
        const val COLUMN_MARKDOWN_NOTES = "markdown_notes"

        const val TABLE_FTS = "snapshots_fts"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createMainTable = """
            CREATE TABLE $TABLE_SNAPSHOTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_SESSION_ID TEXT NOT NULL,
                $COLUMN_TIMESTAMP TEXT NOT NULL,
                $COLUMN_LAST_ACTIVE_MODEL TEXT NOT NULL,
                $COLUMN_CURRENT_TASK TEXT NOT NULL,
                $COLUMN_DECISIONS TEXT NOT NULL,
                $COLUMN_MARKDOWN_NOTES TEXT NOT NULL
            );
        """.trimIndent()

        val createFtsTable = """
            CREATE VIRTUAL TABLE $TABLE_FTS USING fts5(
                $COLUMN_SESSION_ID UNINDEXED,
                $COLUMN_CURRENT_TASK,
                $COLUMN_DECISIONS,
                $COLUMN_MARKDOWN_NOTES,
                content='$TABLE_SNAPSHOTS',
                content_rowid='$COLUMN_ID'
            );
        """.trimIndent()

        val triggerAfterInsert = """
            CREATE TRIGGER snapshots_after_insert AFTER INSERT ON $TABLE_SNAPSHOTS BEGIN
                INSERT INTO $TABLE_FTS(rowid, $COLUMN_SESSION_ID, $COLUMN_CURRENT_TASK, $COLUMN_DECISIONS, $COLUMN_MARKDOWN_NOTES)
                VALUES (new.$COLUMN_ID, new.$COLUMN_SESSION_ID, new.$COLUMN_CURRENT_TASK, new.$COLUMN_DECISIONS, new.$COLUMN_MARKDOWN_NOTES);
            END;
        """.trimIndent()

        val triggerAfterDelete = """
            CREATE TRIGGER snapshots_after_delete AFTER DELETE ON $TABLE_SNAPSHOTS BEGIN
                INSERT INTO $TABLE_FTS($TABLE_FTS, rowid, $COLUMN_SESSION_ID, $COLUMN_CURRENT_TASK, $COLUMN_DECISIONS, $COLUMN_MARKDOWN_NOTES)
                VALUES('delete', old.$COLUMN_ID, old.$COLUMN_SESSION_ID, old.$COLUMN_CURRENT_TASK, old.$COLUMN_DECISIONS, old.$COLUMN_MARKDOWN_NOTES);
            END;
        """.trimIndent()

        val triggerAfterUpdate = """
            CREATE TRIGGER snapshots_after_update AFTER UPDATE ON $TABLE_SNAPSHOTS BEGIN
                INSERT INTO $TABLE_FTS($TABLE_FTS, rowid, $COLUMN_SESSION_ID, $COLUMN_CURRENT_TASK, $COLUMN_DECISIONS, $COLUMN_MARKDOWN_NOTES)
                VALUES('delete', old.$COLUMN_ID, old.$COLUMN_SESSION_ID, old.$COLUMN_CURRENT_TASK, old.$COLUMN_DECISIONS, old.$COLUMN_MARKDOWN_NOTES);
                INSERT INTO $TABLE_FTS(rowid, $COLUMN_SESSION_ID, $COLUMN_CURRENT_TASK, $COLUMN_DECISIONS, $COLUMN_MARKDOWN_NOTES)
                VALUES (new.$COLUMN_ID, new.$COLUMN_SESSION_ID, new.$COLUMN_CURRENT_TASK, new.$COLUMN_DECISIONS, new.$COLUMN_MARKDOWN_NOTES);
            END;
        """.trimIndent()

        db.execSQL(createMainTable)
        db.execSQL(createFtsTable)
        db.execSQL(triggerAfterInsert)
        db.execSQL(triggerAfterDelete)
        db.execSQL(triggerAfterUpdate)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_FTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_SNAPSHOTS")
        onCreate(db)
    }

    fun insertSnapshot(
        sessionId: String,
        timestamp: String,
        lastActiveModel: String,
        currentTask: String,
        decisions: List<String>,
        markdownNotes: String
    ): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_SESSION_ID, sessionId)
            put(COLUMN_TIMESTAMP, timestamp)
            put(COLUMN_LAST_ACTIVE_MODEL, lastActiveModel)
            put(COLUMN_CURRENT_TASK, currentTask)
            put(COLUMN_DECISIONS, JSONArray(decisions).toString())
            put(COLUMN_MARKDOWN_NOTES, markdownNotes)
        }
        return db.insert(TABLE_SNAPSHOTS, null, values)
    }

    fun searchSnapshots(query: String): List<OmniBrainRecord> {
        val results = mutableListOf<OmniBrainRecord>()
        val db = readableDatabase
        val safeQuery = query.replace("'", "''").trim()
        if (safeQuery.isEmpty()) return results

        val sql = """
            SELECT s.$COLUMN_ID, s.$COLUMN_SESSION_ID, s.$COLUMN_TIMESTAMP, 
                   s.$COLUMN_LAST_ACTIVE_MODEL, s.$COLUMN_CURRENT_TASK, 
                   s.$COLUMN_DECISIONS, s.$COLUMN_MARKDOWN_NOTES
            FROM $TABLE_SNAPSHOTS s
            JOIN $TABLE_FTS f ON s.$COLUMN_ID = f.rowid
            WHERE $TABLE_FTS MATCH '$safeQuery*'
            ORDER BY s.$COLUMN_ID DESC;
        """.trimIndent()

        db.rawQuery(sql, null).use { cursor ->
            while (cursor.moveToNext()) {
                results.add(
                    OmniBrainRecord(
                        id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                        sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)),
                        timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                        lastActiveModel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_ACTIVE_MODEL)),
                        currentTask = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_TASK)),
                        decisionsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DECISIONS)),
                        markdownNotes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MARKDOWN_NOTES))
                    )
                )
            }
        }
        return results
    }

    fun getLatestSnapshot(): OmniBrainRecord? {
        val db = readableDatabase
        val sql = "SELECT * FROM $TABLE_SNAPSHOTS ORDER BY $COLUMN_ID DESC LIMIT 1"
        return db.rawQuery(sql, null).use { cursor ->
            if (cursor.moveToFirst()) {
                OmniBrainRecord(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    sessionId = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_SESSION_ID)),
                    timestamp = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    lastActiveModel = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_LAST_ACTIVE_MODEL)),
                    currentTask = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_CURRENT_TASK)),
                    decisionsJson = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_DECISIONS)),
                    markdownNotes = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_MARKDOWN_NOTES))
                )
            } else null
        }
    }
}
