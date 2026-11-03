/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.helplines

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.coreui.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.lineageos.lib.phone.spn.Item
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.SolidGlassTheme
import org.prauga.phone.dialer.ui.TestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Which helplines are listed, how they are named, and what tapping them does. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class HelplinesTest {

    @get:Rule val compose = createComposeRule()

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val byMcc =
        mapOf(
            "310" to listOf(item("Zeta Line", "988"), item("Alpha Line", "211")),
            "234" to listOf(item("Samaritans", "116123")),
        )

    @Test
    fun helplinesForASimInTheUsersCountryAreSortedWithoutCountry() {
        val names =
            HelplineLoader.load(context.resources, listOf("us" to "310")) { byMcc.getValue(it) }
                .map { it.name }
        assertThat(names).containsExactly("Alpha Line", "Zeta Line").inOrder()
    }

    @Test
    fun aForeignSimAddsTheCountryToNames() {
        val names =
            HelplineLoader.load(context.resources, listOf("gb" to "234")) { byMcc.getValue(it) }
                .map { it.name }
        assertThat(names)
            .containsExactly(
                context.getString(R.string.helpline_name_format_country, "Samaritans", "gb")
            )
    }

    @Test
    fun twoSimsOnTheSameNetworkListItsHelplinesOnce() {
        val helplines =
            HelplineLoader.load(context.resources, listOf("us" to "310", "us" to "310")) {
                byMcc.getValue(it)
            }
        assertThat(helplines).hasSize(2)
    }

    @Test
    fun aSimWithoutAnMccIsSkipped() {
        assertThat(
                HelplineLoader.load(context.resources, listOf("us" to null)) { byMcc.getValue(it) }
            )
            .isEmpty()
    }

    @Test
    fun noHelplinesShowsTheEmptyMessage() {
        show(UiState.Success(emptyList()))
        compose.onNodeWithText(context.getString(R.string.helplines_empty)).assertIsDisplayed()
    }

    @Test
    fun theCallButtonCallsThatNumber() {
        var called: String? = null
        val helplines =
            HelplineLoader.load(context.resources, listOf("us" to "310")) { byMcc.getValue(it) }
        show(UiState.Success(helplines), onCall = { called = it })
        compose.onNodeWithTag(TestTags.helplineCall("988")).performClick()
        assertThat(called).isEqualTo("988")
    }

    @Test
    fun firstLaunchExplainsTheScreen() {
        show(UiState.Success(emptyList()), showHelp = true)
        compose.onNodeWithText(context.getString(R.string.helplines_help_title)).assertIsDisplayed()
    }

    private fun show(
        state: UiState<List<HelplineItem>>,
        showHelp: Boolean = false,
        onCall: (String) -> Unit = {},
    ) {
        compose.setContent {
            SolidGlassTheme {
                HelplinesScreen(
                    state = state,
                    showHelpAtStart = showHelp,
                    onBack = {},
                    onCall = onCall,
                )
            }
        }
    }

    private fun item(name: String, number: String) =
        Item().apply {
            setName(name)
            setNumber(number)
        }
}
