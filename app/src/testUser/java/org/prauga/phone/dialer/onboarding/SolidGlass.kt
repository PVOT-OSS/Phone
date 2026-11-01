/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults

/**
 * Shows [content] with the glass solid. Robolectric cannot run the glass shaders, and solid glass
 * holds the live background still, so the test clock can go idle.
 */
@Composable
internal fun SolidGlass(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalPvotGlassReduced provides false,
        LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
        content = content,
    )
}
