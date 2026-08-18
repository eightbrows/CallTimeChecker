package io.github.eightbrows.CallTimeChecker.logic

/** spec: docs/spec.md 6.1 の call_record から集計に必要な列のみを取り出したもの */
data class CallRecord(
    val dateMillis: Long,
    val durationSec: Int,
    val number: String?
)

/** spec: docs/spec.md 5.4.1 / 5.4.2 */
data class Settings(
    val monthlyFreeSec: Int,
    val perCallFreeSec: Int,
    val unitSec: Int,
    val unitPrice: Int,
    val excludePrefixes: List<String>
)

/** spec: docs/spec.md 5.4.3 */
data class Result(
    val countedSec: Int,
    val billedSec: Int,
    val amount: Int,
    val excludedSec: Int,
    val callCount: Int,
    val billedCallCount: Int
)

/** spec: docs/spec.md 5.3.2 除外リスト初期値 */
val DEFAULT_EXCLUDE_PREFIXES = listOf(
    "0570", "0180", "0990", "104", "110", "118", "119", "188", "+"
)

/** spec: docs/spec.md 5.3.1 除外判定 */
fun isExcluded(number: String?, excludePrefixes: List<String>): Boolean {
    if (number.isNullOrEmpty()) return false
    val normalized = number.replace(Regex("[-()\\s]"), "")
    return excludePrefixes.any { normalized.startsWith(it) }
}

fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/** spec: docs/spec.md 5.4.3 料金計算アルゴリズム */
fun calculate(records: List<CallRecord>, s: Settings): Result {
    var pool = s.monthlyFreeSec
    var countedSec = 0
    var billedSec = 0
    var excludedSec = 0
    var callCount = 0
    var billedCallCount = 0

    for (r in records.sortedBy { it.dateMillis }) {
        if (isExcluded(r.number, s.excludePrefixes)) {
            excludedSec += r.durationSec
            continue
        }
        if (r.durationSec == 0) continue // 未応答

        countedSec += r.durationSec
        callCount++

        val over = maxOf(0, r.durationSec - s.perCallFreeSec)
        if (over == 0) continue

        // 通話ごとに課金単位へ切り上げる
        val units = ceilDiv(over, s.unitSec) * s.unitSec
        val consumed = minOf(units, pool)
        pool -= consumed
        val billed = units - consumed
        if (billed > 0) {
            billedSec += billed
            billedCallCount++
        }
    }
    return Result(
        countedSec = countedSec,
        billedSec = billedSec,
        amount = billedSec / s.unitSec * s.unitPrice,
        excludedSec = excludedSec,
        callCount = callCount,
        billedCallCount = billedCallCount
    )
}
