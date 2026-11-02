/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.prauga.pvot.designsystem.effects.ProvidePvotGlassIntensity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings

/**
 * The Pvot theme and background a full Compose screen sits on, with test tags exposed as ids.
 *
 * It also provides the glass intensity from the user's Appearance setting, held to the device by
 * PvotLib (solid under battery saver), so every glass surface in the app reads one setting.
 *
 * @param fillBackground Whether to paint the background and fill the space; off for content that
 *   sizes itself or slides over another screen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhoneScreenTheme(
    fillBackground: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appearance by AppearanceSettings.rememberAppearance()
    PhoneTheme {
        ProvidePvotGlassIntensity(appearance.preferredIntensity) {
            Surface(
                modifier = modifier.then(if (fillBackground) Modifier.fillMaxSize() else Modifier).semantics { testTagsAsResourceId = true },
                color = if (fillBackground) MaterialTheme.colorScheme.background else Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

/**
 * The Pvot theme with Phone's accent: the palette's secondary blue in the primary roles, so every
 * Pvot component's accent (selection, filled buttons, toggles, links) is the secondary color.
 */
@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    PvotAppTheme {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.withSecondaryAccent(), content = content)
    }
}

/** This scheme with its secondary roles copied into the primary ones. */
internal fun ColorScheme.withSecondaryAccent(): ColorScheme = copy(
    primary = secondary,
    onPrimary = onSecondary,
    primaryContainer = secondaryContainer,
    onPrimaryContainer = onSecondaryContainer,
    surfaceTint = secondary,
)
