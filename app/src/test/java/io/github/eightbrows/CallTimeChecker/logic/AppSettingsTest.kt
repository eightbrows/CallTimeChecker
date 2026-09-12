package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * spec: docs/spec.md 5.7 設定項目のテスト。
 * AppSettings(分単位・UI用) <-> Settings(秒単位・Billing.kt が参照する集計ロジック用) の変換、
 * プラン形式による固定ルール、入力値の正規化、除外番号リストのテキスト変換を検証する。
 */
class AppSettingsTest {

    // --- 初期値がPhase2/3のハードコード値と一致すること（回帰防止） ---

    @Test
    fun `default app settings matches previous hardcoded Phase2 3 values`() {
        val billing = toBillingSettings(DEFAULT_APP_SETTINGS)
        assertEquals(70 * 60, billing.monthlyFreeSec)
        assertEquals(0, billing.perCallFreeSec)
        assertEquals(30, billing.unitSec)
        assertEquals(22, billing.unitPrice)
        assertEquals(DEFAULT_EXCLUDE_PREFIXES, billing.excludePrefixes)
        assertEquals(1, DEFAULT_APP_SETTINGS.startDay)
        assertEquals(PlanType.MONTHLY, DEFAULT_APP_SETTINGS.planType)
    }

    // --- プラン形式による固定ルール (5.7 備考) ---

    @Test
    fun `effective settings for monthly plan forces per-call free minutes to zero`() {
        val settings = DEFAULT_APP_SETTINGS.copy(planType = PlanType.MONTHLY, monthlyFreeMin = 70, perCallFreeMin = 5)
        val effective = effectiveAppSettings(settings)
        assertEquals(70, effective.monthlyFreeMin)
        assertEquals(0, effective.perCallFreeMin)
    }

    @Test
    fun `effective settings for per-call plan forces monthly free minutes to zero`() {
        val settings = DEFAULT_APP_SETTINGS.copy(planType = PlanType.PER_CALL, monthlyFreeMin = 70, perCallFreeMin = 5)
        val effective = effectiveAppSettings(settings)
        assertEquals(0, effective.monthlyFreeMin)
        assertEquals(5, effective.perCallFreeMin)
    }

    @Test
    fun `effective settings for pay-as-you-go plan forces both free minutes to zero`() {
        val settings = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.PAY_AS_YOU_GO,
            monthlyFreeMin = 70,
            perCallFreeMin = 5
        )
        val effective = effectiveAppSettings(settings)
        assertEquals(0, effective.monthlyFreeMin)
        assertEquals(0, effective.perCallFreeMin)
    }

    @Test
    fun `toBillingSettings for pay-as-you-go leaves only the metered parameters`() {
        val settings = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.PAY_AS_YOU_GO,
            monthlyFreeMin = 70,
            perCallFreeMin = 5,
            unitSec = 30,
            unitPrice = 22
        )
        val billing = toBillingSettings(settings)
        assertEquals(0, billing.monthlyFreeSec)
        assertEquals(0, billing.perCallFreeSec)
        assertEquals(30, billing.unitSec)
        assertEquals(22, billing.unitPrice)
    }

    // --- toBillingSettings: 分->秒 変換 + プラン形式ルールの適用 ---

    @Test
    fun `toBillingSettings converts minutes to seconds and applies plan type rule`() {
        val settings = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.PER_CALL,
            monthlyFreeMin = 70,
            perCallFreeMin = 5,
            unitSec = 60,
            unitPrice = 30,
            excludePrefixes = listOf("0120")
        )
        val billing = toBillingSettings(settings)
        assertEquals(0, billing.monthlyFreeSec)
        assertEquals(5 * 60, billing.perCallFreeSec)
        assertEquals(60, billing.unitSec)
        assertEquals(30, billing.unitPrice)
        assertEquals(listOf("0120"), billing.excludePrefixes)
    }

    // --- 起算日のクランプ (1..31) ---

    @Test
    fun `clampStartDay clamps below 1 up to 1`() {
        assertEquals(1, clampStartDay(0))
        assertEquals(1, clampStartDay(-5))
    }

    @Test
    fun `clampStartDay clamps above 31 down to 31`() {
        assertEquals(31, clampStartDay(32))
        assertEquals(31, clampStartDay(100))
    }

    @Test
    fun `clampStartDay leaves valid values untouched`() {
        assertEquals(1, clampStartDay(1))
        assertEquals(15, clampStartDay(15))
        assertEquals(31, clampStartDay(31))
    }

    // --- 課金単位 (1〜300 秒) ---

    @Test
    fun `clampUnitSec keeps the presets and any value inside the range`() {
        assertEquals(30, clampUnitSec(30))
        assertEquals(60, clampUnitSec(60))
        // プリセット以外のカスタム値が丸められないこと（保存・読み込みで消えないための要）
        assertEquals(45, clampUnitSec(45))
        assertEquals(90, clampUnitSec(90))
        assertEquals(300, clampUnitSec(300))
        assertEquals(1, clampUnitSec(1))
    }

    @Test
    fun `clampUnitSec pulls out of range values back into 1 to 300`() {
        // 下限が 1 なのは calculate() の unitSec による除算でゼロ除算を起こさないため
        assertEquals(1, clampUnitSec(0))
        assertEquals(1, clampUnitSec(-5))
        assertEquals(300, clampUnitSec(301))
        assertEquals(300, clampUnitSec(100000))
    }

    @Test
    fun `the unit second range matches what the settings screen validates`() {
        assertEquals(1, UNIT_SEC_RANGE.first)
        assertEquals(300, UNIT_SEC_RANGE.last)
    }

    // --- 除外番号リスト: 改行区切りテキスト <-> List<String> ---

    @Test
    fun `parseExcludePrefixes splits on newlines and trims blank lines`() {
        val text = "0570\n 0180 \n\n0990\n"
        assertEquals(listOf("0570", "0180", "0990"), parseExcludePrefixes(text))
    }

    @Test
    fun `parseExcludePrefixes handles CRLF line endings`() {
        val text = "0570\r\n0180\r\n"
        assertEquals(listOf("0570", "0180"), parseExcludePrefixes(text))
    }

    @Test
    fun `parseExcludePrefixes on blank text returns empty list`() {
        assertEquals(emptyList<String>(), parseExcludePrefixes(""))
        assertEquals(emptyList<String>(), parseExcludePrefixes("\n\n  \n"))
    }

    @Test
    fun `excludePrefixesToText joins with newlines`() {
        assertEquals("0570\n0180\n0990", excludePrefixesToText(listOf("0570", "0180", "0990")))
    }

    @Test
    fun `parseExcludePrefixes and excludePrefixesToText round-trip`() {
        val prefixes = DEFAULT_EXCLUDE_PREFIXES
        assertEquals(prefixes, parseExcludePrefixes(excludePrefixesToText(prefixes)))
    }

    // --- 入力値の上限クランプ (5.7) ---

    @Test
    fun `clampMonthlyFreeMin keeps values within 0 to 1440`() {
        assertEquals(0, clampMonthlyFreeMin(-1))
        assertEquals(0, clampMonthlyFreeMin(0))
        assertEquals(70, clampMonthlyFreeMin(70))
        assertEquals(1440, clampMonthlyFreeMin(1440))
        assertEquals(1440, clampMonthlyFreeMin(1441))
        assertEquals(1440, clampMonthlyFreeMin(Int.MAX_VALUE))
    }

    @Test
    fun `clampPerCallFreeMin keeps values within 0 to 180`() {
        assertEquals(0, clampPerCallFreeMin(-1))
        assertEquals(0, clampPerCallFreeMin(0))
        assertEquals(5, clampPerCallFreeMin(5))
        assertEquals(180, clampPerCallFreeMin(180))
        assertEquals(180, clampPerCallFreeMin(181))
    }

    @Test
    fun `clampUnitPrice keeps values within 0 to 999`() {
        assertEquals(0, clampUnitPrice(-1))
        assertEquals(0, clampUnitPrice(0))
        assertEquals(22, clampUnitPrice(22))
        assertEquals(999, clampUnitPrice(999))
        assertEquals(999, clampUnitPrice(1000))
    }

    @Test
    fun `default app settings are within the clamp ranges`() {
        assertEquals(DEFAULT_APP_SETTINGS.monthlyFreeMin, clampMonthlyFreeMin(DEFAULT_APP_SETTINGS.monthlyFreeMin))
        assertEquals(DEFAULT_APP_SETTINGS.perCallFreeMin, clampPerCallFreeMin(DEFAULT_APP_SETTINGS.perCallFreeMin))
        assertEquals(DEFAULT_APP_SETTINGS.unitPrice, clampUnitPrice(DEFAULT_APP_SETTINGS.unitPrice))
        assertEquals(DEFAULT_APP_SETTINGS.startDay, clampStartDay(DEFAULT_APP_SETTINGS.startDay))
    }


    // --- プラン形式の導出とマイグレーション (5.4.2 / 5.7) ---

    @Test
    fun `derivePlanType picks the plan implied by the two free-minute fields`() {
        assertEquals(PlanType.MONTHLY, derivePlanType(70, 0))
        assertEquals(PlanType.PER_CALL, derivePlanType(0, 5))
        assertEquals(PlanType.PAY_AS_YOU_GO, derivePlanType(0, 0))
    }

    @Test
    fun `derivePlanType prefers monthly when both fields are set (old CUSTOM settings)`() {
        assertEquals(PlanType.MONTHLY, derivePlanType(70, 5))
    }

    @Test
    fun `migrateAppSettings converts old CUSTOM settings with both fields set to monthly`() {
        // 旧 CUSTOM は plan_type として復元できないため、値だけが残っている状態を模す
        val stored = DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 70, perCallFreeMin = 5)
        val migrated = migrateAppSettings(stored)
        assertEquals(PlanType.MONTHLY, migrated.planType)
        assertEquals(70, migrated.monthlyFreeMin)
        // 月間定額型では通話別無料時間は 0 固定になる（CUSTOM 廃止に伴う値の切り捨て）
        assertEquals(0, migrated.perCallFreeMin)
    }

    @Test
    fun `migrateAppSettings converts old CUSTOM settings with only per-call set to per-call`() {
        val stored = DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 0, perCallFreeMin = 5)
        val migrated = migrateAppSettings(stored)
        assertEquals(PlanType.PER_CALL, migrated.planType)
        assertEquals(0, migrated.monthlyFreeMin)
        assertEquals(5, migrated.perCallFreeMin)
    }

    @Test
    fun `migrateAppSettings converts settings with no free minutes to pay-as-you-go`() {
        val stored = DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 0, perCallFreeMin = 0)
        assertEquals(PlanType.PAY_AS_YOU_GO, migrateAppSettings(stored).planType)
    }

    @Test
    fun `migrateAppSettings resolves a plan type that contradicts the stored values`() {
        // 月間定額型なのに定額枠 0。新しい下限 1 を満たさないため従量課金として読み込む
        val stored = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.MONTHLY,
            monthlyFreeMin = 0,
            perCallFreeMin = 0
        )
        assertEquals(PlanType.PAY_AS_YOU_GO, migrateAppSettings(stored).planType)
    }

    @Test
    fun `migrateAppSettings leaves consistent settings untouched`() {
        val monthly = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.MONTHLY,
            monthlyFreeMin = 70,
            perCallFreeMin = 0
        )
        assertEquals(monthly, migrateAppSettings(monthly))

        val perCall = DEFAULT_APP_SETTINGS.copy(
            planType = PlanType.PER_CALL,
            monthlyFreeMin = 0,
            perCallFreeMin = 5
        )
        assertEquals(perCall, migrateAppSettings(perCall))
    }

    @Test
    fun `migrated settings always satisfy the input range of the resolved plan type`() {
        val stored = listOf(
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 70, perCallFreeMin = 5),
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 70, perCallFreeMin = 0),
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 0, perCallFreeMin = 5),
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 0, perCallFreeMin = 0)
        )
        for (settings in stored) {
            val migrated = migrateAppSettings(settings)
            assertNull(
                validateRange(migrated.monthlyFreeMin.toString(), monthlyFreeMinRange(migrated.planType))
            )
            assertNull(
                validateRange(migrated.perCallFreeMin.toString(), perCallFreeMinRange(migrated.planType))
            )
        }
    }

    // --- プラン形式ごとの入力範囲 (5.7) ---

    @Test
    fun `monthlyFreeMinRange is 1 to 1440 only for the monthly plan`() {
        assertEquals(1..1440, monthlyFreeMinRange(PlanType.MONTHLY))
        assertNull(monthlyFreeMinRange(PlanType.PER_CALL))
        assertNull(monthlyFreeMinRange(PlanType.PAY_AS_YOU_GO))
    }

    @Test
    fun `perCallFreeMinRange is 1 to 180 only for the per-call plan`() {
        assertEquals(1..180, perCallFreeMinRange(PlanType.PER_CALL))
        assertNull(perCallFreeMinRange(PlanType.MONTHLY))
        assertNull(perCallFreeMinRange(PlanType.PAY_AS_YOU_GO))
    }

    @Test
    fun `zero is rejected for the free minutes of the plan that owns the field`() {
        assertEquals(
            InputError.OutOfRange(1, 1440),
            validateRange("0", monthlyFreeMinRange(PlanType.MONTHLY))
        )
        assertEquals(
            InputError.OutOfRange(1, 180),
            validateRange("0", perCallFreeMinRange(PlanType.PER_CALL))
        )
    }

    @Test
    fun `validateRange skips validation when the field is fixed to zero`() {
        // 0 固定の欄は入力値を使わないため、範囲外の残存値が入っていても保存をブロックしない
        assertNull(validateRange("0", monthlyFreeMinRange(PlanType.PAY_AS_YOU_GO)))
        assertNull(validateRange("9999", perCallFreeMinRange(PlanType.MONTHLY)))
        assertNull(validateRange("", monthlyFreeMinRange(PlanType.PER_CALL)))
    }

    // --- 除外番号リストの折りたたみ表示用プレビュー (5.7) ---

    @Test
    fun `excludePrefixesPreview lists every prefix when there are three or fewer`() {
        assertEquals(
            ExcludePrefixesPreview(listOf("0570", "0180"), rest = 0),
            excludePrefixesPreview(listOf("0570", "0180"))
        )
        assertEquals(
            ExcludePrefixesPreview(listOf("0570", "0180", "0990"), rest = 0),
            excludePrefixesPreview(listOf("0570", "0180", "0990"))
        )
    }

    @Test
    fun `excludePrefixesPreview truncates to the first three with a remainder count`() {
        // DEFAULT_EXCLUDE_PREFIXES は 10 件（5.3.2）。「ほか 7 件」の文言は UI 側が付ける（5.8）
        assertEquals(
            ExcludePrefixesPreview(listOf("0570", "0180", "0990"), rest = 7),
            excludePrefixesPreview(DEFAULT_EXCLUDE_PREFIXES)
        )
        assertEquals(
            ExcludePrefixesPreview(listOf("a", "b", "c"), rest = 1),
            excludePrefixesPreview(listOf("a", "b", "c", "d"))
        )
        assertEquals(3, EXCLUDE_PREVIEW_HEAD_COUNT)
    }

    @Test
    fun `excludePrefixesPreview on an empty list has nothing to show`() {
        assertEquals(ExcludePrefixesPreview(emptyList(), rest = 0), excludePrefixesPreview(emptyList()))
    }

    // --- 入力欄の範囲チェック (5.7)。範囲外は保存をブロックするための InputError を返す（文言は UI 側、5.8） ---

    @Test
    fun `validateRange returns null for values inside the range`() {
        assertNull(validateRange("0", 0, 1440))
        assertNull(validateRange("70", 0, 1440))
        assertNull(validateRange("1440", 0, 1440))
        assertNull(validateRange(" 70 ", 0, 1440))
    }

    @Test
    fun `validateRange reports out-of-range values with the range so the message can quote it`() {
        assertEquals(InputError.OutOfRange(0, 1440), validateRange("1441", 0, 1440))
        assertEquals(InputError.OutOfRange(1, 31), validateRange("0", 1, 31))
        assertEquals(InputError.OutOfRange(1, 31), validateRange("32", 1, 31))
    }

    @Test
    fun `validateRange reports empty and non-numeric input`() {
        assertEquals(InputError.NotANumber, validateRange("", 0, 999))
        assertEquals(InputError.NotANumber, validateRange("   ", 0, 999))
        assertEquals(InputError.NotANumber, validateRange("abc", 0, 999))
    }

    @Test
    fun `validateRange rejects values that overflow Int`() {
        assertEquals(InputError.NotANumber, validateRange("99999999999", 0, 999))
    }

    // --- ウィジェット背景の透過率（5.7） ---

    @Test
    fun `widget background transparency defaults to fully opaque`() {
        assertEquals(0, DEFAULT_APP_SETTINGS.widgetBgTransparencyStep)
    }

    @Test
    fun `widgetBgTransparencyLabel renders 12 point 5 percent steps`() {
        assertEquals(
            listOf("0%", "12.5%", "25%", "37.5%", "50%", "62.5%", "75%", "87.5%", "100%"),
            (0 until WIDGET_BG_TRANSPARENCY_STEP_COUNT).map { widgetBgTransparencyLabel(it) }
        )
    }

    @Test
    fun `clampWidgetBgTransparencyStep keeps the step within the available range`() {
        assertEquals(0, clampWidgetBgTransparencyStep(-1))
        assertEquals(0, clampWidgetBgTransparencyStep(0))
        assertEquals(8, clampWidgetBgTransparencyStep(8))
        assertEquals(8, clampWidgetBgTransparencyStep(9))
    }

    @Test
    fun `widgetBgTransparencyLabel clamps out of range steps`() {
        assertEquals("0%", widgetBgTransparencyLabel(-3))
        assertEquals("100%", widgetBgTransparencyLabel(99))
    }
    // --- 警告しきい値（5.7 の「残り◯分」） ---

    @Test
    fun `warn remaining defaults to 20 percent of the monthly quota`() {
        // 残り 20% ＝ 消費 80% で、しきい値が設定項目になる前と同じ切り替わり位置
        assertEquals(14, DEFAULT_APP_SETTINGS.warnRemainingMin)
        assertEquals(14, defaultWarnRemainingMin(70))
        assertEquals(20, defaultWarnRemainingMin(100))
    }

    @Test
    fun `warn remaining default stays inside the range for tiny quotas`() {
        // 20% が 1 分未満になる定額枠でも、下限の残り 1 分に丸める
        assertEquals(1, defaultWarnRemainingMin(3))
        assertEquals(1, defaultWarnRemainingMin(2))
        // 定額枠 1 分では警告色の帯が存在しない
        assertEquals(0, defaultWarnRemainingMin(1))
    }

    @Test
    fun `warn remaining range stops one minute below the monthly quota`() {
        assertEquals(1..69, warnRemainingMinRange(PlanType.MONTHLY, 70))
        assertEquals(1..1, warnRemainingMinRange(PlanType.MONTHLY, 2))
    }

    @Test
    fun `warn remaining has no range outside the monthly plan`() {
        assertNull(warnRemainingMinRange(PlanType.PER_CALL, 70))
        assertNull(warnRemainingMinRange(PlanType.PAY_AS_YOU_GO, 70))
        // 定額枠 1 分では 1..0 となり範囲が空になるため入力対象外
        assertNull(warnRemainingMinRange(PlanType.MONTHLY, 1))
        assertNull(warnRemainingMinRange(PlanType.MONTHLY, 0))
    }

    @Test
    fun `clampWarnRemainingMin keeps the remaining minutes within the quota`() {
        assertEquals(69, clampWarnRemainingMin(70, 70))
        assertEquals(69, clampWarnRemainingMin(999, 70))
        assertEquals(1, clampWarnRemainingMin(0, 70))
        assertEquals(56, clampWarnRemainingMin(56, 70))
        // 範囲が無いときは 0（警告色を使わない）
        assertEquals(0, clampWarnRemainingMin(56, 1))
    }


    @Test
    fun `migration resets a warn remaining that no longer fits the quota`() {
        // 定額枠を外から 40 分に書き換えられた場合。残り 56 分は範囲外なので既定値へ戻す
        val migrated = migrateAppSettings(
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 40, warnRemainingMin = 56)
        )
        assertEquals(8, migrated.warnRemainingMin)
    }

    @Test
    fun `migration keeps a warn remaining that still fits the quota`() {
        val migrated = migrateAppSettings(
            DEFAULT_APP_SETTINGS.copy(monthlyFreeMin = 100, warnRemainingMin = 56)
        )
        assertEquals(56, migrated.warnRemainingMin)
    }

    @Test
    fun `migration keeps the warn remaining for plans that do not use it`() {
        // 1 通話定額型に切り替えても、月間定額型に戻したときのために値は残す
        val migrated = migrateAppSettings(
            DEFAULT_APP_SETTINGS.copy(
                monthlyFreeMin = 0,
                perCallFreeMin = 5,
                warnRemainingMin = 56
            )
        )
        assertEquals(PlanType.PER_CALL, migrated.planType)
        assertEquals(56, migrated.warnRemainingMin)
    }

    // --- ウィジェット背景色（5.7） ---

    @Test
    fun `widget colors default to the previous fixed palette`() {
        assertEquals(WIDGET_COLOR_INDEX_WHITE, DEFAULT_APP_SETTINGS.widgetColorNormalIndex)
        assertEquals(WIDGET_COLOR_INDEX_ORANGE, DEFAULT_APP_SETTINGS.widgetColorWarningIndex)
        assertEquals(WIDGET_COLOR_INDEX_RED, DEFAULT_APP_SETTINGS.widgetColorOverIndex)
    }

    @Test
    fun `clampWidgetColorIndex keeps the index inside the palette`() {
        assertEquals(0, clampWidgetColorIndex(-1))
        assertEquals(0, clampWidgetColorIndex(0))
        assertEquals(WIDGET_COLOR_PALETTE.lastIndex, clampWidgetColorIndex(WIDGET_COLOR_PALETTE.size))
    }

    @Test
    fun `migration clamps out of range color indices`() {
        val migrated = migrateAppSettings(
            DEFAULT_APP_SETTINGS.copy(
                widgetColorNormalIndex = -5,
                widgetColorWarningIndex = 99,
                widgetColorOverIndex = 3
            )
        )
        assertEquals(0, migrated.widgetColorNormalIndex)
        assertEquals(WIDGET_COLOR_PALETTE.lastIndex, migrated.widgetColorWarningIndex)
        assertEquals(3, migrated.widgetColorOverIndex)
    }

    @Test
    fun `migration clamps every ranged field so the result is safe on its own`() {
        // SettingsRepository.load() を経ずに migrate 単体を通しても範囲内になること。
        // unitSec は calculate() の除数なので、0 が残るとゼロ除算になる
        val migrated = migrateAppSettings(
            DEFAULT_APP_SETTINGS.copy(
                startDay = 40,
                unitSec = 0,
                unitPrice = 5000,
                widgetBgTransparencyStep = 99
            )
        )
        assertEquals(31, migrated.startDay)
        assertEquals(UNIT_SEC_RANGE.first, migrated.unitSec)
        assertEquals(999, migrated.unitPrice)
        assertEquals(WIDGET_BG_TRANSPARENCY_STEP_COUNT - 1, migrated.widgetBgTransparencyStep)
    }

    @Test
    fun `the warn threshold needs at least the documented monthly free minutes`() {
        assertEquals(2, WARN_REMAINING_MIN_MONTHLY_FREE_MIN)
        assertNull(warnRemainingMinRange(PlanType.MONTHLY, WARN_REMAINING_MIN_MONTHLY_FREE_MIN - 1))
        assertEquals(1..1, warnRemainingMinRange(PlanType.MONTHLY, WARN_REMAINING_MIN_MONTHLY_FREE_MIN))
    }

    // --- アプリ本体の配色（5.7） ---

    @Test
    fun `the theme defaults to following the device setting`() {
        assertEquals(ThemeMode.SYSTEM, DEFAULT_APP_SETTINGS.themeMode)
        assertEquals(ThemeMode.SYSTEM, DEFAULT_THEME_MODE)
    }


    @Test
    fun `theme modes round trip through their stored value`() {
        for (mode in ThemeMode.entries) {
            assertEquals(mode, themeModeFromPrefsValue(mode.prefsValue))
        }
    }

    @Test
    fun `stored theme values do not depend on the enum names`() {
        // R8 の難読化で name が変わっても保存済みの値が読めるよう、保存値は独立に持つ。
        // 値を変えると利用者の設定が既定へ戻るので、ここで固定しておく
        assertEquals("system", ThemeMode.SYSTEM.prefsValue)
        assertEquals("light", ThemeMode.LIGHT.prefsValue)
        assertEquals("dark", ThemeMode.DARK.prefsValue)
        assertEquals(ThemeMode.entries.size, ThemeMode.entries.map { it.prefsValue }.toSet().size)
    }

    @Test
    fun `an unsaved or unknown theme value falls back to the default`() {
        assertEquals(DEFAULT_THEME_MODE, themeModeFromPrefsValue(null))
        assertEquals(DEFAULT_THEME_MODE, themeModeFromPrefsValue(""))
        assertEquals(DEFAULT_THEME_MODE, themeModeFromPrefsValue("SYSTEM"))
        assertEquals(DEFAULT_THEME_MODE, themeModeFromPrefsValue("midnight"))
    }

    @Test
    fun `migration leaves the theme alone`() {
        // 配色はプラン形式・定額枠と無関係な独立した設定なので、正規化の影響を受けない
        for (mode in ThemeMode.entries) {
            val stored = DEFAULT_APP_SETTINGS.copy(themeMode = mode)
            assertEquals(mode, migrateAppSettings(stored).themeMode)
            assertEquals(mode, effectiveAppSettings(stored).themeMode)
        }
    }
}
