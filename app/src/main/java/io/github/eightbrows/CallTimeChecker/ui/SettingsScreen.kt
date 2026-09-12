package io.github.eightbrows.CallTimeChecker.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember

import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign

import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.eightbrows.CallTimeChecker.BuildConfig
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_EXCLUDE_PREFIXES
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.ThemeMode
import io.github.eightbrows.CallTimeChecker.logic.clampMonthlyFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampPerCallFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.WARN_REMAINING_MIN_MONTHLY_FREE_MIN
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_BG_TRANSPARENCY_STEP_COUNT
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_COLOR_PALETTE
import io.github.eightbrows.CallTimeChecker.logic.WidgetPaletteColor
import io.github.eightbrows.CallTimeChecker.logic.clampWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetColorIndex
import io.github.eightbrows.CallTimeChecker.logic.defaultWarnRemainingMin
import io.github.eightbrows.CallTimeChecker.logic.warnRemainingMinRange
import io.github.eightbrows.CallTimeChecker.logic.widgetTextColorOn
import io.github.eightbrows.CallTimeChecker.logic.UNIT_SEC_RANGE
import io.github.eightbrows.CallTimeChecker.logic.clampUnitPrice
import io.github.eightbrows.CallTimeChecker.logic.clampUnitSec
import io.github.eightbrows.CallTimeChecker.logic.clampWidgetBgTransparencyStep
import io.github.eightbrows.CallTimeChecker.logic.effectiveAppSettings
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesPreview
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.monthlyFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes
import io.github.eightbrows.CallTimeChecker.logic.perCallFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.validateRange
import io.github.eightbrows.CallTimeChecker.logic.widgetBgTransparencyLabel
import io.github.eightbrows.CallTimeChecker.logic.widgetPaletteArgb
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip

private const val LICENSE_URL =
    "https://github.com/eightbrows/CallTimeChecker/blob/master/LICENSE"
private const val OFFICIAL_SITE_URL = "https://eightbrows.github.io/"

/** 背景色パレットのダイアログの列数。行数はパレットの色数から決まる */
private const val PALETTE_COLUMNS = 5

/**
 * spec: docs/spec.md 5.7 設定画面。
 * 数値・選択項目は「左:項目名(小さめ)・右:値/コントロール」の統一フォーマットに揃える。
 * 例外はプラン形式とアプリの配色で、選択肢が横幅を要するため項目名を上に置き、
 * その下に横幅いっぱいの SegmentedControl を敷く。
 * 区切り線は項目分類が変わる箇所にのみ入れる(①契約内容 ②除外番号 ③ウィジェット表示
 * ④アプリの配色 ⑤権限 ⑥バージョン情報)。
 *
 * 課金単位(unitSec)は30/60をプリセットとして出しつつ、カスタム値も受け付ける。
 * 入力範囲はUNIT_SEC_RANGE(1〜300秒)で、検証もクランプもこの1箇所の定義を参照する。
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
    var unitSecText by remember { mutableStateOf(current.unitSec.toString()) }
    var unitPriceText by remember { mutableStateOf(current.unitPrice.toString()) }
    var widgetBgTransparencyStep by remember { mutableIntStateOf(current.widgetBgTransparencyStep) }
    var warnRemainingText by remember { mutableStateOf(current.warnRemainingMin.toString()) }
    var colorNormalIndex by remember { mutableIntStateOf(current.widgetColorNormalIndex) }
    var colorWarningIndex by remember { mutableIntStateOf(current.widgetColorWarningIndex) }
    var colorOverIndex by remember { mutableIntStateOf(current.widgetColorOverIndex) }
    var themeMode by remember { mutableStateOf(current.themeMode) }
    var excludeText by remember { mutableStateOf(excludePrefixesToText(current.excludePrefixes)) }
    var excludeExpanded by remember { mutableStateOf(false) }

    val monthlyFreeRange = monthlyFreeMinRange(planType)
    val perCallFreeRange = perCallFreeMinRange(planType)
    val monthlyFreeValue = monthlyFreeMinText.trim().toIntOrNull() ?: 0
    val warnRemainingRange = warnRemainingMinRange(planType, monthlyFreeValue)

    val startDayError = validateRange(startDayText, 1, 31)
    val monthlyFreeError = validateRange(monthlyFreeMinText, monthlyFreeRange)
    val perCallFreeError = validateRange(perCallFreeMinText, perCallFreeRange)
    val unitSecError = validateRange(unitSecText, UNIT_SEC_RANGE)
    val unitPriceError = validateRange(unitPriceText, 0, 999)
    val warnRemainingError = validateRange(warnRemainingText, warnRemainingRange)
    val canSave = startDayError == null && monthlyFreeError == null &&
            perCallFreeError == null && unitSecError == null && unitPriceError == null &&
            warnRemainingError == null

    fun selectPlanType(next: PlanType) {
        if (next == PlanType.MONTHLY && (monthlyFreeMinText.trim().toIntOrNull() ?: 0) == 0) {
            monthlyFreeMinText = DEFAULT_APP_SETTINGS.monthlyFreeMin.toString()
        }
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
            unitSec = clampUnitSec(unitSecText.trim().toIntOrNull() ?: current.unitSec),
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
            widgetColorOverIndex = clampWidgetColorIndex(colorOverIndex),
            themeMode = themeMode
        )
        return effectiveAppSettings(raw)
    }

    Column(modifier.fillMaxSize().imePadding()) {
        SettingsHeader(canSave = canSave, onCancel = onBack, onSave = { onSave(buildSettings()) })
        HorizontalDivider()
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            // --- ①契約内容 ---
            Spacer(Modifier.height(6.dp))
            SectionLabel(stringResource(R.string.settings_plan_type))
            SegmentedControl(
                options = PlanType.entries,
                selected = planType,
                label = { stringResource(it.labelRes()) },
                onSelect = { selectPlanType(it) }
            )
            // 0 固定の欄と警告しきい値の注記に埋め込む、現在のプラン形式の表示名
            val planLabel = stringResource(planType.labelRes())

            SteppedNumberRow(
                label = stringResource(R.string.label_start_day),
                value = startDayText,
                onValueChange = { startDayText = it },
                unit = stringResource(R.string.unit_day),
                error = startDayError?.message(),
                enabled = true,
                canDecrease = (startDayText.trim().toIntOrNull() ?: 1) > 1,
                canIncrease = (startDayText.trim().toIntOrNull() ?: 1) < 31,
                onDecrease = {
                    val v = startDayText.trim().toIntOrNull() ?: 1
                    startDayText = (v - 1).coerceAtLeast(1).toString()
                },
                onIncrease = {
                    val v = startDayText.trim().toIntOrNull() ?: 1
                    startDayText = (v + 1).coerceAtMost(31).toString()
                }
            )

            SteppedNumberRow(
                label = stringResource(R.string.settings_monthly_quota),
                value = if (monthlyFreeRange != null) monthlyFreeMinText else "0",
                onValueChange = { monthlyFreeMinText = it },
                unit = stringResource(R.string.unit_min),
                error = monthlyFreeError?.message(),
                enabled = monthlyFreeRange != null,
                canDecrease = monthlyFreeRange != null &&
                        (monthlyFreeMinText.trim().toIntOrNull() ?: 0) > monthlyFreeRange.first,
                canIncrease = monthlyFreeRange != null &&
                        (monthlyFreeMinText.trim().toIntOrNull() ?: 0) < monthlyFreeRange.last,
                onDecrease = {
                    val v = monthlyFreeMinText.trim().toIntOrNull() ?: 0
                    monthlyFreeMinText = (v - 1).toString()
                },
                onIncrease = {
                    val v = monthlyFreeMinText.trim().toIntOrNull() ?: 0
                    monthlyFreeMinText = (v + 1).toString()
                },
                disabledNote = stringResource(R.string.note_fixed_zero, planLabel)
            )

            SteppedNumberRow(
                label = stringResource(R.string.settings_per_call_free),
                value = if (perCallFreeRange != null) perCallFreeMinText else "0",
                onValueChange = { perCallFreeMinText = it },
                unit = stringResource(R.string.unit_min),
                error = perCallFreeError?.message(),
                enabled = perCallFreeRange != null,
                canDecrease = perCallFreeRange != null &&
                        (perCallFreeMinText.trim().toIntOrNull() ?: 0) > perCallFreeRange.first,
                canIncrease = perCallFreeRange != null &&
                        (perCallFreeMinText.trim().toIntOrNull() ?: 0) < perCallFreeRange.last,
                onDecrease = {
                    val v = perCallFreeMinText.trim().toIntOrNull() ?: 0
                    perCallFreeMinText = (v - 1).toString()
                },
                onIncrease = {
                    val v = perCallFreeMinText.trim().toIntOrNull() ?: 0
                    perCallFreeMinText = (v + 1).toString()
                },
                disabledNote = stringResource(R.string.note_fixed_zero, planLabel)
            )

            PresetOrCustomRow(
                label = stringResource(R.string.settings_unit_sec),
                presets = listOf(30, 60),
                presetLabel = { secondsText(it) },
                unitText = stringResource(R.string.unit_sec),
                unitBeforeField = false,
                valueText = unitSecText,
                onValueChange = { unitSecText = it },
                error = unitSecError?.message()
            )

            PresetOrCustomRow(
                label = stringResource(R.string.settings_unit_price),
                presets = listOf(11, 22),
                presetLabel = { yenText(it) },
                unitText = stringResource(R.string.unit_yen),
                // 英語では ¥ を数字の前に置く（5.8）
                unitBeforeField = booleanResource(R.bool.yen_before_value),
                valueText = unitPriceText,
                onValueChange = { unitPriceText = it },
                error = unitPriceError?.message()
            )

            GroupDivider()

            // --- ②除外番号リスト ---
            ExcludePrefixesSection(
                text = excludeText,
                expanded = excludeExpanded,
                onToggle = { excludeExpanded = !excludeExpanded },
                onTextChange = { excludeText = it },
                onReset = { excludeText = excludePrefixesToText(DEFAULT_EXCLUDE_PREFIXES) }
            )

            GroupDivider()

            // --- ③ウィジェット表示 ---
            WidgetColorSection(
                normalIndex = colorNormalIndex,
                warningIndex = colorWarningIndex,
                overIndex = colorOverIndex,
                onNormalChange = { colorNormalIndex = it },
                onWarningChange = { colorWarningIndex = it },
                onOverChange = { colorOverIndex = it }
            )

            SteppedNumberRow(
                label = stringResource(R.string.settings_warn_threshold),
                value = warnRemainingText,
                onValueChange = { warnRemainingText = it },
                unit = stringResource(R.string.unit_min),
                error = warnRemainingError?.message(),
                enabled = warnRemainingRange != null,
                canDecrease = warnRemainingRange != null &&
                        (warnRemainingText.trim().toIntOrNull() ?: 0) > warnRemainingRange.first,
                canIncrease = warnRemainingRange != null &&
                        (warnRemainingText.trim().toIntOrNull() ?: 0) < warnRemainingRange.last,
                onDecrease = {
                    val v = warnRemainingText.trim().toIntOrNull() ?: 0
                    warnRemainingText = (v - 1).toString()
                },
                onIncrease = {
                    val v = warnRemainingText.trim().toIntOrNull() ?: 0
                    warnRemainingText = (v + 1).toString()
                },
                // 月間定額型で無効になるのは定額枠が WARN_REMAINING_MIN_MONTHLY_FREE_MIN 未満のとき
                // （定額枠欄が空・0 のときも含む）。特定の分数を決め打ちで書かない
                disabledNote = if (planType == PlanType.MONTHLY) {
                    stringResource(R.string.note_warn_quota_too_small, WARN_REMAINING_MIN_MONTHLY_FREE_MIN)
                } else {
                    stringResource(R.string.note_not_used_for_plan, planLabel)
                }
            )

            WidgetBgTransparencySection(widgetBgTransparencyStep) { widgetBgTransparencyStep = it }

            GroupDivider()

            // --- ④アプリの配色 ---
            SectionLabel(stringResource(R.string.settings_theme))
            SegmentedControl(
                options = ThemeMode.entries,
                selected = themeMode,
                label = { stringResource(it.labelRes()) },
                onSelect = { themeMode = it }
            )

            GroupDivider()

            // --- ⑤権限 ---
            PermissionRow(hasPermission, onRequestPermission, onOpenAppSettings)

            GroupDivider()

            // --- ⑥バージョン情報 ---
            AboutSection()
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun GroupDivider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
}

/** SegmentedControl の上に置く項目名。選択肢が横幅を要する項目だけ、項目名を上の行に出す */
@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SettingsHeader(canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            stringResource(R.string.action_settings),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        OutlinedButton(onClick = onCancel, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_cancel), style = MaterialTheme.typography.titleMedium)
        }
        Button(onClick = onSave, enabled = canSave, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.action_save), style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * N択の排他選択を横並びの帯(セグメントコントロール)で表す汎用コンポーネント。
 * label は選択肢ごとの表示名。stringResource() を呼べるよう @Composable にしてある
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
    ) {
        options.forEachIndexed { index, option ->
            if (index > 0) {
                Box(
                    Modifier.width(1.dp).height(24.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(option) }
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label(option),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

/** [−][+]を隣接配置し、右に幅固定の入力欄+単位を置く共通の数値行 */
@Composable
private fun SteppedNumberRow(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    unit: String,
    error: String?,
    enabled: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    disabledNote: String? = null
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter(Char::isDigit)) },
                enabled = enabled,
                isError = error != null,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(72.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            StepperButton("+", enabled = enabled && canIncrease, onClick = onIncrease)
            Spacer(Modifier.width(16.dp))
            StepperButton("−", enabled = enabled && canDecrease, onClick = onDecrease)
            Spacer(Modifier.width(12.dp))
        }
        val note = error ?: disabledNote.takeIf { !enabled }
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
private fun StepperButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    FilledTonalIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(36.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge)
    }
}

/**
 * プリセット値(N個)＋カスタム入力を1行に収める。
 *
 * 「カスタム欄を選んでいるか」は値がプリセットと一致するかでは決めず、独立した状態として持つ。
 * 値の一致で判定すると、入力途中の値がたまたまプリセットと一致した瞬間に選択がプリセット側へ
 * 移ってカスタム欄がクリアされ、続きの桁を打てなくなる(「300」を打つ途中の「30」など)。
 * プリセットのラジオを明示的に押したときだけカスタム選択を解除する。
 *
 * presetLabel はプリセット値の表示（`30秒` / `¥11` など、単位込みの書式は言語側で決まる）。
 * unitText はカスタム欄の横に出す単位で、unitBeforeField が true なら欄の前に置く（英語の ¥、5.8）。
 */
@Composable
private fun PresetOrCustomRow(
    label: String,
    presets: List<Int>,
    presetLabel: @Composable (Int) -> String,
    unitText: String,
    unitBeforeField: Boolean,
    valueText: String,
    onValueChange: (String) -> Unit,
    error: String?
) {
    val currentValue = valueText.trim().toIntOrNull()
    // 初期状態だけは保存値から決める(カスタム値で保存されていれば開いた時点でカスタム選択)。
    // 値側(valueText)は呼び出し元が remember で持ち画面回転で current から作り直されるため、
    // こちらも同じ寿命の remember にそろえる。rememberSaveable にすると回転後に
    // 「カスタム選択のまま値だけプリセットに戻る」不整合が起きる
    var isCustomSelected by remember {
        mutableStateOf(currentValue == null || currentValue !in presets)
    }

    fun selectPreset(preset: Int) {
        isCustomSelected = false
        onValueChange(preset.toString())
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                presets.forEach { preset ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { selectPreset(preset) }
                    ) {
                        RadioButton(
                            selected = !isCustomSelected && currentValue == preset,
                            onClick = { selectPreset(preset) },
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(presetLabel(preset), style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // ラジオを押すと、直前のプリセット値を引き継いだ状態でカスタム欄の編集に移る
                    RadioButton(
                        selected = isCustomSelected,
                        onClick = { isCustomSelected = true },
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    if (unitBeforeField) {
                        UnitText(unitText, Modifier.padding(end = 2.dp))
                    }
                    OutlinedTextField(
                        value = if (isCustomSelected) valueText else "",
                        onValueChange = {
                            isCustomSelected = true
                            onValueChange(it.filter(Char::isDigit))
                        },
                        singleLine = true,
                        isError = error != null,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.End),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        // 3 桁（上限 300）が内側余白 16dp × 2 の内側に収まる幅
                        modifier = Modifier.width(68.dp)
                    )
                    if (!unitBeforeField) {
                        UnitText(unitText, Modifier.padding(start = 2.dp))
                    }
                }
            }
        }
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** 入力欄の横に出す単位（`分` / `min` / `¥`） */
@Composable
private fun UnitText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Composable
private fun WidgetColorSection(
    normalIndex: Int,
    warningIndex: Int,
    overIndex: Int,
    onNormalChange: (Int) -> Unit,
    onWarningChange: (Int) -> Unit,
    onOverChange: (Int) -> Unit
) {
    var editing by remember { mutableStateOf<WidgetColorRole?>(null) }
    val selectedIndex = { role: WidgetColorRole ->
        when (role) {
            WidgetColorRole.NORMAL -> normalIndex
            WidgetColorRole.WARNING -> warningIndex
            WidgetColorRole.OVER -> overIndex
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_widget_colors),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        WidgetColorRole.entries.forEachIndexed { position, role ->
            if (position > 0) {
                Text("→", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
            }
            WidgetColorChip(stringResource(role.labelRes), selectedIndex(role)) { editing = role }
        }
    }

    val role = editing
    if (role != null) {
        WidgetColorPickerDialog(
            role = role,
            selectedIndex = selectedIndex(role),
            onSelect = { index ->
                when (role) {
                    WidgetColorRole.NORMAL -> onNormalChange(index)
                    WidgetColorRole.WARNING -> onWarningChange(index)
                    WidgetColorRole.OVER -> onOverChange(index)
                }
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

private enum class WidgetColorRole(@StringRes val labelRes: Int) {
    NORMAL(R.string.color_role_normal),
    WARNING(R.string.color_role_warning),
    OVER(R.string.color_role_over)
}

@Composable
private fun WidgetColorChip(label: String, selectedIndex: Int, onClick: () -> Unit) {
    // 直接添字を引かず widgetPaletteArgb() を通す。範囲外の添字でも例外にならない
    val argb = widgetPaletteArgb(selectedIndex)
    val bg = Color(argb)
    val textColor = Color(widgetTextColorOn(argb))
    Box(
        modifier = Modifier
            .background(bg, RoundedCornerShape(4.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = textColor)
    }
}

/** 背景色パレット。PALETTE_COLUMNS 列で折り返して並べる（10 色なら 5 列 2 行） */
@Composable
private fun WidgetColorPickerDialog(
    role: WidgetColorRole,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_pick_color, stringResource(role.labelRes))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WIDGET_COLOR_PALETTE.chunked(PALETTE_COLUMNS).forEachIndexed { row, colors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        colors.forEachIndexed { column, palette ->
                            val index = row * PALETTE_COLUMNS + column
                            WidgetColorSwatch(
                                palette = palette,
                                selected = index == selectedIndex,
                                modifier = Modifier.weight(1f)
                            ) { onSelect(index) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } }
    )
}

@Composable
private fun WidgetColorSwatch(
    palette: WidgetPaletteColor,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(modifier = modifier.clickable { onClick() }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .background(Color(palette.argb), RoundedCornerShape(4.dp))
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(4.dp)
                )
        ) {
            if (selected) {
                Text("✓", color = Color(widgetTextColorOn(palette.argb)), style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text(
            stringResource(palette.name.labelRes()),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WidgetBgTransparencySection(step: Int, onStepChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val menuScrollState = rememberScrollState()
    val itemHeightPx = with(LocalDensity.current) { 48.dp.toPx() }

    LaunchedEffect(expanded) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { menuScrollState.maxValue }.first { it > 0 }
        val centered = itemHeightPx * step - (menuScrollState.viewportSize - itemHeightPx) / 2f
        menuScrollState.scrollTo(centered.roundToInt())
    }

    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            stringResource(R.string.settings_transparency),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.width(120.dp)) {
            OutlinedTextField(
                value = widgetBgTransparencyLabel(step),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth()
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, scrollState = menuScrollState) {
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
}

/** 権限行。行全体タップで、未許可なら要求ダイアログ、許可済みならOS設定画面を開く */
@Composable
private fun PermissionRow(hasPermission: Boolean, onRequestPermission: () -> Unit, onOpenAppSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { if (hasPermission) onOpenAppSettings() else onRequestPermission() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            stringResource(R.string.settings_call_log_permission),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            stringResource(if (hasPermission) R.string.permission_granted else R.string.permission_denied),
            style = MaterialTheme.typography.bodyMedium,
            color = if (hasPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
        )
    }
}

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
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(if (expanded) "▼" else "▶", style = MaterialTheme.typography.bodyMedium)
        Text(
            stringResource(R.string.settings_excluded_numbers, prefixes.size),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
    if (!expanded) {
        Text(
            excludePrefixesPreviewText(prefixes),
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
        label = { Text(stringResource(R.string.excluded_hint)) },
        minLines = 4,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onReset) { Text(stringResource(R.string.action_reset_defaults)) }
}

/**
 * spec: docs/spec.md 5.7 折りたたみ時の除外番号の要約（`0570, 0180, 0990 ほか7件`）。
 * 先頭件数と残り件数の切り分けは logic（excludePrefixesPreview）、文言はここでリソースから付ける
 */
@Composable
private fun excludePrefixesPreviewText(prefixes: List<String>): String {
    if (prefixes.isEmpty()) return stringResource(R.string.excluded_none)
    val preview = excludePrefixesPreview(prefixes)
    val head = preview.head.joinToString(", ")
    return if (preview.rest > 0) stringResource(R.string.fmt_and_more, head, preview.rest) else head
}

@Composable
private fun AboutSection() {
    val context = LocalContext.current

    fun openInBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
        }
    }

    AboutRow(stringResource(R.string.about_version), BuildConfig.VERSION_NAME)
    AboutRow(stringResource(R.string.about_license), stringResource(R.string.about_license_value)) {
        openInBrowser(LICENSE_URL)
    }
    AboutRow(stringResource(R.string.about_website), stringResource(R.string.about_website_value)) {
        openInBrowser(OFFICIAL_SITE_URL)
    }
}

@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .heightIn(min = if (onClick == null) 0.dp else 40.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = if (onClick == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
        )
    }
}