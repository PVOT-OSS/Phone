/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.app.Application
import android.app.role.RoleManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** What onboarding's buttons start, and how it ends. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class OnboardingActivityTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val roleManager = context.getSystemService(RoleManager::class.java)

    /** Solid glass holds the live background still, so the test clock can go idle; set before launch. */
    @get:Rule(order = 0) val solidGlass = object : ExternalResource() {
        override fun before() {
            AppearanceSettings.setGlass(context, false)
        }

        override fun after() {
            AppearanceSettings.prefs(context).edit().clear().commit()
        }
    }

    @get:Rule(order = 1) val compose = createAndroidComposeRule<OnboardingActivity>()

    @Test
    fun setAsDefaultRequestsTheDialerRole() {
        compose.onNodeWithTag(TestTags.ONBOARDING_PRIMARY).performClick()
        val started = shadowOf(compose.activity).nextStartedActivityForResult
        assertThat(started.intent.action).isEqualTo(ACTION_REQUEST_ROLE)
        assertThat(started.intent.getStringExtra(EXTRA_ROLE_NAME)).isEqualTo(RoleManager.ROLE_DIALER)
    }

    @Test
    fun notNowOnTheRoleMovesOnToPermissionsThenDismisses() {
        compose.onNodeWithTag(TestTags.ONBOARDING_NOT_NOW).performClick()
        compose.onNodeWithText(context.getString(R.string.onboarding_permissions_body)).assertIsDisplayed()
        assertThat(compose.activity.isFinishing).isFalse()

        compose.onNodeWithTag(TestTags.ONBOARDING).performScrollToNode(hasTestTag(TestTags.ONBOARDING_NOT_NOW))
        compose.onNodeWithTag(TestTags.ONBOARDING_NOT_NOW).performClick()
        assertThat(compose.activity.isFinishing).isTrue()
        assertThat(OnboardingRouter.isDismissed(context)).isTrue()
    }

    @Test
    fun backDismissesSoTheMainScreenDoesNotReopenIt() {
        compose.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        assertThat(compose.activity.isFinishing).isTrue()
        assertThat(OnboardingRouter.isDismissed(context)).isTrue()
    }

    @Test
    fun theDefaultAppWithEveryPermissionFinishesStraightAway() {
        shadowOf(roleManager).addHeldRole(RoleManager.ROLE_DIALER)
        shadowOf(context).grantPermissions(*PermissionGroup.entries.flatMap { it.permissions }.toTypedArray())
        val activity = Robolectric.buildActivity(OnboardingActivity::class.java).setup().get()
        assertThat(activity.isFinishing).isTrue()
    }

    private companion object {
        // Hidden from the SDK; what RoleManager.createRequestRoleIntent builds.
        const val ACTION_REQUEST_ROLE = "android.app.role.action.REQUEST_ROLE"
        const val EXTRA_ROLE_NAME = "android.intent.extra.ROLE_NAME"
    }
}
