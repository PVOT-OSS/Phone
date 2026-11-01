/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.components.background.PvotBackgroundDefaults
import com.prauga.pvot.designsystem.components.background.PvotBackgroundMotion
import com.prauga.pvot.designsystem.components.background.PvotGradientBackground
import com.prauga.pvot.designsystem.components.background.PvotLiveBackground
import com.prauga.pvot.designsystem.components.background.PvotPhotoBackground
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop

/** What a [PhoneScaffold] draws under its content, for the glass over it to refract. */
sealed interface PhoneBackground {
    /** The app's background with a slow drift of color; still while the glass is solid. */
    data object Live : PhoneBackground

    /** A still gradient of [colors], for a screen that must not move, such as a call. */
    data class Static(val colors: List<Color>) : PhoneBackground

    /** A photo blurred once and dimmed, for a screen about it, such as a call with a contact's picture. */
    data class Photo(val blurred: ImageBitmap?, val fallback: List<Color>) : PhoneBackground
}

/**
 * The frame of every Phone screen: the background and the content inside one glass backdrop, and
 * the bars, floating action, snackbars and overlays as glass over it. Thin over
 * [PvotScreenScaffold], which hands its backdrop to the chrome through [LocalPvotGlassBackdrop].
 *
 * With the glass solid (the Appearance setting, battery saver, animations off) the live background
 * holds still: there is nothing to refract, and a drifting background would only cost frames.
 *
 * @param background What fills the screen under the content
 * @param topBar The bar at the top, over the content
 * @param navBar The bar at the bottom, over the content
 * @param fab The floating action above the nav bar's end
 * @param snackbarHost The snackbar host above the nav bar
 * @param overlay Sheets, dialogs and anything else drawn last, over everything
 * @param content The screen, given the padding that clears the top bar
 */
@Composable
fun PhoneScaffold(
    background: PhoneBackground = PhoneBackground.Live,
    modifier: Modifier = Modifier,
    backdrop: PvotGlassBackdrop = rememberPvotGlassBackdrop(),
    topBar: @Composable () -> Unit = {},
    navBar: @Composable () -> Unit = {},
    fab: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    PvotScreenScaffold(
        modifier = modifier,
        backdrop = backdrop,
        background = { PhoneBackgroundContent(background) },
        topBar = topBar,
        bottomBar = navBar,
        floatingAction = fab,
        snackbarHost = snackbarHost,
        overlay = overlay,
        content = content,
    )
}

@Composable
private fun PhoneBackgroundContent(background: PhoneBackground) {
    val solid = PvotGlassDefaults.currentIntensity() <= PvotGlassDefaults.SolidIntensity
    when (background) {
        PhoneBackground.Live -> PvotLiveBackground(
            Modifier.fillMaxSize(),
            motion = if (solid) PvotBackgroundMotion.Still else PvotBackgroundDefaults.motion(),
        )
        is PhoneBackground.Static -> PvotGradientBackground(Modifier.fillMaxSize(), colors = background.colors)
        is PhoneBackground.Photo -> PvotPhotoBackground(
            image = background.blurred,
            modifier = Modifier.fillMaxSize(),
            fallback = { PvotGradientBackground(Modifier.fillMaxSize(), colors = background.fallback) },
        )
    }
}
