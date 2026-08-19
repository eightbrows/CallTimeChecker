package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 のウィジェット表示テンプレート・配色判定のテスト
 */
class WidgetPresentationTest {

    private fun settings(monthlyFreeSec: Int, perCallFreeSec: Int = 0) =
        Settings(monthlyFreeSec, perCallFreeSec, unitSec = 30, unitPrice = 22, excludePrefixes = DEFAULT_EXCLUDE_PREFIXES)

    private fun result(countedSec: Int, amount: Int, callCount: Int = 1) =
        Result(countedSec = countedSec, billedSec = 0, amount = amount, excludedSec = 0, callCount = callCount, billedCallCount = 0)

    // --- 月間定額型: テンプレート ---

    @Test
    fun `monthly plan under quota shows minutes, yen and call count without overage note`() {
        val content = presentWidget(result(countedSec = 42 * 60, amount = 0, callCount = 3), settings(monthlyFreeSec = 70 * 60))
        assertEquals("42分 / 70分 (3件)", content.line1)
        assertEquals("¥0", content.line2)
    }

    @Test
    fun `monthly plan over quota shows overage minutes in yen line`() {
        val content = presentWidget(result(countedSec = 78 * 60, amount = 352, callCount = 5), settings(monthlyFreeSec = 70 * 60))
        assertEquals("78分 / 70分 (5件)", content.line1)
        assertEquals("¥352 (超過 8分)", content.line2)
    }

    // --- 月間定額型: 配色境界 (79% / 80% / 99% / 100%) ---

    @Test
    fun `monthly plan usage just under 80 percent is NORMAL`() {
        val settings = settings(monthlyFreeSec = 100)
        val content = presentWidget(result(countedSec = 79, amount = 0), settings)
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    @Test
    fun `monthly plan usage exactly 80 percent is WARNING`() {
        val settings = settings(monthlyFreeSec = 100)
        val content = presentWidget(result(countedSec = 80, amount = 0), settings)
        assertEquals(WidgetColor.WARNING, content.color)
    }

    @Test
    fun `monthly plan usage just under 100 percent is WARNING`() {
        val settings = settings(monthlyFreeSec = 100)
        val content = presentWidget(result(countedSec = 99, amount = 0), settings)
        assertEquals(WidgetColor.WARNING, content.color)
    }

    @Test
    fun `monthly plan usage exactly 100 percent is OVER`() {
        val settings = settings(monthlyFreeSec = 100)
        val content = presentWidget(result(countedSec = 100, amount = 0), settings)
        assertEquals(WidgetColor.OVER, content.color)
    }

    @Test
    fun `monthly plan usage over 100 percent is OVER`() {
        val settings = settings(monthlyFreeSec = 100)
        val content = presentWidget(result(countedSec = 150, amount = 100), settings)
        assertEquals(WidgetColor.OVER, content.color)
    }

    // --- 1 通話定額型: テンプレート・配色 ---

    @Test
    fun `per-call plan shows call minutes, call count and yen`() {
        val content = presentWidget(result(countedSec = 128 * 60, amount = 374, callCount = 4), settings(monthlyFreeSec = 0, perCallFreeSec = 300))
        assertEquals("通話 128分 (4件)", content.line1)
        assertEquals("¥374", content.line2)
    }

    @Test
    fun `per-call plan with zero amount is NORMAL`() {
        val content = presentWidget(result(countedSec = 60, amount = 0), settings(monthlyFreeSec = 0, perCallFreeSec = 300))
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    @Test
    fun `per-call plan with nonzero amount is WARNING, never OVER`() {
        val content = presentWidget(result(countedSec = 1000 * 60, amount = 99999), settings(monthlyFreeSec = 0, perCallFreeSec = 300))
        assertEquals(WidgetColor.WARNING, content.color)
    }
}
