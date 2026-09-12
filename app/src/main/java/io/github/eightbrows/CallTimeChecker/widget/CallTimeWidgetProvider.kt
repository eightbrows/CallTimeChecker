package io.github.eightbrows.CallTimeChecker.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.os.SystemClock
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.eightbrows.CallTimeChecker.MainActivity
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_AUTO_UPDATE_MARK
import io.github.eightbrows.CallTimeChecker.logic.WidgetColor
import io.github.eightbrows.CallTimeChecker.logic.WidgetLabelKind
import io.github.eightbrows.CallTimeChecker.logic.WidgetLine
import io.github.eightbrows.CallTimeChecker.logic.WidgetUnit
import io.github.eightbrows.CallTimeChecker.logic.widgetPaletteArgb
import io.github.eightbrows.CallTimeChecker.logic.widgetTextColorOn
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.presentWidget
import io.github.eightbrows.CallTimeChecker.logic.toBillingSettings
import io.github.eightbrows.CallTimeChecker.logic.widgetBgAlpha
import io.github.eightbrows.CallTimeChecker.logic.withAlpha
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

/** NORMAL状態で色を切り替える対象（上段の最大2行 + 下段1行）のビューID一覧 */
private val NORMAL_STATE_TEXT_VIEW_IDS = listOf(
    R.id.widget_line_time, R.id.widget_line_quota, R.id.widget_line_amount
)

/** 通常表示（上段 / 区切り線 / 下段）のビューID一覧 */
private val CONTENT_ROW_IDS = listOf(R.id.widget_top, R.id.widget_divider, R.id.widget_line_amount)

/** 区切り線の濃さ。文字色をそのまま使うと線が主張しすぎるため薄くする */
private const val WIDGET_DIVIDER_ALPHA = 0x66

/**
 * spec: docs/spec.md 5.5.1 ラベル・単位の文字サイズ。
 * 数字に対する比率で指定する。行全体を 1 つの TextView にして autoSize させているので、
 * この比率はリサイズしても保たれる（autoSize は行全体を一様に拡大縮小するため）。
 * ラベル・数字・単位を別々の TextView にすると autoSize が行ごとに独立して働き、
 * 縦に伸ばしたときに数字だけが不釣り合いに大きくなる。
 */
private const val WIDGET_LABEL_SCALE = 0.45f

/**
 * spec: docs/spec.md 5.8 WidgetLine の種類を、現在の言語の文字列に解決したもの。
 * presentWidget() は Context を持てないため種類だけを返し、ここで初めて文言が決まる。
 * unitBeforeValue は通貨記号を数字の前に置くか（英語の ¥1,220）。分は常に後ろ。
 */
private data class ResolvedLine(
    val label: String,
    val value: String,
    val unit: String,
    val unitBeforeValue: Boolean,
    val reference: String?
)

private fun resolveLine(context: Context, line: WidgetLine): ResolvedLine {
    val labelText = context.getString(
        when (line.label) {
            WidgetLabelKind.CALL_TIME -> R.string.widget_label_talk_time
            WidgetLabelKind.FREE_QUOTA -> R.string.widget_label_remaining
            WidgetLabelKind.CALL_AMOUNT -> R.string.widget_label_charges
        }
    )
    // 自動更新の印はラベルの一部として添える（5.5.1）。言語によらず同じ記号
    val label = if (line.marked) "$labelText $WIDGET_AUTO_UPDATE_MARK" else labelText
    val unit = context.getString(
        when (line.unit) {
            WidgetUnit.MINUTES -> R.string.widget_unit_min
            WidgetUnit.YEN -> R.string.widget_unit_yen
        }
    )
    val before = line.unit == WidgetUnit.YEN && context.resources.getBoolean(R.bool.yen_before_value)
    return ResolvedLine(label, line.value, unit, before, line.reference)
}

/**
 * spec: docs/spec.md 5.5.1 「ラベル（小）」の下に「数字（大）+ 単位（小）」を置く 1 項目。
 * 2 行だが 1 つの CharSequence にまとめ、大きさ・太さの差はスパンで付ける。
 * autoSize は複数行のテキスト全体を一様に拡大縮小するため、改行を挟んでも比率は保たれる。
 * RelativeSizeSpan / StyleSpan はいずれも ParcelableSpan なので RemoteViews 越しに保持される。
 * 単位は言語によって数字の前（英語の ¥）にも後ろ（分、円）にも来る（5.8）。
 */
private fun widgetLineText(line: ResolvedLine): CharSequence {
    // spec 5.5.1: 基準文字列に足りない桁数を数え、数字の前後に同じ幅ずつ埋める。
    // 桁埋めに空白文字ではなく数字を使うのは、行末の空白が描画時に切り詰められるのと、
    // 数字なら基準文字列と同じ字形なので幅が厳密に一致するため
    val padCount = ((line.reference?.length ?: 0) - line.value.length).coerceAtLeast(0)

    val sb = SpannableStringBuilder()
    // ラベルは数字の上の行に出す。改行もラベル側のスパンに含めるので、行送りも小さい方に付く
    if (line.label.isNotEmpty()) appendSmall(sb, line.label + "\n")

    val leadPadStart = sb.length
    appendHalfPad(sb, padCount)
    val leadPadEnd = sb.length
    if (line.unitBeforeValue && line.unit.isNotEmpty()) appendSmall(sb, line.unit)
    val valueStart = sb.length
    sb.append(line.value)
    val valueEnd = sb.length
    if (!line.unitBeforeValue && line.unit.isNotEmpty()) appendSmall(sb, line.unit)
    val trailPadStart = sb.length
    appendHalfPad(sb, padCount)

    // 数字は太字。桁埋めも数字と同じ大きさ・太さでないと基準文字列と幅がそろわないため、
    // 前後の桁埋めも太字にする（単位が間に挟まる場合があるので数字とは別スパン）
    sb.setSpan(StyleSpan(Typeface.BOLD), valueStart, valueEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    // 幅だけ確保して見せないので透明にする
    if (leadPadEnd > leadPadStart) {
        sb.setSpan(StyleSpan(Typeface.BOLD), leadPadStart, leadPadEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        markPad(sb, leadPadStart, leadPadEnd)
        sb.setSpan(StyleSpan(Typeface.BOLD), trailPadStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        markPad(sb, trailPadStart, sb.length)
    }
    return sb
}

/**
 * 読み上げ用の文字列。widgetLineText() の桁埋めは見た目のためだけのものなので、
 * そのまま読み上げられないよう contentDescription には埋めていない文字列を渡す。
 */
private fun widgetLineDescription(line: ResolvedLine): String {
    val number = if (line.unitBeforeValue) line.unit + line.value else line.value + line.unit
    return if (line.label.isEmpty()) number else "${line.label} $number"
}

/**
 * spec: docs/spec.md 5.5.1 片側ぶんの桁埋め（= 不足桁数の半分の幅）を追加する。
 * 同じものを数字の前後に付けるので、埋めた結果は必ず左右対称になり中央寄せが崩れない。
 * 不足が奇数桁のときは 1 桁を半分の大きさにして両側に置き、合わせて 1 桁ぶんにする
 * （片側にまるごと寄せると、見えている文字が半桁ぶん中心からずれる）。
 * 半分より大きい倍率は使わないので、桁埋めが行の高さを押し広げることもない。
 */
private fun appendHalfPad(sb: SpannableStringBuilder, padCount: Int) {
    repeat(padCount / 2) { sb.append(WIDGET_PAD_DIGIT) }
    if (padCount % 2 == 1) {
        val start = sb.length
        sb.append(WIDGET_PAD_DIGIT)
        sb.setSpan(
            RelativeSizeSpan(WIDGET_PAD_HALF_SCALE), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

private fun markPad(sb: SpannableStringBuilder, start: Int, end: Int) {
    sb.setSpan(ForegroundColorSpan(Color.TRANSPARENT), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

/** 桁埋めに使う文字。基準文字列と同じ字形になるよう数字を使う（透明にして見せない） */
private const val WIDGET_PAD_DIGIT = '0'

/** 奇数桁の桁埋めを左右に分けるための倍率 */
private const val WIDGET_PAD_HALF_SCALE = 0.5f

private fun appendSmall(sb: SpannableStringBuilder, text: String) {
    val start = sb.length
    sb.append(text)
    sb.setSpan(RelativeSizeSpan(WIDGET_LABEL_SCALE), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

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

    /**
     * updatePeriodMillis による周期更新とウィジェット追加時に呼ばれる。
     * spec 5.5.1: この経路で描いた表示だけ「通話金額」に自動更新の印を付ける
     * （手動更新・設定変更は onReceive 側の経路なので印は付かない）。
     */
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking {
                    refreshAndRender(context, appWidgetManager, appWidgetIds, autoUpdated = true)
                }
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
                runBlocking {
                    // 手動更新・設定変更のどちらも利用者の操作によるものなので印は付けない
                    refreshAndRender(
                        context, appWidgetManager, appWidgetIds, minDisplayUntil, sync,
                        autoUpdated = false
                    )
                }
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
        sync: Boolean = true,
        autoUpdated: Boolean = false
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

        // 権限要求 / 更新中 / 更新失敗の各表示は「通話金額」の行自体を出さないので、
        // 自動更新の印もこれらの状態には現れない
        val views = if (!hasPermission) {
            buildNoPermissionViews(context)
        } else {
            try {
                buildNormalViews(context, sync, autoUpdated)
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

    private suspend fun buildNormalViews(
        context: Context,
        sync: Boolean,
        autoUpdated: Boolean
    ): RemoteViews {
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
            val content = presentWidget(
                result, settings, appSettings.planType, appSettings.warnRemainingMin * 60,
                autoUpdated
            )

            val views = RemoteViews(context.packageName, R.layout.widget_call_time)
            restoreNormalLayout(views)

            // spec 5.5.1: 項目の種類と数字は presentWidget() が決める。
            // ここでは種類を文字列リソースに解決し、スパンを付けてビューへ割り当てるだけ
            applyLine(context, views, R.id.widget_line_time, content.timeLine)
            applyLine(context, views, R.id.widget_line_amount, content.amountLine)

            // 月間の無料枠が無いプラン形式では行ごと GONE にする。
            // GONE の行は weight を消費しないので、残る通話時間の行が上段いっぱいに広がる
            val quota = content.quotaLine
            views.setViewVisibility(
                R.id.widget_line_quota, if (quota == null) View.GONE else View.VISIBLE
            )
            if (quota != null) {
                applyLine(context, views, R.id.widget_line_quota, quota)
            }

            val bgArgb = backgroundArgb(appSettings, content.color)
            applyBackground(views, bgArgb, appSettings.widgetBgTransparencyStep)
            val textColor = widgetTextColorOn(bgArgb)
            for (id in NORMAL_STATE_TEXT_VIEW_IDS) {
                views.setTextColor(id, textColor)
            }
            views.setInt(
                R.id.widget_divider, "setBackgroundColor", withAlpha(textColor, WIDGET_DIVIDER_ALPHA)
            )
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
            return views
        } finally {
            dbHelper.close()
        }
    }

    private fun applyLine(context: Context, views: RemoteViews, viewId: Int, line: WidgetLine) {
        val resolved = resolveLine(context, line)
        views.setTextViewText(viewId, widgetLineText(resolved))
        views.setContentDescription(viewId, widgetLineDescription(resolved))
    }

    private fun buildNoPermissionViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        val appSettings = SettingsRepository(context).load()
        val bgArgb = backgroundArgb(appSettings, WidgetColor.NORMAL)
        applyStatusLayout(views, context.getString(R.string.widget_status_no_permission), "", widgetTextColorOn(bgArgb))
        applyBackground(views, bgArgb, appSettings.widgetBgTransparencyStep)

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
        val appSettings = SettingsRepository(context).load()
        val bgArgb = backgroundArgb(appSettings, WidgetColor.NORMAL)
        applyStatusLayout(views, context.getString(R.string.widget_status_updating), "", widgetTextColorOn(bgArgb))
        applyBackground(views, bgArgb, appSettings.widgetBgTransparencyStep)
        return views
    }

    private fun buildErrorViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        val appSettings = SettingsRepository(context).load()
        val bgArgb = backgroundArgb(appSettings, WidgetColor.OVER)
        applyStatusLayout(
            views,
            context.getString(R.string.widget_status_error),
            context.getString(R.string.widget_status_retry),
            widgetTextColorOn(bgArgb)
        )
        applyBackground(views, bgArgb, appSettings.widgetBgTransparencyStep)
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
        // 無料枠の行はプラン形式によって変わるため、ここではなく
        // buildNormalViews() 側で毎回 VISIBLE / GONE を明示する
        views.setViewVisibility(R.id.widget_line_time, View.VISIBLE)
        views.setViewVisibility(R.id.widget_status, View.GONE)
    }

    /**
     * 権限要求/更新中/エラーの各状態は「ラベル + 値」のブロック構成ではなく、
     * 中央寄せの専用ビュー（widget_status）にメッセージを出す。
     * 値欄（左寄せかつブロック内の1行）を流用するとメッセージが中央に来ないため、
     * 通常表示のブロックはすべて GONE にして専用ビューだけを表示する。
     */
    private fun applyStatusLayout(views: RemoteViews, primary: String, secondary: String, textColor: Int) {
        for (id in CONTENT_ROW_IDS) {
            views.setViewVisibility(id, View.GONE)
        }
        views.setViewVisibility(R.id.widget_status, View.VISIBLE)
        views.setTextViewText(
            R.id.widget_status,
            if (secondary.isEmpty()) primary else "$primary\n$secondary"
        )
        views.setTextColor(R.id.widget_status, textColor)
    }

    /**
     * spec: docs/spec.md 5.5.3 背景は固定の色リソースではなく、透過率を反映した ARGB を
     * setBackgroundColor で設定する。パレットの色は不透明のまま、alpha だけを差し替える。
     */
    private fun applyBackground(views: RemoteViews, colorArgb: Int, step: Int) {
        views.setInt(R.id.widget_root, "setBackgroundColor", withAlpha(colorArgb, widgetBgAlpha(step)))
    }

    /**
     * spec: docs/spec.md 5.5.3 / 5.7 状態に対応する背景色。
     * 色リソースではなく設定で選ばれたパレットの色（不透明な ARGB）を返す。
     * 権限要求 / 更新中は通常色、更新失敗は超過色を使うため、これらにもカスタム色が反映される。
     */
    private fun backgroundArgb(appSettings: AppSettings, color: WidgetColor): Int {
        val index = when (color) {
            WidgetColor.NORMAL -> appSettings.widgetColorNormalIndex
            WidgetColor.WARNING -> appSettings.widgetColorWarningIndex
            WidgetColor.OVER -> appSettings.widgetColorOverIndex
        }
        return widgetPaletteArgb(index)
    }

    private fun manualRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_MANUAL_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
