package io.github.eightbrows.CallTimeChecker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.eightbrows.CallTimeChecker.logic.CallRecord

private const val DB_NAME = "call_time_checker.db"
private const val DB_VERSION = 1

private const val TABLE = "call_record"
private const val COL_DATE = "date_millis"
private const val COL_DURATION = "duration_sec"
private const val COL_NUMBER = "number"
private const val COL_TYPE = "type"
private const val COL_ACCOUNT = "account_id"

/** CallLog から読み取った生レコード。DB 保存用に type / account_id を含む (spec: docs/spec.md 6.1) */
data class RawCallRecord(
    val dateMillis: Long,
    val durationSec: Int,
    val number: String?,
    val type: Int,
    val accountId: String?
)

/** spec: docs/spec.md 6.1 テーブル定義 */
class CallRecordDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
              $COL_DATE     INTEGER NOT NULL PRIMARY KEY,
              $COL_DURATION INTEGER NOT NULL,
              $COL_NUMBER   TEXT,
              $COL_TYPE     INTEGER NOT NULL,
              $COL_ACCOUNT  TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_call_record_date ON $TABLE($COL_DATE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // スキーマ変更は未発生
    }

    /** spec: docs/spec.md 5.1.2 手順1 (レコードなしの場合は 0) */
    fun maxDateMillis(): Long =
        readableDatabase.rawQuery("SELECT MAX($COL_DATE) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }

    /** spec: docs/spec.md 5.1.2 手順3 (INSERT OR IGNORE により冪等性を担保) */
    fun insertOrIgnore(records: List<RawCallRecord>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in records) {
                val values = ContentValues().apply {
                    put(COL_DATE, r.dateMillis)
                    put(COL_DURATION, r.durationSec)
                    put(COL_NUMBER, r.number)
                    put(COL_TYPE, r.type)
                    put(COL_ACCOUNT, r.accountId)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 指定期間 [start, end) の call_record を通話開始時刻昇順で取得する (Billing.calculate() の入力用) */
    fun queryRange(startMillis: Long, endMillis: Long): List<CallRecord> {
        val result = mutableListOf<CallRecord>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_DATE, COL_DURATION, COL_NUMBER),
            "$COL_DATE >= ? AND $COL_DATE < ?",
            arrayOf(startMillis.toString(), endMillis.toString()),
            null, null, "$COL_DATE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CallRecord(
                    dateMillis = cursor.getLong(0),
                    durationSec = cursor.getInt(1),
                    number = if (cursor.isNull(2)) null else cursor.getString(2)
                )
            }
        }
        return result
    }
}