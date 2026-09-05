package io.github.eightbrows.CallTimeChecker.data

import android.content.Context
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.clampMonthlyFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampPerCallFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.clampUnitPrice
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetBgTransparencyStep
import io.github.eightbrows.CallTimeChecker.logic.clampWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetColorIndex
import io.github.eightbrows.CallTimeChecker.logic.defaultWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.migrateAppSettings
import io.github.eightbrows.CallTimeChecker.logic.normalizeUnitSec
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes
import io.github.eightbrows.CallTimeChecker.widget.notifyWidgetsSettingsChanged

private const val PREFS_NAME = "call_time_checker_settings"
private const val KEY_PLAN_TYPE = "plan_type"
private const val KEY_START_DAY = "start_day"
private const val KEY_MONTHLY_FREE_MIN = "monthly_free_min"
private const val KEY_PER_CALL_FREE_MIN = "per_call_free_min"
private const val KEY_UNIT_SEC = "unit_sec"
private const val KEY_UNIT_PRICE = "unit_price"
private const val KEY_EXCLUDE_PREFIXES = "exclude_prefixes"
private const val KEY_WIDGET_BG_TRANSPARENCY_STEP = "widget_bg_transparency_step"
private const val KEY_WARN_REMAINING_MIN = "warn_remaining_min"

/** 「消費量が◯分に達したら警告」で保存していた頃のキー。読み込み時に残量へ読み替える */
private const val KEY_WARN_CONSUMED_MIN_LEGACY = "warn_threshold_min"
private const val KEY_WIDGET_COLOR_NORMAL = "widget_color_normal"
private const val KEY_WIDGET_COLOR_WARNING = "widget_color_warning"
private const val KEY_WIDGET_COLOR_OVER = "widget_color_over"

/** 警告しきい値（残り時間）が未保存であることを表す番兵。0 は「警告色を使わない」を意味する正当な値のため使えない */
private const val WARN_REMAINING_UNSET = -1

/**
 * spec: docs/spec.md 5.7 設定項目の永続化（保存先は SharedPreferences）。
 * AndroidフレームワークAPI（SharedPreferences）に依存するため、CallRecordDbHelper/CallLogSync と
 * 同様にJVMユニットテスト対象外とする。変換・正規化ロジック自体は logic/AppSettings.kt の
 * 純粋関数（AppSettingsTest.kt でテスト済み）を再利用する。
 */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * 保存済みの plan_type は読まず、migrateAppSettings() で定額枠・通話別無料時間の実値から
     * 導出し直す（5.4.2）。廃止した CUSTOM の読み替えと、値と矛盾したプラン形式の解消を兼ねる。
     * plan_type の書き込み自体は、保存内容を外から読んだときに分かりやすいよう残している。
     */
    fun load(): AppSettings {
        val monthlyFreeMin = clampMonthlyFreeMin(
            prefs.getInt(KEY_MONTHLY_FREE_MIN, DEFAULT_APP_SETTINGS.monthlyFreeMin)
        )
        val warnRemainingMin = prefs.getInt(KEY_WARN_REMAINING_MIN, WARN_REMAINING_UNSET)
            .takeIf { it != WARN_REMAINING_UNSET }
            ?: warnRemainingFromLegacy(monthlyFreeMin)
        return migrateAppSettings(
            AppSettings(
            planType = DEFAULT_APP_SETTINGS.planType, // migrateAppSettings が上書きする
            startDay = clampStartDay(prefs.getInt(KEY_START_DAY, DEFAULT_APP_SETTINGS.startDay)),
            monthlyFreeMin = monthlyFreeMin,
            perCallFreeMin = clampPerCallFreeMin(
                prefs.getInt(KEY_PER_CALL_FREE_MIN, DEFAULT_APP_SETTINGS.perCallFreeMin)
            ),
            unitSec = normalizeUnitSec(prefs.getInt(KEY_UNIT_SEC, DEFAULT_APP_SETTINGS.unitSec)),
            unitPrice = clampUnitPrice(prefs.getInt(KEY_UNIT_PRICE, DEFAULT_APP_SETTINGS.unitPrice)),
            excludePrefixes = prefs.getString(KEY_EXCLUDE_PREFIXES, null)
                ?.let { parseExcludePrefixes(it) }
                ?: DEFAULT_APP_SETTINGS.excludePrefixes,
            widgetBgTransparencyStep = clampWidgetBgTransparencyStep(
                prefs.getInt(
                    KEY_WIDGET_BG_TRANSPARENCY_STEP,
                    DEFAULT_APP_SETTINGS.widgetBgTransparencyStep
                )
            ),
            warnRemainingMin = warnRemainingMin,
            widgetColorNormalIndex = clampWidgetColorIndex(
                prefs.getInt(KEY_WIDGET_COLOR_NORMAL, DEFAULT_APP_SETTINGS.widgetColorNormalIndex)
            ),
            widgetColorWarningIndex = clampWidgetColorIndex(
                prefs.getInt(KEY_WIDGET_COLOR_WARNING, DEFAULT_APP_SETTINGS.widgetColorWarningIndex)
            ),
            widgetColorOverIndex = clampWidgetColorIndex(
                prefs.getInt(KEY_WIDGET_COLOR_OVER, DEFAULT_APP_SETTINGS.widgetColorOverIndex)
            )
            )
        )
    }

    /**
     * 警告しきい値が「残り◯分」で保存されていないときの値。
     *
     * - 消費量ベースだった頃のキーがあれば、切り替わるタイミングが変わらないよう
     *   `定額枠 − 消費しきい値` として読み替える
     * - どちらも無ければ定額枠の 20%（残り 20% ＝ 消費 80% で警告となり、しきい値が
     *   設定項目になる前の「使用率 80% で警告」と同じ位置）を入れる。DEFAULT_APP_SETTINGS の
     *   固定値ではなく実際の定額枠から計算するのは、見た目を変えないため
     */
    private fun warnRemainingFromLegacy(monthlyFreeMin: Int): Int {
        val consumed = prefs.getInt(KEY_WARN_CONSUMED_MIN_LEGACY, WARN_REMAINING_UNSET)
        if (consumed == WARN_REMAINING_UNSET) return defaultWarnRemainingMin(monthlyFreeMin)
        return clampWarnRemainingMin(monthlyFreeMin - consumed, monthlyFreeMin)
    }

    /**
     * spec: docs/spec.md 7.2 設定変更時。保存完了後、同期は行わず全ウィジェットの
     * 再集計・再描画のみをトリガーする（CallTimeWidgetProvider.notifyWidgetsSettingsChanged）。
     */
    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PLAN_TYPE, settings.planType.name)
            .putInt(KEY_START_DAY, settings.startDay)
            .putInt(KEY_MONTHLY_FREE_MIN, settings.monthlyFreeMin)
            .putInt(KEY_PER_CALL_FREE_MIN, settings.perCallFreeMin)
            .putInt(KEY_UNIT_SEC, settings.unitSec)
            .putInt(KEY_UNIT_PRICE, settings.unitPrice)
            .putString(KEY_EXCLUDE_PREFIXES, excludePrefixesToText(settings.excludePrefixes))
            .putInt(KEY_WIDGET_BG_TRANSPARENCY_STEP, settings.widgetBgTransparencyStep)
            .putInt(KEY_WARN_REMAINING_MIN, settings.warnRemainingMin)
            // 読み替え済みの旧キーは残さない。保存内容を外から読んだときに
            // 意味の違う値が 2 つ並ぶのを避ける
            .remove(KEY_WARN_CONSUMED_MIN_LEGACY)
            .putInt(KEY_WIDGET_COLOR_NORMAL, settings.widgetColorNormalIndex)
            .putInt(KEY_WIDGET_COLOR_WARNING, settings.widgetColorWarningIndex)
            .putInt(KEY_WIDGET_COLOR_OVER, settings.widgetColorOverIndex)
            .apply()
        notifyWidgetsSettingsChanged(appContext)
    }
}
