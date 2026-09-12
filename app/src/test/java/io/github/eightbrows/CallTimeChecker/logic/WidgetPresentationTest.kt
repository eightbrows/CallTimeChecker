package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 のウィジェット表示テンプレート・配色判定のテスト
 */
class WidgetPresentationTest {

    private fun settings(monthlyFreeSec: Int, perCallFreeSec: Int = 0) =
        Settings(monthlyFreeSec, perCallFreeSec, unitSec = 30, unitPrice = 22, excludePrefixes = DEFAULT_EXCLUDE_PREFIXES)

    /**
     * 警告しきい値を指定しない呼び出しは、既定（定額枠の残り 20%）を使う。
     * 残り 20% ＝ 消費 80% であり、しきい値が設定項目になる前の「使用率 80% で警告」と
     * 同じ位置なので、配色以外を確かめるテストはこの 3 引数版で書く。
     * しきい値そのものを確かめるテストは、4 引数のトップレベル関数を直接呼ぶ。
     */
    private fun presentWidget(result: Result, settings: Settings, planType: PlanType) =
        presentWidget(result, settings, planType, settings.monthlyFreeSec / 5)

    // 「通話時間」の表示は全プランとも quotaConsumedSec（切り上げ後の課金枠の消費量）基準。
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
        assertEquals(WidgetLine("通話時間", "42.0", "分", WIDGET_TIME_REFERENCE), content.timeLine)
        assertEquals(WidgetLine("無料枠", "28.0", "分", WIDGET_TIME_REFERENCE), content.quotaLine)
        assertEquals(WidgetLine("通話金額", "0", "円", WIDGET_AMOUNT_REFERENCE), content.amountLine)
    }

    @Test
    fun `monthly plan clamps the remaining quota at zero when over`() {
        val content = presentWidget(
            result(countedSec = 78 * 60, amount = 352, callCount = 5),
            settings(monthlyFreeSec = 70 * 60),
            PlanType.MONTHLY
        )
        assertEquals("78.0", content.timeLine.value)
        assertEquals("0.0", content.quotaLine?.value)
        assertEquals("352", content.amountLine.value)
    }

    // --- レイアウト（5.5.1 上段 3/5・下段 2/5）が前提にしている項目の構造 ---

    @Test
    fun `every item keeps label, number and unit apart so the unit can be drawn smaller`() {
        val content = presentWidget(
            result(countedSec = 42 * 60, amount = 220), settings(70 * 60), PlanType.MONTHLY
        )
        for (line in listOfNotNull(content.timeLine, content.quotaLine, content.amountLine)) {
            assertEquals(true, line.label.isNotBlank())
            assertEquals(true, line.value.isNotBlank())
        }
        // 単位は数字と同じ TextView に小さく描くため、数字とは別に持つ
        assertEquals("分", content.timeLine.unit)
        assertEquals("分", content.quotaLine?.unit)
        assertEquals("円", content.amountLine.unit)
    }

    @Test
    fun `plans without a monthly quota drop the quota item entirely`() {
        for (planType in listOf(PlanType.PER_CALL, PlanType.PAY_AS_YOU_GO)) {
            val content = presentWidget(result(countedSec = 60, amount = 0), settings(0), planType)
            assertEquals(null, content.quotaLine)
            assertEquals("通話時間", content.timeLine.label)
        }
    }

    @Test
    fun `amount is grouped in thousands`() {
        val content = presentWidget(result(countedSec = 60, amount = 1220), settings(0), PlanType.PAY_AS_YOU_GO)
        assertEquals("1,220", content.amountLine.value)
        assertEquals("0", formatAmount(0))
        assertEquals("999", formatAmount(999))
        assertEquals("1,000,000", formatAmount(1000000))
    }

    // --- 文字サイズをそろえる基準文字列（5.5.1） ---

    @Test
    fun `all plans share one reference for the minute rows`() {
        val perCall = presentWidget(
            result(countedSec = 60, amount = 0), settings(0, 300), PlanType.PER_CALL
        )
        val payAsYouGo = presentWidget(
            result(countedSec = 60 * 60, amount = 1320), settings(0, 0), PlanType.PAY_AS_YOU_GO
        )
        assertEquals(WIDGET_TIME_REFERENCE, perCall.timeLine.reference)
        assertEquals(perCall.timeLine.reference, payAsYouGo.timeLine.reference)
    }

    @Test
    fun `the monthly plan lines up its two minute rows with each other`() {
        // 行の幅を決めているのは数字行なので、整数部の桁数が違うと autoSize が選ぶサイズが
        // 大きく食い違う（1x2 の実機で cap 高 37px と 29px）。同じ基準でそろえる
        val content = presentWidget(
            result(countedSec = 3 * 60, amount = 0), settings(15 * 60), PlanType.MONTHLY
        )
        assertEquals("3.0", content.timeLine.value)
        assertEquals("12.0", content.quotaLine?.value)
        assertEquals(WIDGET_TIME_REFERENCE, content.timeLine.reference)
        assertEquals(WIDGET_TIME_REFERENCE, content.quotaLine?.reference)
    }

    @Test
    fun `every plan shares one reference for the amount`() {
        val references = listOf(
            presentWidget(result(countedSec = 60, amount = 0), settings(70 * 60), PlanType.MONTHLY),
            presentWidget(result(countedSec = 60, amount = 0), settings(0, 300), PlanType.PER_CALL),
            presentWidget(result(countedSec = 60, amount = 22), settings(0, 0), PlanType.PAY_AS_YOU_GO)
        ).map { it.amountLine.reference }
        assertEquals(listOf(WIDGET_AMOUNT_REFERENCE, WIDGET_AMOUNT_REFERENCE, WIDGET_AMOUNT_REFERENCE), references)
    }

    @Test
    fun `the references are as wide as the values they line up`() {
        // 桁埋めは文字数の差で数えるため、基準と実際の値は「桁数以外は同じ形」である必要がある。
        // 通話時間は必ず小数第一位まで、金額は 3 桁までなら区切り記号が入らない
        assertEquals("00.0", WIDGET_TIME_REFERENCE)
        assertEquals(WIDGET_TIME_REFERENCE.length, formatMinutes(17 * 60).length)
        assertEquals(WIDGET_TIME_REFERENCE.length - 1, formatMinutes(60).length)
        assertEquals("000", WIDGET_AMOUNT_REFERENCE)
        assertEquals(WIDGET_AMOUNT_REFERENCE.length, formatAmount(374).length)
        assertEquals(WIDGET_AMOUNT_REFERENCE.length - 2, formatAmount(0).length)
        // 基準より長い値は桁埋めされず、その行だけ縮小される
        assertEquals(true, formatAmount(6800).length > WIDGET_AMOUNT_REFERENCE.length)
        assertEquals(true, formatMinutes(120 * 60).length > WIDGET_TIME_REFERENCE.length)
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
        assertEquals("1.0", content.timeLine.value)
        assertEquals("0.0", content.quotaLine?.value)
    }

    // --- 1 通話定額型: テンプレート・配色 ---

    @Test
    fun `per-call plan shows call minutes without a remaining quota`() {
        val content = presentWidget(
            result(countedSec = 128 * 60, amount = 374, callCount = 4),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals(WidgetLine("通話時間", "128.0", "分", WIDGET_TIME_REFERENCE), content.timeLine)
        assertEquals(null, content.quotaLine)
        assertEquals(WidgetLine("通話金額", "374", "円", WIDGET_AMOUNT_REFERENCE), content.amountLine)
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
        assertEquals(WidgetLine("通話時間", "42.0", "分", WIDGET_TIME_REFERENCE), content.timeLine)
        assertEquals(null, content.quotaLine)
        assertEquals("451", content.amountLine.value)
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

    // --- 全プラン共通: 通話時間は quotaConsumedSec 基準であること（5.5.1） ---

    @Test
    fun `monthly plan shows quotaConsumedSec, not countedSec`() {
        // 実時間 6 秒でも 30 秒単位の切り上げで枠を 20 分消費しているケース
        val content = presentWidget(
            result(countedSec = 6, amount = 0, callCount = 3, quotaConsumedSec = 20 * 60),
            settings(monthlyFreeSec = 70 * 60),
            PlanType.MONTHLY
        )
        assertEquals("20.0", content.timeLine.value)
        assertEquals("50.0", content.quotaLine?.value)
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
    fun `per-call plan shows quotaConsumedSec, not countedSec`() {
        val content = presentWidget(
            result(countedSec = 128 * 60, amount = 374, callCount = 4, quotaConsumedSec = 200 * 60),
            settings(monthlyFreeSec = 0, perCallFreeSec = 300),
            PlanType.PER_CALL
        )
        assertEquals("200.0", content.timeLine.value)
    }

    @Test
    fun `pay-as-you-go plan shows quotaConsumedSec, not countedSec`() {
        val content = presentWidget(
            result(countedSec = 30, amount = 66, callCount = 3, quotaConsumedSec = 90),
            settings(monthlyFreeSec = 0, perCallFreeSec = 0),
            PlanType.PAY_AS_YOU_GO
        )
        assertEquals("1.5", content.timeLine.value)
    }

    // --- 表示している通話時間が、通話金額の計算根拠と一致すること（5.6.1） ---

    @Test
    fun `pay-as-you-go display minutes are the exact basis of the amount`() {
        // 10 秒の通話 3 件。実時間は 30 秒だが、30 秒単位の切り上げで課金対象は 90 秒
        val s = settings(monthlyFreeSec = 0, perCallFreeSec = 0)
        val calculated = calculate(
            List(3) { CallRecord(dateMillis = 1000L * it, durationSec = 10, number = "09000000000") },
            s
        )

        assertEquals(30, calculated.countedSec)
        assertEquals(90, calculated.quotaConsumedSec)
        // monthlyFreeSec = 0 なのでプールが無く、枠消費量と課金対象秒数は常に一致する
        assertEquals(calculated.quotaConsumedSec, calculated.billedSec)
        assertEquals(calculated.quotaConsumedSec / s.unitSec * s.unitPrice, calculated.amount)

        val content = presentWidget(calculated, s, PlanType.PAY_AS_YOU_GO)
        assertEquals("1.5", content.timeLine.value)
        assertEquals("66", content.amountLine.value)
    }

    @Test
    fun `per-call display minutes are the exact basis of the amount`() {
        // 1 通話無料 5 分に対し 400 秒の通話 2 件。超過 100 秒 -> 30 秒単位で 120 秒ずつ
        val s = settings(monthlyFreeSec = 0, perCallFreeSec = 300)
        val calculated = calculate(
            List(2) { CallRecord(dateMillis = 1000L * it, durationSec = 400, number = "09000000000") },
            s
        )

        assertEquals(800, calculated.countedSec)
        assertEquals(240, calculated.quotaConsumedSec)
        assertEquals(calculated.quotaConsumedSec, calculated.billedSec)
        assertEquals(calculated.quotaConsumedSec / s.unitSec * s.unitPrice, calculated.amount)

        val content = presentWidget(calculated, s, PlanType.PER_CALL)
        assertEquals("4.0", content.timeLine.value)
        assertEquals("176", content.amountLine.value)
    }

    // --- 背景の透過率（5.5.3 / 5.7） ---

    @Test
    fun `widgetBgAlpha maps the step range onto the full alpha range`() {
        assertEquals(255, widgetBgAlpha(0))
        assertEquals(127, widgetBgAlpha(4))
        assertEquals(0, widgetBgAlpha(8))
    }

    @Test
    fun `widgetBgAlpha decreases monotonically as transparency increases`() {
        val alphas = (0 until WIDGET_BG_TRANSPARENCY_STEP_COUNT).map { widgetBgAlpha(it) }
        assertEquals(alphas.sortedDescending(), alphas)
        assertEquals(alphas.distinct(), alphas)
    }

    @Test
    fun `widgetBgAlpha clamps out of range steps`() {
        assertEquals(255, widgetBgAlpha(-1))
        assertEquals(0, widgetBgAlpha(99))
    }

    @Test
    fun `withAlpha replaces only the alpha channel`() {
        // 不透明な超過色（#FFD32F2F）の RGB を保ったまま alpha だけが変わる
        assertEquals(0xFFD32F2F.toInt(), withAlpha(0xFFD32F2F.toInt(), 255))
        assertEquals(0x7FD32F2F, withAlpha(0xFFD32F2F.toInt(), 127))
        assertEquals(0x00D32F2F, withAlpha(0xFFD32F2F.toInt(), 0))
    }
    // --- 警告しきい値（5.7 の「残り◯分」） ---

    @Test
    fun `warning color starts when the remaining quota reaches the configured threshold`() {
        // 残り 50 秒で警告。定額枠 100 秒なので消費 50 秒が境界
        val above = presentWidget(
            result(countedSec = 49, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 50
        )
        val atThreshold = presentWidget(
            result(countedSec = 50, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 50
        )
        assertEquals(WidgetColor.NORMAL, above.color)
        assertEquals(WidgetColor.WARNING, atThreshold.color)
    }

    @Test
    fun `warning threshold means remaining time, not consumed time`() {
        // 残り 20 秒のしきい値を消費量として読むと消費 20 秒で警告になってしまう。
        // 実際に警告色になるのは残りが 20 秒以下、つまり消費 80 秒以上のとき
        val consumedTwenty = presentWidget(
            result(countedSec = 20, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 20
        )
        val consumedEighty = presentWidget(
            result(countedSec = 80, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 20
        )
        assertEquals(WidgetColor.NORMAL, consumedTwenty.color)
        assertEquals(WidgetColor.WARNING, consumedEighty.color)
    }

    @Test
    fun `over color still switches at the monthly quota whatever the threshold is`() {
        val content = presentWidget(
            result(countedSec = 100, amount = 22), settings(100), PlanType.MONTHLY, warnRemainingSec = 50
        )
        assertEquals(WidgetColor.OVER, content.color)
    }

    @Test
    fun `no warning band when the threshold is not below the monthly quota`() {
        // 残りのしきい値が定額枠と同じだと消費 0 分で警告になってしまうため、帯を作らない。
        // 定額枠 1 分で範囲が空になるケースもこれに当たる
        val untouched = presentWidget(
            result(countedSec = 0, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 100
        )
        val justBelow = presentWidget(
            result(countedSec = 99, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 100
        )
        val atQuota = presentWidget(
            result(countedSec = 100, amount = 22), settings(100), PlanType.MONTHLY, warnRemainingSec = 100
        )
        assertEquals(WidgetColor.NORMAL, untouched.color)
        assertEquals(WidgetColor.NORMAL, justBelow.color)
        assertEquals(WidgetColor.OVER, atQuota.color)
    }

    @Test
    fun `threshold of zero disables the warning color`() {
        val content = presentWidget(
            result(countedSec = 99, amount = 0), settings(100), PlanType.MONTHLY, warnRemainingSec = 0
        )
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    @Test
    fun `zero monthly quota stays normal even at zero consumption`() {
        // 定額枠 0 は設定画面から作れないが、0 秒消費が超過扱いにならないことを保証する
        val content = presentWidget(
            result(countedSec = 0, amount = 0), settings(0), PlanType.MONTHLY, warnRemainingSec = 0
        )
        assertEquals(WidgetColor.NORMAL, content.color)
    }

    // --- ウィジェット背景色のパレット（5.7） ---

    @Test
    fun `widget color palette keeps the default colors at their documented indices`() {
        assertEquals(10, WIDGET_COLOR_PALETTE.size)
        assertEquals(0xFFFFFFFF.toInt(), WIDGET_COLOR_PALETTE[WIDGET_COLOR_INDEX_WHITE].argb)
        assertEquals(0xFFFFA000.toInt(), WIDGET_COLOR_PALETTE[WIDGET_COLOR_INDEX_ORANGE].argb)
        assertEquals(0xFFD32F2F.toInt(), WIDGET_COLOR_PALETTE[WIDGET_COLOR_INDEX_RED].argb)
    }

    @Test
    fun `every palette color is opaque and has a label`() {
        for (color in WIDGET_COLOR_PALETTE) {
            assertEquals(0xFF, (color.argb ushr 24) and 0xFF)
            assertEquals(true, color.label.isNotBlank())
        }
    }

    @Test
    fun `widgetPaletteArgb clamps out of range indices`() {
        assertEquals(WIDGET_COLOR_PALETTE.first().argb, widgetPaletteArgb(-1))
        assertEquals(WIDGET_COLOR_PALETTE.last().argb, widgetPaletteArgb(WIDGET_COLOR_PALETTE.size))
    }

    // --- 背景色に対する文字色（5.5.3） ---

    @Test
    fun `widgetTextColorOn picks black on light backgrounds and white on dark ones`() {
        assertEquals(0xFF000000.toInt(), widgetTextColorOn(0xFFFFFFFF.toInt()))
        assertEquals(0xFFFFFFFF.toInt(), widgetTextColorOn(0xFF000000.toInt()))
    }

    @Test
    fun `widgetTextColorOn picks black on the default warning orange`() {
        // 固定対応では白文字だった（コントラスト比 2.05）。自動選択では黒文字になる（同 10.3）
        assertEquals(0xFF000000.toInt(), widgetTextColorOn(0xFFFFA000.toInt()))
    }

    @Test
    fun `widgetTextColorOn picks white on the default over red`() {
        assertEquals(0xFFFFFFFF.toInt(), widgetTextColorOn(0xFFD32F2F.toInt()))
    }

    @Test
    fun `widgetTextColorOn ignores the alpha channel`() {
        // 透過後の見え方は壁紙次第で決まらないため、不透明色として判定する
        assertEquals(
            widgetTextColorOn(0xFFFFFFFF.toInt()),
            widgetTextColorOn(0x00FFFFFF)
        )
    }

    @Test
    fun `every palette color gets a readable text color`() {
        // WCAG のコントラスト比 4.5 以上（通常の本文テキストの基準）を全色で満たす
        for (color in WIDGET_COLOR_PALETTE) {
            val text = widgetTextColorOn(color.argb)
            assertEquals(true, contrastRatio(color.argb, text) >= 4.5)
        }
    }

    // --- 自動更新の印（5.5.1） ---

    @Test
    fun `the amount label carries a mark when the redraw came from the periodic update`() {
        val content = presentWidget(
            result(20 * 60, 0), settings(70 * 60), PlanType.MONTHLY, 14 * 60, autoUpdated = true
        )
        assertEquals("通話金額 ⌚", content.amountLine.label)
        // 印はラベルだけの話で、数字・単位・桁そろえの基準には触れない
        assertEquals("0", content.amountLine.value)
        assertEquals("円", content.amountLine.unit)
        assertEquals(WIDGET_AMOUNT_REFERENCE, content.amountLine.reference)
    }

    @Test
    fun `the amount label has no mark for a manual or settings driven redraw`() {
        for (plan in PlanType.entries) {
            val content = presentWidget(result(20 * 60, 0), settings(70 * 60), plan)
            assertEquals("通話金額", content.amountLine.label)
        }
    }

    @Test
    fun `the mark is on the amount only, never on the minute rows`() {
        val content = presentWidget(
            result(20 * 60, 0), settings(70 * 60), PlanType.MONTHLY, 14 * 60, autoUpdated = true
        )
        assertEquals("通話時間", content.timeLine.label)
        assertEquals("無料枠", content.quotaLine?.label)
    }

    @Test
    fun `every plan can show the mark`() {
        for (plan in PlanType.entries) {
            val content = presentWidget(
                result(20 * 60, 0), settings(70 * 60), plan, 14 * 60, autoUpdated = true
            )
            assertEquals("通話金額 $WIDGET_AUTO_UPDATE_MARK", content.amountLine.label)
        }
    }

    private fun contrastRatio(a: Int, b: Int): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(argb: Int): Double {
        fun linear(channel: Int): Double {
            val s = channel / 255.0
            return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * linear((argb shr 16) and 0xFF) +
            0.7152 * linear((argb shr 8) and 0xFF) +
            0.0722 * linear(argb and 0xFF)
    }
}
