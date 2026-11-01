/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.prauga.pvot.designsystem.effects.LocalPvotGlassIntensity
import com.prauga.pvot.designsystem.effects.LocalPvotGlassReduced
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults

/**
 * [PhoneScreenTheme] with the glass solid, for tests of screens on [PhoneScaffold]. Robolectric
 * cannot run the glass shaders, so the screenshots show the solid surfaces.
 */
@Composable
fun SolidGlassTheme(content: @Composable () -> Unit) {
    PhoneScreenTheme {
        CompositionLocalProvider(
            LocalPvotGlassReduced provides false,
            LocalPvotGlassIntensity provides PvotGlassDefaults.SolidIntensity,
            content = content,
        )
    }
}
