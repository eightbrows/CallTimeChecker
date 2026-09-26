package io.github.eightbrows.CallTimeChecker

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import io.github.eightbrows.CallTimeChecker.data.SettingsRepository
import io.github.eightbrows.CallTimeChecker.logic.AppLanguage
import java.util.Locale

/**
 * spec: docs/spec.md 5.9 アプリ内の表示言語の適用。
 *
 * 言語そのものの選択肢（AppLanguage）は logic 側が持ち、ここは「選ばれた言語を
 * 実際にリソース解決へ効かせる」という Android 依存の部分だけを受け持つ。
 *
 * API 33 以降は OS が「アプリごとの言語」を持っているので LocaleManager に預ける。
 * 預けた時点で OS がアプリのリソースを差し替えて Activity を作り直すため、
 * こちら側での再生成は不要。ウィジェット（別プロセスの BroadcastReceiver）にも効く。
 *
 * API 32 以下には同等の仕組みが無いため、保存値を読んで Context を包み直す
 * （localizedContext）方式で自前に適用する。Activity は attachBaseContext() で、
 * ウィジェットは描画のたびに包む。
 */

/** API 33 以降は OS 側（LocaleManager）に言語の保持と適用を任せられる */
private val HAS_PLATFORM_LOCALE_MANAGER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

/**
 * 選ばれた言語を OS に反映する（API 33 以降のみ）。
 * 設定保存後に呼ぶ。SYSTEM は空の LocaleList＝端末の言語に従うという意味。
 * API 32 以下では何もしない（呼び出し側が Activity.recreate() で作り直す）。
 */
fun applyAppLanguage(context: Context, language: AppLanguage) {
    if (!HAS_PLATFORM_LOCALE_MANAGER) return
    val manager = context.getSystemService(LocaleManager::class.java) ?: return
    manager.applicationLocales = language.toLocaleList()
}

/** AppLanguage → OS に渡す LocaleList。SYSTEM は空リスト（端末の言語に従う） */
fun AppLanguage.toLocaleList(): LocaleList {
    val tag = languageTag ?: return LocaleList.getEmptyLocaleList()
    return LocaleList.forLanguageTags(tag)
}

/**
 * 保存済みの言語を適用した Context を返す。API 32 以下でのみ意味を持ち、
 * 33 以降は OS が既に適用済みなので base をそのまま返す。
 *
 * Activity では attachBaseContext()、ウィジェットでは描画の入口で通す。
 * 「システムに従う」は包まずに base をそのまま使う（端末の設定がそのまま効く）。
 *
 * lint の AppBundleLocaleChanges は、App Bundle で言語別リソースを分割配信している場合に
 * Play Core で追加言語を取りに行けという警告。本アプリは GitHub Releases で APK を
 * そのまま配る（2.2 / 11.1）ので全言語が最初から入っており、当てはまらない。
 */
@Suppress("AppBundleLocaleChanges")
fun localizedContext(base: Context): Context {
    if (HAS_PLATFORM_LOCALE_MANAGER) return base
    val tag = SettingsRepository(base).loadLanguage().languageTag ?: return base
    val locale = Locale.forLanguageTag(tag)
    val config = Configuration(base.resources.configuration)
    config.setLocales(LocaleList(locale))
    return base.createConfigurationContext(config)
}

/**
 * Compose の LocalContext から Activity を取り出す。
 * ContextWrapper で包まれている（localizedContext もその 1 つ）ことがあるため、
 * 素のキャストではなく順に剥がして探す。
 */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * 言語の変更を画面に反映する。API 33 以降は applyAppLanguage() だけで OS が
 * Activity を作り直すので、32 以下のときだけ自前で recreate() する。
 * recreate() は Activity を作り直すので attachBaseContext() が再実行され、
 * 新しい言語でリソースが読み直される（単一 Activity 構成なのでこれで全画面に効く）。
 */
fun applyAppLanguageChange(context: Context, language: AppLanguage) {
    applyAppLanguage(context, language)
    if (!HAS_PLATFORM_LOCALE_MANAGER) context.findActivity()?.recreate()
}
