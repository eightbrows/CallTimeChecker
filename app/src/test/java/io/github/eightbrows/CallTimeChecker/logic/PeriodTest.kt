package io.github.eightbrows.CallTimeChecker.logic

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * spec: docs/spec.md 5.2 集計期間の決定 / 10.1 期間算出テストケース
 */
class PeriodTest {

    private val zone = ZoneId.of("Asia/Tokyo")

    private fun millisOf(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    // --- periodStartDate: 起算日 1 / 25 / 31 ---

    @Test
    fun `periodStartDate startDay 1 returns first day of month`() {
        assertEquals(LocalDate.of(2026, 8, 1), periodStartDate(1, YearMonth.of(2026, 8)))
    }

    @Test
    fun `periodStartDate startDay 25 returns 25th of month`() {
        assertEquals(LocalDate.of(2026, 8, 25), periodStartDate(25, YearMonth.of(2026, 8)))
    }

    @Test
    fun `periodStartDate startDay 31 in 31-day month returns 31st`() {
        assertEquals(LocalDate.of(2026, 8, 31), periodStartDate(31, YearMonth.of(2026, 8)))
    }

    @Test
    fun `periodStartDate startDay 31 in 30-day month clamps to 30th`() {
        assertEquals(LocalDate.of(2026, 4, 30), periodStartDate(31, YearMonth.of(2026, 4)))
    }

    // --- periodStartDate: 2 月（閏年・平年） ---

    @Test
    fun `periodStartDate startDay 31 in non-leap February clamps to 28th`() {
        assertEquals(LocalDate.of(2026, 2, 28), periodStartDate(31, YearMonth.of(2026, 2)))
    }

    @Test
    fun `periodStartDate startDay 31 in leap February clamps to 29th`() {
        assertEquals(LocalDate.of(2024, 2, 29), periodStartDate(31, YearMonth.of(2024, 2)))
    }

    @Test
    fun `periodStartDate startDay 29 in non-leap February clamps to 28th`() {
        assertEquals(LocalDate.of(2026, 2, 28), periodStartDate(29, YearMonth.of(2026, 2)))
    }

    @Test
    fun `periodStartDate startDay 29 in leap February returns 29th`() {
        assertEquals(LocalDate.of(2024, 2, 29), periodStartDate(29, YearMonth.of(2024, 2)))
    }

    // --- currentPeriod: 月初日・月末日の境界 (startDay = 1) ---

    @Test
    fun `currentPeriod startDay 1, today is 1st, period is this calendar month`() {
        val (start, end) = currentPeriod(1, zone, LocalDate.of(2026, 8, 1))
        assertEquals(millisOf(LocalDate.of(2026, 8, 1)), start)
        assertEquals(millisOf(LocalDate.of(2026, 9, 1)), end)
    }

    @Test
    fun `currentPeriod startDay 1, today is last day of month, period is still this calendar month`() {
        val (start, end) = currentPeriod(1, zone, LocalDate.of(2026, 8, 31))
        assertEquals(millisOf(LocalDate.of(2026, 8, 1)), start)
        assertEquals(millisOf(LocalDate.of(2026, 9, 1)), end)
    }

    @Test
    fun `currentPeriod startDay 1, today is year boundary Dec 31, period is December`() {
        val (start, end) = currentPeriod(1, zone, LocalDate.of(2025, 12, 31))
        assertEquals(millisOf(LocalDate.of(2025, 12, 1)), start)
        assertEquals(millisOf(LocalDate.of(2026, 1, 1)), end)
    }

    // --- currentPeriod: 起算日 25 の境界 (spec 5.2.1: 25日00:00〜翌月24日23:59:59.999) ---

    @Test
    fun `currentPeriod startDay 25, today equals start day, period is this-to-next-month 25th`() {
        val (start, end) = currentPeriod(25, zone, LocalDate.of(2026, 8, 25))
        assertEquals(millisOf(LocalDate.of(2026, 8, 25)), start)
        assertEquals(millisOf(LocalDate.of(2026, 9, 25)), end)
    }

    @Test
    fun `currentPeriod startDay 25, today is day before start, period is previous-to-this-month 25th`() {
        val (start, end) = currentPeriod(25, zone, LocalDate.of(2026, 8, 24))
        assertEquals(millisOf(LocalDate.of(2026, 7, 25)), start)
        assertEquals(millisOf(LocalDate.of(2026, 8, 25)), end)
    }

    // --- currentPeriod: 起算日 31、2 月をまたぐ場合 ---

    @Test
    fun `currentPeriod startDay 31, today in non-leap February, period start clamped to Jan 31, end clamped to Feb 28`() {
        val (start, end) = currentPeriod(31, zone, LocalDate.of(2026, 2, 15))
        assertEquals(millisOf(LocalDate.of(2026, 1, 31)), start)
        assertEquals(millisOf(LocalDate.of(2026, 2, 28)), end)
    }

    @Test
    fun `currentPeriod startDay 31, today in leap February, period end clamped to Feb 29`() {
        val (start, end) = currentPeriod(31, zone, LocalDate.of(2024, 2, 15))
        assertEquals(millisOf(LocalDate.of(2024, 1, 31)), start)
        assertEquals(millisOf(LocalDate.of(2024, 2, 29)), end)
    }

    // --- 期間開始時刻ちょうど、期間終了時刻の直前・直後 (半開区間) ---

    @Test
    fun `call exactly at period start millis is within the period`() {
        val (start, end) = currentPeriod(25, zone, LocalDate.of(2026, 8, 25))
        val callAtStart = start
        assertEquals(true, callAtStart >= start && callAtStart < end)
    }

    @Test
    fun `call one millisecond before period end is within the period`() {
        val (start, end) = currentPeriod(25, zone, LocalDate.of(2026, 8, 25))
        val callJustBeforeEnd = end - 1
        assertEquals(true, callJustBeforeEnd >= start && callJustBeforeEnd < end)
    }

    @Test
    fun `call exactly at period end millis belongs to the next period, not this one`() {
        val (start, end) = currentPeriod(25, zone, LocalDate.of(2026, 8, 25))
        val callAtEnd = end
        assertEquals(false, callAtEnd >= start && callAtEnd < end)
    }
}
