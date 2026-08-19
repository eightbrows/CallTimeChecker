package io.github.eightbrows.CallTimeChecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
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
import io.github.eightbrows.CallTimeChecker.logic.CallDetail
import io.github.eightbrows.CallTimeChecker.logic.CallRecord
import io.github.eightbrows.CallTimeChecker.logic.Result
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.calculateDetails
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
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

        setContent {
            CallTimeCheckerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CallTimeCheckerApp(
                        dbHelper = dbHelper,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

private sealed interface UiState {
    data object NoPermission : UiState
    data object Loading : UiState
    data class Loaded(
        val period: Pair<Long, Long>,
        val result: Result,
        val details: List<CallDetail>
    ) : UiState
    data class Error(val message: String) : UiState
}

/** spec: docs/spec.md 5.6 アプリ本体（サマリ表示・内訳リスト・権限要求） */
@Composable
private fun CallTimeCheckerApp(dbHelper: CallRecordDbHelper, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

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
            val period = currentPeriod(DEFAULT_START_DAY, zone)
            val records = withContext(Dispatchers.IO) {
                dbHelper.queryRange(period.first, period.second)
            }
            val result = calculate(records, DEFAULT_SETTINGS)
            val details = calculateDetails(records, DEFAULT_SETTINGS)
            state = UiState.Loaded(period, result, details)
        } catch (e: Exception) {
            state = UiState.Error(e.message ?: "更新に失敗しました")
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) refresh()
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        when (val s = state) {
            is UiState.NoPermission -> PermissionRequest {
                permissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
            }
            is UiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is UiState.Error -> ErrorView(s.message) { scope.launch { refresh() } }
            is UiState.Loaded -> LoadedContent(s, zone) { scope.launch { refresh() } }
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
private fun LoadedContent(state: UiState.Loaded, zone: ZoneId, onRefresh: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        SummarySection(state.period, state.result, zone)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRefresh) { Text("手動更新") }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        BreakdownList(state.details, modifier = Modifier.weight(1f))
    }
}

private val PERIOD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

/** spec: docs/spec.md 5.6 サマリ（積算通話時間、定額枠、残り時間、概算料金、集計期間） */
@Composable
private fun SummarySection(period: Pair<Long, Long>, result: Result, zone: ZoneId) {
    val startDate = Instant.ofEpochMilli(period.first).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(period.second - 1).atZone(zone).toLocalDate()
    val quotaSec = DEFAULT_SETTINGS.monthlyFreeSec
    val remainingSec = (quotaSec - result.countedSec).coerceAtLeast(0)

    Column {
        Text(
            "${PERIOD_FORMATTER.format(startDate)} - ${PERIOD_FORMATTER.format(endDate)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${result.countedSec / 60}分 / ${quotaSec / 60}分",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text("¥${result.amount}", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("残り: ${remainingSec / 60}分")
        Text("通話件数: ${result.callCount}件（課金対象 ${result.billedCallCount}件）")
        Text("除外通話時間: ${result.excludedSec / 60}分${result.excludedSec % 60}秒")
    }
}

/** spec: docs/spec.md 5.6 内訳リスト（日時、番号、通話時間、判定結果、課金対象秒数） */
@Composable
private fun BreakdownList(details: List<CallDetail>, modifier: Modifier = Modifier) {
    if (details.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("この期間の通話はありません")
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(details.sortedByDescending { it.record.dateMillis }) { detail ->
            BreakdownRow(detail)
            HorizontalDivider()
        }
    }
}

@Composable
private fun BreakdownRow(detail: CallDetail) {
    val zone = remember { ZoneId.systemDefault() }
    val record: CallRecord = detail.record
    val dateTime = remember(record.dateMillis) {
        Instant.ofEpochMilli(record.dateMillis).atZone(zone).toLocalDateTime()
    }
    val judgement = when {
        detail.excluded -> "除外"
        record.durationSec == 0 -> "未応答"
        detail.billedSec > 0 -> "課金 ¥${detail.billedSec / DEFAULT_SETTINGS.unitSec * DEFAULT_SETTINGS.unitPrice}"
        else -> "定額内"
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
            Text(judgement, style = MaterialTheme.typography.bodySmall)
        }
    }
}
