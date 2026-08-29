package io.github.eightbrows.CallTimeChecker.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import io.github.eightbrows.CallTimeChecker.BuildConfig
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.effectiveAppSettings
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.normalizeUnitSec
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes

/**
 * spec: docs/spec.md 5.7 設定項目（プラン形式・起算日・定額枠・通話別無料時間・課金単位・単位金額・除外番号リスト）。
 * 保存時に effectiveAppSettings() を適用するため、プラン形式による固定ルール（0固定）が
 * 永続化される値にも反映される。数値入力の妥当性検証・正規化は logic/AppSettings.kt の
 * 純粋関数（clampStartDay/normalizeUnitSec/parseExcludePrefixes、AppSettingsTest.kt でテスト済み）に委譲する。
 */
@Composable
fun SettingsScreen(
    current: AppSettings,
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

    val monthlyFreeEnabled = planType != PlanType.PER_CALL
    val perCallFreeEnabled = planType != PlanType.MONTHLY

    Column(
        modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("設定", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        Text("プラン形式", style = MaterialTheme.typography.titleMedium)
        PlanTypeOption(PlanType.MONTHLY, "月間定額型", planType) { planType = it }
        PlanTypeOption(PlanType.PER_CALL, "1通話定額型", planType) { planType = it }
        PlanTypeOption(PlanType.CUSTOM, "カスタム", planType) { planType = it }
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = startDayText,
            onValueChange = { startDayText = it.filter(Char::isDigit) },
            label = { Text("起算日（1〜31）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = if (monthlyFreeEnabled) monthlyFreeMinText else "0",
            onValueChange = { monthlyFreeMinText = it.filter(Char::isDigit) },
            label = { Text("定額枠（分）") },
            enabled = monthlyFreeEnabled,
            supportingText = if (!monthlyFreeEnabled) {
                { Text("1通話定額型のため0固定") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = if (perCallFreeEnabled) perCallFreeMinText else "0",
            onValueChange = { perCallFreeMinText = it.filter(Char::isDigit) },
            label = { Text("通話別無料時間（分）") },
            enabled = perCallFreeEnabled,
            supportingText = if (!perCallFreeEnabled) {
                { Text("月間定額型のため0固定") }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        Text("課金単位", style = MaterialTheme.typography.titleMedium)
        Row {
            UnitSecOption(30, unitSec) { unitSec = it }
            UnitSecOption(60, unitSec) { unitSec = it }
        }
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = unitPriceText,
            onValueChange = { unitPriceText = it.filter(Char::isDigit) },
            label = { Text("単位金額（円）") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = excludeText,
            onValueChange = { excludeText = it },
            label = { Text("除外番号リスト（改行区切り）") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) { Text("キャンセル") }
            Button(onClick = {
                val raw = AppSettings(
                    planType = planType,
                    startDay = clampStartDay(startDayText.toIntOrNull() ?: current.startDay),
                    monthlyFreeMin = monthlyFreeMinText.toIntOrNull() ?: current.monthlyFreeMin,
                    perCallFreeMin = perCallFreeMinText.toIntOrNull() ?: current.perCallFreeMin,
                    unitSec = normalizeUnitSec(unitSec),
                    unitPrice = unitPriceText.toIntOrNull() ?: current.unitPrice,
                    excludePrefixes = parseExcludePrefixes(excludeText)
                )
                onSave(effectiveAppSettings(raw))
            }) { Text("保存") }
        }
        Spacer(Modifier.height(16.dp))

        Text(
            "バージョン: ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PlanTypeOption(value: PlanType, label: String, selected: PlanType, onSelect: (PlanType) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(label)
    }
}

@Composable
private fun UnitSecOption(value: Int, selected: Int, onSelect: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text("${value}秒")
    }
}
