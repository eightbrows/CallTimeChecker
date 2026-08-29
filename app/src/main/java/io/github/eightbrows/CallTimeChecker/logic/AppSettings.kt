package io.github.eightbrows.CallTimeChecker.logic

/**
 * spec: docs/spec.md 5.7 プラン形式。UI 上のプリセットに過ぎず、
 * 集計ロジック（Billing.kt の Settings/calculate）はプラン形式を参照しない。
 */
enum class PlanType { MONTHLY, PER_CALL, CUSTOM }

/**
 * spec: docs/spec.md 5.7 設定画面が保持する設定値（分単位）。
 * Billing.kt の Settings（秒単位）とは独立に保持し、toBillingSettings() で変換する。
 */
data class AppSettings(
    val planType: PlanType,
    val startDay: Int,
    val monthlyFreeMin: Int,
    val perCallFreeMin: Int,
    val unitSec: Int,
    val unitPrice: Int,
    val excludePrefixes: List<String>
)

/** spec: docs/spec.md 5.7 初期値 */
val DEFAULT_APP_SETTINGS = AppSettings(
    planType = PlanType.MONTHLY,
    startDay = 1,
    monthlyFreeMin = 70,
    perCallFreeMin = 5,
    unitSec = 30,
    unitPrice = 22,
    excludePrefixes = DEFAULT_EXCLUDE_PREFIXES
)

/**
 * spec: docs/spec.md 5.7 備考「定額枠はプラン形式が1通話定額型のとき0固定」
 * 「通話別無料時間はプラン形式が月間定額型のとき0固定」。
 */
fun effectiveAppSettings(settings: AppSettings): AppSettings = when (settings.planType) {
    PlanType.MONTHLY -> settings.copy(perCallFreeMin = 0)
    PlanType.PER_CALL -> settings.copy(monthlyFreeMin = 0)
    PlanType.CUSTOM -> settings
}

/**
 * spec: docs/spec.md 5.7「プラン形式は...内部的には monthlyFreeSec と perCallFreeSec の
 * 2 フィールドに書き込むのみ」。Billing.kt の Settings（秒単位）へ変換する。
 */
fun toBillingSettings(settings: AppSettings): Settings {
    val effective = effectiveAppSettings(settings)
    return Settings(
        monthlyFreeSec = effective.monthlyFreeMin * 60,
        perCallFreeSec = effective.perCallFreeMin * 60,
        unitSec = effective.unitSec,
        unitPrice = effective.unitPrice,
        excludePrefixes = effective.excludePrefixes
    )
}

/** spec: docs/spec.md 5.7 起算日は 1〜31 */
fun clampStartDay(day: Int): Int = day.coerceIn(1, 31)

/** spec: docs/spec.md 5.7 課金単位は 30 / 60 のみ */
fun normalizeUnitSec(sec: Int): Int = if (sec == 60) 60 else 30

/** spec: docs/spec.md 5.7 除外番号リスト（改行区切りテキスト）→ プレフィックスのリスト */
fun parseExcludePrefixes(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** parseExcludePrefixes の逆変換。設定画面のテキストフィールド初期表示に使用 */
fun excludePrefixesToText(prefixes: List<String>): String = prefixes.joinToString("\n")
