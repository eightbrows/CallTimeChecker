package io.github.eightbrows.CallTimeChecker.logic

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * spec: docs/spec.md 5.2 集計期間の決定
 *
 * 指定月における期間開始日。存在しない日は月末にクランプする。
 */
fun periodStartDate(startDay: Int, ym: YearMonth): LocalDate =
    ym.atDay(minOf(startDay, ym.lengthOfMonth()))

/**
 * `today` を基準とした集計期間 [開始, 終了) を epoch millis の半開区間で返す。
 */
fun currentPeriod(startDay: Int, zone: ZoneId, today: LocalDate): Pair<Long, Long> {
    val thisMonthStart = periodStartDate(startDay, YearMonth.from(today))
    val start = if (!today.isBefore(thisMonthStart)) thisMonthStart
                else periodStartDate(startDay, YearMonth.from(today).minusMonths(1))
    val end = periodStartDate(startDay, YearMonth.from(start).plusMonths(1))
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to
           end.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** 現在時刻を基準とした集計期間。 */
fun currentPeriod(startDay: Int, zone: ZoneId): Pair<Long, Long> =
    currentPeriod(startDay, zone, LocalDate.now(zone))
