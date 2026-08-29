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
