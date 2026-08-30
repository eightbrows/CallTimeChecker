package io.github.eightbrows.CallTimeChecker.logic

/** spec: docs/spec.md 5.5.3 配色（通常色 / 警告色（橙）/ 超過色（赤）） */
enum class WidgetColor { NORMAL, WARNING, OVER }

/** spec: docs/spec.md 5.5.1 ウィジェット表示内容（2行）+ 5.5.3 配色 */
data class WidgetContent(
    val line1: String,
    val line2: String,
    val color: WidgetColor
)

/**
 * spec: docs/spec.md 5.5.1 / 5.5.3 の表示テンプレート・配色判定。
 * calculate() の結果 (Result) と Settings のみから決まる純粋関数。
 */
fun presentWidget(result: Result, settings: Settings): WidgetContent {
    if (settings.monthlyFreeSec > 0) {
        // 月間定額型。定額枠に対する分子は実通話時間（countedSec）ではなく、
        // 通話ごとに課金単位へ切り上げた実際の枠消費量（quotaConsumedSec、5.4.4）を使う
        val usedMin = result.quotaConsumedSec / 60
        val quotaMin = settings.monthlyFreeSec / 60
        val line1 = "${usedMin}分 / ${quotaMin}分 (${result.callCount}件)"
        val line2 = if (result.quotaConsumedSec > settings.monthlyFreeSec) {
            val overMin = (result.quotaConsumedSec - settings.monthlyFreeSec) / 60
            "¥${result.amount} (超過 ${overMin}分)"
        } else {
            "¥${result.amount}"
        }
        val usage = result.quotaConsumedSec.toDouble() / settings.monthlyFreeSec
        val color = when {
            usage >= 1.0 -> WidgetColor.OVER
            usage >= 0.8 -> WidgetColor.WARNING
            else -> WidgetColor.NORMAL
        }
        return WidgetContent(line1, line2, color)
    }

    // 1 通話定額型（monthlyFreeSec = 0）: 消費すべき定額枠が無いため、表示は実通話時間のまま。
    // 使用率が定義できないので課金額 0 円かどうかで色を切り替える
    val countedMin = result.countedSec / 60
    val line1 = "通話 ${countedMin}分 (${result.callCount}件)"
    val line2 = "¥${result.amount}"
    val color = if (result.amount == 0) WidgetColor.NORMAL else WidgetColor.WARNING
    return WidgetContent(line1, line2, color)
}
