/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.blockreportspam

import android.os.Looper
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The block and report spam dialogs: what their buttons report and how they close. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class BlockReportSpamDialogsTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()

    private val activity: FragmentActivity by lazy {
        Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
    }

    private fun show(fragment: DialogFragment): DialogFragment {
        compose.runOnUiThread { fragment.showNow(activity.supportFragmentManager, "dialog") }
        compose.waitForIdle()
        return fragment
    }

    private fun idle() {
        shadowOf(Looper.getMainLooper()).idle()
        compose.waitForIdle()
    }

    private fun blockAndReport() =
        show(
            BlockReportSpamDialogs.DialogFragmentForBlockingNumberAndOptionallyReportingAsSpam
                .newInstance(
                    "555-0100",
                    false,
                    { events += "block spam=$it" },
                    { events += "dismissed" },
                )
        )

    @Test
    fun blockingReportsTheSpamCheckbox() {
        val fragment = blockAndReport()
        compose.onNodeWithText("Block 555-0100?").assertExists()
        compose.onNodeWithText("Report call as spam").performClick()
        compose.onNodeWithText("BLOCK").performClick()
        idle()
        assertThat(events).containsExactly("dismissed", "block spam=true").inOrder()
        assertThat(fragment.isAdded).isFalse()
    }

    @Test
    fun cancelClosesWithoutConfirming() {
        val fragment =
            show(
                BlockReportSpamDialogs.DialogFragmentForUnblockingNumber.newInstance(
                    "555-0100",
                    { events += "unblock" },
                    { events += "dismissed" },
                )
            )
        compose.onNodeWithText("Unblock 555-0100?").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        idle()
        assertThat(events).containsExactly("dismissed")
        assertThat(fragment.isAdded).isFalse()
    }

    @Test
    fun reportingNotSpamConfirms() {
        show(
            BlockReportSpamDialogs.DialogFragmentForReportingNotSpam.newInstance(
                "555-0100",
                { events += "report" },
                null,
            )
        )
        compose.onNodeWithText("Report").performClick()
        idle()
        assertThat(events).containsExactly("report")
    }

    @Test
    fun blockingANumberConfirms() {
        show(
            BlockReportSpamDialogs.DialogFragmentForBlockingNumber.newInstance(
                "555-0100",
                { events += "block" },
                null,
            )
        )
        compose.onNodeWithText("BLOCK").performClick()
        idle()
        assertThat(events).containsExactly("block")
    }

    @Test
    fun screenshotDark() {
        blockAndReport()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/block_report_spam_dark.png")
    }

    @Test
    @Config(qualifiers = "en-rUS-w411dp-h891dp-notnight-xxhdpi")
    fun screenshotLight() {
        blockAndReport()
        compose
            .onAllNodes(isRoot())
            .onLast()
            .captureRoboImage("src/test/screenshots/dialogs/block_report_spam_light.png")
    }
}
