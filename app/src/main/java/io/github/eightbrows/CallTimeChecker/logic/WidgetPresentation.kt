package io.github.eightbrows.CallTimeChecker.logic

import java.util.Locale

/** spec: docs/spec.md 5.5.3 配色（通常色 / 警告色（橙）/ 超過色（赤）） */
enum class WidgetColor { NORMAL, WARNING, OVER }

/**
 * spec: docs/spec.md 5.5.1 ウィジェット表示内容。
 * 「ラベル（小）+ 値（大）」のブロック 2 つ分。ラベルも値もプラン形式によって変わるため、
 * 文字列の組み立てはすべてここで行い、Provider 側はビューへの割り当てだけを行う。
 */
data class WidgetContent(
    val timeLabel: String,
    val timeValue: String,
    val amountLabel: String,
    val amountValue: String,
    val color: WidgetColor
)

/**
 * 分単位・小数第一位。ウィジェット（5.5.1）とアプリ本体（5.6.1）で同じ数字を出すため共用する。
 */
fun formatMinutes(sec: Int): String = String.format(Locale.JAPAN, "%.1f", sec / 60.0)

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 の表示テンプレート・配色判定。
 * 通話時間は全プランとも、通話金額の計算根拠と一致させるため切り上げ後の課金枠の
 * 消費量（quotaConsumedSec、5.4.4）を使う。実通話時間はアプリ本体の「履歴集計」で確認する。
 * calculate() の結果 (Result) と Settings / PlanType のみから決まる純粋関数。
 * 表示項目はアプリ本体の「現在の状況」（5.6.1）と揃える。
 */
fun presentWidget(result: Result, settings: Settings, planType: PlanType): WidgetContent {
    val amountValue = "${result.amount}円"

    return when (planType) {
        PlanType.MONTHLY -> {
            // 定額枠に対する分子は実通話時間（countedSec）ではなく、
            // 通話ごとに課金単位へ切り上げた実際の枠消費量（quotaConsumedSec、5.4.4）を使う
            val remainingSec = (settings.monthlyFreeSec - result.quotaConsumedSec).coerceAtLeast(0)
            // 定額枠 0 分の月間定額型は設定画面から作れない（5.7 のマイグレーション）が、
            // 使用率がゼロ除算になるため念のため通常色に倒す
            val color = if (settings.monthlyFreeSec > 0) {
                val usage = result.quotaConsumedSec.toDouble() / settings.monthlyFreeSec
                when {
                    usage >= 1.0 -> WidgetColor.OVER
                    usage >= 0.8 -> WidgetColor.WARNING
                    else -> WidgetColor.NORMAL
                }
            } else {
                WidgetColor.NORMAL
            }
            WidgetContent(
                timeLabel = "通話時間 / 無料枠残",
                timeValue = "${formatMinutes(result.quotaConsumedSec)} / ${formatMinutes(remainingSec)}分",
                amountLabel = "通話金額",
                amountValue = amountValue,
                color = color
            )
        }
        // 1 通話定額型: 月間の定額枠が無いため使用率が定義できない。
        // 課金額 0 円かどうかで色を切り替える（超過色は使わない）
        PlanType.PER_CALL -> WidgetContent(
            timeLabel = "通話時間",
            timeValue = "${formatMinutes(result.quotaConsumedSec)}分",
            amountLabel = "通話金額",
            amountValue = amountValue,
            color = if (result.amount == 0) WidgetColor.NORMAL else WidgetColor.WARNING
        )
        // 従量課金: 無料枠が無く「超過」という概念自体が無いため、金額が出ていても常に通常色
        PlanType.PAY_AS_YOU_GO -> WidgetContent(
            timeLabel = "通話時間",
            timeValue = "${formatMinutes(result.quotaConsumedSec)}分",
            amountLabel = "通話金額",
            amountValue = amountValue,
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
