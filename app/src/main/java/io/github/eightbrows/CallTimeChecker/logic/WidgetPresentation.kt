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
        // 月間定額型
        val countedMin = result.countedSec / 60
        val quotaMin = settings.monthlyFreeSec / 60
        val line1 = "${countedMin}分 / ${quotaMin}分 (${result.callCount}件)"
        val line2 = if (result.countedSec > settings.monthlyFreeSec) {
            val overMin = (result.countedSec - settings.monthlyFreeSec) / 60
            "¥${result.amount} (超過 ${overMin}分)"
        } else {
            "¥${result.amount}"
        }
        val usage = result.countedSec.toDouble() / settings.monthlyFreeSec
        val color = when {
            usage >= 1.0 -> WidgetColor.OVER
            usage >= 0.8 -> WidgetColor.WARNING
            else -> WidgetColor.NORMAL
        }
        return WidgetContent(line1, line2, color)
    }

    // 1 通話定額型（monthlyFreeSec = 0）: 使用率が定義できないため課金額 0 円かどうかで色を切り替える
    val countedMin = result.countedSec / 60
    val line1 = "通話 ${countedMin}分 (${result.callCount}件)"
    val line2 = "¥${result.amount}"
    val color = if (result.amount == 0) WidgetColor.NORMAL else WidgetColor.WARNING
    return WidgetContent(line1, line2, color)
}
