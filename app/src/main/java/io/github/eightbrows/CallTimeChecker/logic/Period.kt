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
 * spec: docs/spec.md 5.2 集計期間の決定。
 * `today` が属する集計期間の「開始月」。期間は開始月と 1 対 1 に対応する
 * （periodStartDate() が開始月から開始日を一意に決めるため）ので、
 * 月送り（5.6.1）はこの開始月を YearMonth 単位でずらして表現する。
 */
fun periodMonth(startDay: Int, today: LocalDate): YearMonth {
    val ym = YearMonth.from(today)
    return if (!today.isBefore(periodStartDate(startDay, ym))) ym else ym.minusMonths(1)
}

/**
 * spec: docs/spec.md 5.2 集計期間の決定。
 * 開始月を指定した集計期間 [開始, 終了) を epoch millis の半開区間で返す。
 */
fun periodOf(startDay: Int, zone: ZoneId, month: YearMonth): Pair<Long, Long> {
    val start = periodStartDate(startDay, month)
    val end = periodStartDate(startDay, month.plusMonths(1))
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to
           end.atStartOfDay(zone).toInstant().toEpochMilli()
}

/**
 * `today` を基準とした集計期間 [開始, 終了) を epoch millis の半開区間で返す。
 */
fun currentPeriod(startDay: Int, zone: ZoneId, today: LocalDate): Pair<Long, Long> =
    periodOf(startDay, zone, periodMonth(startDay, today))

/** 現在時刻を基準とした集計期間。 */
fun currentPeriod(startDay: Int, zone: ZoneId): Pair<Long, Long> =
    currentPeriod(startDay, zone, LocalDate.now(zone))
