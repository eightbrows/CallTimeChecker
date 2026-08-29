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