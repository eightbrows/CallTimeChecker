package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 のウィジェット表示テンプレート・配色判定のテスト
 */
class WidgetPresentationTest {

    private fun settings(monthlyFreeSec: Int, perCallFreeSec: Int = 0) =
        Settings(monthlyFreeSec, perCallFreeSec, unitSec = 30, unitPrice = 22, excludePrefixes = DEFAULT_EXCLUDE_PREFIXES)

    // 月間定額型の表示は quotaConsumedSec（切り上げ後の枠消費量）基準。
    // 切り上げが発生しないケースでは countedSec と一致するため、既定値は countedSec とする。
    private fun result(
        countedSec: Int,
        amount: Int,
        callCount: Int = 1,
        quotaConsumedSec: Int = countedSec
    ) = Result(
        countedSec = countedSec,
        quotaConsumedSec = quotaConsumedSec,
        billedSec = 0,
        amount = amount,
        excludedSec = 0,
        callCount = callCount,
        billedCallCount = 0
    )

    // --- 分表示のフォーマット（5.5.1 / 5.6.1 で共用） ---

    @Test
    fun `formatMinutes renders minutes with one decimal place`() {
        assertEquals("0.0", formatMinutes(0))
        assertEquals("1.6", formatMinutes(95))
        assertEquals("20.5", formatMinutes(20 * 60 + 30))
        assertEquals("70.0", formatMinutes(70 * 60))
    }

    @Test
    fun `formatMinutes rounds to the nearest tenth`() {
        // 3 秒 = 0.05 分。四捨五入で 0.1 分になる（切り捨てではない）
        assertEquals("0.1", formatMinutes(3))
        assertEquals("0.0", formatMinutes(2))
    }

    // --- 月間定額型: テンプレート ---

    @Test
    fun `monthly plan shows consumed minutes and remaining quota`() {
        val content = presentWidget(
            result(countedSec = 42 * 60, amount = 0, callCount = 3),
            settings(monthlyFreeSec = 70 * 60),
            PlanType.MONTHLY
        )
        assertEquals("通話時間 / 無料枠残", content.timeLabel)
        assertEquals("42.0 / 28.0分", content.timeValue)
        assertEquals("通話金額", content.amountLabel)
        assertEquals("0円", content.amountValue)
    }

    @Test
    fun `monthly plan clamps the remaining quota at zero when over`() {
        val content = presentWidget(
            result(countedSec = 78 * 60, amount = 352, callCount = 5),
            settings(monthlyFreeSec = 70 * 60),
            PlanType.MONTHLY
        )
        assertEquals("78.0 / 0.0分", content.timeValue)
        assertEquals("352円", content.amountValue)
    }

    // --- 月間定額型: 配色境界 (79% / 80% / 99% / 100%) ---

    @Test
    fun `monthly plan usage just under 80 percent is NORMAL`() {
        val content = presentWidget(result(countedSec = 79, amount = 0), settings(100), PlanType.MONTHLY)
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    @Test
    fun `monthly plan usage exactly 80 percent is WARNING`() {
        val content = presentWidget(result(countedSec = 80, amount = 0), settings(100), PlanType.MONTHLY)
        assertEquals(WidgetColor.WARNING, content.color)
    }

    @Test
    fun `monthly plan usage just under 100 percent is WARNING`() {
        val content = presentWidget(result(countedSec = 99, amount = 0), settings(100), PlanType.MONTHLY)
        assertEquals(WidgetColor.WARNING, content.color)
    }

    @Test
    fun `monthly plan usage exactly 100 percent is OVER`() {
        val content = presentWidget(result(countedSec = 100, amount = 0), settings(100), PlanType.MONTHLY)
        assertEquals(WidgetColor.OVER, content.color)
    }

    @Test
    fun `monthly plan usage over 100 percent is OVER`() {
        val content = presentWidget(result(countedSec = 150, amount = 100), settings(100), PlanType.MONTHLY)
        assertEquals(WidgetColor.OVER, content.color)
    }

    @Test
    fun `monthly plan with a zero quota falls back to NORMAL instead of dividing by zero`() {
        // 5.7 のマイグレーションにより設定画面からは作れない組み合わせ
        val content = presentWidget(result(countedSec = 60, amount = 22), settings(0), PlanType.MONTHLY)
        assertEquals(WidgetColor.NORMAL, content.color)
        assertEquals("1.0 / 0.0分", content.timeValue)
    }

    // --- 1 通話定額型: テンプレート・配色 ---

    @Test
    fun `per-call plan shows call minutes without a remaining quota`() {
        val content = presentWidget(
            result(countedSec = 128 * 60, amount = 374, callCount = 4),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals("通話時間", content.timeLabel)
        assertEquals("128.0分", content.timeValue)
        assertEquals("通話金額", content.amountLabel)
        assertEquals("374円", content.amountValue)
    }

    @Test
    fun `per-call plan with zero amount is NORMAL`() {
        val content = presentWidget(
            result(countedSec = 60, amount = 0),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    @Test
    fun `per-call plan with nonzero amount is WARNING, never OVER`() {
        val content = presentWidget(
            result(countedSec = 1000 * 60, amount = 99999),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals(WidgetColor.WARNING, content.color)
    }

    // --- 従量課金: テンプレート・配色 ---

    @Test
    fun `pay-as-you-go plan shows call minutes and the amount`() {
        val content = presentWidget(
            result(countedSec = 42 * 60, amount = 451, callCount = 41),
            settings(monthlyFreeSec = 0, perCallFreeSec = 0),
            PlanType.PAY_AS_YOU_GO
        )
        assertEquals("通話時間", content.timeLabel)
        assertEquals("42.0分", content.timeValue)
        assertEquals("451円", content.amountValue)
    }

    @Test
    fun `pay-as-you-go plan is always NORMAL because there is no quota to exceed`() {
        val zero = presentWidget(
            result(countedSec = 0, amount = 0),
            settings(0, 0),
            PlanType.PAY_AS_YOU_GO
        )
        val large = presentWidget(
            result(countedSec = 1000 * 60, amount = 99999),
            settings(0, 0),
            PlanType.PAY_AS_YOU_GO
        )
        assertEquals(WidgetColor.NORMAL, zero.color)
        assertEquals(WidgetColor.NORMAL, large.color)
    }

    // --- 月間定額型: quotaConsumedSec 基準であること（5.5.1） ---

    @Test
    fun `monthly plan shows quotaConsumedSec, not countedSec`() {
        // 実時間 6 秒でも 30 秒単位の切り上げで枠を 20 分消費しているケース
        val content = presentWidget(
            result(countedSec = 6, amount = 0, callCount = 3, quotaConsumedSec = 20 * 60),
            settings(monthlyFreeSec = 70 * 60),
            PlanType.MONTHLY
        )
        assertEquals("20.0 / 50.0分", content.timeValue)
    }

    @Test
    fun `monthly plan color is based on quotaConsumedSec`() {
        // countedSec だけ見れば 10% だが、切り上げ後の枠消費は 100% に達している
        val content = presentWidget(
            result(countedSec = 10, amount = 0, quotaConsumedSec = 100),
            settings(100),
            PlanType.MONTHLY
        )
        assertEquals(WidgetColor.OVER, content.color)
    }

    @Test
    fun `per-call plan keeps using countedSec because there is no quota`() {
        val content = presentWidget(
            result(countedSec = 128 * 60, amount = 374, callCount = 4, quotaConsumedSec = 200 * 60),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals("128.0分", content.timeValue)
    }
}
