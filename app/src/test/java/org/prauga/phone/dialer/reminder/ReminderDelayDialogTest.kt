/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.reminder

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner

/** The reminder dialog offers each delay and hands the chosen one back. */
@RunWith(RobolectricTestRunner::class)
class ReminderDelayDialogTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun pickingADelayReportsIt() {
        var picked: ReminderDelay? = null
        compose.setContent {
            PhoneTheme {
                ReminderDelayDialog(name = "Ada Lovelace", onPick = { picked = it }, onDismiss = {})
            }
        }

        compose.onNodeWithText("Remind me to call Ada Lovelace").assertIsDisplayed()
        for (delay in ReminderDelay.entries) compose
            .onNodeWithTag(TestTags.callbackReminderDelay(delay.name))
            .assertIsDisplayed()
        compose.onNodeWithText("In an hour").performClick()

        assertThat(picked).isEqualTo(ReminderDelay.IN_AN_HOUR)
    }

    @Test
    fun cancelDismisses() {
        var dismissed = false
        compose.setContent {
            PhoneTheme {
                ReminderDelayDialog(
                    name = "+15550199",
                    onPick = {},
                    onDismiss = { dismissed = true },
                )
            }
        }
        compose.onNodeWithText("Cancel").performClick()
        assertThat(dismissed).isTrue()
    }
}
