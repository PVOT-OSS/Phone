/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.Manifest
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import org.prauga.phone.dialer.R

/** The runtime permissions onboarding asks for, grouped the way Android shows them. */
enum class PermissionGroup(
    val permissions: List<String>,
    @StringRes val title: Int,
    @StringRes val reason: Int,
    @DrawableRes val icon: Int,
) {
    PHONE(
        // READ_PHONE_NUMBERS shares the dialog: Telecom asks for it to read calling accounts.
        listOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE, Manifest.permission.READ_PHONE_NUMBERS),
        R.string.onboarding_permission_phone,
        R.string.onboarding_permission_phone_reason,
        R.drawable.ic_phone,
    ),
    CALL_LOG(
        listOf(Manifest.permission.READ_CALL_LOG, Manifest.permission.WRITE_CALL_LOG),
        R.string.onboarding_permission_call_log,
        R.string.onboarding_permission_call_log_reason,
        R.drawable.ic_history,
    ),
    CONTACTS(
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS),
        R.string.onboarding_permission_contacts,
        R.string.onboarding_permission_contacts_reason,
        R.drawable.ic_users,
    ),
    NOTIFICATIONS(
        listOf(Manifest.permission.POST_NOTIFICATIONS),
        R.string.onboarding_permission_notifications,
        R.string.onboarding_permission_notifications_reason,
        R.drawable.ic_bell,
    ),
}

/** Where onboarding is: asking for the phone role, asking for permissions, or finished. */
enum class OnboardingStep { ROLE, PERMISSIONS, DONE }

/**
 * What onboarding knows about the app's setup.
 *
 * @param isDefaultDialer Whether the app holds the dialer role
 * @param roleSkipped Whether the user chose "Not now" for the role
 * @param roleDenied Whether a role request came back without the role, so Android may no longer ask
 * @param granted The permission groups whose permissions are all granted
 * @param blocked The groups Android won't ask for again; only app settings can grant them
 */
data class OnboardingState(
    val isDefaultDialer: Boolean = false,
    val roleSkipped: Boolean = false,
    val roleDenied: Boolean = false,
    val granted: Set<PermissionGroup> = emptySet(),
    val blocked: Set<PermissionGroup> = emptySet(),
) {
    val missing: List<PermissionGroup> get() = PermissionGroup.entries.filterNot { it in granted }

    /** Missing groups Android can still ask for. */
    val requestable: List<PermissionGroup> get() = missing.filterNot { it in blocked }

    val step: OnboardingStep
        get() = when {
            !isDefaultDialer && !roleSkipped -> OnboardingStep.ROLE
            missing.isNotEmpty() -> OnboardingStep.PERMISSIONS
            else -> OnboardingStep.DONE
        }

    /** This state after a permission request, given which groups Android would still explain. */
    fun afterPermissionRequest(
        nowGranted: Set<PermissionGroup>,
        asked: Collection<PermissionGroup>,
        showsRationale: (PermissionGroup) -> Boolean,
    ): OnboardingState {
        // Denied right after asking, with no rationale to show: Android won't show the dialog again.
        val newlyBlocked = asked.filter { it !in nowGranted && !showsRationale(it) }
        return copy(granted = nowGranted, blocked = (blocked + newlyBlocked) - nowGranted)
    }
}
