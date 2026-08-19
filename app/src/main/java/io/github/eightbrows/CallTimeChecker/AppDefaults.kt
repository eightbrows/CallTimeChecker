package io.github.eightbrows.CallTimeChecker

import io.github.eightbrows.CallTimeChecker.logic.DEFAULT_EXCLUDE_PREFIXES
import io.github.eightbrows.CallTimeChecker.logic.Settings

/**
 * spec: docs/spec.md 5.7 初期値。設定画面 (5.7) は未実装のため暫定固定値を使用する。
 * プラン形式は「月間定額型」相当（monthlyFreeSec のみ正、perCallFreeSec=0）。
 * MainActivity（アプリ本体）とウィジェットの両方から共有する。
 */
val DEFAULT_SETTINGS = Settings(
    monthlyFreeSec = 70 * 60,
    perCallFreeSec = 0,
    unitSec = 30,
    unitPrice = 22,
    excludePrefixes = DEFAULT_EXCLUDE_PREFIXES
)
const val DEFAULT_START_DAY = 1
