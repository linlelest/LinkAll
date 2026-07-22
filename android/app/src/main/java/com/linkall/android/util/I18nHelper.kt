package com.linkall.android.util

import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * 国际化工具：基于 AppCompat 的 per-app language API（AndroidX AppCompat 1.6+）
 * 实现 zh-CN / en-US 热切换，无需重启
 */
object I18nHelper {

    const val LANG_SYSTEM = "system"
    const val LANG_ZH = "zh-CN"
    const val LANG_EN = "en-US"

    /** 当前应用语言代码 */
    fun currentLanguage(context: Context): String {
        val locales = androidx.appcompat.app.AppCompatDelegate.getApplicationLocales()
        if (locales.isEmpty) {
            // 跟随系统
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sys = context.resources.configuration.locales[0]
                langFromLocale(sys)
            } else LANG_ZH
        }
        return langFromLocale(locales[0])
    }

    private fun langFromLocale(locale: Locale): String {
        val lang = locale.language
        return when {
            lang.startsWith("zh") -> LANG_ZH
            lang.startsWith("en") -> LANG_EN
            else -> LANG_EN
        }
    }

    /**
     * 切换应用语言（立即生效，无需重启）
     */
    fun setLanguage(lang: String) {
        val locales = when (lang) {
            LANG_ZH -> LocaleListCompat.forLanguageTags(LANG_ZH)
            LANG_EN -> LocaleListCompat.forLanguageTags(LANG_EN)
            else -> LocaleListCompat.getEmptyLocaleList() // 跟随系统
        }
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(locales)
    }

    /**
     * 初始化语言（从设置读取并应用）
     */
    fun applyLanguage(context: Context, lang: String) {
        setLanguage(lang)
    }
}
