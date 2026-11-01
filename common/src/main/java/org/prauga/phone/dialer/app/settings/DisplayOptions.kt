/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.app.UiModeManager
import android.content.Context
import android.os.UserManager
import androidx.appcompat.app.AppCompatDelegate
import androidx.preference.PreferenceManager
import org.prauga.phone.common.R

/** The app's display options: its theme and whether the call screen stays in portrait. */
object DisplayOptions {

    /**
     * Applies the chosen theme (light, dark or the system's) to the whole app: to the AppCompat
     * screens and, through the platform's per-app night mode, to the Compose ones.
     */
    @JvmStatic
    fun applyTheme(context: Context) {
        applyTheme(context, themeValue(context))
    }

    /** Applies the theme preference [value] to the whole app. */
    @JvmStatic
    fun applyTheme(context: Context, value: String?) {
        AppCompatDelegate.setDefaultNightMode(nightModeOf(value))
        context.getSystemService(UiModeManager::class.java)?.setApplicationNightMode(
            when (value) {
                THEME_LIGHT -> UiModeManager.MODE_NIGHT_NO
                THEME_DARK -> UiModeManager.MODE_NIGHT_YES
                else -> UiModeManager.MODE_NIGHT_AUTO
            },
        )
    }

    /** The AppCompat night mode for a theme preference value. */
    @JvmStatic
    fun nightModeOf(value: String?): Int = when (value) {
        THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
        THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
        else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    }

    /**
     * Whether the voice call screen should stay in portrait: when the user asked for it, and always
     * before the first unlock after a restart.
     */
    @JvmStatic
    fun keepCallScreenPortrait(context: Context): Boolean {
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (!unlocked) return true
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getBoolean(context.getString(R.string.display_options_force_portrait_key), false)
    }

    private fun themeValue(context: Context): String? {
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (!unlocked) return null
        return PreferenceManager.getDefaultSharedPreferences(context)
            .getString(context.getString(R.string.display_options_theme_key), THEME_SYSTEM)
    }

    const val THEME_LIGHT = "1"
    const val THEME_DARK = "2"
    const val THEME_SYSTEM = "-1"
}
