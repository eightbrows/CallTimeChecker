package io.github.eightbrows.CallTimeChecker

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.CallDetail
import io.github.eightbrows.CallTimeChecker.logic.CallRecord
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.Result
import io.github.eightbrows.CallTimeChecker.logic.Settings
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.calculateDetails
import io.github.eightbrows.CallTimeChecker.logic.formatAmount
import io.github.eightbrows.CallTimeChecker.logic.formatMinutes
import io.github.eightbrows.CallTimeChecker.logic.periodMonth
import io.github.eightbrows.CallTimeChecker.logic.periodOf
import io.github.eightbrows.CallTimeChecker.logic.planTypeLabel
import io.github.eightbrows.CallTimeChecker.logic.toBillingSettings
import io.github.eightbrows.CallTimeChecker.ui.SettingsScreen
import io.github.eightbrows.CallTimeChecker.ui.theme.CallTimeCheckerTheme
import io.github.eightbrows.CallTimeChecker.ui.theme.shouldUseDarkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import io.github.eightbrows.CallTimeChecker.ui.SegmentedControl

import androidx.compose.ui.graphics.Color

/**
 * enableEdgeToEdge() の既定値と同じナビゲーションバーのスクリム。
 * 定数自体は androidx.activity の非公開値のため、同じ色をここに置く
 * （システムバーのアイコン色だけを差し替えたいので、他は既定のまま揃える）。
 */
private const val NAV_BAR_LIGHT_SCRIM = 0xE6FFFFFF.toInt()
private const val NAV_BAR_DARK_SCRIM = 0x801B1B1B.toInt()
private const val SYSTEM_BAR_TRANSPARENT = 0

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val dbHelper = CallRecordDbHelper(applicationContext)
        val settingsRepository = SettingsRepository(applicationContext)

        setContent {
            // spec 5.7: 配色（themeMode）はテーマの引数なので、設定値の状態は
            // CallTimeCheckerTheme より外側で持つ必要がある。設定画面から書き換わった値を
            // そのまま画面側でも使うため、MutableState のまま CallTimeCheckerApp へ渡す
            val appSettingsState = remember { mutableStateOf(settingsRepository.load()) }
            val darkTheme = shouldUseDarkTheme(appSettingsState.value.themeMode)

            // enableEdgeToEdge() はシステムバーのアイコン色を端末のダークテーマ設定から決めるため、
            // 端末がライトでアプリだけダーク（およびその逆）のときにアイコンが背景に埋もれる。
            // 選ばれた配色を渡して呼び直す
            LaunchedEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        SYSTEM_BAR_TRANSPARENT, SYSTEM_BAR_TRANSPARENT
                    ) { darkTheme },
                    navigationBarStyle = SystemBarStyle.auto(
                        NAV_BAR_LIGHT_SCRIM, NAV_BAR_DARK_SCRIM
                    ) { darkTheme }
                )
            }

            CallTimeCheckerTheme(darkTheme = darkTheme) {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CallTimeCheckerApp(
                        dbHelper = dbHelper,
                        settingsRepository = settingsRepository,
                        appSettingsState = appSettingsState,
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
    appSettingsState: MutableState<AppSettings>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val zone = remember { ZoneId.systemDefault() }

    var screen by remember { mutableStateOf<Screen>(Screen.Main) }
    // 状態の持ち主は MainActivity（配色をテーマに渡すため）。ここでは読み書きするだけ
    var appSettings by appSettingsState
    var breakdownFilter by remember { mutableStateOf(BreakdownFilter.ALL) }
    // spec 5.6.1 月送り。今月からの相対位置（0 = 今月、-1 = 前月…）で保持する。
    // 画面の状態としてだけ持つため、アプリを終了して開き直すと今月に戻る
    var monthOffset by remember { mutableIntStateOf(0) }
    // 更新のたびに UiState.Loading を挟むため、選択中のタブは LoadedContent の外で保持する
    val pagerState = rememberPagerState(pageCount = { MainTab.entries.size })

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
            // 期間は開始月と 1 対 1 に対応するので、月送りは開始月をずらして表す（5.2）。
            // 基準日そのものを LocalDate.plusMonths() でずらすと、起算日が月末に
            // クランプされる月で前月が今月と同じ期間になってしまう
            val month = periodMonth(appSettings.startDay, LocalDate.now(zone))
                .plusMonths(monthOffset.toLong())
            val period = periodOf(appSettings.startDay, zone, month)
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

    // 月送りボタンで monthOffset が変わったときも集計し直す
    LaunchedEffect(hasPermission, monthOffset) {
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
                    startDay = appSettings.startDay,
                    zone = zone,
                    pagerState = pagerState,
                    monthOffset = monthOffset,
                    onMonthOffsetChange = { monthOffset = it },
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
    startDay: Int,
    zone: ZoneId,
    pagerState: PagerState,
    monthOffset: Int,
    onMonthOffsetChange: (Int) -> Unit,
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
            HeaderButton("更新", onClick = onRefresh)
            HeaderButton("設定", onClick = onOpenSettings)
        }
        // 月送りはサマリ・通話履歴の両タブに効くため、タブの中ではなくヘッダ側に置く。
        // 上下の余白は、すぐ上のヘッダボタンやすぐ下のタブを誤タップしないためのもの
        Spacer(Modifier.height(12.dp))
        MonthNavigation(monthOffset, onMonthOffsetChange)
        Spacer(Modifier.height(16.dp))
        val scope = rememberCoroutineScope()
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            indicator = { tabPositions -> PagerTabIndicator(tabPositions, pagerState) }
        ) {
            MainTab.entries.forEachIndexed { index, entry ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                    text = { Text(entry.label) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        // 縦スクロール（内訳リスト）と横スワイプ（ページ送り）の振り分けは、
        // HorizontalPager と LazyColumn の入れ子スクロールに任せる。リストがスクロールできる限り
        // 縦ドラッグはリスト側が消費するため、多少斜めに振れてもページ送りは始まらない
        // （リストの端まで来ると親のページ送りに渡る）
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            Column(Modifier.fillMaxSize()) {
                when (MainTab.entries[page]) {
                    MainTab.SUMMARY -> SummarySection(
                        period = state.period,
                        result = state.result,
                        settings = state.settings,
                        planType = planType,
                        startDay = startDay,
                        zone = zone
                    )
                    MainTab.HISTORY -> HistoryTab(state, filter, onFilterChange)
                }
            }
        }
    }
}

/**
 * spec: docs/spec.md 5.6 タブのインジケーター。
 * TabRow の既定インジケーターは選択が確定したときにアニメーションするだけで、
 * スワイプ途中の指の位置には追従しない。ページ位置（currentPage + currentPageOffsetFraction）で
 * 隣のタブとの間を補間し、スワイプ量に比例して動くようにする。
 *
 * ページ位置はコンポジション中には読まず、Modifier.layout の中（レイアウト段階）で読む。
 * currentPageOffsetFraction はスワイプ中フレームごとに変わるため、コンポジションで読むと
 * 毎フレーム再コンポーズになる（lint: FrequentlyChangingValue）。レイアウト段階で読めば
 * 値が変わったときに走るのは再レイアウトだけで、見た目の追従は変わらない。
 */
@Composable
private fun PagerTabIndicator(tabPositions: List<TabPosition>, pagerState: PagerState) {
    if (tabPositions.isEmpty()) return
    TabRowDefaults.SecondaryIndicator(
        Modifier
            .wrapContentSize(Alignment.BottomStart)
            .pagerIndicatorPosition(tabPositions, pagerState)
    )
}

/**
 * インジケーターの幅と横位置を、ページ位置から補間してレイアウト段階で決める。
 * 幅は measure（子の constraints を固定幅にする）、横位置は place で反映する。
 * wrapContentSize の下に置く前提で、受け取る constraints は下限 0・上限がタブ行の幅と高さ。
 * 自身はタブ行いっぱいの幅を申告し、その中でインジケーターを left の位置に置く
 * （wrapContentSize が BottomStart にそろえるので縦位置はそちらに任せる）。
 */
private fun Modifier.pagerIndicatorPosition(
    tabPositions: List<TabPosition>,
    pagerState: PagerState
): Modifier = layout { measurable, constraints ->
    // ここが pagerState を読む唯一の場所。コンポジションではなく measure のたびに評価される
    val position = (pagerState.currentPage + pagerState.currentPageOffsetFraction)
        .coerceIn(0f, tabPositions.lastIndex.toFloat())
    val index = position.toInt().coerceIn(0, maxOf(0, tabPositions.lastIndex - 1))
    val next = (index + 1).coerceAtMost(tabPositions.lastIndex)
    val fraction = position - index
    val leftPx = lerp(tabPositions[index].left, tabPositions[next].left, fraction).roundToPx()
    val widthPx = lerp(tabPositions[index].width, tabPositions[next].width, fraction)
        .roundToPx()
        .coerceIn(constraints.minWidth, constraints.maxWidth)

    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    val layoutWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else leftPx + placeable.width
    layout(layoutWidth, placeable.height) {
        placeable.placeRelative(leftPx, 0)
    }
}

/**
 * spec: docs/spec.md 5.6 ヘッダの操作ボタン。
 * TextButton の既定サイズ（58x40dp）はタップしづらいため、Material のタップターゲット
 * 推奨最小である 48dp の高さを確保し、背景色付き（FilledTonalButton）で押せることを明示する。
 */
@Composable
private fun HeaderButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

/**
 * spec: docs/spec.md 5.6 月送り。
 * タブの外（タイトル行とタブの間）に置き、どちらのタブを見ていても同じ位置で操作できるようにする。
 * 左側のラベルは「2026年9月」のような絶対表記にすると、起算日が 1 日でない場合
 * （例: 8/25-9/24）にどちらの月を指すのか読み手によって解釈が割れるため、
 * 今月からの相対表記にする。正確な日付はサマリタブの「期間:」行で確認できる。
 * ボタンはヘッダの更新・設定と同じ FilledTonalButton で、押せることを背景色で示す。
 * 「次月」は今月より先へは進めないため、monthOffset が 0 のとき無効になる。
 * ラベルを左・ボタン 2 つを右に寄せて隣り合わせるのは、前月と次月を続けて押すときの
 * 指の移動量を減らすため（両端に振り分けると 1 回ごとに画面幅を横断することになる）。
 */
@Composable
private fun MonthNavigation(monthOffset: Int, onMonthOffsetChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            // 「次月」を無効化しているため monthOffset が正になることはない
            if (monthOffset == 0) "今月" else "${-monthOffset}ヶ月前",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            // 余った幅をラベル側が持つことでボタン 2 つが右端に寄る
            modifier = Modifier.weight(1f)
        )
        // 過去方向には制限を設けない（記録が無ければ 0 件の結果になるだけ）
        HeaderButton("← 前月") { onMonthOffsetChange(monthOffset - 1) }
        HeaderButton("次月 →", enabled = monthOffset < 0) { onMonthOffsetChange(monthOffset + 1) }
    }
}

/**
 * spec: docs/spec.md 5.6.1 / 5.6.2 履歴集計。
 * 通話履歴を見に行かなくても件数と実時間が分かるよう、サマリタブと通話履歴タブの両方に出す。
 */
@Composable
private fun HistorySummary(result: Result) {
    SectionHeading("履歴集計")
    Spacer(Modifier.height(4.dp))
    InfoRow("通話件数", "${result.callCount}件（課金対象 ${result.billedCallCount}件）")
    InfoRow("通話時間（実時間）", "${result.countedSec / 60}分${result.countedSec % 60}秒")
    InfoRow("通話時間（課金枠換算）", "${result.quotaConsumedSec / 60}分${result.quotaConsumedSec % 60}秒")
    InfoRow("通話時間（除外）", "${result.excludedSec / 60}分${result.excludedSec % 60}秒")
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
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        SectionHeading("通話履歴")
        Box(Modifier.width(200.dp)) {
            SegmentedControl(
                options = BreakdownFilter.entries,
                selected = filter,
                label = { if (it == BreakdownFilter.ALL) "全件" else "課金対象のみ" },
                onSelect = onFilterChange
            )
        }
    }
    val filteredDetails = when (filter) {
        BreakdownFilter.ALL -> state.details
        BreakdownFilter.BILLED_ONLY -> state.details.filter { it.billedSec > 0 }
    }
    BreakdownList(filteredDetails, state.settings, modifier = Modifier.weight(1f))
}

private val PERIOD_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd")
private val DATETIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

/**
 * spec: docs/spec.md 5.6.1 サマリタブの中身。
 * 上段「設定値」（プラン情報）+ 中段「現在の状況」（通話時間・通話金額）+ 下段「履歴集計」。
 * 履歴集計は通話履歴タブ（5.6.2）と同じものを HistorySummary で再掲する。
 * アプリ名と月送りはタブの外のヘッダ行に置く。
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
    startDay: Int,
    zone: ZoneId
) {
    val startDate = Instant.ofEpochMilli(period.first).atZone(zone).toLocalDate()
    val endDate = Instant.ofEpochMilli(period.second - 1).atZone(zone).toLocalDate()

    Column {
        SectionHeading("設定値")
        InfoRow("プラン", planTypeLabel(planType))
        InfoRow("起算日", "${startDay}日")
        InfoRow("期間", "${PERIOD_FORMATTER.format(startDate)} - ${PERIOD_FORMATTER.format(endDate)}")
        when (planType) {
            PlanType.MONTHLY -> InfoRow("無料枠", "${settings.monthlyFreeSec / 60}分")
            PlanType.PER_CALL -> InfoRow("1通話無料枠", "${settings.perCallFreeSec / 60}分")
            PlanType.PAY_AS_YOU_GO -> Unit
        }
        InfoRow("単価", "${formatAmount(settings.unitPrice)}円 / ${settings.unitSec}秒")

        SectionDivider()
        SectionHeading("現在の状況")
        Spacer(Modifier.height(8.dp))
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
                over = result.billedCallCount > 0
            )
            PlanType.PAY_AS_YOU_GO -> StatusItem(
                label = "通話時間",
                value = "${formatMinutes(result.quotaConsumedSec)}分",
                over = null
            )
        }
        Spacer(Modifier.height(12.dp))
        StatusItem(label = "通話金額", value = "${formatAmount(result.amount)}円", over = null)

        SectionDivider()
        HistorySummary(result)
    }
}

/** 見出し。内容(bodyMedium相当)より大きく・太字にして、区切り線の代わりに階層を示す */
@Composable
private fun SectionHeading(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/** 「項目名(左・小さめ)・値(右)」の統一フォーマットの1行 */
@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodySmall)
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
    // 並べ替えは details が変わったときだけ。items() に直接渡すと再コンポーズのたびに走る
    val newestFirst = remember(details) { details.sortedByDescending { it.record.dateMillis } }
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(newestFirst) { detail ->
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

    val amountLabel = if (detail.billedSec > 0) {
        "¥${formatAmount(detail.billedSec / settings.unitSec * settings.unitPrice)}"
    } else {
        "無料"
    }
    val statusLabel = when {
        detail.excluded -> "除外"
        record.durationSec == 0 -> "未応答"
        detail.billedSec > 0 -> "課金対象"
        else -> "定額内"
    }
    val judgementText = if (detail.quotaConsumedSec > 0) {
        "$amountLabel・$statusLabel・課金枠 ${detail.quotaConsumedSec}秒"
    } else {
        "$amountLabel・$statusLabel"
    }
    // 枠の計算対象になった通話(quotaConsumedSec > 0)だけ、結果に応じて色分けしたドットを付ける。
    // 除外・未応答はそもそも計算対象外なのでドットなし
    val dotColor = when {
        detail.quotaConsumedSec <= 0 -> null
        detail.billedSec > 0 -> MaterialTheme.colorScheme.error
        else -> Color(0xFF2E7D32) // 課金なしで済んだことを示す緑
    }

    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(DATETIME_FORMATTER.format(dateTime), style = MaterialTheme.typography.bodySmall)
            Text("${record.durationSec}秒", style = MaterialTheme.typography.bodySmall)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                record.number ?: "非通知",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (dotColor != null) {
                    Text("●", style = MaterialTheme.typography.labelSmall, color = dotColor)
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    judgementText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
