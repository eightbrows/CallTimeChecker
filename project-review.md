# CallTimeChecker ソースコード一覧

app/src/main 配下の全 Kotlin/XML ソース、app/build.gradle.kts、AndroidManifest.xml、docs/spec.md をまとめたもの。
ビルド生成物・テストコードは含まない。

## 目次

- [docs/spec.md](#docsspecmd)
- [app/build.gradle.kts](#appbuildgradlekts)
- [app/src/main/AndroidManifest.xml](#appsrcmainandroidmanifestxml)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/MainActivity.kt](#mainactivitykt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/Billing.kt](#logicbillingkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/Period.kt](#logicperiodkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/AppSettings.kt](#logicappsettingskt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/WidgetPresentation.kt](#logicwidgetpresentationkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/data/CallRecordDbHelper.kt](#datacallrecorddbhelperkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/data/CallLogSync.kt](#datacalllogsynckt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/data/SettingsRepository.kt](#datasettingsrepositorykt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/widget/CallTimeWidgetProvider.kt](#widgetcalltimewidgetproviderkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/SettingsScreen.kt](#uisettingsscreenkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Color.kt](#uithemecolorkt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Type.kt](#uithemetypekt)
- [app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Theme.kt](#uithemethemekt)
- [app/src/main/res/layout/widget_call_time.xml](#reslayoutwidget_call_timexml)
- [app/src/main/res/xml/call_time_widget_info.xml](#resxmlcall_time_widget_infoxml)
- [app/src/main/res/values/colors.xml](#resvaluescolorsxml)
- [app/src/main/res/values/strings.xml](#resvaluesstringsxml)
- [app/src/main/res/values/themes.xml](#resvaluesthemesxml)
- [app/src/main/res/xml/backup_rules.xml](#resxmlbackup_rulesxml)
- [app/src/main/res/xml/data_extraction_rules.xml](#resxmldata_extraction_rulesxml)
- [app/src/main/res/mipmap-anydpi/ic_launcher.xml](#resmipmap-anydpiic_launcherxml)
- [app/src/main/res/mipmap-anydpi/ic_launcher_round.xml](#resmipmap-anydpiic_launcher_roundxml)
- [app/src/main/res/drawable/ic_launcher_background.xml](#resdrawableic_launcher_backgroundxml)
- [app/src/main/res/drawable/ic_launcher_foreground.xml](#resdrawableic_launcher_foregroundxml)

---

## docs/spec.md

````markdown
# CallTimeWidget 仕様書

パッケージ名:io.github.eightbrows.CallTimeChecker

通話時間・通話料金積算ウィジェット

| 項目 | 内容 |
|---|---|
| ドキュメント版数 | 0.1 |
| 作成日 | 2026-08-18 |
| 状態 | 検討中 |

---

## 1. 概要

契約中の音声通話プランについて、当月の積算通話時間と概算通話料金をホーム画面ウィジェットに常時表示する Android アプリケーション。

端末の通話履歴 (`CallLog`) を定期的に取得してローカル DB に蓄積し、契約プランの設定値に基づいて集計・課金計算を行う。

### 1.1 解決したい課題

- 定額枠（月間 70 分等）をどれだけ消費したかがキャリアアプリを開かないと分からない
- キャリアアプリは起動が重く、反映も遅い
- ホーム画面で残量を一目で把握したい

### 1.2 設計方針

| 方針 | 理由 |
|---|---|
| 生ログをそのまま保存し、判定・課金計算は集計時に実施 | 設定変更時に過去分も再計算され、キャリア明細と突き合わせて設定を追い込める |
| 集計ロジックを純粋関数として分離 | 実機なしで JVM ローカルテストが可能 |
| 表示は常にローカル DB を参照 | 端末の `CallLog` が削除されても影響を受けない |
| プラン形式を 2 パラメータに抽象化 | 月間定額型・1 通話定額型・併用型を分岐なしで扱える |
| 外部ライブラリ・ネットワーク通信なし | 権限の追加要求が不要、保守が容易 |

---

## 2. スコープ

### 2.1 対象

- 標準の電話アプリによる音声発信の積算
- 契約起算日に基づく期間集計
- 定額枠消費量および超過料金の算出
- ウィジェットへの表示（30 分自動更新・タップ手動更新）

### 2.2 対象外

- 着信通話の積算（日本の一般的なプランでは課金対象外のため）
- IP 電話 / VoIP アプリ（Google Meet 等）の通話。`CallLog` に記録されないため原理的に取得不可
- データ通信量の集計
- キャリアの請求システムとの連携・照合
- 複数 SIM の個別集計（シングル SIM 運用前提）
- Google Play への公開（`READ_CALL_LOG` は Play 側で用途審査対象。GitHub Releases での自己配布を想定）

---

## 3. 動作環境

| 項目 | 内容 |
|---|---|
| minSdk | 26 (Android 8.0) |
| targetSdk | 35 |
| 言語 | Kotlin |
| 日時 API | `java.time`（API 26 以降でネイティブ利用可能。desugaring 不要） |
| DB | Room または SQLiteOpenHelper |
| UI | View システム + RemoteViews |
| 想定 SIM 構成 | シングル SIM |
| 想定発信手段 | 標準の電話アプリのみ（プレフィックス発信アプリ非使用） |

---

## 4. 用語定義

| 用語 | 定義 |
|---|---|
| 起算日 | 契約開始日に対応する、集計期間の開始日（1〜31） |
| 集計期間 | 起算日から翌月起算日の前日までの範囲 |
| 定額枠 | 月間で無料通話可能な総秒数（`monthlyFreeSec`） |
| 通話別無料時間 | 1 通話あたり無料となる秒数（`perCallFreeSec`） |
| 課金単位 | 超過分の課金の刻み秒数（30 または 60） |
| 除外番号 | 定額対象外としてカウントしない番号のプレフィックス |
| 課金対象秒数 | 単位切り上げ後、定額枠でも通話別無料時間でも吸収されなかった秒数 |

---

## 5. 機能要件

### 5.1 通話記録の取得

#### 5.1.1 取得元

`android.provider.CallLog.Calls` コンテンツプロバイダ。

| 列 | 用途 |
|---|---|
| `DATE` | 通話開始時刻（epoch millis）。主キーとして使用 |
| `DURATION` | 通話接続時間（秒）。未応答は 0 |
| `NUMBER` | 相手先番号。除外判定に使用 |
| `TYPE` | 通話種別。`OUTGOING_TYPE`(2) のみ対象 |
| `PHONE_ACCOUNT_ID` | 発信元アカウント。IP 電話混入時の切り分け用 |

#### 5.1.2 同期処理

```
1. last = SELECT MAX(date_millis) FROM call_record   (レコードなしの場合は 0)
2. CallLog を以下の条件で問い合わせ
     TYPE = 2 AND DATE >= (last - 86400000)
   ※ 24 時間分の重複読み込みにより、ログ書き込み順序の揺らぎを吸収する
3. 取得したレコードを INSERT OR IGNORE で投入
```

- 主キー重複により冪等性が担保される。何度実行しても結果は変わらない
- 差分のみを対象とするため、30 分間隔での実行でも負荷は無視できる
- 初回実行時のみ `CallLog` 全件を読み込む

#### 5.1.3 実行タイミング

| 契機 | 備考 |
|---|---|
| ウィジェット自動更新（30 分周期） | `updatePeriodMillis` による |
| ウィジェットタップ | 手動更新 |
| アプリ本体の起動時 | |
| 設定変更後 | 同期は不要だが再集計・再描画を行う |

### 5.2 集計期間の決定

起算日 `startDay`（1〜31）に対し、集計期間は `[開始日 00:00:00.000, 翌月開始日 00:00:00.000)` の半開区間とする。

タイムゾーンは端末のデフォルトを使用する。

```kotlin
/** 指定月における期間開始日。存在しない日は月末にクランプする */
fun periodStartDate(startDay: Int, ym: YearMonth): LocalDate =
    ym.atDay(minOf(startDay, ym.lengthOfMonth()))

fun currentPeriod(startDay: Int, zone: ZoneId): Pair<Long, Long> {
    val today = LocalDate.now(zone)
    val thisMonthStart = periodStartDate(startDay, YearMonth.from(today))
    val start = if (!today.isBefore(thisMonthStart)) thisMonthStart
                else periodStartDate(startDay, YearMonth.from(today).minusMonths(1))
    val end = periodStartDate(startDay, YearMonth.from(start).plusMonths(1))
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to
           end.atStartOfDay(zone).toInstant().toEpochMilli()
}
```

#### 5.2.1 仕様上の決定事項

| 論点 | 決定 |
|---|---|
| 起算日 25 の場合の範囲 | 25 日 00:00 〜 翌月 24 日 23:59:59.999 |
| 起算日 31、2 月の扱い | 2 月 28 日（閏年は 29 日）にクランプ |
| 期間をまたぐ通話 | 通話**開始時刻**が属する期間に計上する |
| 設定可能範囲 | 1〜31 |

### 5.3 除外判定

#### 5.3.1 判定手順

1. 番号文字列からハイフン、スペース、括弧を除去する
2. 除外リストの各エントリと前方一致比較を行う
3. いずれかに一致した場合、その通話は定額枠を消費せず、課金計算の対象外とする
4. 除外された通話は「除外通話時間」として別枠で集計・表示する

番号が空文字列または `null`（非通知等）の場合は除外扱いとしない。

#### 5.3.2 除外リスト初期値

| プレフィックス | 内容 |
|---|---|
| `0570` | ナビダイヤル |
| `0180` | テレドーム |
| `0990` | ダイヤルQ2 |
| `0120` | フリーダイヤル |
| `104` | 番号案内 |
| `110` | 警察 |
| `118` | 海上保安庁 |
| `119` | 消防・救急 |
| `188` | 消費者ホットライン |
| `+` | 国際発信 |

設定画面で編集可能とする（改行区切りのテキストとして保持）。

### 5.4 料金計算

#### 5.4.1 パラメータ

| 変数 | 意味 | 単位 |
|---|---|---|
| `monthlyFreeSec` | 月間定額枠 | 秒 |
| `perCallFreeSec` | 1 通話あたり無料時間 | 秒 |
| `unitSec` | 課金単位 | 秒（30 または 60） |
| `unitPrice` | 単位あたり金額 | 円 |

#### 5.4.2 プラン形式との対応

| プラン形式 | `monthlyFreeSec` | `perCallFreeSec` | 例 |
|---|---|---|---|
| 月間定額型 | 定額枠の秒数 | 0 | 月 70 分 + 22 円/30 秒 |
| 1 通話定額型 | 0 | 無料時間の秒数 | 5 分かけ放題 + 22 円/30 秒 |
| 併用型（カスタム） | 任意 | 任意 | 1 通話 5 分無料、超過分は月 70 分枠から消費 |
| 従量のみ | 0 | 0 | 22 円/30 秒 |

#### 5.4.3 アルゴリズム

```kotlin
data class Settings(
    val monthlyFreeSec: Int,
    val perCallFreeSec: Int,
    val unitSec: Int,
    val unitPrice: Int,
    val excludePrefixes: List<String>
)

data class Result(
    val countedSec: Int,     // 定額対象として計上した通話の実時間合計
    val billedSec: Int,      // 課金対象秒数（単位切り上げ済み）
    val amount: Int,         // 概算料金（円）
    val excludedSec: Int,    // 除外番号への通話の実時間合計
    val callCount: Int,      // 対象通話件数
    val billedCallCount: Int // 課金が発生した通話件数
)

fun calculate(records: List<CallRecord>, s: Settings): Result {
    var pool = s.monthlyFreeSec
    var countedSec = 0
    var billedSec = 0
    var excludedSec = 0
    var callCount = 0
    var billedCallCount = 0

    for (r in records.sortedBy { it.dateMillis }) {
        if (isExcluded(r.number, s.excludePrefixes)) {
            excludedSec += r.durationSec
            continue
        }
        if (r.durationSec == 0) continue   // 未応答

        countedSec += r.durationSec
        callCount++

        val over = maxOf(0, r.durationSec - s.perCallFreeSec)
        if (over == 0) continue

        // 通話ごとに課金単位へ切り上げる
        val units = ceilDiv(over, s.unitSec) * s.unitSec
        val consumed = minOf(units, pool)
        pool -= consumed
        val billed = units - consumed
        if (billed > 0) {
            billedSec += billed
            billedCallCount++
        }
    }
    return Result(
        countedSec = countedSec,
        billedSec = billedSec,
        amount = billedSec / s.unitSec * s.unitPrice,
        excludedSec = excludedSec,
        callCount = callCount,
        billedCallCount = billedCallCount
    )
}

fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
```

#### 5.4.4 計算上の決定事項

| 論点 | 決定 | 理由 |
|---|---|---|
| 切り上げの単位 | 通話ごとに切り上げ | キャリアの課金方式に最も近い |
| 定額枠の消費量 | 切り上げ**後**の秒数 | 同上 |
| 通話の処理順 | 通話開始時刻の昇順 | 定額枠の消費順序を実際の時系列に一致させる |
| 通話時間 0 秒 | 集計対象外 | 未応答・不通のため |
| 除外番号への通話 | 定額枠を消費せず、料金も計上しない | 別建て課金のため本アプリでは追跡しない |

#### 5.4.5 計算例

**例 A: 1 通話定額型（5 分かけ放題、22 円/30 秒）**

`monthlyFreeSec=0, perCallFreeSec=300, unitSec=30, unitPrice=22`

| 通話 | 実時間 | 超過分 | 切り上げ後 | 課金 |
|---|---|---|---|---|
| A | 3:00 | 0 秒 | 0 秒 | ¥0 |
| B | 5:00 | 0 秒 | 0 秒 | ¥0 |
| C | 5:10 | 10 秒 | 30 秒 | ¥22 |
| D | 7:40 | 160 秒 | 180 秒 | ¥132 |
| 合計 | 20:50 | | 210 秒 | **¥154** |

**例 B: 月間定額型（月 70 分、22 円/30 秒）**

`monthlyFreeSec=4200, perCallFreeSec=0, unitSec=30, unitPrice=22`

| 通話 | 実時間 | 切り上げ後 | 枠残 | 課金対象 |
|---|---|---|---|---|
| 〜通算 68 分 | | 4080 秒 | 120 秒 | 0 秒 |
| X | 4:10 | 270 秒 | 0 秒 | 150 秒 |
| 合計 | 72:10 | 4350 秒 | | **¥110** |

### 5.5 ウィジェット

#### 5.5.1 表示内容

サイズは 2×2 を推奨（2×1 では 2 行が窮屈になるため）。

**月間定額型（`monthlyFreeSec > 0`）**

```
42分 / 70分 (3件)
¥0
```

超過時:

```
78分 / 70分 (5件)
¥352 (超過 8分)
```

**1 通話定額型（`monthlyFreeSec = 0`）**

分母となる枠が存在しないため、テンプレートを切り替える。

```
通話 128分 (4件)
¥374
```

いずれのテンプレートも 1 行目末尾に `Result.callCount`（対象通話件数、5.4.3）を `(N件)` の形式で付記する。

#### 5.5.2 表示状態

| 状態 | 表示 | 遷移先 |
|---|---|---|
| `NORMAL` | 上記の通常表示 | タップで手動更新 |
| `NO_PERMISSION` | 「タップして権限を許可」 | タップでアプリ本体を起動 |
| `ERROR` | 「更新失敗」 | タップで再試行 |

#### 5.5.3 配色

| 条件 | 色 |
|---|---|
| 使用率 80% 未満 | 通常色 |
| 使用率 80% 以上 100% 未満 | 警告色（橙） |
| 使用率 100% 以上 | 超過色（赤） |

1 通話定額型では使用率が定義できないため、課金額 0 円かどうかで通常色／警告色を切り替える。

#### 5.5.4 更新

| 項目 | 内容 |
|---|---|
| 自動更新 | `appwidget-provider` の `updatePeriodMillis="1800000"`（30 分。システムの最小値） |
| 手動更新 | ウィジェットタップ → `PendingIntent.getBroadcast`（API 31 以降は `FLAG_IMMUTABLE` 必須）→ 自身の `onReceive` でカスタムアクションを処理 |
| 処理内容 | 同期 → 再集計 → `AppWidgetManager.updateAppWidget` |
| スレッド | `onUpdate` / `onReceive` はメインスレッドかつ 10 秒制限のため、`goAsync()` + Executor でバックグラウンド実行する |
| 再起動時 | `updatePeriodMillis` はシステムが再スケジュールするため `BOOT_COMPLETED` の受信は不要 |

Doze 中は自動更新が遅延するが、用途上許容する。

### 5.6 アプリ本体

単一 Activity 構成。

| 領域 | 内容 |
|---|---|
| サマリ | 積算通話時間、定額枠、残り時間、概算料金、集計期間（`2026/08/25 - 2026/09/24`） |
| 内訳リスト | 期間内の通話一覧。日時、番号、通話時間、判定結果（定額内 / 課金 ¥XX / 除外）、課金対象秒数。「全件 / 課金対象のみ」の表示フィルタ切り替え可 |
| 権限 | 未許可時に要求ボタンを表示 |
| 操作 | 手動更新、設定画面への遷移 |

内訳リストは、キャリアの請求明細と突き合わせて端数処理・除外ルールの妥当性を検証する唯一の手段であるため、初期リリースに含める。

「全件 / 課金対象のみ」フィルタは表示上の絞り込みのみで、集計ロジック（`calculate()`/`calculateDetails()`）には影響しない。「課金対象のみ」は各通話の課金対象秒数（`CallDetail.billedSec`）が 0 より大きいものを表示する。

### 5.7 設定項目

| 項目 | 型 | 初期値 | 備考 |
|---|---|---|---|
| プラン形式 | 選択 | 月間定額型 | 月間定額型 / 1 通話定額型 / カスタム |
| 起算日 | 1〜31 | 1 | 契約開始日 |
| 定額枠 | 分 | 70 | プラン形式が 1 通話定額型のとき 0 固定 |
| 通話別無料時間 | 分 | 5 | プラン形式が月間定額型のとき 0 固定 |
| 課金単位 | 30 / 60 | 30 | 秒 |
| 単位金額 | 円 | 22 | |
| 除外番号リスト | テキスト | 5.3.2 の初期値 | 改行区切り |

プラン形式は UI 上のプリセットに過ぎず、内部的には `monthlyFreeSec` と `perCallFreeSec` の 2 フィールドに書き込むのみ。集計ロジックはプラン形式を参照しない。

保存先は `SharedPreferences`。

---

## 6. データ設計

### 6.1 テーブル定義

```sql
CREATE TABLE call_record (
  date_millis  INTEGER NOT NULL PRIMARY KEY,  -- 通話開始時刻
  duration_sec INTEGER NOT NULL,              -- 接続時間（秒）
  number       TEXT,                          -- 相手先番号
  type         INTEGER NOT NULL,              -- CallLog.Calls.TYPE
  account_id   TEXT                           -- PHONE_ACCOUNT_ID
);

CREATE INDEX idx_call_record_date ON call_record(date_millis);
```

### 6.2 設計上の決定事項

| 論点 | 決定 | 理由 |
|---|---|---|
| 主キー | `date_millis` | `CallLog._ID` は端末側でログが削除・再構築されると再利用される可能性があるため使用しない。通話開始時刻はミリ秒精度で実質的に一意 |
| 保存内容 | 生ログのみ | 除外判定・課金計算の結果は保存しない。設定変更時に過去分を含めて再計算するため |
| 保持期間 | 無期限 | 年間数千件程度であり容量上の問題がない |
| 削除 | 行わない | 過去期間の参照および設定検証のため |
| `account_id` | 保持する | IP 電話の混入が判明した場合の切り分けに使用 |

---

## 7. 処理フロー

### 7.1 ウィジェット更新

```
onUpdate / onReceive(ACTION_MANUAL_REFRESH)
  ↓ goAsync()
  ↓ [Executor]
  権限チェック
    未許可 → NO_PERMISSION 状態で描画 → finish()
  ↓
  CallLog 同期（5.1.2）
  ↓
  集計期間の算出（5.2）
  ↓
  DB から期間内レコードを取得
  ↓
  calculate()（5.4.3）
  ↓
  RemoteViews 構築・updateAppWidget
  ↓
  PendingResult.finish()
```

### 7.2 設定変更時

同期は行わず、再集計と全ウィジェットの再描画のみを実行する。

---

## 8. 権限

| 権限 | 保護レベル | 用途 |
|---|---|---|
| `android.permission.READ_CALL_LOG` | dangerous | 通話履歴の取得 |

- 実行時権限として要求する。ウィジェットからは権限要求ができないため、未許可時はウィジェットに `NO_PERMISSION` を表示し、タップでアプリ本体を起動して要求する
- ネットワーク権限は不要

`AndroidManifest.xml` では `AppWidgetProvider` に `android:exported="true"` を明示する（targetSdk 31 以降で必須）。

---

## 9. 制約・既知の限界

| 項目 | 内容 | 対応 |
|---|---|---|
| 通話中の反映 | `CallLog` は通話終了後に書き込まれるため、通話中は積算に反映されない | 仕様として許容 |
| IP 電話 | Google Meet 等は `CallLog` に記録されないため取得不可 | 積算不要のため問題なし。混入が判明した場合は `account_id` で切り分け、除外ルールを追加 |
| `CallLog` の保持上限 | 機種により保持件数に上限がある（500 件程度の例あり） | ローカル DB への蓄積により回避 |
| 初回導入時 | 導入以前の通話は `CallLog` に残っている分しか取得できない | 期間途中での導入時は数値が不完全になる旨をアプリ内に表示 |
| 課金計算の精度 | 切り上げ単位・カウント開始タイミングがキャリアにより異なる | 内訳リストで明細と突き合わせ、設定で調整 |
| Doze による遅延 | 自動更新が最大で数十分遅延する場合がある | 手動更新で補完。必要ならバッテリー最適化の除外を案内 |
| 表示金額の位置づけ | あくまで概算。基本料金・ユニバーサルサービス料・消費税等は含まない | アプリ内に注記 |

---

## 10. テスト方針

### 10.1 ユニットテスト（JVM ローカル）

集計ロジックは `List<CallRecord>` と `Settings` を受けて `Result` を返す純粋関数であり、実機なしで検証可能。以下を優先的にテストする。

| 対象 | ケース |
|---|---|
| 期間算出 | 起算日 1 / 25 / 31、2 月（閏年・平年）、月初日・月末日の境界 |
| 切り上げ | 端数 0 秒、1 秒、29 秒、30 秒、31 秒（`unitSec` = 30 / 60 の両方） |
| 定額枠 | 枠未使用、枠内、枠ちょうど、枠をまたぐ 1 通話、枠超過 |
| 通話別無料時間 | 無料時間ちょうど、1 秒超過、大幅超過 |
| 併用型 | `monthlyFreeSec` と `perCallFreeSec` の両方が正の場合 |
| 除外判定 | 各初期値プレフィックス、ハイフン付き番号、空番号、`null` |
| 境界 | 通話時間 0 秒、期間開始時刻ちょうど、期間終了時刻の直前・直後 |

### 10.2 実機確認

| 項目 | 確認内容 |
|---|---|
| 同期の冪等性 | 複数回更新しても件数・積算値が変化しないこと |
| 自動更新 | 30 分周期で更新されること（Doze 下での遅延幅も確認） |
| 権限フロー | 未許可 → ウィジェット表示 → タップ → 許可 → 反映 |
| `CallLog` 保持 | 1 期間運用し、期間先頭の通話がローカル DB に残存すること |
| 明細突き合わせ | キャリアの請求明細と積算値・金額を比較し、設定値を調整 |

---

## 11. 実装フェーズ

| Phase | 内容 | 備考 |
|---|---|---|
| 1 | DB、同期処理、期間算出、集計ロジック + ユニットテスト | UI なし。ロジックを先に固める |
| 2 | アプリ本体（サマリ、内訳リスト、権限要求） | 手動更新のみ |
| 3 | ウィジェット（表示、配色、自動更新、タップ更新） | |
| 4 | 設定画面 | それまでは定数で仮置き |
| 5 | GitHub Actions による CI、APK 署名、リリース | |

---

## 12. 未決事項

| # | 内容 |
|---|---|
| 1 | ウィジェットのサイズバリエーション（2×1 のコンパクト版を用意するか） |
| 2 | 定額枠の使用率が一定値を超えた際の通知機能の要否 |
| 3 | 過去期間の集計結果を参照する画面の要否 |
| 4 | 通話 0 秒（未応答）のレコードを DB に保存するか、同期時点で除外するか |
| 5 | ウィジェットの通話時間表示を「定額枠消費量（countedSec）」ではなく「課金対象秒数（billedSec）ベース」に変更する案の検討（現状は仕様書 5.5.1 通り countedSec ベース） |
````

---

## app/build.gradle.kts

```kotlin
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

/**
 * versionName: "YYYYMMDD-Xnn"（Xは R=タグによるリリースビルド / D=通常のdevビルド、
 * nnは当日のコミット数を2桁ゼロ埋め、3桁以上はそのまま拡張）。
 * versionCode: 上記から "-D"/"-R" を除いた数字部分（YYYYMMDDnn）をそのまま整数化したもの。
 * git が使えない環境・コミット履歴がまだない場合は "0.0.0-DEV" / 1 にフォールバックする。
 */
fun runGit(vararg args: String): String? = try {
    val output = providers.exec {
        commandLine(listOf("git") + args.toList())
        isIgnoreExitValue = true
    }
    if (output.result.get().exitValue == 0) output.standardOutput.asText.get().trim() else null
} catch (e: Exception) {
    null
}

// git のコミット・タグは外部プロセス経由でしか取得できず Configuration Cache に自動追跡されないため、
// コミット/タグ操作で必ず更新される .git 配下のファイルを明示的に読み、キャッシュの再評価契機とする。
File(rootDir, ".git").takeIf { it.exists() }?.let { gitDir ->
    runCatching { File(gitDir, "HEAD").readText() }
    runCatching { File(gitDir, "logs/HEAD").readText() }
    runCatching { File(gitDir, "packed-refs").readText() }
    runCatching { File(gitDir, "refs/tags").list()?.sorted()?.joinToString() }
}

data class BuildVersion(val name: String, val code: Int)

fun computeBuildVersion(): BuildVersion {
    if (runGit("rev-parse", "HEAD") == null) return BuildVersion("0.0.0-DEV", 1)

    val dateStr = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)

    val releaseTag = runGit("tag", "--points-at", "HEAD")
        ?.lineSequence()
        ?.map { it.trim() }
        ?.firstOrNull { it.startsWith("$dateStr-R") }

    val versionName = releaseTag ?: run {
        // git の "today"/"tomorrow" という相対日付キーワードは環境（TZ未設定のGit for Windows等）
        // によって解釈がずれることがあるため、当日日付から組み立てた絶対日時を明示的に渡す。
        val today = LocalDate.now()
        val since = today.format(DateTimeFormatter.ISO_LOCAL_DATE) + " 00:00:00"
        val until = today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE) + " 00:00:00"
        val todaysCommits = runGit(
            "log",
            "--since=$since",
            "--until=$until",
            "--oneline"
        ).orEmpty().lineSequence().count { it.isNotBlank() }
        "$dateStr-D${todaysCommits.toString().padStart(2, '0')}"
    }

    val versionCode = versionName.filter { it.isDigit() }.toIntOrNull() ?: 1
    return BuildVersion(versionName, versionCode)
}

val buildVersion = computeBuildVersion()
println("CallTimeChecker version: name=${buildVersion.name} code=${buildVersion.code}")

android {
    namespace = "io.github.eightbrows.CallTimeChecker"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.eightbrows.CallTimeChecker"
        minSdk = 26
        targetSdk = 37
        versionCode = buildVersion.code
        versionName = buildVersion.name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

---

## app/src/main/AndroidManifest.xml

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.READ_CALL_LOG" />

    <application
        android:allowBackup="true"
        android:dataExtractionRules="@xml/data_extraction_rules"
        android:fullBackupContent="@xml/backup_rules"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.CallTimeChecker">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:label="@string/app_name"
            android:theme="@style/Theme.CallTimeChecker"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />

                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <receiver
            android:name=".widget.CallTimeWidgetProvider"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/call_time_widget_info" />
        </receiver>
    </application>

</manifest>
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/MainActivity.kt {#mainactivitykt}

```kotlin
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
    val judgement = when {
        detail.excluded -> "除外"
        record.durationSec == 0 -> "未応答"
        detail.billedSec > 0 -> "課金 ¥${detail.billedSec / settings.unitSec * settings.unitPrice}"
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
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/Billing.kt {#logicbillingkt}

```kotlin
package io.github.eightbrows.CallTimeChecker.logic

/** spec: docs/spec.md 6.1 の call_record から集計に必要な列のみを取り出したもの */
data class CallRecord(
    val dateMillis: Long,
    val durationSec: Int,
    val number: String?
)

/** spec: docs/spec.md 5.4.1 / 5.4.2 */
data class Settings(
    val monthlyFreeSec: Int,
    val perCallFreeSec: Int,
    val unitSec: Int,
    val unitPrice: Int,
    val excludePrefixes: List<String>
)

/** spec: docs/spec.md 5.4.3 */
data class Result(
    val countedSec: Int,
    val billedSec: Int,
    val amount: Int,
    val excludedSec: Int,
    val callCount: Int,
    val billedCallCount: Int
)

/** spec: docs/spec.md 5.3.2 除外リスト初期値 */
val DEFAULT_EXCLUDE_PREFIXES = listOf(
    "0570", "0180", "0990", "0120", "104", "110", "118", "119", "188", "+"
)

/** spec: docs/spec.md 5.3.1 除外判定 */
fun isExcluded(number: String?, excludePrefixes: List<String>): Boolean {
    if (number.isNullOrEmpty()) return false
    val normalized = number.replace(Regex("[-()\\s]"), "")
    return excludePrefixes.any { normalized.startsWith(it) }
}

fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b

/** spec: docs/spec.md 5.4.3 料金計算アルゴリズム */
fun calculate(records: List<CallRecord>, s: Settings): Result {
    var pool = s.monthlyFreeSec
    var countedSec = 0
    var billedSec = 0
    var excludedSec = 0
    var callCount = 0
    var billedCallCount = 0

    for (r in records.sortedBy { it.dateMillis }) {
        if (isExcluded(r.number, s.excludePrefixes)) {
            excludedSec += r.durationSec
            continue
        }
        if (r.durationSec == 0) continue // 未応答

        countedSec += r.durationSec
        callCount++

        val over = maxOf(0, r.durationSec - s.perCallFreeSec)
        if (over == 0) continue

        // 通話ごとに課金単位へ切り上げる
        val units = ceilDiv(over, s.unitSec) * s.unitSec
        val consumed = minOf(units, pool)
        pool -= consumed
        val billed = units - consumed
        if (billed > 0) {
            billedSec += billed
            billedCallCount++
        }
    }
    return Result(
        countedSec = countedSec,
        billedSec = billedSec,
        amount = billedSec / s.unitSec * s.unitPrice,
        excludedSec = excludedSec,
        callCount = callCount,
        billedCallCount = billedCallCount
    )
}

/** spec: docs/spec.md 5.6 内訳リストの1件分の判定結果（定額内 / 課金 / 除外 / 未応答） */
data class CallDetail(
    val record: CallRecord,
    val excluded: Boolean,
    val billedSec: Int
)

/**
 * spec: docs/spec.md 5.6 内訳リスト用。calculate() と同じアルゴリズム（5.4.3）を通話ごとに適用し、
 * 各通話の判定結果を返す。calculate() の集計結果とは独立に計算するため、calculate() 自体は変更しない。
 */
fun calculateDetails(records: List<CallRecord>, s: Settings): List<CallDetail> {
    var pool = s.monthlyFreeSec
    val details = mutableListOf<CallDetail>()

    for (r in records.sortedBy { it.dateMillis }) {
        if (isExcluded(r.number, s.excludePrefixes)) {
            details += CallDetail(r, excluded = true, billedSec = 0)
            continue
        }
        if (r.durationSec == 0) {
            details += CallDetail(r, excluded = false, billedSec = 0)
            continue
        }

        val over = maxOf(0, r.durationSec - s.perCallFreeSec)
        if (over == 0) {
            details += CallDetail(r, excluded = false, billedSec = 0)
            continue
        }

        val units = ceilDiv(over, s.unitSec) * s.unitSec
        val consumed = minOf(units, pool)
        pool -= consumed
        details += CallDetail(r, excluded = false, billedSec = units - consumed)
    }
    return details
}
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/Period.kt {#logicperiodkt}

```kotlin
package io.github.eightbrows.CallTimeChecker.logic

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * spec: docs/spec.md 5.2 集計期間の決定
 *
 * 指定月における期間開始日。存在しない日は月末にクランプする。
 */
fun periodStartDate(startDay: Int, ym: YearMonth): LocalDate =
    ym.atDay(minOf(startDay, ym.lengthOfMonth()))

/**
 * `today` を基準とした集計期間 [開始, 終了) を epoch millis の半開区間で返す。
 */
fun currentPeriod(startDay: Int, zone: ZoneId, today: LocalDate): Pair<Long, Long> {
    val thisMonthStart = periodStartDate(startDay, YearMonth.from(today))
    val start = if (!today.isBefore(thisMonthStart)) thisMonthStart
                else periodStartDate(startDay, YearMonth.from(today).minusMonths(1))
    val end = periodStartDate(startDay, YearMonth.from(start).plusMonths(1))
    return start.atStartOfDay(zone).toInstant().toEpochMilli() to
           end.atStartOfDay(zone).toInstant().toEpochMilli()
}

/** 現在時刻を基準とした集計期間。 */
fun currentPeriod(startDay: Int, zone: ZoneId): Pair<Long, Long> =
    currentPeriod(startDay, zone, LocalDate.now(zone))
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/AppSettings.kt {#logicappsettingskt}

```kotlin
package io.github.eightbrows.CallTimeChecker.logic

/**
 * spec: docs/spec.md 5.7 プラン形式。UI 上のプリセットに過ぎず、
 * 集計ロジック（Billing.kt の Settings/calculate）はプラン形式を参照しない。
 */
enum class PlanType { MONTHLY, PER_CALL, CUSTOM }

/**
 * spec: docs/spec.md 5.7 設定画面が保持する設定値（分単位）。
 * Billing.kt の Settings（秒単位）とは独立に保持し、toBillingSettings() で変換する。
 */
data class AppSettings(
    val planType: PlanType,
    val startDay: Int,
    val monthlyFreeMin: Int,
    val perCallFreeMin: Int,
    val unitSec: Int,
    val unitPrice: Int,
    val excludePrefixes: List<String>
)

/** spec: docs/spec.md 5.7 初期値 */
val DEFAULT_APP_SETTINGS = AppSettings(
    planType = PlanType.MONTHLY,
    startDay = 1,
    monthlyFreeMin = 70,
    perCallFreeMin = 5,
    unitSec = 30,
    unitPrice = 22,
    excludePrefixes = DEFAULT_EXCLUDE_PREFIXES
)

/**
 * spec: docs/spec.md 5.7 備考「定額枠はプラン形式が1通話定額型のとき0固定」
 * 「通話別無料時間はプラン形式が月間定額型のとき0固定」。
 */
fun effectiveAppSettings(settings: AppSettings): AppSettings = when (settings.planType) {
    PlanType.MONTHLY -> settings.copy(perCallFreeMin = 0)
    PlanType.PER_CALL -> settings.copy(monthlyFreeMin = 0)
    PlanType.CUSTOM -> settings
}

/**
 * spec: docs/spec.md 5.7「プラン形式は...内部的には monthlyFreeSec と perCallFreeSec の
 * 2 フィールドに書き込むのみ」。Billing.kt の Settings（秒単位）へ変換する。
 */
fun toBillingSettings(settings: AppSettings): Settings {
    val effective = effectiveAppSettings(settings)
    return Settings(
        monthlyFreeSec = effective.monthlyFreeMin * 60,
        perCallFreeSec = effective.perCallFreeMin * 60,
        unitSec = effective.unitSec,
        unitPrice = effective.unitPrice,
        excludePrefixes = effective.excludePrefixes
    )
}

/** spec: docs/spec.md 5.7 起算日は 1〜31 */
fun clampStartDay(day: Int): Int = day.coerceIn(1, 31)

/** spec: docs/spec.md 5.7 課金単位は 30 / 60 のみ */
fun normalizeUnitSec(sec: Int): Int = if (sec == 60) 60 else 30

/** spec: docs/spec.md 5.7 除外番号リスト（改行区切りテキスト）→ プレフィックスのリスト */
fun parseExcludePrefixes(text: String): List<String> =
    text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** parseExcludePrefixes の逆変換。設定画面のテキストフィールド初期表示に使用 */
fun excludePrefixesToText(prefixes: List<String>): String = prefixes.joinToString("\n")
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/logic/WidgetPresentation.kt {#logicwidgetpresentationkt}

```kotlin
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
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/data/CallRecordDbHelper.kt {#datacallrecorddbhelperkt}

```kotlin
package io.github.eightbrows.CallTimeChecker.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import io.github.eightbrows.CallTimeChecker.logic.CallRecord

private const val DB_NAME = "call_time_checker.db"
private const val DB_VERSION = 1

private const val TABLE = "call_record"
private const val COL_DATE = "date_millis"
private const val COL_DURATION = "duration_sec"
private const val COL_NUMBER = "number"
private const val COL_TYPE = "type"
private const val COL_ACCOUNT = "account_id"

/** CallLog から読み取った生レコード。DB 保存用に type / account_id を含む (spec: docs/spec.md 6.1) */
data class RawCallRecord(
    val dateMillis: Long,
    val durationSec: Int,
    val number: String?,
    val type: Int,
    val accountId: String?
)

/** spec: docs/spec.md 6.1 テーブル定義 */
class CallRecordDbHelper(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
              $COL_DATE     INTEGER NOT NULL PRIMARY KEY,
              $COL_DURATION INTEGER NOT NULL,
              $COL_NUMBER   TEXT,
              $COL_TYPE     INTEGER NOT NULL,
              $COL_ACCOUNT  TEXT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_call_record_date ON $TABLE($COL_DATE)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // スキーマ変更は未発生
    }

    /** spec: docs/spec.md 5.1.2 手順1 (レコードなしの場合は 0) */
    fun maxDateMillis(): Long =
        readableDatabase.rawQuery("SELECT MAX($COL_DATE) FROM $TABLE", null).use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else 0L
        }

    /** spec: docs/spec.md 5.1.2 手順3 (INSERT OR IGNORE により冪等性を担保) */
    fun insertOrIgnore(records: List<RawCallRecord>) {
        if (records.isEmpty()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            for (r in records) {
                val values = ContentValues().apply {
                    put(COL_DATE, r.dateMillis)
                    put(COL_DURATION, r.durationSec)
                    put(COL_NUMBER, r.number)
                    put(COL_TYPE, r.type)
                    put(COL_ACCOUNT, r.accountId)
                }
                db.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** 指定期間 [start, end) の call_record を通話開始時刻昇順で取得する (Billing.calculate() の入力用) */
    fun queryRange(startMillis: Long, endMillis: Long): List<CallRecord> {
        val result = mutableListOf<CallRecord>()
        readableDatabase.query(
            TABLE,
            arrayOf(COL_DATE, COL_DURATION, COL_NUMBER),
            "$COL_DATE >= ? AND $COL_DATE < ?",
            arrayOf(startMillis.toString(), endMillis.toString()),
            null, null, "$COL_DATE ASC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += CallRecord(
                    dateMillis = cursor.getLong(0),
                    durationSec = cursor.getInt(1),
                    number = if (cursor.isNull(2)) null else cursor.getString(2)
                )
            }
        }
        return result
    }
}
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/data/CallLogSync.kt {#datacalllogsynckt}

```kotlin
package io.github.eightbrows.CallTimeChecker.data

import android.content.ContentResolver
import android.provider.CallLog

private const val DUPLICATE_WINDOW_MILLIS = 24L * 60 * 60 * 1000 // 5.1.2: 24時間分の重複読み込み

/**
 * spec: docs/spec.md 5.1.2 同期処理
 *
 * 呼び出し元は事前に READ_CALL_LOG 権限の許可を確認しておくこと。
 */
class CallLogSync(
    private val contentResolver: ContentResolver,
    private val dbHelper: CallRecordDbHelper
) {
    /** CallLog から未取り込み分を読み込み、DB へ反映する。取り込みを試みた件数を返す */
    fun sync(): Int {
        val last = dbHelper.maxDateMillis()
        val since = maxOf(0L, last - DUPLICATE_WINDOW_MILLIS)

        val records = mutableListOf<RawCallRecord>()
        val projection = arrayOf(
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.PHONE_ACCOUNT_ID
        )
        contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            projection,
            "${CallLog.Calls.TYPE} = ? AND ${CallLog.Calls.DATE} >= ?",
            arrayOf(CallLog.Calls.OUTGOING_TYPE.toString(), since.toString()),
            null
        )?.use { cursor ->
            val dateIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DATE)
            val durationIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.DURATION)
            val numberIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.NUMBER)
            val typeIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.TYPE)
            val accountIdx = cursor.getColumnIndexOrThrow(CallLog.Calls.PHONE_ACCOUNT_ID)
            while (cursor.moveToNext()) {
                records += RawCallRecord(
                    dateMillis = cursor.getLong(dateIdx),
                    durationSec = cursor.getInt(durationIdx),
                    number = cursor.getString(numberIdx),
                    type = cursor.getInt(typeIdx),
                    accountId = cursor.getString(accountIdx)
                )
            }
        }
        dbHelper.insertOrIgnore(records)
        return records.size
    }
}
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/data/SettingsRepository.kt {#datasettingsrepositorykt}

```kotlin
package io.github.eightbrows.CallTimeChecker.data

import android.content.Context
import io.github.eightbrows.CallTimeChecker.logic.AppSettings
import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_APP_SETTINGS
import io.github.eightbrows.CallTimeChecker.logic.PlanType
import io.github.eightbrows.CallTimeChecker.logic.clampStartDay
import io.github.eightbrows.CallTimeChecker.logic.excludePrefixesToText
import io.github.eightbrows.CallTimeChecker.logic.normalizeUnitSec
import io.github.eightbrows.CallTimeChecker.logic.parseExcludePrefixes
import io.github.eightbrows.CallTimeChecker.widget.notifyWidgetsSettingsChanged

private const val PREFS_NAME = "call_time_checker_settings"
private const val KEY_PLAN_TYPE = "plan_type"
private const val KEY_START_DAY = "start_day"
private const val KEY_MONTHLY_FREE_MIN = "monthly_free_min"
private const val KEY_PER_CALL_FREE_MIN = "per_call_free_min"
private const val KEY_UNIT_SEC = "unit_sec"
private const val KEY_UNIT_PRICE = "unit_price"
private const val KEY_EXCLUDE_PREFIXES = "exclude_prefixes"

/**
 * spec: docs/spec.md 5.7 設定項目の永続化（保存先は SharedPreferences）。
 * AndroidフレームワークAPI（SharedPreferences）に依存するため、CallRecordDbHelper/CallLogSync と
 * 同様にJVMユニットテスト対象外とする。変換・正規化ロジック自体は logic/AppSettings.kt の
 * 純粋関数（AppSettingsTest.kt でテスト済み）を再利用する。
 */
class SettingsRepository(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val planType = prefs.getString(KEY_PLAN_TYPE, null)?.let {
            runCatching { PlanType.valueOf(it) }.getOrNull()
        } ?: DEFAULT_APP_SETTINGS.planType

        return AppSettings(
            planType = planType,
            startDay = clampStartDay(prefs.getInt(KEY_START_DAY, DEFAULT_APP_SETTINGS.startDay)),
            monthlyFreeMin = prefs.getInt(KEY_MONTHLY_FREE_MIN, DEFAULT_APP_SETTINGS.monthlyFreeMin),
            perCallFreeMin = prefs.getInt(KEY_PER_CALL_FREE_MIN, DEFAULT_APP_SETTINGS.perCallFreeMin),
            unitSec = normalizeUnitSec(prefs.getInt(KEY_UNIT_SEC, DEFAULT_APP_SETTINGS.unitSec)),
            unitPrice = prefs.getInt(KEY_UNIT_PRICE, DEFAULT_APP_SETTINGS.unitPrice),
            excludePrefixes = prefs.getString(KEY_EXCLUDE_PREFIXES, null)
                ?.let { parseExcludePrefixes(it) }
                ?: DEFAULT_APP_SETTINGS.excludePrefixes
        )
    }

    /**
     * spec: docs/spec.md 7.2 設定変更時。保存完了後、同期は行わず全ウィジェットの
     * 再集計・再描画のみをトリガーする（CallTimeWidgetProvider.notifyWidgetsSettingsChanged）。
     */
    fun save(settings: AppSettings) {
        prefs.edit()
            .putString(KEY_PLAN_TYPE, settings.planType.name)
            .putInt(KEY_START_DAY, settings.startDay)
            .putInt(KEY_MONTHLY_FREE_MIN, settings.monthlyFreeMin)
            .putInt(KEY_PER_CALL_FREE_MIN, settings.perCallFreeMin)
            .putInt(KEY_UNIT_SEC, settings.unitSec)
            .putInt(KEY_UNIT_PRICE, settings.unitPrice)
            .putString(KEY_EXCLUDE_PREFIXES, excludePrefixesToText(settings.excludePrefixes))
            .apply()
        notifyWidgetsSettingsChanged(appContext)
    }
}
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/widget/CallTimeWidgetProvider.kt {#widgetcalltimewidgetproviderkt}

```kotlin
package io.github.eightbrows.CallTimeChecker.widget

import android.Manifest
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import io.github.eightbrows.CallTimeChecker.MainActivity
import io.github.eightbrows.CallTimeChecker.R
import io.github.eightbrows.CallTimeChecker.data.CallLogSync
import io.github.eightbrows.CallTimeChecker.data.CallRecordDbHelper
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.WidgetColor
import io.github.eightbrows.CallTimeChecker.logic.calculate
import io.github.eightbrows.CallTimeChecker.logic.currentPeriod
import io.github.eightbrows.CallTimeChecker.logic.presentWidget
import io.github.eightbrows.CallTimeChecker.logic.toBillingSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.util.concurrent.Executors

private const val TAG = "CallTimeWidget"
private const val ACTION_MANUAL_REFRESH = "io.github.eightbrows.CallTimeChecker.widget.ACTION_MANUAL_REFRESH"

/** spec: docs/spec.md 7.2 設定変更時（同期は行わず、再集計と全ウィジェットの再描画のみ実行） */
const val ACTION_SETTINGS_CHANGED = "io.github.eightbrows.CallTimeChecker.widget.ACTION_SETTINGS_CHANGED"

private const val MIN_UPDATING_DISPLAY_MILLIS = 500L
private val EXECUTOR = Executors.newSingleThreadExecutor()

/**
 * spec: docs/spec.md 7.2 設定変更時のトリガー。SettingsRepository.save() 完了時に呼び出す想定。
 * 自身への明示的Intentのため、manifest の intent-filter には依存しない（ACTION_MANUAL_REFRESH と同様）。
 */
fun notifyWidgetsSettingsChanged(context: Context) {
    val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_SETTINGS_CHANGED)
    context.sendBroadcast(intent)
}

/**
 * spec: docs/spec.md 5.5 ウィジェット / 7.1 ウィジェット更新フロー。
 * onUpdate / onReceive はメインスレッド・10秒制限のため goAsync() + Executor でバックグラウンド実行する。
 */
class CallTimeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking { refreshAndRender(context, appWidgetManager, appWidgetIds) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        Log.d(TAG, "onReceive action=${intent.action}")
        if (intent.action != ACTION_MANUAL_REFRESH && intent.action != ACTION_SETTINGS_CHANGED) return

        val appWidgetManager = AppWidgetManager.getInstance(context)
        val appWidgetIds = appWidgetManager.getAppWidgetIds(ComponentName(context, CallTimeWidgetProvider::class.java))
        if (appWidgetIds.isEmpty()) return

        // spec 7.2: 設定変更時は同期を行わず、再集計と再描画のみ。手動更新時のみ同期する
        val sync = intent.action == ACTION_MANUAL_REFRESH
        var minDisplayUntil: Long? = null

        if (sync) {
            Log.d(TAG, "manual refresh received, ids=${appWidgetIds.toList()}")
            // タップの反応をすぐに示すため、バックグラウンド処理の前に一瞬「更新中」を表示する
            val updatingViews = buildUpdatingViews(context)
            for (id in appWidgetIds) {
                appWidgetManager.updateAppWidget(id, updatingViews)
            }
            Log.d(TAG, "updating placeholder shown for ${appWidgetIds.toList()}")
            minDisplayUntil = SystemClock.elapsedRealtime() + MIN_UPDATING_DISPLAY_MILLIS
        } else {
            Log.d(TAG, "settings changed received, ids=${appWidgetIds.toList()}")
        }

        val pendingResult = goAsync()
        EXECUTOR.execute {
            try {
                runBlocking { refreshAndRender(context, appWidgetManager, appWidgetIds, minDisplayUntil, sync) }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun refreshAndRender(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
        minDisplayUntil: Long? = null,
        sync: Boolean = true
    ) {
        val hasPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALL_LOG) ==
            PackageManager.PERMISSION_GRANTED

        val views = if (!hasPermission) {
            buildNoPermissionViews(context)
        } else {
            try {
                buildNormalViews(context, sync)
            } catch (e: Exception) {
                Log.e(TAG, "refresh failed", e)
                buildErrorViews(context)
            }
        }

        // タップ直後に表示した「更新中」が一瞬で消えないよう、最低表示時間に満たない分だけ待つ
        if (minDisplayUntil != null) {
            val remaining = minDisplayUntil - SystemClock.elapsedRealtime()
            if (remaining > 0) delay(remaining)
        }

        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
        Log.d(TAG, "updateAppWidget done for ${appWidgetIds.toList()}")
    }

    private suspend fun buildNormalViews(context: Context, sync: Boolean): RemoteViews {
        val dbHelper = CallRecordDbHelper(context)
        try {
            val appSettings = SettingsRepository(context).load()
            val settings = toBillingSettings(appSettings)
            if (sync) {
                withContext(Dispatchers.IO) {
                    CallLogSync(context.contentResolver, dbHelper).sync()
                }
            }
            val period = currentPeriod(appSettings.startDay, ZoneId.systemDefault())
            val records = withContext(Dispatchers.IO) {
                dbHelper.queryRange(period.first, period.second)
            }
            val result = calculate(records, settings)
            val content = presentWidget(result, settings)

            val views = RemoteViews(context.packageName, R.layout.widget_call_time)
            views.setTextViewText(R.id.widget_line1, content.line1)
            views.setTextViewText(R.id.widget_line2, content.line2)
            val bgColor = when (content.color) {
                WidgetColor.NORMAL -> R.color.widget_bg_normal
                WidgetColor.WARNING -> R.color.widget_bg_warning
                WidgetColor.OVER -> R.color.widget_bg_over
            }
            views.setInt(R.id.widget_root, "setBackgroundResource", bgColor)
            val textColor = if (content.color == WidgetColor.NORMAL) {
                context.getColor(R.color.widget_text_normal)
            } else {
                context.getColor(R.color.widget_text_on_color)
            }
            views.setTextColor(R.id.widget_line1, textColor)
            views.setTextColor(R.id.widget_line2, textColor)
            views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
            return views
        } finally {
            dbHelper.close()
        }
    }

    private fun buildNoPermissionViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        views.setTextViewText(R.id.widget_line1, "タップして権限を許可")
        views.setTextViewText(R.id.widget_line2, "")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_normal))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_normal))

        val intent = Intent(context, MainActivity::class.java).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
        return views
    }

    private fun buildUpdatingViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        views.setTextViewText(R.id.widget_line1, "更新中…")
        views.setTextViewText(R.id.widget_line2, "")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_normal)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_normal))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_normal))
        return views
    }

    private fun buildErrorViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_call_time)
        views.setTextViewText(R.id.widget_line1, "更新失敗")
        views.setTextViewText(R.id.widget_line2, "タップして再試行")
        views.setInt(R.id.widget_root, "setBackgroundResource", R.color.widget_bg_over)
        views.setTextColor(R.id.widget_line1, context.getColor(R.color.widget_text_on_color))
        views.setTextColor(R.id.widget_line2, context.getColor(R.color.widget_text_on_color))
        views.setOnClickPendingIntent(R.id.widget_root, manualRefreshPendingIntent(context))
        return views
    }

    private fun manualRefreshPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, CallTimeWidgetProvider::class.java).setAction(ACTION_MANUAL_REFRESH)
        return PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/SettingsScreen.kt {#uisettingsscreenkt}

```kotlin
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
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Color.kt {#uithemecolorkt}

```kotlin
package io.github.eightbrows.CallTimeChecker.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Type.kt {#uithemetypekt}

```kotlin
package io.github.eightbrows.CallTimeChecker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)
```

---

## app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/Theme.kt {#uithemethemekt}

```kotlin
package io.github.eightbrows.CallTimeChecker.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)

@Composable
fun CallTimeCheckerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

## app/src/main/res/layout/widget_call_time.xml {#reslayoutwidget_call_timexml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:id="@+id/widget_root"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:gravity="center"
    android:background="@color/widget_bg_normal"
    android:padding="8dp">

    <TextView
        android:id="@+id/widget_line1"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:textSize="18sp"
        android:textStyle="bold"
        android:textColor="@color/widget_text_normal" />

    <TextView
        android:id="@+id/widget_line2"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="4dp"
        android:textSize="14sp"
        android:textColor="@color/widget_text_normal" />

</LinearLayout>
```

---

## app/src/main/res/xml/call_time_widget_info.xml {#resxmlcall_time_widget_infoxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- spec: docs/spec.md 5.5 ウィジェット（2x2 推奨、30分自動更新） -->
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="110dp"
    android:minHeight="110dp"
    android:targetCellWidth="2"
    android:targetCellHeight="2"
    android:updatePeriodMillis="1800000"
    android:initialLayout="@layout/widget_call_time"
    android:resizeMode="horizontal|vertical"
    android:widgetCategory="home_screen"
    android:previewImage="@mipmap/ic_launcher" />
```

---

## app/src/main/res/values/colors.xml {#resvaluescolorsxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="purple_200">#FFBB86FC</color>
    <color name="purple_500">#FF6200EE</color>
    <color name="purple_700">#FF3700B3</color>
    <color name="teal_200">#FF03DAC5</color>
    <color name="teal_700">#FF018786</color>
    <color name="black">#FF000000</color>
    <color name="white">#FFFFFFFF</color>

    <!-- spec: docs/spec.md 5.5.3 ウィジェット配色 -->
    <color name="widget_bg_normal">#FFFFFFFF</color>
    <color name="widget_bg_warning">#FFFFA000</color>
    <color name="widget_bg_over">#FFD32F2F</color>
    <color name="widget_text_normal">#FF000000</color>
    <color name="widget_text_on_color">#FFFFFFFF</color>
</resources>
```

---

## app/src/main/res/values/strings.xml {#resvaluesstringsxml}

```xml
<resources>
    <string name="app_name">CallTimeChecker</string>
</resources>
```

---

## app/src/main/res/values/themes.xml {#resvaluesthemesxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.CallTimeChecker" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

---

## app/src/main/res/xml/backup_rules.xml {#resxmlbackup_rulesxml}

```xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample backup rules file; uncomment and customize as necessary.
   See https://developer.android.com/guide/topics/data/autobackup
   for details.
   Note: This file is ignored for devices older than API 31
   See https://developer.android.com/about/versions/12/backup-restore
-->
<full-backup-content>
    <!--
   <include domain="sharedpref" path="."/>
   <exclude domain="sharedpref" path="device.xml"/>
-->
</full-backup-content>
```

---

## app/src/main/res/xml/data_extraction_rules.xml {#resxmldata_extraction_rulesxml}

```xml
<?xml version="1.0" encoding="utf-8"?><!--
   Sample data extraction rules file; uncomment and customize as necessary.
   See https://developer.android.com/about/versions/12/backup-restore#xml-changes
   for details.
-->
<data-extraction-rules>
    <cloud-backup>
        <!-- TODO: Use <include> and <exclude> to control what is backed up.
        <include .../>
        <exclude .../>
        -->
    </cloud-backup>
    <!--
    <device-transfer>
        <include .../>
        <exclude .../>
    </device-transfer>
    -->
</data-extraction-rules>
```

---

## app/src/main/res/mipmap-anydpi/ic_launcher.xml {#resmipmap-anydpiic_launcherxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

## app/src/main/res/mipmap-anydpi/ic_launcher_round.xml {#resmipmap-anydpiic_launcher_roundxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
```

---

## app/src/main/res/drawable/ic_launcher_background.xml {#resdrawableic_launcher_backgroundxml}

```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#3DDC84"
        android:pathData="M0,0h108v108h-108z" />
    <path
        android:fillColor="#00000000"
        android:pathData="M9,0L9,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,0L19,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,0L29,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,0L39,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,0L49,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,0L59,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,0L69,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,0L79,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M89,0L89,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M99,0L99,108"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,9L108,9"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,19L108,19"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,29L108,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,39L108,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,49L108,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,59L108,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,69L108,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,79L108,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,89L108,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M0,99L108,99"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,29L89,29"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,39L89,39"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,49L89,49"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,59L89,59"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,69L89,69"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M19,79L89,79"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M29,19L29,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M39,19L39,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M49,19L49,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M59,19L59,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M69,19L69,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
    <path
        android:fillColor="#00000000"
        android:pathData="M79,19L79,89"
        android:strokeWidth="0.8"
        android:strokeColor="#33FFFFFF" />
</vector>
```

---

## app/src/main/res/drawable/ic_launcher_foreground.xml {#resdrawableic_launcher_foregroundxml}

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path android:pathData="M31,63.928c0,0 6.4,-11 12.1,-13.1c7.2,-2.6 26,-1.4 26,-1.4l38.1,38.1L107,108.928l-32,-1L31,63.928z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:endX="85.84757"
                android:endY="92.4963"
                android:startX="42.9492"
                android:startY="49.59793"
                android:type="linear">
                <item
                    android:color="#44000000"
                    android:offset="0.0" />
                <item
                    android:color="#00000000"
                    android:offset="1.0" />
            </gradient>
        </aapt:attr>
    </path>
    <path
        android:fillColor="#FFFFFF"
        android:fillType="nonZero"
        android:pathData="M65.3,45.828l3.8,-6.6c0.2,-0.4 0.1,-0.9 -0.3,-1.1c-0.4,-0.2 -0.9,-0.1 -1.1,0.3l-3.9,6.7c-6.3,-2.8 -13.4,-2.8 -19.7,0l-3.9,-6.7c-0.2,-0.4 -0.7,-0.5 -1.1,-0.3C38.8,38.328 38.7,38.828 38.9,39.228l3.8,6.6C36.2,49.428 31.7,56.028 31,63.928h46C76.3,56.028 71.8,49.428 65.3,45.828zM43.4,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2c-0.3,-0.7 -0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C45.3,56.528 44.5,57.328 43.4,57.328L43.4,57.328zM64.6,57.328c-0.8,0 -1.5,-0.5 -1.8,-1.2s-0.1,-1.5 0.4,-2.1c0.5,-0.5 1.4,-0.7 2.1,-0.4c0.7,0.3 1.2,1 1.2,1.8C66.5,56.528 65.6,57.328 64.6,57.328L64.6,57.328z"
        android:strokeWidth="1"
        android:strokeColor="#00000000" />
</vector>
```
