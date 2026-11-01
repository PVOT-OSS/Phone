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
import androidx.compose.ui.graphics.ImageBitmap
import com.prauga.pvot.designsystem.components.PvotScreenScaffold
import com.prauga.pvot.designsystem.components.background.PvotPhotoBackground
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop

/** What a [PhoneScaffold] draws over the theme background, under the content. */
sealed interface PhoneBackground {
    /** Nothing: the plain theme background, with the glass refracting the content under it. */
    data object Plain : PhoneBackground

    /**
     * A photo blurred once and dimmed, for a screen about it, such as a call with a contact's
     * picture.
     */
    data class Photo(val blurred: ImageBitmap?) : PhoneBackground
}

/**
 * The frame of every Phone screen: the background and the content inside one glass backdrop, and
 * the bars, floating action, snackbars and overlays as glass over it. Thin over
 * [PvotScreenScaffold], which hands its backdrop to the chrome through [LocalPvotGlassBackdrop].
 *
 * @param background What draws over the theme background under the content; plain unless a screen
 *   is about a photo
 * @param topBar The bar at the top, over the content
 * @param navBar The bar at the bottom, over the content
 * @param fab The floating action above the nav bar's end
 * @param snackbarHost The snackbar host above the nav bar
 * @param overlay Sheets, dialogs and anything else drawn last, over everything
 * @param content The screen, given the padding that clears the top bar
 */
@Composable
fun PhoneScaffold(
    background: PhoneBackground = PhoneBackground.Plain,
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
    when (background) {
        PhoneBackground.Plain -> Unit
        is PhoneBackground.Photo ->
            PvotPhotoBackground(image = background.blurred, modifier = Modifier.fillMaxSize())
    }
}
