/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The calling account chooser. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class AccountSelectionTest {

    @get:Rule val compose = createComposeRule()

    private fun account(id: String, label: String, number: String) =
        CallingAccount(PhoneAccountHandle(ComponentName("com.android.phone", "Telephony"), id), label, number, null)

    private val accounts = listOf(account("1", "Work SIM", "+1 555-0100"), account("2", "Personal SIM", "+1 555-0199"))

    @Test
    fun noChooserWithoutANumber() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(AccountSelectionActivity.createIntent(context, "", CallInitiationType.Type.CALL_DETAILS)).isNull()
    }

    @Test
    fun pickingAnAccountChoosesIt() {
        var picked: CallingAccount? = null
        compose.setContent { PvotAppTheme { AccountSheet(accounts, onSelect = { picked = it }, onDismiss = {}) } }
        compose.onNodeWithTag(TestTags.callingAccount("2")).performClick()
        assertThat(picked?.label).isEqualTo("Personal SIM")
    }

    @Test
    fun screenshotDark() {
        compose.setContent { PvotAppTheme { AccountSheet(accounts, onSelect = {}, onDismiss = {}) } }
        compose.waitForIdle()
        compose.onAllNodes(isRoot()).onFirst().captureRoboImage("src/test/screenshots/dialogs/account_selection_dark.png")
    }
}
