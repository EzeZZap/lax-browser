package com.javzzap.laxbrowser.ui

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** Languages the app is translated into. [SYSTEM] follows the device language. */
enum class AppLanguage(val tag: String?) {
    SYSTEM(null),
    ENGLISH("en"),
    SPANISH("es"),
    ;

    companion object {
        /** The language chosen in the app (or the system per-app language settings). */
        fun current(): AppLanguage {
            val tags = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            return entries.firstOrNull { it.tag != null && tags.startsWith(it.tag) } ?: SYSTEM
        }

        /** Applies [language]; AppCompat recreates activities and remembers the choice. */
        fun apply(language: AppLanguage) {
            val locales = language.tag?.let { LocaleListCompat.forLanguageTags(it) } ?: LocaleListCompat.getEmptyLocaleList()
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
}
