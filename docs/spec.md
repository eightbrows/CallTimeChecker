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
| targetSdk | 37（targetSdk 37 = Android 17（API level 37、コードネーム Cinnamon Bun、2026年6月16日リリース）。READ_CALL_LOG・CallLog・SharedPreferencesの挙動に直接影響する変更点は確認されていない（ACCESS_LOCAL_NETWORK権限必須化・SMS OTP遅延・大画面UI強制・リフレクション制限などのAndroid 17新規変更点は、いずれも本アプリの機能範囲外）） |
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
    val countedSec: Int,       // 定額対象として計上した通話の実時間合計
    val quotaConsumedSec: Int, // 実際の定額枠消費量（通話ごとに単位切り上げした値の合計）
    val billedSec: Int,        // 課金対象秒数（単位切り上げ済み）
    val amount: Int,           // 概算料金（円）
    val excludedSec: Int,      // 除外番号への通話の実時間合計
    val callCount: Int,        // 対象通話件数
    val billedCallCount: Int   // 課金が発生した通話件数
)

fun calculate(records: List<CallRecord>, s: Settings): Result {
    var pool = s.monthlyFreeSec
    var countedSec = 0
    var quotaConsumedSec = 0
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
        // 枠が尽きた後も、消費量そのものは切り上げ値の合計として積み上げる
        quotaConsumedSec += units
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
        quotaConsumedSec = quotaConsumedSec,
        billedSec = billedSec,
        amount = billedSec / s.unitSec * s.unitPrice,
        excludedSec = excludedSec,
        callCount = callCount,
        billedCallCount = billedCallCount
    )
}

fun ceilDiv(a: Int, b: Int): Int = (a + b - 1) / b
```

`countedSec` と `quotaConsumedSec` は用途が異なる。

| フィールド | 意味 | 主な用途 |
|---|---|---|
| `countedSec` | 通話の実時間合計（切り上げなし） | 実際に何分話したかの参考表示 |
| `quotaConsumedSec` | 通話ごとに課金単位へ切り上げた値の合計 | 定額枠の使用量表示・使用率による配色判定 |

短い通話が多いほど両者は乖離する（例: 2 秒の通話 39 件は `countedSec = 78` 秒だが、30 秒単位では `quotaConsumedSec = 1170` 秒）。定額枠を実際に減らすのは後者のため、枠に対する使用量表示には `quotaConsumedSec` を使う。

#### 5.4.4 計算上の決定事項

| 論点 | 決定 | 理由 |
|---|---|---|
| 切り上げの単位 | 通話ごとに切り上げ | キャリアの課金方式に最も近い |
| 定額枠の消費量 | 切り上げ**後**の秒数（`quotaConsumedSec`） | 同上 |
| 枠が尽きた後の消費量 | 切り上げ値の合計を積み上げ続ける | 枠に対して何分超過したかを表示するため |
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

分子・超過量・配色判定はいずれも `Result.quotaConsumedSec`（切り上げ後の定額枠消費量、5.4.3）を用いる。実通話時間（`countedSec`）ではないため、短い通話が多い月は実際に話した時間より大きい値が表示される。これは定額枠が実際に減る量と一致させるための意図的な仕様。

**1 通話定額型（`monthlyFreeSec = 0`）**

分母となる枠が存在しないため、テンプレートを切り替える。消費すべき枠が無いので、こちらは実通話時間（`countedSec`）を表示する。

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
| サマリ | 枠消費量、定額枠、残り時間、実通話時間、概算料金、集計期間（`2026/08/25 - 2026/09/24`） |
| 内訳リスト | 期間内の通話一覧。日時、番号、通話時間、判定結果（定額内 / 課金 ¥XX / 除外 / 未応答）、枠消費秒数。「全件 / 課金対象のみ」の表示フィルタ切り替え可 |
| 権限 | 未許可時に要求ボタンを表示 |
| 操作 | 手動更新、設定画面への遷移 |

サマリの「枠消費量 / 定額枠」と「残り時間」は、ウィジェット（5.5.1）と数字が食い違わないよう同じ基準で算出する。すなわち月間定額型（`monthlyFreeSec > 0`）では `Result.quotaConsumedSec`、1 通話定額型では `Result.countedSec` を分子とする。実際に何分話したかは別途「実通話時間」として `countedSec` を併記する。

内訳リストは、キャリアの請求明細と突き合わせて端数処理・除外ルールの妥当性を検証する唯一の手段であるため、初期リリースに含める。

各行の判定結果は「実際に料金が発生したか」を表すため `CallDetail.billedSec` 基準のままとする。加えて、サマリの枠消費量と突き合わせられるよう、枠を消費した通話には `CallDetail.quotaConsumedSec`（切り上げ後の秒数）を `定額内・枠消費 30秒` の形式で併記する。

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