/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Which step onboarding is on, and when a denied permission counts as blocked. */
class OnboardingStateTest {

    private val all = PermissionGroup.entries.toSet()

    @Test
    fun withoutTheRoleItAsksForTheRoleFirst() {
        assertThat(OnboardingState(isDefaultDialer = false, granted = all).step).isEqualTo(OnboardingStep.ROLE)
    }

    @Test
    fun skippingTheRoleMovesOnToPermissions() {
        assertThat(OnboardingState(roleSkipped = true).step).isEqualTo(OnboardingStep.PERMISSIONS)
    }

    @Test
    fun skippingTheRoleWithEverythingGrantedIsDone() {
        assertThat(OnboardingState(roleSkipped = true, granted = all).step).isEqualTo(OnboardingStep.DONE)
    }

    @Test
    fun theDefaultAppWithEveryPermissionIsDone() {
        assertThat(OnboardingState(isDefaultDialer = true, granted = all).step).isEqualTo(OnboardingStep.DONE)
    }

    @Test
    fun aDenialWithARationaleCanBeAskedAgain() {
        val state = OnboardingState(isDefaultDialer = true).afterPermissionRequest(
            nowGranted = setOf(PermissionGroup.PHONE),
            asked = PermissionGroup.entries,
            showsRationale = { true },
        )
        assertThat(state.blocked).isEmpty()
        assertThat(state.requestable).containsExactly(
            PermissionGroup.CALL_LOG, PermissionGroup.CONTACTS, PermissionGroup.NOTIFICATIONS,
        ).inOrder()
    }

    @Test
    fun aDenialWithoutARationaleIsBlocked() {
        val state = OnboardingState(isDefaultDialer = true).afterPermissionRequest(
            nowGranted = setOf(PermissionGroup.PHONE, PermissionGroup.CALL_LOG),
            asked = PermissionGroup.entries,
            showsRationale = { it == PermissionGroup.NOTIFICATIONS },
        )
        assertThat(state.blocked).containsExactly(PermissionGroup.CONTACTS)
        assertThat(state.requestable).containsExactly(PermissionGroup.NOTIFICATIONS)
    }

    @Test
    fun aGroupGrantedInSettingsIsNoLongerBlocked() {
        val state = OnboardingState(isDefaultDialer = true, blocked = setOf(PermissionGroup.CONTACTS))
            .afterPermissionRequest(nowGranted = setOf(PermissionGroup.CONTACTS), asked = emptyList(), showsRationale = { false })
        assertThat(state.blocked).isEmpty()
    }

    @Test
    fun groupsNotAskedForAreNotBlocked() {
        val state = OnboardingState(isDefaultDialer = true).afterPermissionRequest(
            nowGranted = emptySet(),
            asked = listOf(PermissionGroup.PHONE),
            showsRationale = { false },
        )
        assertThat(state.blocked).containsExactly(PermissionGroup.PHONE)
    }
}
