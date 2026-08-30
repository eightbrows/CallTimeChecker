package io.github.eightbrows.CallTimeChecker.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.eightbrows.CallTimeChecker.MainActivity
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.WidgetColor
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.presentWidget
import io.github.eightbrows.CallTimeChecker.logic.toBillingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.Executors

private const val TAG = "CallTimeWidget"
private const val ACTION_MANUAL_REFRESH = "io.github.eightbrows.CallTimeChecker.widget.ACTION_MANUAL_REFRESH"

/** spec: docs/spec.md 7.2 設定変更時（同期は行わず、再集計と全ウィジェットの再描画のみ実行） */
const val ACTION_SETTINGS_CHANGED = "io.github.eightbrows.CallTimeChecker.widget.ACTION_SETTINGS_CHANGED"

private const val MIN_UPDATING_DISPLAY_MILLIS = 500L
private val EXECUTOR = Executors.newSingleThreadExecutor()

/**
 * spec: docs/spec.md 5.5.1 line1（例: "42分 / 70分 (3件)" / "通話 128分 (4件)"）末尾の
 * 件数表記を切り出すための正規表現。WidgetPresentation.presentWidget() 自体は変更せず、
 * 3行ラベル付きレイアウトへの割り当てはこの Provider 側でのみ行う。
 */
private val CALL_COUNT_SUFFIX_REGEX = Regex("\\((\\d+)件\\)$")

/**
 * spec: docs/spec.md 5.5.1 line2（超過時は "¥352 (超過 8分)"）から、金額部分と超過量を分離するための正規表現。
 * 金額行は "¥352" のみのシンプルな表示にし、超過量（"8分"）は「超過」ラベル付きの独立した4行目に出す。
 */
private val OVERAGE_SUFFIX_REGEX = Regex("^(.+?)\\s*\\(超過 (.+?)\\)$")

/** NORMAL状態で色を切り替える対象（4行×ラベル/値）のビューID一覧 */
private val NORMAL_STATE_TEXT_VIEW_IDS = listOf(
    R.id.widget_time_label, R.id.widget_time_value,
    R.id.widget_count_label, R.id.widget_count_value,
    R.id.widget_amount_label, R.id.widget_amount_value,
    R.id.widget_overage_label, R.id.widget_overage_value
)

/** 通常表示（使用/件数/金額）の行コンテナのビューID一覧。超過行は有無に応じて別途制御する */
private val CONTENT_ROW_IDS = listOf(
    R.id.widget_time_row, R.id.widget_count_row, R.id.widget_amount_row
)

/**
 * spec: docs/spec.md 7.2 設定変更時のトリガー。SettingsRepository.save() 完了時に呼び出す想定。
 * 自身への明示的Intentのため、manifest の intent-filter には依存しない（ACTION_MANUAL_REFRESH と同様）。
 */
fun notifyWidgetsSettingsChanged(context: Context) {
    val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_SETTINGS_CHANGED)
    context.sendBroadcast(intent)
}

/**
 * spec: docs/spec.md 5.5 ウィジェット / 7.1 ウィジェット更新フロー。
 * onUpdate / onReceive はメインスレッド・10秒制限のため goAsync() + Executor でバックグラウンド実行する。
 */
class CallTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking { refreshAndRender(context, appWidgetManager, appWidgetIds) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action != ACTION_MANUAL_REFRESH && intent.action != ACTION_SETTINGS_CHANGED) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CallTimeWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        // spec 7.2: 設定変更時は同期を行わず、再集計と再描画のみ。手動更新時のみ同期する
        val sync = intent.action == ACTION_MANUAL_REFRESH
        var minDisplayUntil: Long? = null

        if (sync) {
            Log.d(TAG, "manual refresh received, ids=${appWidgetIds.toList()}")
            // タップの反応をすぐに示すため、バックグラウンド処理の前に一瞬「更新中」を表示する
            val updatingViews = buildUpdatingViews(context)
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, updatingViews)
            }
            Log.d(TAG, "updating placeholder shown for ${appWidgetIds.toList()}")
            minDisplayUntil = SystemClock.elapsedRealtime() + MIN_UPDATING_DISPLAY_MILLIS
        } else {
            Log.d(TAG, "settings changed received, ids=${appWidgetIds.toList()}")
        }

        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking { refreshAndRender(context, appWidgetManager, appWidgetIds, minDisplayUntil, sync) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun refreshAndRender(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        minDisplayUntil: Long? = null,
        sync: Boolean = true
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

        val views = if (!hasPermission) {
            buildNoPermissionViews(context)
        } else {
            try {
                buildNormalViews(context, sync)
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
                buildErrorViews(context)
            }
        }

        // タップ直後に表示した「更新中」が一瞬で消えないよう、最低表示時間に満たない分だけ待つ
        if (minDisplayUntil != null) {
            val remaining = minDisplayUntil - SystemClock.elapsedRealtime()
            if (remaining > 0) delay(remaining)
        }

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
        Log.d(TAG, "updateAppWidget done for ${appWidgetIds.toList()}")
    }

    private suspend fun buildNormalViews(context: Context, sync: Boolean): RemoteViews {
        val dbHelper = CallRecordDbHelper(context)
        try {
            val appSettings = SettingsRepository(context).load()
            val settings = toBillingSettings(appSettings)
            if (sync) {
                withContext(Dispatchers.IO) {
                    CallLogSync(context.contentResolver, dbHelper).sync()
                }
            }
            val period = currentPeriod(appSettings.startDay, ZoneId.systemDefault())
            val records = withContext(Dispatchers.IO) {
                dbHelper.queryRange(period.first, period.second)
            }
            val result = calculate(records, settings)
            val content = presentWidget(result, settings)

            val views = RemoteViews(context.packageName, R.layout.widget_call_time)
            restoreNormalLayout(views)

            // spec 5.5.1: WidgetPresentation.presentWidget() の line1/line2 自体は変更せず、
            // 「使用/件数/金額/超過」の4行ラベル付きレイアウトへの割り当てだけをここで行う
            val (timeText, countText) = splitLine1(content.line1)
            views.setTextViewText(R.id.widget_time_value, timeText)
            views.setTextViewText(R.id.widget_count_value, countText)

            // 超過注記は金額行に混ぜず独立した4行目に出す。超過なしの場合は行ごと GONE。
            // ホストはビューを再利用するため、VISIBLE/GONE は毎回必ず両方向を明示的に指定する。
            val overageMatch = OVERAGE_SUFFIX_REGEX.find(content.line2)
            if (overageMatch != null) {
                views.setTextViewText(R.id.widget_amount_value, overageMatch.groupValues[1])
                views.setTextViewText(R.id.widget_overage_value, overageMatch.groupValues[2])
                views.setViewVisibility(R.id.widget_overage_row, View.VISIBLE)
            } else {
                views.setTextViewText(R.id.widget_amount_value, content.line2)
                views.setTextViewText(R.id.widget_overage_value, "")
                views.setViewVisibility(R.id.widget_overage_row, View.GONE)
            }

            val bgColor = when (content.color) {
                WidgetColor.NORMAL -> R.color.widget_bg_normal
                WidgetColor.WARNING -> R.color.widget_bg_warning
                WidgetColor.OVER -> R.color.widget_bg_over
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgColor)
            val textColor = if (content.color == WidgetColor.NORMAL) {
                context.getColor(R.color.widget_text_normal)
            } else {
                context.getColor(R.color.widget_text_on_color)
            }
            for (id in NORMAL_STATE_TEXT_VIEW_IDS) {
                views.setTextColor(id, textColor)
            }
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
            return views
        } finally {
            dbHelper.close()
        }
    }

    private fun buildNoPermissionViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        applyStatusLayout(views, "タップして権限を許可", "", context.getColor(R.color.widget_text_normal))
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)

        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return views
    }

    private fun buildUpdatingViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        applyStatusLayout(views, "更新中…", "", context.getColor(R.color.widget_text_normal))
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)
        return views
    }

    private fun buildErrorViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        applyStatusLayout(views, "更新失敗", "タップして再試行", context.getColor(R.color.widget_text_on_color))
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_over)
        views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
        return views
    }

    /**
     * applyStatusLayout() で GONE にした通常表示の行を元に戻す。
     * ウィジェットホストは同じレイアウトの RemoteViews を再適用する際に既存のビューを再利用し、
     * 新しい RemoteViews に含まれるアクションだけを差分適用するため、
     * 明示的に VISIBLE を指定しないと直前の「更新中」表示の GONE が残り続ける。
     */
    private fun restoreNormalLayout(views: RemoteViews) {
        for (id in CONTENT_ROW_IDS) {
            views.setViewVisibility(id, View.VISIBLE)
        }
        // 旧バージョンではラベル・値を個別に GONE にしていたため、そのまま更新された
        // 既存ウィジェットのために個別の VISIBLE 復帰も明示しておく
        for (id in NORMAL_STATE_TEXT_VIEW_IDS) {
            views.setViewVisibility(id, View.VISIBLE)
        }
        views.setViewVisibility(R.id.widget_status, View.GONE)
        // widget_overage_row の VISIBLE/GONE は超過の有無に応じて呼び出し元で必ず明示する
    }

    /**
     * 権限要求/更新中/エラーの各状態は「使用/件数/金額」のラベル付き行構成ではなく、
     * 中央寄せの専用ビュー（widget_status）にメッセージを出す。
     * 値欄（gravity="end" かつラベル分の幅を差し引いた TextView）を流用すると右寄りに見えるため、
     * 通常表示の行はすべて GONE にして専用ビューだけを表示する。
     */
    private fun applyStatusLayout(views: RemoteViews, primary: String, secondary: String, textColor: Int) {
        for (id in CONTENT_ROW_IDS) {
            views.setViewVisibility(id, View.GONE)
        }
        views.setViewVisibility(R.id.widget_overage_row, View.GONE)
        views.setViewVisibility(R.id.widget_status, View.VISIBLE)
        views.setTextViewText(
            R.id.widget_status,
            if (secondary.isEmpty()) primary else "$primary\n$secondary"
        )
        views.setTextColor(R.id.widget_status, textColor)
    }

    /**
     * spec: docs/spec.md 5.5.1 line1（例: "42分 / 70分 (3件)" / "通話 128分 (4件)"）を
     * 「使用」行の値（通話時間）と「件数」行の値に分解する。WidgetPresentation.presentWidget()
     * 自体（line1/line2 の文字列）は変更しない。
     */
    private fun splitLine1(line1: String): Pair<String, String> {
        val match = CALL_COUNT_SUFFIX_REGEX.find(line1) ?: return line1 to ""
        val countText = "${match.groupValues[1]}件"
        val timeText = line1.removeSuffix(match.value).trim().removePrefix("通話").trim()
        return timeText to countText
    }

    private fun manualRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_MANUAL_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
