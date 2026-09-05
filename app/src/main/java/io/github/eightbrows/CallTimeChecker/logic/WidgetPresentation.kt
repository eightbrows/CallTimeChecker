package io.github.eightbrows.CallTimeChecker.logic

import java.util.Locale

/** spec: docs/spec.md 5.5.3 配色（通常色 / 警告色（橙）/ 超過色（赤）） */
enum class WidgetColor { NORMAL, WARNING, OVER }

/**
 * spec: docs/spec.md 5.5.1 ウィジェットの 1 項目。
 * 「ラベル（小）+ 数字（大）+ 単位（小）」を 1 つの TextView にまとめ、
 * 大きさの差はスパンで付けて描画するため、大きさの違う 3 つの部品として持つ。
 */
data class WidgetLine(val label: String, val value: String, val unit: String)

/**
 * spec: docs/spec.md 5.5.1 ウィジェット表示内容。
 * 上段（3/5）は通話時間と無料枠、下段（2/5）は通話金額。
 * ラベルも数字もプラン形式によって変わるため、文字列の組み立てはすべてここで行い、
 * Provider 側はビューへの割り当てだけを行う。
 */
data class WidgetContent(
    /** 上段 1 項目目。全プラン共通の「通話時間」 */
    val timeLine: WidgetLine,
    /** 上段 2 項目目。月間定額型の「無料枠」（残り）のみ。他プランは null で行ごと非表示にする */
    val quotaLine: WidgetLine?,
    /** 下段の「通話金額」 */
    val amountLine: WidgetLine,
    val color: WidgetColor
)

/**
 * 分単位・小数第一位。ウィジェット（5.5.1）とアプリ本体（5.6.1）で同じ数字を出すため共用する。
 */
fun formatMinutes(sec: Int): String = String.format(Locale.JAPAN, "%.1f", sec / 60.0)

/**
 * spec: docs/spec.md 5.5.1 金額は 3 桁区切り。
 * 1x1 の狭い幅でも桁数を読み取れるようにするため。アプリ本体（5.6.1）とも共用する。
 */
fun formatAmount(amount: Int): String = String.format(Locale.JAPAN, "%,d", amount)

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 の表示テンプレート・配色判定。
 * 通話時間は全プランとも、通話金額の計算根拠と一致させるため切り上げ後の課金枠の
 * 消費量（quotaConsumedSec、5.4.4）を使う。実通話時間はアプリ本体の「履歴集計」で確認する。
 * calculate() の結果 (Result) と Settings / PlanType のみから決まる純粋関数。
 * 表示する数字はアプリ本体の「現在の状況」（5.6.1）と揃える。
 */
fun presentWidget(
    result: Result,
    settings: Settings,
    planType: PlanType,
    warnRemainingSec: Int
): WidgetContent {
    val amountLine = WidgetLine(label = "通話金額", value = formatAmount(result.amount), unit = "円")
    val timeLine = WidgetLine(
        label = "通話時間", value = formatMinutes(result.quotaConsumedSec), unit = "分"
    )

    return when (planType) {
        PlanType.MONTHLY -> {
            // 定額枠に対する分子は実通話時間（countedSec）ではなく、
            // 通話ごとに課金単位へ切り上げた実際の枠消費量（quotaConsumedSec、5.4.4）を使う
            val remainingSec = (settings.monthlyFreeSec - result.quotaConsumedSec).coerceAtLeast(0)
            // 警告色の境界は利用者が「残り何分で警告か」を分で指定する（5.7）。
            // 超過色の境界は定額枠そのもので固定。
            // 定額枠 0 分の月間定額型は設定画面から作れない（5.7 のマイグレーション）が、
            // その場合に消費 0 秒が超過扱いにならないよう定額枠が正のときだけ判定する。
            // しきい値が定額枠以上／0 以下のときは警告色の帯が存在しない（定額枠 1 分など）
            val color = when {
                settings.monthlyFreeSec <= 0 -> WidgetColor.NORMAL
                result.quotaConsumedSec >= settings.monthlyFreeSec -> WidgetColor.OVER
                warnRemainingSec in 1 until settings.monthlyFreeSec &&
                    remainingSec <= warnRemainingSec -> WidgetColor.WARNING
                else -> WidgetColor.NORMAL
            }
            WidgetContent(
                timeLine = timeLine,
                quotaLine = WidgetLine(label = "無料枠", value = formatMinutes(remainingSec), unit = "分"),
                amountLine = amountLine,
                color = color
            )
        }
        // 1 通話定額型: 月間の定額枠が無いため使用率が定義できない。
        // 課金額 0 円かどうかで色を切り替える（超過色は使わない）
        PlanType.PER_CALL -> WidgetContent(
            timeLine = timeLine,
            // 月間の無料枠が無いプラン形式では「無料枠」の項目自体を出さない
            quotaLine = null,
            amountLine = amountLine,
            color = if (result.amount == 0) WidgetColor.NORMAL else WidgetColor.WARNING
        )
        // 従量課金: 無料枠が無く「超過」という概念自体が無いため、金額が出ていても常に通常色
        PlanType.PAY_AS_YOU_GO -> WidgetContent(
            timeLine = timeLine,
            quotaLine = null,
            amountLine = amountLine,
            color = WidgetColor.NORMAL
        )
    }
}


/**
 * spec: docs/spec.md 5.5.3 ウィジェット背景の透過率。
 * 段階インデックス（0〜8、5.7）を alpha チャンネルの値（255〜0）に変換する。
 * 0 段階目が完全不透明（255）、8 段階目が完全透明（0）。
 * 255 は 8 で割り切れないため中間の段階では 0.5 未満の誤差が出るが、見た目には影響しない。
 */
fun widgetBgAlpha(step: Int): Int = 255 * (WIDGET_BG_TRANSPARENCY_STEP_COUNT - 1 -
    clampWidgetBgTransparencyStep(step)) / (WIDGET_BG_TRANSPARENCY_STEP_COUNT - 1)

/**
 * spec: docs/spec.md 5.5.3 配色リソース（不透明な ARGB）の alpha だけを差し替える。
 * 色相は変えずに透過率だけを調整するため、RGB はそのまま使う。
 */
fun withAlpha(colorArgb: Int, alpha: Int): Int =
    (alpha shl 24) or (colorArgb and 0x00FFFFFF)

/** spec: docs/spec.md 5.7 ウィジェット背景色のプリセット 1 色分 */
data class WidgetPaletteColor(val label: String, val argb: Int)

/**
 * spec: docs/spec.md 5.5.3 / 5.7 ウィジェット背景色のプリセットパレット。
 * 保存するのは ARGB ではなくこのリストの添字。色の実体をここ 1 箇所に閉じ込めるため。
 * 並び順が保存値の意味そのものになるので、色を入れ替えるときは位置を保つ。
 * ライトグレー / ダークグレーをピンク / パープルに差し替えた際も、他の色の添字が
 * ずれないよう同じ位置に置いた（削除した 2 色を指していた保存値だけが別の色になる）。
 */
val WIDGET_COLOR_PALETTE = listOf(
    WidgetPaletteColor("ホワイト", 0xFFFFFFFF.toInt()),
    WidgetPaletteColor("ピンク", 0xFFE91E63.toInt()),
    WidgetPaletteColor("パープル", 0xFF7B1FA2.toInt()),
    WidgetPaletteColor("ブルー", 0xFF1976D2.toInt()),
    WidgetPaletteColor("グリーン", 0xFF388E3C.toInt()),
    WidgetPaletteColor("イエロー", 0xFFFBC02D.toInt()),
    WidgetPaletteColor("オレンジ", 0xFFFFA000.toInt()),
    WidgetPaletteColor("レッド", 0xFFD32F2F.toInt())
)

const val WIDGET_COLOR_INDEX_WHITE = 0
const val WIDGET_COLOR_INDEX_ORANGE = 6
const val WIDGET_COLOR_INDEX_RED = 7

/** パレット添字 → 不透明な ARGB。範囲外の添字は先頭色に倒す */
fun widgetPaletteArgb(index: Int): Int =
    WIDGET_COLOR_PALETTE[index.coerceIn(0, WIDGET_COLOR_PALETTE.lastIndex)].argb

private const val WIDGET_TEXT_BLACK = 0xFF000000.toInt()
private const val WIDGET_TEXT_WHITE = 0xFFFFFFFF.toInt()

/** sRGB の 1 チャンネルを相対輝度の線形値に戻す（WCAG 2.x の定義） */
private fun linearize(channel: Int): Double {
    val s = channel / 255.0
    return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4)
}

/**
 * spec: docs/spec.md 5.5.3 背景色に対する文字色。
 * 背景色を利用者が選べるようになったため固定の対応（通常色=黒 / 警告色・超過色=白）では
 * 読めない組み合わせが作れてしまう。背景の相対輝度から、コントラスト比が高い方を選ぶ。
 * alpha は無視する（透過後の見え方は壁紙次第で決まらないため、不透明色として判定する）。
 */
fun widgetTextColorOn(backgroundArgb: Int): Int {
    val luminance = 0.2126 * linearize((backgroundArgb shr 16) and 0xFF) +
        0.7152 * linearize((backgroundArgb shr 8) and 0xFF) +
        0.0722 * linearize(backgroundArgb and 0xFF)
    // 黒文字とのコントラスト比 (L+0.05)/0.05 と白文字との 1.05/(L+0.05) を比べる
    val blackContrast = (luminance + 0.05) / 0.05
    val whiteContrast = 1.05 / (luminance + 0.05)
    return if (blackContrast >= whiteContrast) WIDGET_TEXT_BLACK else WIDGET_TEXT_WHITE
}
