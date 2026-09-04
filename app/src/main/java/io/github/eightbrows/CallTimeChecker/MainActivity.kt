package io.github.eightbrows.CallTimeChecker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.CallDetail
import io.github.eightbrows.CallTimeChecker.logic.CallRecord
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.Result
import io.github.eightbrows.CallTimeChecker.logic.Settings
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.calculateDetails
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.formatMinutes
import io.github.eightbrows.CallTimeChecker.logic.planTypeLabel
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

/** spec: docs/spec.md 5.6 アプリ本体のタブ構成。宣言順が TabRow の並び順になる */
private enum class MainTab(val label: String) {
    SUMMARY("サマリ"),
    HISTORY("通話履歴")
}

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
    // 更新のたびに UiState.Loading を挟むため、選択中のタブは LoadedContent の外で保持する
    var mainTab by remember { mutableStateOf(MainTab.SUMMARY) }

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

    // OS のアプリ権限設定から戻ってきたときに許可状態を取り直す。
    // 設定画面の「アプリの権限設定を開く」導線で許可された場合、これがないと未許可のままになる
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
                PackageManager.PERMISSION_GRANTED
            if (granted != hasPermission) hasPermission = granted
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun openAppSettings() {
        val intent = Intent(
            AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
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
            hasPermission = hasPermission,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.READ_CALL_LOG) },
            onOpenAppSettings = { openAppSettings() },
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
                    planType = appSettings.planType,
                    zone = zone,
                    tab = mainTab,
                    onTabChange = { mainTab = it },
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

/**
 * spec: docs/spec.md 5.6 アプリ本体。
 * 手動更新・設定はどちらのタブからも押せる必要があるため、タブの中身の外（ヘッダ行）に置く。
 * タブの中に置くと履歴タブでリストと一緒にスクロールしてしまい、リストの縦幅も削ってしまう。
 */
@Composable
private fun LoadedContent(
    state: UiState.Loaded,
    planType: PlanType,
    zone: ZoneId,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    filter: BreakdownFilter,
    onFilterChange: (BreakdownFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "通話時間確認アプリ",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                // ボタン 2 つで幅を使うため、狭い端末ではタイトル側を省略して折り返さないようにする
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            HeaderButton("更新", onRefresh)
            HeaderButton("設定", onOpenSettings)
        }
        TabRow(selectedTabIndex = tab.ordinal) {
            for (entry in MainTab.entries) {
                Tab(
                    selected = tab == entry,
                    onClick = { onTabChange(entry) },
                    text = { Text(entry.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        when (tab) {
            MainTab.SUMMARY -> SummarySection(state.period, state.result, state.settings, planType, zone)
            MainTab.HISTORY -> HistoryTab(state, filter, onFilterChange)
        }
    }
}

/**
 * spec: docs/spec.md 5.6 ヘッダの操作ボタン。
 * TextButton の既定サイズ（58x40dp）はタップしづらいため、Material のタップターゲット
 * 推奨最小である 48dp の高さを確保し、背景色付き（FilledTonalButton）で押せることを明示する。
 */
@Composable
private fun HeaderButton(label: String, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * spec: docs/spec.md 5.6.1 / 5.6.2 履歴集計。
 * 通話履歴を見に行かなくても件数と実時間が分かるよう、サマリタブと通話履歴タブの両方に出す。
 */
@Composable
private fun HistorySummary(result: Result) {
    Text("履歴集計", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(4.dp))
    Text("通話件数: ${result.callCount}件（課金対象 ${result.billedCallCount}件）")
    Text("通話時間（実時間）: ${result.countedSec / 60}分${result.countedSec % 60}秒")
    Text("通話時間（除外）: ${result.excludedSec / 60}分${result.excludedSec % 60}秒")
}

/** spec: docs/spec.md 5.6.2 通話履歴タブ（履歴集計 + 通話履歴の内訳リスト） */
@Composable
private fun ColumnScope.HistoryTab(
    state: UiState.Loaded,
    filter: BreakdownFilter,
    onFilterChange: (BreakdownFilter) -> Unit
) {
    HistorySummary(state.result)
    Spacer(Modifier.height(8.dp))
    HorizontalDivider()
    Spacer(Modifier.height(8.dp))
    Text("通話履歴", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    BreakdownFilterRow(filter, onFilterChange)
    val filteredDetails = when (filter) {
        BreakdownFilter.ALL -> state.details
        BreakdownFilter.BILLED_ONLY -> state.details.filter { it.billedSec > 0 }
    }
    BreakdownList(filteredDetails, state.settings, modifier = Modifier.weight(1f))
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

/**
 * spec: docs/spec.md 5.6 サマリ。
 * サマリタブの中身。上段（プラン情報）+ 中段「現在の状況」（通話時間・通話金額）。
 * アプリ名はタブの外のヘッダ行に、履歴集計は履歴タブに置く。
 * 中段は「何を表す数字か」が一目で分かるよう、小さいラベル行と大きい値行のペアで並べる。
 * 表示項目はプラン形式ごとに変わるため、`monthlyFreeSec > 0` のような値の判定ではなく
 * PlanType で分岐する（5.4.2 のとおり両者は 1 対 1 に対応する）。
 */
@Composable
private fun SummarySection(
    period: Pair<Long, Long>,
    result: Result,
    settings: Settings,
    planType: PlanType,
    zone: ZoneId
) {
    val startDate = Instant.ofEpochMilli(period.first).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(period.second - 1).atZone(zone).toLocalDate()

    Column {
        Text("プラン: ${planTypeLabel(planType)}")
        Text("期間: ${PERIOD_FORMATTER.format(startDate)} - ${PERIOD_FORMATTER.format(endDate)}")
        // 無料枠はプラン形式ごとに意味が違うため、そのプランで有効な方だけを出す
        when (planType) {
            PlanType.MONTHLY -> Text("無料枠: ${settings.monthlyFreeSec / 60}分")
            PlanType.PER_CALL -> Text("1通話無料枠: ${settings.perCallFreeSec / 60}分")
            PlanType.PAY_AS_YOU_GO -> Unit
        }
        Text("単価: ${settings.unitSec}秒 / ${settings.unitPrice}円")

        SectionDivider()
        Text("現在の状況", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        // 通話時間の基準はウィジェット（5.5.1）と揃える。
        // 全プランとも、通話金額の計算根拠と一致させるため切り上げ後の課金枠の消費量
        // （quotaConsumedSec、5.4.4）を使う。実通話時間は「履歴集計」側に併記する。
        when (planType) {
            PlanType.MONTHLY -> {
                val remainingSec = (settings.monthlyFreeSec - result.quotaConsumedSec).coerceAtLeast(0)
                StatusItem(
                    label = "通話時間 / 無料枠残",
                    value = "${formatMinutes(result.quotaConsumedSec)} / ${formatMinutes(remainingSec)}分",
                    over = result.quotaConsumedSec > settings.monthlyFreeSec
                )
            }
            PlanType.PER_CALL -> StatusItem(
                label = "通話時間",
                value = "${formatMinutes(result.quotaConsumedSec)}分",
                // 1 通話ごとの無料時間を超えた通話があれば超過。枠残という概念は無い
                over = result.billedCallCount > 0
            )
            // 従量課金は無料枠が無く「超過」が定義できないため、バッジ自体を出さない
            PlanType.PAY_AS_YOU_GO -> StatusItem(
                label = "通話時間",
                value = "${formatMinutes(result.quotaConsumedSec)}分",
                over = null
            )
        }
        Spacer(Modifier.height(12.dp))
        StatusItem(label = "通話金額", value = "${result.amount}円", over = null)

        SectionDivider()
        HistorySummary(result)
    }
}

/** 「現在の状況」の 1 項目。小さいラベル行 + 大きい値行のペア。over が null ならバッジを出さない */
@Composable
private fun StatusItem(label: String, value: String, over: Boolean?) {
    Text(
        label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        if (over != null) {
            Spacer(Modifier.width(8.dp))
            OverBadge(over)
        }
    }
}

@Composable
private fun OverBadge(over: Boolean) {
    Surface(
        color = if (over) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        contentColor = if (over) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        shape = RoundedCornerShape(50)
    ) {
        Text(
            if (over) "超過あり" else "超過なし",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SectionDivider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider()
    Spacer(Modifier.height(12.dp))
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
    // ただしサマリの通話時間は切り上げ後の課金枠の消費量（quotaConsumedSec）基準のため、
    // 突き合わせられるよう各行に課金枠の秒数も併記する（実時間と一致しない通話がある）
    val judgement = when {
        detail.excluded -> "除外"
        record.durationSec == 0 -> "未応答"
        detail.billedSec > 0 -> "課金 ¥${detail.billedSec / settings.unitSec * settings.unitPrice}"
        else -> "定額内"
    }
    val judgementText = if (detail.quotaConsumedSec > 0) {
        "$judgement・課金枠 ${detail.quotaConsumedSec}秒"
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
