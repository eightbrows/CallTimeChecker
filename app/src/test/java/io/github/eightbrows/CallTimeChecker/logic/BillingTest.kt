package io.github.eightbrows.CallTimeChecker.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * spec: docs/spec.md 5.4 料金計算 / 10.1 のテストケース
 */
class BillingTest {

    private fun record(dateMillis: Long, durationSec: Int, number: String? = "09000000000") =
        CallRecord(dateMillis = dateMillis, durationSec = durationSec, number = number)

    private fun settings(
        monthlyFreeSec: Int = 0,
        perCallFreeSec: Int = 0,
        unitSec: Int = 30,
        unitPrice: Int = 22,
        excludePrefixes: List<String> = DEFAULT_EXCLUDE_PREFIXES
    ) = Settings(monthlyFreeSec, perCallFreeSec, unitSec, unitPrice, excludePrefixes)

    // --- ceilDiv ---

    @Test
    fun `ceilDiv rounds up to the next unit, exact multiples stay unchanged`() {
        assertEquals(0, ceilDiv(0, 30))
        assertEquals(1, ceilDiv(1, 30))
        assertEquals(1, ceilDiv(29, 30))
        assertEquals(1, ceilDiv(30, 30))
        assertEquals(2, ceilDiv(31, 30))
    }

    // --- 切り上げ: 端数 0 / 1 / 29 / 30 / 31 秒, unitSec = 30 ---
    // perCallFreeSec=60 で「超過秒数」を作り、monthlyFreeSec=0 で枠消費を無効化して丸めだけを見る

    @Test
    fun `unitSec 30, remainder 0s bills nothing`() {
        val result = calculate(listOf(record(1, 60)), settings(perCallFreeSec = 60, unitSec = 30))
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
    }

    @Test
    fun `unitSec 30, remainder 1s rounds up to one unit`() {
        val result = calculate(listOf(record(1, 61)), settings(perCallFreeSec = 60, unitSec = 30))
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 30, remainder 29s rounds up to one unit`() {
        val result = calculate(listOf(record(1, 89)), settings(perCallFreeSec = 60, unitSec = 30))
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 30, remainder exactly 30s stays one unit`() {
        val result = calculate(listOf(record(1, 90)), settings(perCallFreeSec = 60, unitSec = 30))
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 30, remainder 31s rounds up to two units`() {
        val result = calculate(listOf(record(1, 91)), settings(perCallFreeSec = 60, unitSec = 30))
        assertEquals(60, result.billedSec)
        assertEquals(44, result.amount)
    }

    // --- 切り上げ: 端数 0 / 1 / 29 / 30 / 31 秒, unitSec = 60 ---

    @Test
    fun `unitSec 60, remainder 0s bills nothing`() {
        val result = calculate(listOf(record(1, 60)), settings(perCallFreeSec = 60, unitSec = 60))
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
    }

    @Test
    fun `unitSec 60, remainder 1s rounds up to one unit`() {
        val result = calculate(listOf(record(1, 61)), settings(perCallFreeSec = 60, unitSec = 60))
        assertEquals(60, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 60, remainder 29s rounds up to one unit`() {
        val result = calculate(listOf(record(1, 89)), settings(perCallFreeSec = 60, unitSec = 60))
        assertEquals(60, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 60, remainder exactly 30s stays one unit`() {
        val result = calculate(listOf(record(1, 90)), settings(perCallFreeSec = 60, unitSec = 60))
        assertEquals(60, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `unitSec 60, remainder 31s stays one unit`() {
        val result = calculate(listOf(record(1, 91)), settings(perCallFreeSec = 60, unitSec = 60))
        assertEquals(60, result.billedSec)
        assertEquals(22, result.amount)
    }

    // --- 定額枠: 枠未使用、枠内、枠ちょうど、枠をまたぐ 1 通話、枠超過 ---
    // monthlyFreeSec = 90 (unitSec 30 の 3 単位分), perCallFreeSec = 0

    @Test
    fun `monthly quota untouched when there are no calls`() {
        val result = calculate(emptyList(), settings(monthlyFreeSec = 90, unitSec = 30))
        assertEquals(0, result.countedSec)
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
        assertEquals(0, result.callCount)
        assertEquals(0, result.billedCallCount)
    }

    @Test
    fun `call fully within remaining quota is not billed`() {
        val result = calculate(listOf(record(1, 60)), settings(monthlyFreeSec = 90, unitSec = 30))
        assertEquals(60, result.countedSec)
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
    }

    @Test
    fun `call that exactly exhausts the quota is not billed`() {
        val result = calculate(listOf(record(1, 90)), settings(monthlyFreeSec = 90, unitSec = 30))
        assertEquals(90, result.countedSec)
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
    }

    @Test
    fun `a single call straddling the remaining quota is partially billed`() {
        val records = listOf(record(1, 60), record(2, 40))
        val result = calculate(records, settings(monthlyFreeSec = 90, unitSec = 30))
        // call1: units=60, pool 90->30, billed 0
        // call2: over=40 -> units=60, pool 30->0, billed=30
        assertEquals(100, result.countedSec)
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
        assertEquals(2, result.callCount)
        assertEquals(1, result.billedCallCount)
    }

    @Test
    fun `once the quota is exhausted, further calls are billed in full`() {
        val records = listOf(record(1, 90), record(2, 30))
        val result = calculate(records, settings(monthlyFreeSec = 90, unitSec = 30))
        // call1 exhausts the pool exactly, call2 has no pool left to draw from
        assertEquals(120, result.countedSec)
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
        assertEquals(1, result.billedCallCount)
    }

    // --- 通話別無料時間: ちょうど、1 秒超過、大幅超過 ---
    // perCallFreeSec = 300 (5 分), monthlyFreeSec = 0

    @Test
    fun `per-call free time exactly used up bills nothing`() {
        val result = calculate(listOf(record(1, 300)), settings(perCallFreeSec = 300, unitSec = 30))
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
    }

    @Test
    fun `one second over per-call free time bills a single unit`() {
        val result = calculate(listOf(record(1, 301)), settings(perCallFreeSec = 300, unitSec = 30))
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
    }

    @Test
    fun `far exceeding per-call free time bills multiple units`() {
        val result = calculate(listOf(record(1, 700)), settings(perCallFreeSec = 300, unitSec = 30))
        // over = 400 -> ceilDiv(400,30)*30 = 420
        assertEquals(420, result.billedSec)
        assertEquals(308, result.amount)
    }

    // --- 併用型: monthlyFreeSec と perCallFreeSec の両方が正 ---

    @Test
    fun `combined plan absorbs per-call free time first, then the monthly pool, before billing`() {
        val records = listOf(record(1, 330), record(2, 400))
        val result = calculate(
            records,
            settings(monthlyFreeSec = 60, perCallFreeSec = 300, unitSec = 30)
        )
        // call1: over=30 -> units=30, pool 60->30, billed 0
        // call2: over=100 -> units=120, pool 30->0, billed=90
        assertEquals(730, result.countedSec)
        assertEquals(90, result.billedSec)
        assertEquals(66, result.amount)
        assertEquals(2, result.callCount)
        assertEquals(1, result.billedCallCount)
    }

    // --- 境界: 通話時間 0 秒 ---

    @Test
    fun `zero-duration unanswered call is excluded from all counts`() {
        val result = calculate(listOf(record(1, 0)), settings(monthlyFreeSec = 90, unitSec = 30))
        assertEquals(0, result.countedSec)
        assertEquals(0, result.billedSec)
        assertEquals(0, result.amount)
        assertEquals(0, result.callCount)
        assertEquals(0, result.billedCallCount)
    }

    // --- 除外判定: 各初期値プレフィックス、ハイフン付き番号、空番号、null ---

    @Test
    fun `each default exclude prefix matches`() {
        val samples = mapOf(
            "0570" to "0570000000",
            "0180" to "0180000000",
            "0990" to "0990000000",
            "104" to "104",
            "110" to "110",
            "118" to "118",
            "119" to "119",
            "188" to "188",
            "+" to "+819000000000"
        )
        samples.forEach { (prefix, number) ->
            assertTrue("prefix $prefix should exclude $number", isExcluded(number, DEFAULT_EXCLUDE_PREFIXES))
        }
    }

    @Test
    fun `hyphenated number is normalized before prefix matching`() {
        assertTrue(isExcluded("0570-00-0000", DEFAULT_EXCLUDE_PREFIXES))
        assertTrue(isExcluded("110", listOf("110")))
        assertTrue(isExcluded("(110) 000-000", listOf("110")))
    }

    @Test
    fun `empty number is not treated as excluded`() {
        assertFalse(isExcluded("", DEFAULT_EXCLUDE_PREFIXES))
    }

    @Test
    fun `null number is not treated as excluded`() {
        assertFalse(isExcluded(null, DEFAULT_EXCLUDE_PREFIXES))
    }

    @Test
    fun `non-matching number is not excluded`() {
        assertFalse(isExcluded("09000000000", DEFAULT_EXCLUDE_PREFIXES))
    }

    @Test
    fun `excluded calls do not consume the monthly pool or get billed, but are tallied separately`() {
        val records = listOf(record(1, 600, number = "0570-000-000"), record(2, 100))
        val result = calculate(records, settings(monthlyFreeSec = 90, unitSec = 30))
        assertEquals(600, result.excludedSec)
        assertEquals(100, result.countedSec)
        // call2: over=100 -> units=120, pool untouched by the excluded call so still 90 -> billed=30
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
        assertEquals(1, result.callCount)
    }

    // --- calls are processed in start-time order regardless of input order (5.4.4) ---

    @Test
    fun `records are processed in ascending start-time order even if the input list is unordered`() {
        val records = listOf(record(dateMillis = 2, durationSec = 30), record(dateMillis = 1, durationSec = 90))
        val result = calculate(records, settings(monthlyFreeSec = 90, unitSec = 30))
        // chronological order: call@1 (90s) exhausts the pool exactly, then call@2 (30s) is billed in full
        assertEquals(30, result.billedSec)
        assertEquals(22, result.amount)
    }

    // --- spec 5.4.5 worked examples ---

    @Test
    fun `spec example A, per-call plan with 5-minute free time`() {
        val records = listOf(
            record(1, 180), // A 3:00
            record(2, 300), // B 5:00
            record(3, 310), // C 5:10
            record(4, 460)  // D 7:40
        )
        val result = calculate(records, settings(perCallFreeSec = 300, unitSec = 30, unitPrice = 22))
        assertEquals(1250, result.countedSec)
        assertEquals(210, result.billedSec)
        assertEquals(154, result.amount)
        assertEquals(4, result.callCount)
        assertEquals(2, result.billedCallCount)
    }

    @Test
    fun `spec example B, monthly quota plan of 70 minutes`() {
        val records = listOf(
            record(1, 4080), // 通算 68 分相当をまとめて消費
            record(2, 250)   // X 4:10
        )
        val result = calculate(records, settings(monthlyFreeSec = 4200, unitSec = 30, unitPrice = 22))
        assertEquals(4330, result.countedSec)
        assertEquals(150, result.billedSec)
        assertEquals(110, result.amount)
    }
}
