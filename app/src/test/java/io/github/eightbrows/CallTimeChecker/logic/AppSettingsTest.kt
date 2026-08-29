package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
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
    fun `effective settings for custom plan leaves both fields untouched`() {
        val settings = DEFAULT_APP_SETTINGS.copy(planType = PlanType.CUSTOM, monthlyFreeMin = 70, perCallFreeMin = 5)
        val effective = effectiveAppSettings(settings)
        assertEquals(70, effective.monthlyFreeMin)
        assertEquals(5, effective.perCallFreeMin)
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
}
