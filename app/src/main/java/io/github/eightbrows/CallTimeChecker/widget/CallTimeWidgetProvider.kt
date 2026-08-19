package io.github.eightbrows.CallTimeChecker.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.eightbrows.CallTimeChecker.DEFAULT_SETTINGS
import io.github.eightbrows.CallTimeChecker.DEFAULT_START_DAY
import io.github.eightbrows.CallTimeChecker.MainActivity
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.logic.WidgetColor
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.presentWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.Executors

private const val TAG = "CallTimeWidget"
private const val ACTION_MANUAL_REFRESH = "io.github.eightbrows.CallTimeChecker.widget.ACTION_MANUAL_REFRESH"
private val EXECUTOR = Executors.newSingleThreadExecutor()

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
        if (intent.action != ACTION_MANUAL_REFRESH) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CallTimeWidgetProvider::class.java))
        Log.d(TAG, "manual refresh received, ids=${appWidgetIds.toList()}")
        if (appWidgetIds.isEmpty()) return

        // タップの反応をすぐに示すため、バックグラウンド処理の前に一瞬「更新中」を表示する
        val updatingViews = buildUpdatingViews(context)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, updatingViews)
        }
        Log.d(TAG, "updating placeholder shown for ${appWidgetIds.toList()}")

        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking { refreshAndRender(context, appWidgetManager, appWidgetIds) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun refreshAndRender(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

        val views = if (!hasPermission) {
            buildNoPermissionViews(context)
        } else {
            try {
                buildNormalViews(context)
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
                buildErrorViews(context)
            }
        }

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
        Log.d(TAG, "updateAppWidget done for ${appWidgetIds.toList()}")
    }

    private suspend fun buildNormalViews(context: Context): RemoteViews {
        val dbHelper = CallRecordDbHelper(context)
        try {
            withContext(Dispatchers.IO) {
                CallLogSync(context.contentResolver, dbHelper).sync()
            }
            val period = currentPeriod(DEFAULT_START_DAY, ZoneId.systemDefault())
            val records = withContext(Dispatchers.IO) {
                dbHelper.queryRange(period.first, period.second)
            }
            val result = calculate(records, DEFAULT_SETTINGS)
            val content = presentWidget(result, DEFAULT_SETTINGS)

            val views = RemoteViews(context.packageName, R.layout.widget_call_time)
            views.setTextViewText(R.id.widget_line1, content.line1)
            views.setTextViewText(R.id.widget_line2, content.line2)
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
            views.setTextColor(R.id.widget_line1, textColor)
            views.setTextColor(R.id.widget_line2, textColor)
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
            return views
        } finally {
            dbHelper.close()
        }
    }

    private fun buildNoPermissionViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        views.setTextViewText(R.id.widget_line1, "タップして権限を許可")
        views.setTextViewText(R.id.widget_line2, "")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_normal))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_normal))

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
        views.setTextViewText(R.id.widget_line1, "更新中…")
        views.setTextViewText(R.id.widget_line2, "")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_normal))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_normal))
        return views
    }

    private fun buildErrorViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        views.setTextViewText(R.id.widget_line1, "更新失敗")
        views.setTextViewText(R.id.widget_line2, "タップして再試行")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_over)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_on_color))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_on_color))
        views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
        return views
    }

    private fun manualRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_MANUAL_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
