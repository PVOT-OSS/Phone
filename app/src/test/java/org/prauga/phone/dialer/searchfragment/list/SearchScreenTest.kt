/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.searchfragment.list

import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The search results screen. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class SearchScreenTest {

    @get:Rule val compose = createComposeRule()

    private val events = mutableListOf<String>()
    private val actions =
        object : SearchActions {
            override fun call(number: String, position: Int) {
                events += "call $number"
            }

            override fun videoCall(number: String, position: Int) {
                events += "video $number"
            }

            override fun addToContact(number: String) {
                events += "add $number"
            }

            override fun createContact(number: String) {
                events += "create $number"
            }

            override fun sendSms(number: String) {
                events += "sms $number"
            }

            override fun allowLocation() {
                events += "allow"
            }

            override fun dismissLocation() {
                events += "dismiss"
            }

            override fun turnOnPermission() {
                events += "permission"
            }

            override fun listTouched() {
                events += "touched"
            }
        }

    private fun number(key: String, name: String?, number: String, video: Boolean = false) =
        SearchRow.Number(
            key = key,
            kind = SearchRow.Kind.CONTACT,
            position = 1,
            number = number,
            name = name?.let(::AnnotatedString),
            details = AnnotatedString("Mobile $number"),
            photoUri = null,
            colorKey = key,
            canVideoCall = video,
            isWork = false,
        )

    private val rows =
        listOf(
            SearchRow.Header("h", "Contacts"),
            number("ada1", "Ada Lovelace", "+15550100"),
            number("ada2", null, "+15550109", video = true),
            SearchRow.Action("action_3", SearchAction.SEND_SMS, 3, "5550100"),
        )

    private fun show(needsPermission: Boolean = false) =
        compose.setContent { PhoneScreenTheme { SearchScreen(rows, needsPermission, actions) } }

    @Test
    fun tappingANumberCallsIt() {
        show()
        compose.onNodeWithTag(TestTags.searchRow("ada1")).performClick()
        assertThat(events).containsExactly("touched", "call +15550100").inOrder()
    }

    @Test
    fun aVideoCapableNumberOffersVideo() {
        show()
        compose.onNodeWithContentDescription("Video call").performClick()
        assertThat(events).contains("video +15550109")
    }

    @Test
    fun shortcutsActOnTheTypedNumber() {
        show()
        compose.onNodeWithTag(TestTags.searchAction(SearchAction.SEND_SMS)).performClick()
        assertThat(events).contains("sms 5550100")
    }

    @Test
    fun missingPermissionOffersToTurnItOn() {
        show(needsPermission = true)
        compose.onNodeWithText("Turn on").performClick()
        assertThat(events).contains("permission")
    }

    @Test
    fun screenshotDark() {
        show()
        compose.waitForIdle()
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/search/search_dark.png")
    }
}
