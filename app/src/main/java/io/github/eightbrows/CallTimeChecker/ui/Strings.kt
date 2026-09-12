package io.github.eightbrows.CallTimeChecker.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.logic.InputError
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.ThemeMode
import io.github.eightbrows.CallTimeChecker.logic.WidgetPaletteName
import io.github.eightbrows.CallTimeChecker.logic.formatAmount
import io.github.eightbrows.CallTimeChecker.logic.formatMinutes

/**
 * spec: docs/spec.md 5.8 多言語対応。
 * logic 層は Context を持てないため文字列を返さず、「種類」（enum / sealed）を返す。
 * 種類 → 文字列リソースの対応はここに集約し、画面側は解決済みの文字列だけを扱う。
 * 数値の整形（3 桁区切り・小数第一位）は logic の formatAmount / formatMinutes のままで、
 * ここでは単位や語順だけを言語に合わせる。
 */

/** spec: docs/spec.md 5.7 プラン形式の表示名。設定画面の SegmentedControl とサマリで共用する */
@StringRes
fun PlanType.labelRes(): Int = when (this) {
    PlanType.MONTHLY -> R.string.plan_monthly
    PlanType.PER_CALL -> R.string.plan_per_call
    PlanType.PAY_AS_YOU_GO -> R.string.plan_pay_as_you_go
}

/** spec: docs/spec.md 5.7 配色の表示名 */
@StringRes
fun ThemeMode.labelRes(): Int = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

/** spec: docs/spec.md 5.7 ウィジェット背景色パレットの色名 */
@StringRes
fun WidgetPaletteName.labelRes(): Int = when (this) {
    WidgetPaletteName.WHITE -> R.string.palette_white
    WidgetPaletteName.TEAL -> R.string.palette_teal
    WidgetPaletteName.BLUE -> R.string.palette_blue
    WidgetPaletteName.INDIGO -> R.string.palette_indigo
    WidgetPaletteName.PURPLE -> R.string.palette_purple
    WidgetPaletteName.PINK -> R.string.palette_pink
    WidgetPaletteName.RED -> R.string.palette_red
    WidgetPaletteName.ORANGE -> R.string.palette_orange
    WidgetPaletteName.YELLOW -> R.string.palette_yellow
    WidgetPaletteName.OLIVE -> R.string.palette_olive
    WidgetPaletteName.GREEN -> R.string.palette_green
    WidgetPaletteName.BLACK -> R.string.palette_black
}

/** spec: docs/spec.md 5.7 入力欄の検証結果 → 表示メッセージ */
@Composable
fun InputError.message(): String = when (this) {
    InputError.NotANumber -> stringResource(R.string.error_not_a_number)
    is InputError.OutOfRange -> stringResource(R.string.error_out_of_range, min, max)
}

/** 分単位・小数第一位 + 単位（`20.5分` / `20.5 min`） */
@Composable
fun minutesText(sec: Int): String = stringResource(R.string.fmt_minutes, formatMinutes(sec))

/** 整数の分 + 単位（`70分` / `70 min`）。設定値など秒に端数の無い値用 */
@Composable
fun wholeMinutesText(min: Int): String = stringResource(R.string.fmt_minutes, min.toString())

/** 分と秒（`1分27秒` / `1m 27s`）。履歴集計の実時間表示用 */
@Composable
fun minSecText(sec: Int): String = stringResource(R.string.fmt_min_sec, sec / 60, sec % 60)

/** 秒 + 単位（`30秒` / `30 sec`） */
@Composable
fun secondsText(sec: Int): String = stringResource(R.string.fmt_seconds, sec)

/** 金額。3 桁区切りに通貨を付ける（`1,220円` / `¥1,220`）。位置は言語側の書式で決まる */
@Composable
fun yenText(amount: Int): String = stringResource(R.string.fmt_yen, formatAmount(amount))

/** 通話件数と課金対象件数（`30件（課金対象 0件）` / `30 calls (0 billed)`） */
@Composable
fun callsWithBilledText(callCount: Int, billedCallCount: Int): String =
    pluralStringResource(R.plurals.calls_with_billed, callCount, callCount, billedCallCount)

/** 月送りのラベル（`今月` / `3ヶ月前`）。monthOffset は 0 以下 */
@Composable
fun monthOffsetText(monthOffset: Int): String =
    if (monthOffset == 0) {
        stringResource(R.string.month_current)
    } else {
        pluralStringResource(R.plurals.months_ago, -monthOffset, -monthOffset)
    }
