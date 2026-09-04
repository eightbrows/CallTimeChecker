# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

CallTimeChecker is an Android app (Kotlin + Jetpack Compose). Package/namespace: `io.github.eightbrows.CallTimeChecker`. Single module: `app`.

The project currently contains only the default Android Studio "Empty Activity" Compose template (`MainActivity.kt` with a placeholder `Greeting` composable) — no app-specific features have been implemented yet.

Full product spec: `docs/spec.md`. Implementation proceeds in the phases listed in its section 11, starting with Phase 1 (DB, sync, period calculation, and billing calculation as pure functions with unit tests — no UI).

## Build system

Gradle with Kotlin DSL and a version catalog (`gradle/libs.versions.toml`). Key versions: AGP 9.3.1, Kotlin 2.2.10, compileSdk/targetSdk 37, minSdk 26, Compose BOM 2026.02.01.

Use the Gradle wrapper (`gradlew.bat` on Windows / `./gradlew` in Git Bash) for all commands — do not invoke a system-installed Gradle.

### Common commands

```
# Build debug APK
gradlew.bat assembleDebug

# Full build (includes lint + tests)
gradlew.bat build

# Run unit tests (app/src/test)
gradlew.bat test

# Run a single unit test class
gradlew.bat testDebugUnitTest --tests "io.github.eightbrows.CallTimeChecker.ExampleUnitTest"

# Run instrumented tests (app/src/androidTest) — requires a connected device/emulator
gradlew.bat connectedAndroidTest

# Lint
gradlew.bat lint

# Clean build outputs
gradlew.bat clean
```

## Architecture

- `app/src/main/java/io/github/eightbrows/CallTimeChecker/MainActivity.kt` — single entry point `ComponentActivity`, sets Compose content via `setContent`.
- `app/src/main/java/io/github/eightbrows/CallTimeChecker/ui/theme/` — standard generated Compose theme files (`Color.kt`, `Theme.kt`, `Type.kt`); wrap all screens in `CallTimeCheckerTheme`.
- `app/src/test/` — JVM unit tests (JUnit4).
- `app/src/androidTest/` — instrumented tests (JUnit4 + Espresso + Compose UI test).

Since there is no existing feature architecture (data layer, navigation, DI, networking, etc.), decide and establish these patterns deliberately when adding the first real feature rather than assuming a convention from elsewhere in the file tree.

## Git運用

- git commitはユーザー(Yohei)が手動で実施する。Claude Codeは指示がない限りcommitしないこと

## 実機確認の運用

実機(adb接続)で動作確認を行うときは、確認中に画面が自動で消えないよう、開始前後で以下を必ず実行すること。

```
# 確認開始前: 充電中はスリープしない
adb shell settings put global stay_on_while_plugged_in 3

# 確認終了後: 元の設定に戻す
adb shell settings put global stay_on_while_plugged_in 0
```

確認が途中で失敗・中断した場合も、必ず `0` に戻してから報告すること。

## 通知ルール(モールス信号ビープ)

以下のタイミングでPC側にモールス信号のビープ音を鳴らすこと。

- 実装・検証が完了し、完了報告を出す直前:「EE」

```
powershell -c "[console]::beep(800,150); Start-Sleep -Milliseconds 450; [console]::beep(800,150)"
```

- ユーザーの判断・確認が必要な場面(方針確認、実装案の選択依頼など、通常の質問時):「K」

```
powershell -c "[console]::beep(800,450); Start-Sleep -Milliseconds 150; [console]::beep(800,150); Start-Sleep -Milliseconds 150; [console]::beep(800,450)"
```

- 実機確認中に端末のロックを検知した場合: 「K」を鳴らした上で、adb経由の解除試行(`input keyevent KEYCODE_WAKEUP`等)は行わず、その場で作業を止めてユーザーにロック解除を求める。ユーザーからの応答を待たずに他の作業や代替の確認方法に進まないこと。
