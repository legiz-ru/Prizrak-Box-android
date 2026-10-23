package com.github.kr328.clash.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import com.github.kr328.clash.design.R

/** The app's own language (Android 13+), or "as the system". */
fun Context.appLanguageSummary(): String {
    val locales = AppCompatDelegate.getApplicationLocales()
    val locale = if (locales.isEmpty) null else locales[0]
    return locale?.getDisplayName(locale)?.replaceFirstChar { it.uppercase() }
        ?: getString(R.string.language_follow_system)
}

/** The system's per-app language screen (Android 13+), the app's details before that. */
fun Context.openAppLanguageSettings() {
    val uri = Uri.fromParts("package", packageName, null)
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Intent(Settings.ACTION_APP_LOCALE_SETTINGS, uri)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
    }
    runCatching { startActivity(intent) }
}
