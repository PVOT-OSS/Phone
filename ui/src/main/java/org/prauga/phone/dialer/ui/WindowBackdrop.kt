/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.view.View
import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import androidx.core.graphics.withTranslation
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop

/**
 * Records what the window shows behind [content] as a glass backdrop, for a Compose part placed in
 * a view layout, whose background and neighbours are views rather than composables. Draws the
 * window's background and then [behind], each moved to where it sits relative to this part, so the
 * glass in [content] frosts what is really under it. [behind]'s children are drawn from their own
 * render nodes, so the copy follows them as they scroll.
 *
 * The copy is only for the glass: on screen this part draws [content] alone.
 *
 * @param behind The view under this part, such as the container of a screen's lists; never a view
 *   that holds this part
 */
@Composable
fun WindowBackdrop(behind: View? = null, content: @Composable () -> Unit) {
    val backdrop = rememberPvotGlassBackdrop()
    val root = LocalView.current.rootView
    var inWindow by remember { mutableStateOf(Offset.Zero) }
    // Reused on every draw, which runs each frame something behind changes.
    val behindLocation = remember { IntArray(2) }
    // A child of [behind] shown, hidden or moved changes [behind]'s own drawing, so redraw then.
    var layouts by remember { mutableIntStateOf(0) }
    if (behind != null) {
        DisposableEffect(behind) {
            val listener = ViewTreeObserver.OnGlobalLayoutListener { layouts++ }
            behind.viewTreeObserver.addOnGlobalLayoutListener(listener)
            onDispose { behind.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
        }
    }
    Box(Modifier.onGloballyPositioned { inWindow = it.positionInWindow() }) {
        Box(
            Modifier.matchParentSize()
                .graphicsLayer { alpha = 0f }
                .pvotGlassBackdrop(backdrop)
                .drawBehind {
                    layouts
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        root.background?.let { background ->
                            native.withTranslation(-inWindow.x, -inWindow.y) {
                                background.draw(this)
                            }
                        }
                        if (behind != null && behind.isShown) {
                            behind.getLocationInWindow(behindLocation)
                            native.withTranslation(
                                behindLocation[0] - inWindow.x,
                                behindLocation[1] - inWindow.y,
                            ) {
                                behind.draw(this)
                            }
                        }
                    }
                }
        )
        CompositionLocalProvider(LocalPvotGlassBackdrop provides backdrop) { content() }
    }
}
