package io.github.eightbrows.CallTimeChecker.data

import android.content.Context
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
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

/**
 * spec: docs/spec.md 5.7 設定項目の永続化（保存先は SharedPreferences）。
 * AndroidフレームワークAPI（SharedPreferences）に依存するため、CallRecordDbHelper/CallLogSync と
 * 同様にJVMユニットテスト対象外とする。変換・正規化ロジック自体は logic/AppSettings.kt の
 * 純粋関数（AppSettingsTest.kt でテスト済み）を再利用する。
 */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val planType = prefs.getString(KEY_PLAN_TYPE, null)?.let {
            runCatching { PlanType.valueOf(it) }.getOrNull()
        } ?: DEFAULT_APP_SETTINGS.planType

        return AppSettings(
            planType = planType,
            startDay = clampStartDay(prefs.getInt(KEY_START_DAY, DEFAULT_APP_SETTINGS.startDay)),
            monthlyFreeMin = prefs.getInt(KEY_MONTHLY_FREE_MIN, DEFAULT_APP_SETTINGS.monthlyFreeMin),
            perCallFreeMin = prefs.getInt(KEY_PER_CALL_FREE_MIN, DEFAULT_APP_SETTINGS.perCallFreeMin),
            unitSec = normalizeUnitSec(prefs.getInt(KEY_UNIT_SEC, DEFAULT_APP_SETTINGS.unitSec)),
            unitPrice = prefs.getInt(KEY_UNIT_PRICE, DEFAULT_APP_SETTINGS.unitPrice),
            excludePrefixes = prefs.getString(KEY_EXCLUDE_PREFIXES, null)
                ?.let { parseExcludePrefixes(it) }
                ?: DEFAULT_APP_SETTINGS.excludePrefixes
        )
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
            .apply()
        notifyWidgetsSettingsChanged(appContext)
    }
}
