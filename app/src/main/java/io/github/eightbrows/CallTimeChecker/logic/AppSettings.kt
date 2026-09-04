package io.github.eightbrows.CallTimeChecker.logic

/**
 * spec: docs/spec.md 5.4.2 / 5.7 プラン形式。UI 上のプリセットに過ぎず、
 * 集計ロジック（Billing.kt の Settings/calculate）はプラン形式を参照しない。
 * 3 択はいずれも monthlyFreeMin / perCallFreeMin の組み合わせと 1 対 1 に対応する
 * （どちらか一方だけが正、または両方 0）。併用型（旧 CUSTOM）は廃止した。
 */
enum class PlanType { MONTHLY, PER_CALL, PAY_AS_YOU_GO }

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
    val excludePrefixes: List<String>,
    /** spec: docs/spec.md 5.7 ウィジェット背景の透過率。0（不透明）〜8（完全透明）の段階インデックス */
    val widgetBgTransparencyStep: Int
)

/** spec: docs/spec.md 5.7 初期値 */
val DEFAULT_APP_SETTINGS = AppSettings(
    planType = PlanType.MONTHLY,
    startDay = 1,
    monthlyFreeMin = 70,
    perCallFreeMin = 5,
    unitSec = 30,
    unitPrice = 22,
    excludePrefixes = DEFAULT_EXCLUDE_PREFIXES,
    widgetBgTransparencyStep = 0
)

/**
 * spec: docs/spec.md 5.7 備考「定額枠はプラン形式が1通話定額型のとき0固定」
 * 「通話別無料時間はプラン形式が月間定額型のとき0固定」「従量課金では両方0固定」。
 */
fun effectiveAppSettings(settings: AppSettings): AppSettings = when (settings.planType) {
    PlanType.MONTHLY -> settings.copy(perCallFreeMin = 0)
    PlanType.PER_CALL -> settings.copy(monthlyFreeMin = 0)
    PlanType.PAY_AS_YOU_GO -> settings.copy(monthlyFreeMin = 0, perCallFreeMin = 0)
}

/**
 * spec: docs/spec.md 5.4.2 定額枠・通話別無料時間の値からプラン形式を導出する。
 * effectiveAppSettings() により保存される値は必ず「一方だけが正」か「両方 0」になるため、
 * この導出は保存された組み合わせに対して一意に定まる。
 * 両方が正になり得るのは旧 CUSTOM の保存値だけで、その場合は月間定額型を優先する。
 */
fun derivePlanType(monthlyFreeMin: Int, perCallFreeMin: Int): PlanType = when {
    monthlyFreeMin > 0 -> PlanType.MONTHLY
    perCallFreeMin > 0 -> PlanType.PER_CALL
    else -> PlanType.PAY_AS_YOU_GO
}

/**
 * spec: docs/spec.md 5.7 読み込み時のプラン形式の正規化（マイグレーション）。
 * 保存済みのプラン形式は信用せず、常に定額枠・通話別無料時間の実値から導出し直す。
 *
 * - 廃止した CUSTOM が保存されている場合の読み替え（両方 > 0 なら月間定額型を優先）
 * - プラン形式と値が矛盾している場合の解消（例: 月間定額型なのに定額枠 0 → 従量課金）
 *
 * のどちらもこれ 1 つで賄える。月間定額型の定額枠は 1 分以上（5.7）であるため、
 * 矛盾した値をそのまま読み込むと設定画面が開いた直後に保存不可になってしまう。
 */
fun migrateAppSettings(settings: AppSettings): AppSettings {
    val planType = derivePlanType(settings.monthlyFreeMin, settings.perCallFreeMin)
    return effectiveAppSettings(settings.copy(planType = planType))
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

/** spec: docs/spec.md 5.7 定額枠は 0〜1440 分（24 時間）。0 は 0 固定のプラン形式用 */
fun clampMonthlyFreeMin(min: Int): Int = min.coerceIn(0, 1440)

/** spec: docs/spec.md 5.7 通話別無料時間は 0〜180 分。0 は 0 固定のプラン形式用 */
fun clampPerCallFreeMin(min: Int): Int = min.coerceIn(0, 180)

/** spec: docs/spec.md 5.7 単位金額は 0〜999 円 */
fun clampUnitPrice(price: Int): Int = price.coerceIn(0, 999)

/**
 * spec: docs/spec.md 5.7 ウィジェット背景の透過率の段階数。
 * 0%〜100% を 12.5% 刻みで 9 段階。連続値ではなく段階で持つのは、
 * 保存値を整数にして端数の丸め方を 1 箇所（widgetBgAlpha）に閉じ込めるため。
 */
const val WIDGET_BG_TRANSPARENCY_STEP_COUNT = 9

/** spec: docs/spec.md 5.7 透過率の段階は 0〜8 */
fun clampWidgetBgTransparencyStep(step: Int): Int =
    step.coerceIn(0, WIDGET_BG_TRANSPARENCY_STEP_COUNT - 1)

/**
 * spec: docs/spec.md 5.7 透過率の表示名。12.5% 刻みのため、
 * 端数が出る段階だけ小数第 1 位まで出す（"25%" と "12.5%" が混在する）。
 */
fun widgetBgTransparencyLabel(step: Int): String {
    val permille = clampWidgetBgTransparencyStep(step) * 125
    return if (permille % 10 == 0) "${permille / 10}%" else "${permille / 10}.${permille % 10}%"
}

/** spec: docs/spec.md 5.7 課金単位は 30 / 60 のみ */
fun normalizeUnitSec(sec: Int): Int = if (sec == 60) 60 else 30

/**
 * spec: docs/spec.md 5.7 定額枠の入力範囲。月間定額型のときだけ入力可能で、下限は 1（0 は不可）。
 * それ以外のプラン形式では 0 固定のため入力欄を無効化する（null = 入力対象外）。
 */
fun monthlyFreeMinRange(planType: PlanType): IntRange? =
    if (planType == PlanType.MONTHLY) 1..1440 else null

/**
 * spec: docs/spec.md 5.7 通話別無料時間の入力範囲。1 通話定額型のときだけ入力可能で、下限は 1。
 * それ以外のプラン形式では 0 固定のため入力欄を無効化する（null = 入力対象外）。
 */
fun perCallFreeMinRange(planType: PlanType): IntRange? =
    if (planType == PlanType.PER_CALL) 1..180 else null

/**
 * spec: docs/spec.md 5.7 入力欄の範囲チェック。範囲外・空欄・非数値ならエラーメッセージ、正常なら null。
 * 入力そのものは制限せず（編集途中の中間状態を壊さないため）、エラーがある間は保存ボタンを無効化する。
 */
fun validateRange(text: String, min: Int, max: Int): String? {
    val value = text.trim().toIntOrNull() ?: return "数値を入力してください"
    return if (value in min..max) null else "$min〜$max の範囲で入力してください"
}

/** 入力対象外（0 固定）の欄は検証しない。範囲が null のときは常に null を返す */
fun validateRange(text: String, range: IntRange?): String? =
    if (range == null) null else validateRange(text, range.first, range.last)

/** spec: docs/spec.md 5.7 プラン形式の表示名。設定画面のラジオとメイン画面の表示で共用する */
fun planTypeLabel(planType: PlanType): String = when (planType) {
    PlanType.MONTHLY -> "月間定額型"
    PlanType.PER_CALL -> "1通話定額型"
    PlanType.PAY_AS_YOU_GO -> "従量課金"
}

/**
 * spec: docs/spec.md 5.7 除外番号リストの折りたたみ表示用。
 * 閉じている状態で中身の見当がつくよう、先頭 3 件と残り件数を 1 行にまとめる。
 */
fun excludePrefixesPreview(prefixes: List<String>): String {
    if (prefixes.isEmpty()) return "（なし）"
    val head = prefixes.take(3).joinToString(", ")
    val rest = prefixes.size - 3
    return if (rest > 0) "$head ほか${rest}件" else head
}

/** spec: docs/spec.md 5.7 除外番号リスト（改行区切りテキスト）→ プレフィックスのリスト */
fun parseExcludePrefixes(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** parseExcludePrefixes の逆変換。設定画面のテキストフィールド初期表示に使用 */
fun excludePrefixesToText(prefixes: List<String>): String = prefixes.joinToString("\n")
