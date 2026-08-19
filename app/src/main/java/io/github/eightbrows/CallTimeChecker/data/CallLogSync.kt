package io.github.eightbrows.CallTimeChecker.data

import android.content.ContentResolver
import android.provider.CallLog

private const val DUPLICATE_WINDOW_MILLIS = 24L * 60 * 60 * 1000 // 5.1.2: 24時間分の重複読み込み

/**
 * spec: docs/spec.md 5.1.2 同期処理
 *
 * 呼び出し元は事前に READ_CALL_LOG 権限の許可を確認しておくこと。
 */
class CallLogSync(
    private val contentResolver: ContentResolver,
    private val dbHelper: CallRecordDbHelper
) {
    /** CallLog から未取り込み分を読み込み、DB へ反映する。取り込みを試みた件数を返す */
    fun sync(): Int {
        val last = dbHelper.maxDateMillis()
        val since = maxOf(0L, last - DUPLICATE_WINDOW_MILLIS)

        val records = mutableListOf<RawCallRecord>()
        val projection = arrayOf(
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
            arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), since.toString()),
            null
        )?.use { cursor ->
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val accountIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
            while (cursor.moveToNext()) {
                records += RawCallRecord(
                    dateMillis = cursor.getLong(dateIdx),
                    durationSec = cursor.getInt(durationIdx),
                    number = cursor.getString(numberIdx),
                    type = cursor.getInt(typeIdx),
                    accountId = cursor.getString(accountIdx)
                )
            }
        }
        dbHelper.insertOrIgnore(records)
        return records.size
    }
}