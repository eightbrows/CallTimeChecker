package io.github.eightbrows.CallTimeChecker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.eightbrows.CallTimeChecker.BuildConfig
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_EXCLUDE_PREFIXES
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.clampMonthlyFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampPerCallFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_BG_TRANSPARENCY_STEP_COUNT
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_COLOR_PALETTE
import io.github.eightbrows.CallTimeChecker.logic.clampWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetColorIndex
import io.github.eightbrows.CallTimeChecker.logic.defaultWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.warnRemainingLabel
import io.github.eightbrows.CallTimeChecker.logic.warnRemainingMinRange
import io.github.eightbrows.CallTimeChecker.logic.widgetTextColorOn
import io.github.eightbrows.CallTimeChecker.logic.clampUnitPrice
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetBgTransparencyStep
import io.github.eightbrows.CallTimeChecker.logic.effectiveAppSettings
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesPreview
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.monthlyFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.normalizeUnitSec
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes
import io.github.eightbrows.CallTimeChecker.logic.perCallFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.planTypeLabel
import io.github.eightbrows.CallTimeChecker.logic.validateRange
import io.github.eightbrows.CallTimeChecker.logic.widgetBgTransparencyLabel
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * spec: docs/spec.md 5.7 設定項目（プラン形式・起算日・定額枠・通話別無料時間・課金単位・単位金額・除外番号リスト）。
 * 保存時に effectiveAppSettings() を適用するため、プラン形式による固定ルール（0固定）が
 * 永続化される値にも反映される。入力値の検証・正規化は logic/AppSettings.kt の純粋関数
 * （validateRange / clamp* / normalizeUnitSec / parseExcludePrefixes、AppSettingsTest.kt でテスト済み）に委譲する。
 *
 * 範囲チェックは入力のたびに評価し、範囲外の欄はエラー表示 + 保存ボタン無効化とする。
 * 入力そのものは制限しない（"1440" を "70" に直す途中の中間状態を壊さないため）。
 *
 * spec: docs/spec.md 8 権限。READ_CALL_LOG の許可状態をここにも表示し、
 * 未許可ならその場で要求、許可済みでも OS のアプリ権限設定へ遷移できるようにする。
 */
@Composable
fun SettingsScreen(
    current: AppSettings,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onSave: (AppSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var planType by remember { mutableStateOf(current.planType) }
    var startDayText by remember { mutableStateOf(current.startDay.toString()) }
    var monthlyFreeMinText by remember { mutableStateOf(current.monthlyFreeMin.toString()) }
    var perCallFreeMinText by remember { mutableStateOf(current.perCallFreeMin.toString()) }
    var unitSec by remember { mutableStateOf(current.unitSec) }
    var unitPriceText by remember { mutableStateOf(current.unitPrice.toString()) }
    var widgetBgTransparencyStep by remember { mutableStateOf(current.widgetBgTransparencyStep) }
    var warnRemainingText by remember { mutableStateOf(current.warnRemainingMin.toString()) }
    var colorNormalIndex by remember { mutableStateOf(current.widgetColorNormalIndex) }
    var colorWarningIndex by remember { mutableStateOf(current.widgetColorWarningIndex) }
    var colorOverIndex by remember { mutableStateOf(current.widgetColorOverIndex) }
    var excludeText by remember { mutableStateOf(excludePrefixesToText(current.excludePrefixes)) }
    var excludeExpanded by remember { mutableStateOf(false) }

    // 範囲が null のプラン形式では 0 固定（グレーアウト）。validateRange も検証をスキップする
    val monthlyFreeRange = monthlyFreeMinRange(planType)
    val perCallFreeRange = perCallFreeMinRange(planType)

    // 警告しきい値の範囲は定額枠に従属する。定額枠を編集している最中の値で都度評価するため、
    // 定額枠を下げるとその場で警告しきい値の欄がエラーになる（値自体は書き換えない）
    val monthlyFreeValue = monthlyFreeMinText.trim().toIntOrNull() ?: 0
    val warnRemainingRange = warnRemainingMinRange(planType, monthlyFreeValue)

    val startDayError = validateRange(startDayText, 1, 31)
    val monthlyFreeError = validateRange(monthlyFreeMinText, monthlyFreeRange)
    val perCallFreeError = validateRange(perCallFreeMinText, perCallFreeRange)
    val unitPriceError = validateRange(unitPriceText, 0, 999)
    val warnRemainingError = validateRange(warnRemainingText, warnRemainingRange)
    val canSave = startDayError == null && monthlyFreeError == null &&
        perCallFreeError == null && unitPriceError == null && warnRemainingError == null

    // プラン形式を切り替えると 0 固定だった欄が有効になる。値が 0 のままだと下限 1 を
    // 満たさず即エラーになるため、その場合だけ初期値を入れておく
    fun selectPlanType(next: PlanType) {
        if (next == PlanType.MONTHLY && (monthlyFreeMinText.trim().toIntOrNull() ?: 0) == 0) {
            monthlyFreeMinText = DEFAULT_APP_SETTINGS.monthlyFreeMin.toString()
        }
        // 定額枠が変わると警告しきい値の上限も変わる。範囲外になる場合だけ既定値（残り 20%）に
        // 戻す。範囲内ならユーザーが設定した残り分数をそのまま残す
        if (next == PlanType.MONTHLY) {
            val freeMin = monthlyFreeMinText.trim().toIntOrNull() ?: 0
            val range = warnRemainingMinRange(next, freeMin)
            val warn = warnRemainingText.trim().toIntOrNull()
            if (range != null && (warn == null || warn !in range)) {
                warnRemainingText = defaultWarnRemainingMin(freeMin).toString()
            }
        }
        if (next == PlanType.PER_CALL && (perCallFreeMinText.trim().toIntOrNull() ?: 0) == 0) {
            perCallFreeMinText = DEFAULT_APP_SETTINGS.perCallFreeMin.toString()
        }
        planType = next
    }

    // 保存する値の組み立て。固定ヘッダの「保存」から呼ぶため、ボタンの中には置かない。
    // canSave のときのみ呼ばれるため clamp は保険で、値は既に範囲内
    fun buildSettings(): AppSettings {
        val raw = AppSettings(
            planType = planType,
            startDay = clampStartDay(startDayText.trim().toIntOrNull() ?: current.startDay),
            monthlyFreeMin = clampMonthlyFreeMin(
                monthlyFreeMinText.trim().toIntOrNull() ?: current.monthlyFreeMin
            ),
            perCallFreeMin = clampPerCallFreeMin(
                perCallFreeMinText.trim().toIntOrNull() ?: current.perCallFreeMin
            ),
            unitSec = normalizeUnitSec(unitSec),
            unitPrice = clampUnitPrice(unitPriceText.trim().toIntOrNull() ?: current.unitPrice),
            excludePrefixes = parseExcludePrefixes(excludeText),
            widgetBgTransparencyStep = clampWidgetBgTransparencyStep(widgetBgTransparencyStep),
            warnRemainingMin = clampWarnRemainingMin(
                warnRemainingText.trim().toIntOrNull() ?: current.warnRemainingMin,
                clampMonthlyFreeMin(
                    monthlyFreeMinText.trim().toIntOrNull() ?: current.monthlyFreeMin
                )
            ),
            widgetColorNormalIndex = clampWidgetColorIndex(colorNormalIndex),
            widgetColorWarningIndex = clampWidgetColorIndex(colorWarningIndex),
            widgetColorOverIndex = clampWidgetColorIndex(colorOverIndex)
        )
        return effectiveAppSettings(raw)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        SettingsHeader(
            canSave = canSave,
            onCancel = onBack,
            onSave = { onSave(buildSettings()) }
        )
        HorizontalDivider()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("プラン形式", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth()) {
                PlanTypeOption(PlanType.MONTHLY, planType, Modifier.weight(1f)) { selectPlanType(it) }
                PlanTypeOption(PlanType.PER_CALL, planType, Modifier.weight(1f)) { selectPlanType(it) }
                PlanTypeOption(PlanType.PAY_AS_YOU_GO, planType, Modifier.weight(1f)) { selectPlanType(it) }
            }
            Spacer(Modifier.height(8.dp))

            // 起算日・単位金額はプラン形式に関係なく常に有効な独立パラメータ
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = startDayText,
                    onValueChange = { startDayText = it },
                    label = "起算日（日）",
                    error = startDayError,
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = unitPriceText,
                    onValueChange = { unitPriceText = it },
                    label = "単位金額（円）",
                    error = unitPriceError,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))

            // 定額枠と通話別無料時間は対の項目。プラン形式によって最低でも一方は 0 固定になるため横に並べる
            // （従量課金では両方 0 固定）
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField(
                    value = if (monthlyFreeRange != null) monthlyFreeMinText else "0",
                    onValueChange = { monthlyFreeMinText = it },
                    label = "定額枠（分）",
                    error = monthlyFreeError,
                    enabled = monthlyFreeRange != null,
                    disabledNote = "${planTypeLabel(planType)}では0固定",
                    modifier = Modifier.weight(1f)
                )
                NumberField(
                    value = if (perCallFreeRange != null) perCallFreeMinText else "0",
                    onValueChange = { perCallFreeMinText = it },
                    label = "通話別無料（分）",
                    error = perCallFreeError,
                    enabled = perCallFreeRange != null,
                    disabledNote = "${planTypeLabel(planType)}では0固定",
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(8.dp))

            WarnRemainingSection(
                value = warnRemainingText,
                onValueChange = { warnRemainingText = it },
                range = warnRemainingRange,
                error = warnRemainingError,
                planType = planType
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("課金単位", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.width(8.dp))
                UnitSecOption(30, unitSec) { unitSec = it }
                UnitSecOption(60, unitSec) { unitSec = it }
            }
            Spacer(Modifier.height(8.dp))

            ExcludePrefixesSection(
                text = excludeText,
                expanded = excludeExpanded,
                onToggle = { excludeExpanded = !excludeExpanded },
                onTextChange = { excludeText = it },
                onReset = { excludeText = excludePrefixesToText(DEFAULT_EXCLUDE_PREFIXES) }
            )
            Spacer(Modifier.height(8.dp))

            WidgetBgTransparencySection(widgetBgTransparencyStep) { widgetBgTransparencyStep = it }
            Spacer(Modifier.height(8.dp))

            WidgetColorSection(
                normalIndex = colorNormalIndex,
                warningIndex = colorWarningIndex,
                overIndex = colorOverIndex,
                onNormalChange = { colorNormalIndex = it },
                onWarningChange = { colorWarningIndex = it },
                onOverChange = { colorOverIndex = it }
            )
            Spacer(Modifier.height(16.dp))

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            PermissionSection(hasPermission, onRequestPermission, onOpenAppSettings)
            Spacer(Modifier.height(12.dp))

            Text(
                "バージョン: ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

/**
 * spec: docs/spec.md 5.7 設定画面の固定ヘッダ。
 * 設定項目は縦に長く、以前は画面末尾まで送らないと保存・取り消しができなかったため、
 * 見出しと一緒に上部へ固定してスクロール領域から分離する。
 * 「保存」は主操作なので塗りボタン（Button）、「キャンセル」は副操作なので枠線ボタン
 * （OutlinedButton）とする。両者を同じ見た目にすると、押し間違いのコストが非対称
 * （保存の取り消しは再入力が必要）なのに見分けがつかないため。入力エラー中は「保存」が
 * 無効になるので、無効状態がはっきり出る塗りボタンを保存側に割り当てる。
 * サイズはアプリ本体のヘッダ（HeaderButton）と同じ 48dp 高・titleMedium に揃える。
 */
@Composable
private fun SettingsHeader(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "設定",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text("キャンセル", style = MaterialTheme.typography.titleMedium)
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.heightIn(min = 48.dp)
        ) {
            Text("保存", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** DropdownMenuItem の最小高。メニュー内のスクロール位置の計算に使う */
private val MENU_ITEM_HEIGHT = 48.dp

/**
 * spec: docs/spec.md 5.7 ウィジェット背景の透過率。
 * 0%〜100% の 12.5% 刻み 9 段階をプルダウン（ExposedDropdownMenuBox）で選ぶ。
 * ラジオ 9 個は横幅に収まらず、数値入力にすると刻みの制約を利用者に押し付けることになるため。
 * 選択肢が 9 個と多く、選択中の値を閉じた状態でもそのまま文字で読めるプルダウンを採用する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetBgTransparencySection(step: Int, onStepChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val itemHeightPx = with(LocalDensity.current) { MENU_ITEM_HEIGHT.toPx() }

    // この項目は画面中ほどにあるためメニューは上方向に展開し、9 項目すべてが一度には
    // 収まらない。開いたときに選択中の項目が見えるよう、その項目が中央に来る位置まで
    // スクロールしておく（scrollTo は 0..maxValue に丸められるので端の項目でも安全）。
    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        // メニューの高さが確定するまで maxValue / viewportSize は 0 のため、確定を待つ。
        // 全項目が収まる場合は maxValue が 0 のままとなり、この await は完了しない
        // （スクロール不要なので、閉じるときのキャンセルに任せてよい）
        snapshotFlow { menuScrollState.maxValue }.first { it > 0 }
        val centered = itemHeightPx * step - (menuScrollState.viewportSize - itemHeightPx) / 2f
        menuScrollState.scrollTo(centered.roundToInt())
    }

    // 項目名とプルダウンを横に並べて縦幅を詰める。プルダウンは "100%" と
    // 展開アイコンが収まる固定幅とし、余った幅を項目名に回す
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "ウィジェット背景の透過率",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.width(140.dp)
        ) {
            OutlinedTextField(
                value = widgetBgTransparencyLabel(step),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                scrollState = menuScrollState
            ) {
                for (candidate in 0 until WIDGET_BG_TRANSPARENCY_STEP_COUNT) {
                    DropdownMenuItem(
                        text = { Text(widgetBgTransparencyLabel(candidate)) },
                        onClick = {
                            onStepChange(clampWidgetBgTransparencyStep(candidate))
                            expanded = false
                        }
                    )
                }
            }
        }
    }
    Text(
        "0% は不透明（従来どおり）、100% で壁紙が完全に透ける。文字色は変わらない",
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * spec: docs/spec.md 5.7 警告しきい値（定額枠の残り時間、分）。
 * 「残り何分になったら警告色にするか」を分で入力する。刻みが 1 分と細かく、かつ
 * 「あと数分だけ動かしたい」調整が多いため、直接入力に +/- ボタンを添えた形にする。
 * 意味を持つのは月間定額型のときだけなので、それ以外ではグレーアウトする（定額枠と同じ扱い）。
 */
@Composable
private fun WarnRemainingSection(
    value: String,
    onValueChange: (String) -> Unit,
    range: IntRange?,
    error: String?,
    planType: PlanType
) {
    val current = value.trim().toIntOrNull()
    // 範囲の端では対応するボタンを無効化する。数値として読めない入力中は両方とも無効
    val canDecrease = range != null && current != null && current > range.first
    val canIncrease = range != null && current != null && current < range.last
    val enabled = range != null

    // 項目名・エラー・注記は行の外に出す。他の数値欄と同じく枠内ラベルと supportingText に
    // すると、+/- ボタンで狭くなった欄の中で 2 行に折り返してしまうため
    Text("警告しきい値（残り分）", style = MaterialTheme.typography.bodySmall)
    Row(verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−", enabled = canDecrease) {
            if (current != null) onValueChange((current - 1).toString())
        }
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = value,
            onValueChange = { onValueChange(it.filter(Char::isDigit)) },
            enabled = enabled,
            isError = error != null,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(110.dp)
        )
        Spacer(Modifier.width(8.dp))
        StepperButton("+", enabled = canIncrease) {
            if (current != null) onValueChange((current + 1).toString())
        }
        Spacer(Modifier.width(12.dp))
        if (enabled && current != null) {
            Text(
                // 入力値そのものの言い換え。単位（残り時間であること）を取り違えないようにする
                warnRemainingLabel(current),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
    val note = when {
        error != null -> error
        enabled -> null
        planType == PlanType.MONTHLY -> "定額枠が1分のため警告色は使用しない"
        else -> "${planTypeLabel(planType)}では使用しない"
    }
    if (note != null) {
        Text(
            note,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** +/- ボタン。タップターゲットの推奨最小 48dp を確保する */
@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(48.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * spec: docs/spec.md 5.5.3 / 5.7 ウィジェット背景色。
 * 通常色 / 警告色 / 超過色それぞれをプリセットパレットから選ぶ。任意色（カラーピッカー）に
 * しないのは、文字が読めない・状態が見分けられない配色を作れてしまうため。
 * 透過率（5.7）は 3 色共通のまま。
 */
@Composable
private fun WidgetColorSection(
    normalIndex: Int,
    warningIndex: Int,
    overIndex: Int,
    onNormalChange: (Int) -> Unit,
    onWarningChange: (Int) -> Unit,
    onOverChange: (Int) -> Unit
) {
    Text("ウィジェット背景色", style = MaterialTheme.typography.titleMedium)
    WidgetColorRow("通常色", normalIndex, onNormalChange)
    WidgetColorRow("警告色", warningIndex, onWarningChange)
    WidgetColorRow("超過色", overIndex, onOverChange)
    Text(
        "文字色は背景色の明るさから自動で決まる。透過率は3色共通",
        style = MaterialTheme.typography.bodySmall
    )
}

/**
 * 1 つの役割ぶんの色見本。8 色を等幅で並べるため個々の幅は weight に任せる
 * （固定幅にすると画面幅の狭い端末で溢れる）。選択中は枠とチェックで示す。
 */
@Composable
private fun WidgetColorRow(label: String, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Text(label, style = MaterialTheme.typography.bodySmall)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WIDGET_COLOR_PALETTE.forEachIndexed { index, palette ->
            val selected = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .background(Color(palette.argb), RoundedCornerShape(4.dp))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = RoundedCornerShape(4.dp)
                    )
                    .clickable { onSelect(index) }
            ) {
                // 枠だけでは選択中が分かりにくいため、色見本の上にチェックを重ねる。
                // 見本の色に対して読める文字色はウィジェット本体と同じ規則で決める
                if (selected) {
                    Text(
                        "✓",
                        color = Color(widgetTextColorOn(palette.argb)),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}

/** 数値入力欄。範囲外ならエラー表示にし、保存の可否は呼び出し元がまとめて判定する */
@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    disabledNote: String? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.filter(Char::isDigit)) },
        label = { Text(label, style = MaterialTheme.typography.bodySmall) },
        enabled = enabled,
        isError = error != null,
        supportingText = when {
            error != null -> {
                { Text(error, style = MaterialTheme.typography.bodySmall) }
            }
            !enabled && disabledNote != null -> {
                { Text(disabledNote, style = MaterialTheme.typography.bodySmall) }
            }
            else -> null
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

/**
 * spec: docs/spec.md 8 権限。未許可ならその場で要求できるようにし、
 * 許可済みでも OS のアプリ権限設定への導線は残す（誤って取り消した場合の復旧経路）。
 */
@Composable
private fun PermissionSection(
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit
) {
    Text("通話履歴の読み取り権限", style = MaterialTheme.typography.titleMedium)
    Text(
        if (hasPermission) "許可済み" else "未許可（集計できません）",
        style = MaterialTheme.typography.bodyMedium,
        color = if (hasPermission) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.error
        }
    )
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasPermission) {
            Button(onClick = onRequestPermission) { Text("許可する") }
        }
        OutlinedButton(onClick = onOpenAppSettings) { Text("アプリの権限設定を開く") }
    }
}

/**
 * spec: docs/spec.md 5.7 除外番号リスト。
 * 常時4行分の高さを占めないよう折りたたみ式にし、閉じている間は件数と先頭数件だけを見せる。
 */
@Composable
private fun ExcludePrefixesSection(
    text: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    onTextChange: (String) -> Unit,
    onReset: () -> Unit
) {
    val prefixes = parseExcludePrefixes(text)

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.titleMedium)
        Text(
            "除外番号リスト（${prefixes.size}件）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    if (!expanded) {
        Text(
            excludePrefixesPreview(prefixes),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
        )
        return
    }

    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text("改行区切り") },
        minLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    // 保存済みリストは初期値が更新されても自動では追従しないため（5.3.2 の初期値変更時など）、
    // 明示的に初期値へ戻す手段を用意する。押した時点では入力欄を書き換えるだけで、確定は「保存」。
    OutlinedButton(onClick = onReset) { Text("初期値に戻す") }
}

@Composable
private fun PlanTypeOption(
    value: PlanType,
    selected: PlanType,
    modifier: Modifier = Modifier,
    onSelect: (PlanType) -> Unit
) {
    // 3 択を 1 行に収めるため、ラジオの横ではなく下にラベルを置く。
    // 横並びだと「1通話定額型」がカラム幅に入らず 2 行に折り返してしまう
    Column(
        modifier = modifier.clickable { onSelect(value) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(
            planTypeLabel(value),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1
        )
    }
}

@Composable
private fun UnitSecOption(value: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text("${value}秒", style = MaterialTheme.typography.bodyMedium)
    }
}
