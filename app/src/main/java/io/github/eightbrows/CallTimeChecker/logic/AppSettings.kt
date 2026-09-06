package io.github.eightbrows.CallTimeChecker.logic

/**
 * spec: docs/spec.md 5.4.2 / 5.7 プラン形式。UI 上のプリセットに過ぎず、
 * 集計ロジック（Billing.kt の Settings/calculate）はプラン形式を参照しない。
 * 3 択はいずれも monthlyFreeMin / perCallFreeMin の組み合わせと 1 対 1 に対応する
 * （どちらか一方だけが正、または両方 0）。併用型（旧 CUSTOM）は廃止した。
 */
enum class PlanType { MONTHLY, PER_CALL, PAY_AS_YOU_GO }

/**
 * spec: docs/spec.md 5.7 アプリ本体の配色。
 * SYSTEM は端末のダークテーマ設定に従う（Compose の isSystemInDarkTheme()）。
 *
 * prefsValue は保存用の文字列で、enum の名前とは独立に持つ。リリースビルドは R8 で
 * 難読化されるため、name に依存すると保存済みの値が読めなくなる可能性がある。
 */
enum class ThemeMode(val prefsValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark")
}

/** spec: docs/spec.md 5.7 配色の表示名（設定画面のラジオ） */
fun themeModeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "システムに従う"
    ThemeMode.LIGHT -> "ライト"
    ThemeMode.DARK -> "ダーク"
}

/** 保存値（prefsValue）→ ThemeMode。未保存・未知の値はいずれも既定の「システムに従う」に倒す */
fun themeModeFromPrefsValue(value: String?): ThemeMode =
    ThemeMode.entries.firstOrNull { it.prefsValue == value } ?: DEFAULT_THEME_MODE

/** spec: docs/spec.md 5.7 配色の初期値。端末の設定を尊重するため「システムに従う」 */
val DEFAULT_THEME_MODE = ThemeMode.SYSTEM

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
    val widgetBgTransparencyStep: Int,
    /**
     * spec: docs/spec.md 5.7 警告色に切り替わる「定額枠の残り時間」（分）。
     * 消費量ではなく残量で持つのは、「あと何分使えるか」が利用者の関心そのものだから。
     * 定額枠を変えても利用者が明示的に変えない限りこの値は動かさない
     * （残り 10 分で警告、という設定は定額枠が何分でも同じ意味を持つ）。
     * 意味を持つのは月間定額型のときだけ（5.5.3）。
     */
    val warnRemainingMin: Int,
    /** spec: docs/spec.md 5.7 ウィジェット背景色（通常色）のパレット添字 */
    val widgetColorNormalIndex: Int,
    /** spec: docs/spec.md 5.7 ウィジェット背景色（警告色）のパレット添字 */
    val widgetColorWarningIndex: Int,
    /** spec: docs/spec.md 5.7 ウィジェット背景色（超過色）のパレット添字 */
    val widgetColorOverIndex: Int,
    /**
     * spec: docs/spec.md 5.7 アプリ本体の配色。ウィジェットの配色（背景色パレット）とは
     * 別物で、こちらはアプリ画面のライト／ダークだけを決める。
     */
    val themeMode: ThemeMode
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
    widgetBgTransparencyStep = 0,
    // 定額枠 70 分の 20%。消費量で言えば 80% に達した時点で、従来と同じ切り替わり位置
    warnRemainingMin = 14,
    widgetColorNormalIndex = WIDGET_COLOR_INDEX_WHITE,
    widgetColorWarningIndex = WIDGET_COLOR_INDEX_ORANGE,
    widgetColorOverIndex = WIDGET_COLOR_INDEX_RED,
    themeMode = DEFAULT_THEME_MODE
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
    val effective = effectiveAppSettings(settings.copy(planType = planType))
    // 警告しきい値も同じ理由で正規化する。範囲は定額枠に依存するため、定額枠が
    // 外部で書き換えられていると範囲外になり得る（範囲が無いプラン形式では値を保つ）
    val range = warnRemainingMinRange(planType, effective.monthlyFreeMin)
    val warn = if (range != null && effective.warnRemainingMin !in range) {
        defaultWarnRemainingMin(effective.monthlyFreeMin)
    } else {
        effective.warnRemainingMin
    }
    return effective.copy(
        warnRemainingMin = warn,
        widgetColorNormalIndex = clampWidgetColorIndex(effective.widgetColorNormalIndex),
        widgetColorWarningIndex = clampWidgetColorIndex(effective.widgetColorWarningIndex),
        widgetColorOverIndex = clampWidgetColorIndex(effective.widgetColorOverIndex)
    )
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

/**
 * spec: docs/spec.md 5.7 警告しきい値（残り時間）の入力範囲。月間定額型のときだけ入力でき、
 * 上限は「定額枠 − 1」。残りが定額枠と同じ値、つまり消費 0 分で既に警告色になるのは無意味なため。
 * 定額枠が 1 分だと範囲が空になるので、その場合も入力対象外（null）とし、警告色を使わない。
 */
fun warnRemainingMinRange(planType: PlanType, monthlyFreeMin: Int): IntRange? =
    if (planType == PlanType.MONTHLY && monthlyFreeMin >= 2) 1..(monthlyFreeMin - 1) else null

/**
 * spec: docs/spec.md 5.7 警告しきい値（残り時間）の既定値。定額枠の 20%（切り捨て）とする。
 * 消費量で言えば 80% に達した時点であり、しきい値が設定項目になる前の
 * 「使用率 80% で警告」と同じ切り替わり位置になる。定額枠 70 分なら残り 14 分。
 */
fun defaultWarnRemainingMin(monthlyFreeMin: Int): Int {
    val range = warnRemainingMinRange(PlanType.MONTHLY, monthlyFreeMin) ?: return 0
    return (monthlyFreeMin / 5).coerceIn(range.first, range.last)
}

/** spec: docs/spec.md 5.7 警告しきい値（残り時間）は 1〜(定額枠 − 1)。範囲が無いときは 0 に倒す */
fun clampWarnRemainingMin(min: Int, monthlyFreeMin: Int): Int {
    val range = warnRemainingMinRange(PlanType.MONTHLY, monthlyFreeMin) ?: return 0
    return min.coerceIn(range.first, range.last)
}

/**
 * spec: docs/spec.md 5.7 警告しきい値の表示（`残り14分`）。
 * 残量そのものが設定値なので、定額枠に対する割合は併記しない。
 */
fun warnRemainingLabel(warnRemainingMin: Int): String = "残り${warnRemainingMin}分"

/** spec: docs/spec.md 5.7 ウィジェット背景色のパレット添字は 0〜(パレット長 − 1) */
fun clampWidgetColorIndex(index: Int): Int = index.coerceIn(0, WIDGET_COLOR_PALETTE.lastIndex)

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
