/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.widget.FrameLayout
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.google.common.truth.Truth.assertThat
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The backdrop a Compose part in a view layout gives its glass. */
@RunWith(RobolectricTestRunner::class)
class WindowBackdropTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun givesItsContentABackdrop() {
        var seen: PvotGlassBackdrop? = null
        compose.setContent {
            WindowBackdrop {
                seen = LocalPvotGlassBackdrop.current
                Text("Dock")
            }
        }
        compose.onNodeWithText("Dock").assertExists()
        assertThat(seen).isNotNull()
    }

    @Test
    fun drawsWithAViewBehind() {
        var seen: PvotGlassBackdrop? = null
        compose.setContent {
            val behind = FrameLayout(LocalContext.current)
            WindowBackdrop(behind) {
                seen = LocalPvotGlassBackdrop.current
                Text("Dock")
            }
        }
        compose.onNodeWithText("Dock").assertExists()
        assertThat(seen).isNotNull()
    }
}
