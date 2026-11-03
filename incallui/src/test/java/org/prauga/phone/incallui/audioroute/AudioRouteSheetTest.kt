/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.audioroute

import android.telecom.CallAudioState
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.incallui.R
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The audio route picker: which routes it offers and what tapping one does. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "en-rUS-w411dp-h891dp-night-xxhdpi")
class AudioRouteSheetTest {

    @get:Rule val compose = createComposeRule()

    private fun state(route: Int, mask: Int) = CallAudioState(false, route, mask)

    @Test
    fun onlySupportedRoutesAreOffered() {
        val routes =
            audioRoutesOf(
                state(
                    CallAudioState.ROUTE_EARPIECE,
                    CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
                )
            ) {
                null
            }
        assertThat(routes.map { it.route })
            .containsExactly(CallAudioState.ROUTE_SPEAKER, CallAudioState.ROUTE_EARPIECE)
            .inOrder()
        assertThat(routes.single { it.selected }.route).isEqualTo(CallAudioState.ROUTE_EARPIECE)
    }

    @Test
    fun aHeadsetReplacesNothingButIsListed() {
        val routes =
            audioRoutesOf(
                state(
                    CallAudioState.ROUTE_WIRED_HEADSET,
                    CallAudioState.ROUTE_WIRED_HEADSET or CallAudioState.ROUTE_SPEAKER,
                )
            ) {
                null
            }
        assertThat(routes.map { it.icon })
            .containsExactly(R.drawable.ic_volume, R.drawable.ic_headphones)
            .inOrder()
    }

    @Test
    fun tappingARoutePicksIt() {
        val routes =
            audioRoutesOf(
                state(
                    CallAudioState.ROUTE_EARPIECE,
                    CallAudioState.ROUTE_EARPIECE or CallAudioState.ROUTE_SPEAKER,
                )
            ) {
                null
            }
        var picked: AudioRoute? = null
        compose.setContent {
            PhoneScreenTheme { AudioRouteSheet(routes, onSelect = { picked = it }) }
        }
        compose.onNodeWithText("Speaker").performClick()
        assertThat(picked?.route).isEqualTo(CallAudioState.ROUTE_SPEAKER)
    }

    @Test
    fun screenshotDark() {
        val routes =
            audioRoutesOf(
                state(
                    CallAudioState.ROUTE_SPEAKER,
                    CallAudioState.ROUTE_EARPIECE or
                        CallAudioState.ROUTE_SPEAKER or
                        CallAudioState.ROUTE_WIRED_HEADSET,
                )
            ) {
                null
            }
        compose.setContent {
            PhoneScreenTheme(fillBackground = false) { AudioRouteSheet(routes, onSelect = {}) }
        }
        compose
            .onAllNodes(isRoot())
            .onFirst()
            .captureRoboImage("src/test/screenshots/incall/audio_route_dark.png")
    }
}
