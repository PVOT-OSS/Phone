/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.content.Context
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** What each onboarding step explains and which buttons it offers. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val actions = RecordingActions()

    @Test
    fun theRoleStepExplainsWhyAndAsksForTheRole() {
        show(OnboardingState())
        compose.onNodeWithText(context.getString(R.string.onboarding_role_body)).assertIsDisplayed()
        compose.onNodeWithTag(TestTags.ONBOARDING_SETTINGS).assertDoesNotExist()
        compose.onNodeWithTag(TestTags.ONBOARDING_PRIMARY).performClick()
        assertThat(actions.calls).containsExactly("role")
    }

    @Test
    fun aDeniedRoleOffersDefaultAppsSettings() {
        show(OnboardingState(roleDenied = true))
        compose
            .onNodeWithText(context.getString(R.string.onboarding_role_denied))
            .assertIsDisplayed()
        compose.onNodeWithTag(TestTags.ONBOARDING_SETTINGS).performClick()
        assertThat(actions.calls).containsExactly("default_apps")
    }

    @Test
    fun notNowIsOfferedOnTheRoleStep() {
        show(OnboardingState())
        compose.onNodeWithTag(TestTags.ONBOARDING_NOT_NOW).performClick()
        assertThat(actions.calls).containsExactly("not_now")
    }

    @Test
    fun thePermissionStepExplainsEveryGroup() {
        show(OnboardingState(isDefaultDialer = true))
        PermissionGroup.entries.forEach { group ->
            compose.onNodeWithTag(TestTags.ONBOARDING).performScrollToNode(hasTestTag(tag(group)))
            compose.onNodeWithText(context.getString(group.reason)).assertIsDisplayed()
        }
        scrollTo(TestTags.ONBOARDING_PRIMARY).performClick()
        assertThat(actions.calls).containsExactly("permissions")
    }

    @Test
    fun grantedGroupsAreMarkedAllowed() {
        show(OnboardingState(isDefaultDialer = true, granted = setOf(PermissionGroup.PHONE)))
        val allowed = context.getString(R.string.onboarding_permission_granted)
        compose
            .onAllNodesWithContentDescription(allowed, useUnmergedTree = true)
            .assertCountEquals(1)
    }

    @Test
    fun blockedGroupsLinkToAppSettings() {
        val everythingElse = PermissionGroup.entries.toSet() - PermissionGroup.CONTACTS
        show(
            OnboardingState(
                isDefaultDialer = true,
                granted = everythingElse,
                blocked = setOf(PermissionGroup.CONTACTS),
            )
        )
        scrollTo(tag(PermissionGroup.CONTACTS))
        compose
            .onNodeWithText(context.getString(R.string.onboarding_permission_blocked))
            .assertIsDisplayed()
        // Nothing left that Android would ask for, so the only way on is app settings.
        compose.onNodeWithTag(TestTags.ONBOARDING_PRIMARY).assertDoesNotExist()
        scrollTo(TestTags.ONBOARDING_SETTINGS).performClick()
        scrollTo(TestTags.ONBOARDING_NOT_NOW).performClick()
        assertThat(actions.calls).containsExactly("app_settings", "not_now").inOrder()
    }

    private fun show(state: OnboardingState) {
        compose.setContent { PhoneScreenTheme { SolidGlass { OnboardingScreen(state, actions) } } }
    }

    private fun scrollTo(tag: String) =
        compose.onNodeWithTag(TestTags.ONBOARDING).performScrollToNode(hasTestTag(tag)).let {
            compose.onNodeWithTag(tag)
        }

    private fun tag(group: PermissionGroup) = TestTags.onboardingPermission(group.name.lowercase())

    private class RecordingActions : OnboardingActions {
        val calls = mutableListOf<String>()

        override fun onRequestRole() {
            calls += "role"
        }

        override fun onOpenDefaultApps() {
            calls += "default_apps"
        }

        override fun onRequestPermissions() {
            calls += "permissions"
        }

        override fun onOpenAppSettings() {
            calls += "app_settings"
        }

        override fun onNotNow() {
            calls += "not_now"
        }
    }
}
