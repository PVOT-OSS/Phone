/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.app.Application
import android.app.role.RoleManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** When the main screen of the user build sends people to onboarding. */
@RunWith(RobolectricTestRunner::class)
class OnboardingRouterTest {

    private val context: Application = ApplicationProvider.getApplicationContext()
    private val roleManager = context.getSystemService(RoleManager::class.java)

    @Test
    fun theRouterOpensOnboardingWhenNotTheDefaultPhoneApp() {
        val intent = OnboardingRouter.intentIfNeeded(context)
        assertThat(intent?.component?.className).isEqualTo(OnboardingActivity::class.java.name)
    }

    @Test
    fun theRouterSkipsOnboardingForTheDefaultPhoneApp() {
        shadowOf(roleManager).addHeldRole(RoleManager.ROLE_DIALER)
        assertThat(OnboardingRouter.intentIfNeeded(context)).isNull()
    }

    @Test
    fun theRouterSkipsOnboardingOnceDismissed() {
        OnboardingRouter.markDismissed(context)
        assertThat(OnboardingRouter.intentIfNeeded(context)).isNull()
    }
}
