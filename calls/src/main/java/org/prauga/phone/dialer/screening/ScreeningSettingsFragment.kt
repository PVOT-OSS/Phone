/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import org.prauga.phone.calls.R

/**
 * Lets the user choose whether each kind of unknown caller rings, rings silently or is declined.
 */
class ScreeningSettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val screen = preferenceManager.createPreferenceScreen(requireContext())
        for (category in CallerCategory.entries) {
            screen.addPreference(categoryPreference(category))
        }
        screen.addPreference(
            Preference(requireContext()).apply {
                summary = getString(R.string.screening_settings_footer)
                isSelectable = false
                isIconSpaceReserved = false
            }
        )
        preferenceScreen = screen
    }

    private fun categoryPreference(category: CallerCategory) =
        ListPreference(requireContext()).apply {
            key = ScreeningSettings.keyFor(category)
            setTitle(titleOf(category))
            dialogTitle = getString(titleOf(category))
            entries = CategoryAction.entries.map { getString(labelOf(it)) }.toTypedArray()
            entryValues =
                CategoryAction.entries.map { ScreeningSettings.valueOf(it) }.toTypedArray()
            setDefaultValue(ScreeningSettings.valueOf(CategoryAction.RING))
            val description = getString(descriptionOf(category))
            summaryProvider =
                Preference.SummaryProvider<ListPreference> { "${it.entry}\n$description" }
            isIconSpaceReserved = false
        }

    private fun titleOf(category: CallerCategory) =
        when (category) {
            CallerCategory.PRIVATE_OR_HIDDEN -> R.string.screening_category_private_or_hidden
            CallerCategory.POSSIBLY_FAKED -> R.string.screening_category_possibly_faked
            CallerCategory.FIRST_TIME -> R.string.screening_category_first_time
        }

    private fun descriptionOf(category: CallerCategory) =
        when (category) {
            CallerCategory.PRIVATE_OR_HIDDEN ->
                R.string.screening_category_private_or_hidden_description
            CallerCategory.POSSIBLY_FAKED -> R.string.screening_category_possibly_faked_description
            CallerCategory.FIRST_TIME -> R.string.screening_category_first_time_description
        }

    private fun labelOf(action: CategoryAction) =
        when (action) {
            CategoryAction.RING -> R.string.screening_action_ring
            CategoryAction.SILENCE -> R.string.screening_action_silence
            CategoryAction.DECLINE -> R.string.screening_action_decline
        }
}
