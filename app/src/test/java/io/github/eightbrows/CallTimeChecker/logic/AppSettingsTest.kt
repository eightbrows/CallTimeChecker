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

    // --- 課金単位の正規化 (30 / 60 のみ) ---

    @Test
    fun `normalizeUnitSec keeps 30 and 60`() {
        assertEquals(30, normalizeUnitSec(30))
        assertEquals(60, normalizeUnitSec(60))
    }

    @Test
    fun `normalizeUnitSec falls back to 30 for any other value`() {
        assertEquals(30, normalizeUnitSec(45))
        assertEquals(30, normalizeUnitSec(0))
        assertEquals(30, normalizeUnitSec(90))
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

    // --- プラン形式の表示名 (5.7) ---

    @Test
    fun `planTypeLabel returns the Japanese label used in the UI`() {
        assertEquals("月間定額型", planTypeLabel(PlanType.MONTHLY))
        assertEquals("1通話定額型", planTypeLabel(PlanType.PER_CALL))
        assertEquals("従量課金", planTypeLabel(PlanType.PAY_AS_YOU_GO))
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
            "1〜1440 の範囲で入力してください",
            validateRange("0", monthlyFreeMinRange(PlanType.MONTHLY))
        )
        assertEquals(
            "1〜180 の範囲で入力してください",
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
        assertEquals("0570, 0180", excludePrefixesPreview(listOf("0570", "0180")))
        assertEquals("0570, 0180, 0990", excludePrefixesPreview(listOf("0570", "0180", "0990")))
    }

    @Test
    fun `excludePrefixesPreview truncates to the first three with a remainder count`() {
        // DEFAULT_EXCLUDE_PREFIXES は 10 件（5.3.2）
        assertEquals("0570, 0180, 0990 ほか7件", excludePrefixesPreview(DEFAULT_EXCLUDE_PREFIXES))
        assertEquals("a, b, c ほか1件", excludePrefixesPreview(listOf("a", "b", "c", "d")))
    }

    @Test
    fun `excludePrefixesPreview on an empty list says so`() {
        assertEquals("（なし）", excludePrefixesPreview(emptyList()))
    }

    // --- 入力欄の範囲チェック (5.7)。範囲外は保存をブロックするためのエラーメッセージを返す ---

    @Test
    fun `validateRange returns null for values inside the range`() {
        assertNull(validateRange("0", 0, 1440))
        assertNull(validateRange("70", 0, 1440))
        assertNull(validateRange("1440", 0, 1440))
        assertNull(validateRange(" 70 ", 0, 1440))
    }

    @Test
    fun `validateRange reports out-of-range values`() {
        assertEquals("0〜1440 の範囲で入力してください", validateRange("1441", 0, 1440))
        assertEquals("1〜31 の範囲で入力してください", validateRange("0", 1, 31))
        assertEquals("1〜31 の範囲で入力してください", validateRange("32", 1, 31))
    }

    @Test
    fun `validateRange reports empty and non-numeric input`() {
        assertEquals("数値を入力してください", validateRange("", 0, 999))
        assertEquals("数値を入力してください", validateRange("   ", 0, 999))
        assertEquals("数値を入力してください", validateRange("abc", 0, 999))
    }

    @Test
    fun `validateRange rejects values that overflow Int`() {
        assertEquals("数値を入力してください", validateRange("99999999999", 0, 999))
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
}
