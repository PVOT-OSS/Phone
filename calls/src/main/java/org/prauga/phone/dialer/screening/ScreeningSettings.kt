/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.PreferenceManager

/**
 * The user's chosen [CategoryAction] for each [CallerCategory], kept in the default preferences.
 */
class ScreeningSettings(private val prefs: SharedPreferences) {

    /** The action for [category]; [CategoryAction.RING] until the user picks another. */
    fun actionFor(category: CallerCategory): CategoryAction {
        val value = prefs.getString(keyFor(category), null) ?: return CategoryAction.RING
        return CategoryAction.entries.firstOrNull { valueOf(it) == value } ?: CategoryAction.RING
    }

    fun setAction(category: CallerCategory, action: CategoryAction) {
        prefs.edit { putString(keyFor(category), valueOf(action)) }
    }

    companion object {
        fun from(context: Context): ScreeningSettings =
            ScreeningSettings(PreferenceManager.getDefaultSharedPreferences(context))

        /** The preference key holding the action for [category]. */
        fun keyFor(category: CallerCategory): String =
            "screening_action_" + category.name.lowercase()

        /** The stored value for [action]. */
        fun valueOf(action: CategoryAction): String = action.name.lowercase()
    }
}
