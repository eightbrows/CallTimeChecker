package io.github.eightbrows.CallTimeChecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.CallDetail
import io.github.eightbrows.CallTimeChecker.logic.CallRecord
import io.github.eightbrows.CallTimeChecker.logic.Result
import io.github.eightbrows.CallTimeChecker.logic.Settings
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.calculateDetails
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.toBillingSettings
import io.github.eightbrows.CallTimeChecker.ui.SettingsScreen
import io.github.eightbrows.CallTimeChecker.ui.theme.CallTimeCheckerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dbHelper = CallRecordDbHelper(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            CallTimeCheckerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CallTimeCheckerApp(
                        dbHelper = dbHelper,
                        settingsRepository = settingsRepository,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private sealed interface Screen {
    data object Main : Screen
    data object Settings : Screen
}

/** spec: docs/spec.md 12 未決事項「内訳リストに『課金対象のみ / 全件』フィルタ機能」。UI表示上の絞り込みのみで Billing.kt には影響しない */
private enum class BreakdownFilter { ALL, BILLED_ONLY }

private sealed interface UiState {
    data object NoPermission : UiState
    data object Loading : UiState
    data class Loaded(
        val period: Pair<Long, Long>,
        val result: Result,
        val details: List<CallDetail>,
        val settings: Settings
    ) : UiState
    data class Error(val message: String) : UiState
}

/** spec: docs/spec.md 5.6 アプリ本体（サマリ表示・内訳リスト・権限要求・設定画面への遷移） */
@Composable
private fun CallTimeCheckerApp(
    dbHelper: CallRecordDbHelper,
    settingsRepository: SettingsRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    var appSettings by remember { mutableStateOf(settingsRepository.load()) }
    var breakdownFilter by remember { mutableStateOf(BreakdownFilter.ALL) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var state by remember {
        mutableStateOf<UiState>(if (hasPermission) UiState.Loading else UiState.NoPermission)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        state = if (granted) UiState.Loading else UiState.NoPermission
    }

    suspend fun refresh() {
        state = UiState.Loading
        try {
            withContext(Dispatchers.IO) {
                CallLogSync(context.contentResolver, dbHelper).sync()
            }
            val billingSettings = toBillingSettings(appSettings)
            val period = currentPeriod(appSettings.startDay, zone)
            val records = withContext(Dispatchers.IO) {
                dbHelper.queryRange(period.first, period.second)
            }
            val result = calculate(records, billingSettings)
            val details = calculateDetails(records, billingSettings)
            state = UiState.Loaded(period, result, details, billingSettings)
        } catch (e: Exception) {
            state = UiState.Error(e.message ?: "更新に失敗しました")
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) refresh()
    }

    // 設定画面はバックスタックを使わない自前の画面遷移のため、Backキー/ジェスチャーバックを
    // ここで受け取って一覧画面へ戻す（有効なのは設定画面表示中のみ）
    BackHandler(enabled = screen is Screen.Settings) {
        screen = Screen.Main
    }

    when (screen) {
        is Screen.Settings -> SettingsScreen(
            current = appSettings,
            onSave = { updated ->
                appSettings = updated
                settingsRepository.save(updated)
                screen = Screen.Main
                scope.launch { refresh() }
            },
            onBack = { screen = Screen.Main },
            modifier = modifier
        )
        is Screen.Main -> Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
            when (val s = state) {
                is UiState.NoPermission -> PermissionRequest {
                    permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
                }
                is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is UiState.Error -> ErrorView(s.message) { scope.launch { refresh() } }
                is UiState.Loaded -> LoadedContent(
                    state = s,
                    zone = zone,
                    filter = breakdownFilter,
                    onFilterChange = { breakdownFilter = it },
                    onRefresh = { scope.launch { refresh() } },
                    onOpenSettings = { screen = Screen.Settings }
                )
            }
        }
    }
}

/** spec: docs/spec.md 5.6 権限（未許可時に要求ボタンを表示） */
@Composable
private fun PermissionRequest(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("通話時間を集計するには、通話履歴の読み取り権限が必要です")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRequest) { Text("権限を許可") }
    }
}

@Composable
private fun ErrorView(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("更新に失敗しました: $message")
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text("再試行") }
    }
}

@Composable
private fun LoadedContent(
    state: UiState.Loaded,
    zone: ZoneId,
    filter: BreakdownFilter,
    onFilterChange: (BreakdownFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        SummarySection(state.period, state.result, state.settings, zone)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onRefresh) { Text("手動更新") }
            Button(onClick = onOpenSettings) { Text("設定") }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        BreakdownFilterRow(filter, onFilterChange)
        val filteredDetails = when (filter) {
            BreakdownFilter.ALL -> state.details
            BreakdownFilter.BILLED_ONLY -> state.details.filter { it.billedSec > 0 }
        }
        BreakdownList(filteredDetails, state.settings, modifier = Modifier.weight(1f))
    }
}

/** spec: docs/spec.md 12 未決事項「内訳リストに『課金対象のみ / 全件』フィルタ機能」 */
@Composable
private fun BreakdownFilterRow(filter: BreakdownFilter, onFilterChange: (BreakdownFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterOptionButton("全件", selected = filter == BreakdownFilter.ALL) {
            onFilterChange(BreakdownFilter.ALL)
        }
        FilterOptionButton("課金対象のみ", selected = filter == BreakdownFilter.BILLED_ONLY) {
            onFilterChange(BreakdownFilter.BILLED_ONLY)
        }
    }
}

@Composable
private fun FilterOptionButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private val PERIOD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

/** spec: docs/spec.md 5.6 サマリ（積算通話時間、定額枠、残り時間、概算料金、集計期間） */
@Composable
private fun SummarySection(period: Pair<Long, Long>, result: Result, settings: Settings, zone: ZoneId) {
    val startDate = Instant.ofEpochMilli(period.first).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(period.second - 1).atZone(zone).toLocalDate()
    val quotaSec = settings.monthlyFreeSec
    // ウィジェット（5.5.1）と数字が食い違わないよう、同じ基準で分子を選ぶ。
    // 月間定額型は切り上げ後の枠消費量、1通話定額型は枠が無いため実通話時間。
    val usedSec = if (quotaSec > 0) result.quotaConsumedSec else result.countedSec
    val remainingSec = (quotaSec - usedSec).coerceAtLeast(0)

    Column {
        Text(
            "${PERIOD_FORMATTER.format(startDate)} - ${PERIOD_FORMATTER.format(endDate)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${usedSec / 60}分 / ${quotaSec / 60}分",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("¥${result.amount}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("残り: ${remainingSec / 60}分")
        Text("実通話時間: ${result.countedSec / 60}分${result.countedSec % 60}秒")
        Text("通話件数: ${result.callCount}件（課金対象 ${result.billedCallCount}件）")
        Text("除外通話時間: ${result.excludedSec / 60}分${result.excludedSec % 60}秒")
    }
}

/** spec: docs/spec.md 5.6 内訳リスト（日時、番号、通話時間、判定結果、課金対象秒数） */
@Composable
private fun BreakdownList(details: List<CallDetail>, settings: Settings, modifier: Modifier = Modifier) {
    if (details.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("該当する通話はありません")
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(details.sortedByDescending { it.record.dateMillis }) { detail ->
            BreakdownRow(detail, settings)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BreakdownRow(detail: CallDetail, settings: Settings) {
    val zone = remember { ZoneId.systemDefault() }
    val record: CallRecord = detail.record
    val dateTime = remember(record.dateMillis) {
        Instant.ofEpochMilli(record.dateMillis).atZone(zone).toLocalDateTime()
    }
    // 判定は「実際に料金が発生したか」なので billedSec 基準のまま。
    // ただしサマリの「使用」分数は切り上げ後の枠消費量（quotaConsumedSec）基準になったため、
    // 突き合わせられるよう各行に枠消費量も併記する（実時間と一致しない通話がある）
    val judgement = when {
        detail.excluded -> "除外"
        record.durationSec == 0 -> "未応答"
        detail.billedSec > 0 -> "課金 ¥${detail.billedSec / settings.unitSec * settings.unitPrice}"
        else -> "定額内"
    }
    val judgementText = if (detail.quotaConsumedSec > 0) {
        "$judgement・枠消費 ${detail.quotaConsumedSec}秒"
    } else {
        judgement
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(DATETIME_FORMATTER.format(dateTime))
            Text(record.number ?: "非通知", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${record.durationSec}秒")
            Text(judgementText, style = MaterialTheme.typography.bodySmall)
        }
    }
}
