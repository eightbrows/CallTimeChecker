package io.github.eightbrows.CallTimeChecker.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.eightbrows.CallTimeChecker.BuildConfig
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_EXCLUDE_PREFIXES
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.ThemeMode
import io.github.eightbrows.CallTimeChecker.logic.clampMonthlyFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampPerCallFreeMin
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_BG_TRANSPARENCY_STEP_COUNT
import io.github.eightbrows.CallTimeChecker.logic.WIDGET_COLOR_PALETTE
import io.github.eightbrows.CallTimeChecker.logic.WidgetPaletteColor
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
import io.github.eightbrows.CallTimeChecker.logic.themeModeLabel
import io.github.eightbrows.CallTimeChecker.logic.validateRange
import io.github.eightbrows.CallTimeChecker.logic.widgetBgTransparencyLabel
import kotlinx.coroutines.flow.first
import kotlin.math.roundToInt

/**
 * spec: docs/spec.md 5.7 バージョン情報から開くリンク。
 * ライセンスはリポジトリの LICENSE を直接指す（既定ブランチは master）。
 * アプリ内にライセンス全文を同梱せず GitHub 上のファイルを開くのは、
 * 表示のためだけに 200 行のテキストと専用画面を抱えないため。
 */
private const val LICENSE_URL =
    "https://github.com/eightbrows/CallTimeChecker/blob/master/LICENSE"
private const val OFFICIAL_SITE_URL = "https://eightbrows.github.io/"

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
    var themeMode by remember { mutableStateOf(current.themeMode) }
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
            widgetColorOverIndex = clampWidgetColorIndex(colorOverIndex),
            themeMode = themeMode
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

            WidgetColorSection(
                normalIndex = colorNormalIndex,
                warningIndex = colorWarningIndex,
                overIndex = colorOverIndex,
                onNormalChange = { colorNormalIndex = it },
                onWarningChange = { colorWarningIndex = it },
                onOverChange = { colorOverIndex = it }
            )
            Spacer(Modifier.height(8.dp))

            WidgetBgTransparencySection(widgetBgTransparencyStep) { widgetBgTransparencyStep = it }
            Spacer(Modifier.height(8.dp))

            ThemeModeSection(themeMode) { themeMode = it }
            Spacer(Modifier.height(16.dp))

            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            PermissionSection(hasPermission, onRequestPermission, onOpenAppSettings)
            Spacer(Modifier.height(12.dp))

            AboutSection()
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

    // 入力欄そのものは定額枠などと同じ NumberField（枠内ラベル + supportingText）にし、
    // +/- ボタンだけを枠の外の右に置く。ボタンの上下中央を入力欄（56dp）の中央に合わせるため
    // 上に 4dp 空ける（Row 全体を中央揃えにすると supportingText の分だけ上にずれる）
    Row(verticalAlignment = Alignment.Top) {
        NumberField(
            value = value,
            onValueChange = onValueChange,
            label = "定額枠残警告（残り分）",
            error = error,
            enabled = enabled,
            disabledNote = if (planType == PlanType.MONTHLY) {
                "定額枠が1分のため警告色は使用しない"
            } else {
                "${planTypeLabel(planType)}では使用しない"
            },
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        StepperButton("−", enabled = canDecrease, modifier = Modifier.padding(top = 4.dp)) {
            if (current != null) onValueChange((current - 1).toString())
        }
        Spacer(Modifier.width(8.dp))
        StepperButton("+", enabled = canIncrease, modifier = Modifier.padding(top = 4.dp)) {
            if (current != null) onValueChange((current + 1).toString())
        }
    }
}

/** +/- ボタン。タップターゲットの推奨最小 48dp を確保する */
@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(48.dp)
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
    // 開いているダイアログの役割。null なら閉じている
    var editing by remember { mutableStateOf<WidgetColorRole?>(null) }
    val selectedIndex = { role: WidgetColorRole ->
        when (role) {
            WidgetColorRole.NORMAL -> normalIndex
            WidgetColorRole.WARNING -> warningIndex
            WidgetColorRole.OVER -> overIndex
        }
    }

    Text("ウィジェット背景色", style = MaterialTheme.typography.titleMedium)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WidgetColorRole.entries.forEachIndexed { position, role ->
            if (position > 0) {
                Text(
                    "→",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            WidgetColorChip(role.label, selectedIndex(role)) { editing = role }
        }
    }
    Text(
        "使用量が増えるとこの順に切り替わる。文字色は背景色の明るさから自動で決まる。透過率は3色共通",
        style = MaterialTheme.typography.bodySmall
    )

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
                // 選んだら閉じる。3 色を順に変えるときに毎回「閉じる」を押させない
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** spec: docs/spec.md 5.5.3 背景色の 3 役割。宣言順が使用量の増える順（切り替わる順）になる */
private enum class WidgetColorRole(val label: String) {
    NORMAL("通常色"),
    WARNING("警告色"),
    OVER("超過色")
}

/**
 * 1 役割ぶんの現在の色。役割名と色見本を並べ、タップでパレットのダイアログを開く。
 * 8 色を 3 行ぶん並べるのをやめてこの形にしたのは、設定画面の縦幅を大きく取るわりに
 * 「今どの色か」が読み取りにくかったため。
 */
@Composable
private fun WidgetColorChip(label: String, selectedIndex: Int, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    Color(WIDGET_COLOR_PALETTE[selectedIndex].argb),
                    RoundedCornerShape(4.dp)
                )
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                .clickable { onClick() }
        )
    }
}

/**
 * spec: docs/spec.md 5.7 背景色のパレット。8 色を 4 列 2 行で出す。
 * 選択中は枠とチェックで示し、選ぶと即座に閉じる。
 */
@Composable
private fun WidgetColorPickerDialog(
    role: WidgetColorRole,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${role.label}を選ぶ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                WIDGET_COLOR_PALETTE.chunked(4).forEachIndexed { row, colors ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        colors.forEachIndexed { column, palette ->
                            val index = row * 4 + column
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
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        }
    )
}

/** パレット 1 色ぶんの見本。色名を添えて、色覚に頼らずに選べるようにする */
@Composable
private fun WidgetColorSwatch(
    palette: WidgetPaletteColor,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
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
        ) {
            // 見本の色に対して読める文字色はウィジェット本体と同じ規則で決める
            if (selected) {
                Text(
                    "✓",
                    color = Color(widgetTextColorOn(palette.argb)),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        Text(
            palette.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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

/**
 * spec: docs/spec.md 5.7 アプリ本体の配色（ライト / ダーク / システムに従う）。
 * ウィジェットの背景色とは別物なので、ウィジェット関連の項目の後ろに独立して置く。
 */
@Composable
private fun ThemeModeSection(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Text("アプリの配色", style = MaterialTheme.typography.titleMedium)
    Row(Modifier.fillMaxWidth()) {
        ThemeMode.entries.forEach { mode ->
            ThemeModeOption(mode, selected, Modifier.weight(1f), onSelect)
        }
    }
}

@Composable
private fun ThemeModeOption(
    value: ThemeMode,
    selected: ThemeMode,
    modifier: Modifier = Modifier,
    onSelect: (ThemeMode) -> Unit
) {
    // PlanTypeOption と同じ縦並び。「システムに従う」はラジオの横に置くと 1 行に収まらない
    Column(
        modifier = modifier.clickable { onSelect(value) },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        RadioButton(selected = selected == value, onClick = { onSelect(value) })
        Text(themeModeLabel(value), style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

/**
 * spec: docs/spec.md 5.7 バージョン情報。
 * 「項目名: 値」の並びで、外部ページを開く行だけリンク色 + 下線にして押せることを示す。
 */
@Composable
private fun AboutSection() {
    val context = LocalContext.current

    fun openInBrowser(url: String) {
        // ブラウザは別タスクで開く。設定画面のバックスタックに積むと「戻る」の行き先が変わる
        val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // ブラウザを持たない端末でも落とさない
            Toast.makeText(context, "ブラウザが見つかりません", Toast.LENGTH_SHORT).show()
        }
    }

    AboutRow("バージョン", BuildConfig.VERSION_NAME)
    AboutRow("ライセンス", "Apache License 2.0") { openInBrowser(LICENSE_URL) }
    AboutRow("公式サイト", "eightbrows.github.io") { openInBrowser(OFFICIAL_SITE_URL) }
}

/**
 * バージョン情報の 1 行。onClick を渡した行はリンクとして描く。
 * 行全体を押せるようにしたうえで、下線が付くのは値の側だけにしている
 * （リンク先は値が表しているため）。文字が小さいぶん、行に最低高さを持たせて
 * 指で押せる大きさを確保する。
 */
@Composable
private fun AboutRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick))
            .heightIn(min = if (onClick == null) 0.dp else 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$label: ", style = MaterialTheme.typography.bodySmall)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            color = if (onClick == null) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.primary
            },
            textDecoration = if (onClick == null) null else TextDecoration.Underline
        )
    }
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
