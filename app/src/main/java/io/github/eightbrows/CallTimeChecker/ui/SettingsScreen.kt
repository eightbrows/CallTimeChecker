package io.github.eightbrows.CallTimeChecker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import io.github.eightbrows.CallTimeChecker.logic.clampUnitPrice
import io.github.eightbrows.CallTimeChecker.logic.effectiveAppSettings
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesPreview
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.monthlyFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.normalizeUnitSec
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes
import io.github.eightbrows.CallTimeChecker.logic.perCallFreeMinRange
import io.github.eightbrows.CallTimeChecker.logic.planTypeLabel
import io.github.eightbrows.CallTimeChecker.logic.validateRange

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
    var excludeText by remember { mutableStateOf(excludePrefixesToText(current.excludePrefixes)) }
    var excludeExpanded by remember { mutableStateOf(false) }

    // 範囲が null のプラン形式では 0 固定（グレーアウト）。validateRange も検証をスキップする
    val monthlyFreeRange = monthlyFreeMinRange(planType)
    val perCallFreeRange = perCallFreeMinRange(planType)

    val startDayError = validateRange(startDayText, 1, 31)
    val monthlyFreeError = validateRange(monthlyFreeMinText, monthlyFreeRange)
    val perCallFreeError = validateRange(perCallFreeMinText, perCallFreeRange)
    val unitPriceError = validateRange(unitPriceText, 0, 999)
    val canSave = startDayError == null && monthlyFreeError == null &&
        perCallFreeError == null && unitPriceError == null

    // プラン形式を切り替えると 0 固定だった欄が有効になる。値が 0 のままだと下限 1 を
    // 満たさず即エラーになるため、その場合だけ初期値を入れておく
    fun selectPlanType(next: PlanType) {
        if (next == PlanType.MONTHLY && (monthlyFreeMinText.trim().toIntOrNull() ?: 0) == 0) {
            monthlyFreeMinText = DEFAULT_APP_SETTINGS.monthlyFreeMin.toString()
        }
        if (next == PlanType.PER_CALL && (perCallFreeMinText.trim().toIntOrNull() ?: 0) == 0) {
            perCallFreeMinText = DEFAULT_APP_SETTINGS.perCallFreeMin.toString()
        }
        planType = next
    }

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))

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
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("キャンセル") }
            Button(
                enabled = canSave,
                onClick = {
                    // canSave のときのみ到達するため clamp は保険。値は既に範囲内
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
                        excludePrefixes = parseExcludePrefixes(excludeText)
                    )
                    onSave(effectiveAppSettings(raw))
                }
            ) { Text("保存") }
        }
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
