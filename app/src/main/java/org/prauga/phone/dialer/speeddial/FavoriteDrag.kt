/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.geometry.Size

/** A laid out item of the favorites row: its key and its bounds within the row. */
class FavoriteItemBounds(val key: Any, val bounds: Rect)

/**
 * Drag state of the favorites row: which favorite is lifted, how far it has moved, and whether
 * it is over the remove target.
 *
 * Favorites are keyed by [favoriteKey]. While dragging, the lifted favorite swaps places with the
 * one under its center through [onMove]; dropping it on the remove target calls [onRemove].
 * [items] gives the visible items' bounds, as [rowItems] does for a horizontal lazy row.
 */
class FavoriteDragState(
    private val items: () -> List<FavoriteItemBounds>,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onRemove: (key: Any) -> Unit,
) {
    /** The lifted favorite, or null. */
    var draggingKey by mutableStateOf<Any?>(null)
        private set

    /** Whether the lifted favorite has moved, which shows the remove target. */
    var isMoving by mutableStateOf(false)
        private set

    /** Distance from the lifted favorite's layout position to the finger. */
    var offset by mutableStateOf(Offset.Zero)
        private set

    /** Whether the lifted favorite is over the remove target. */
    var isOverRemove by mutableStateOf(false)
        private set

    /** The row's and the remove target's bounds in the root, set from layout. */
    var rowBounds: Rect = Rect.Zero
    var removeBounds: Rect = Rect.Zero

    /** Keys of the favorites in order, updated each composition. */
    var favoriteKeys: List<Any> = emptyList()

    fun lift(key: Any) {
        draggingKey = key
        offset = Offset.Zero
        isMoving = false
        isOverRemove = false
    }

    fun startMoving() {
        isMoving = true
    }

    fun drag(delta: Offset) {
        val key = draggingKey ?: return
        offset += delta
        val visible = items()
        val dragged = visible.firstOrNull { it.key == key } ?: return
        val center = dragged.bounds.topLeft + offset + dragged.bounds.size.center()
        isOverRemove = removeBounds.contains(rowBounds.topLeft + center)
        if (isOverRemove) return
        val target = visible.firstOrNull {
            it.key != key && it.key in favoriteKeys && it.bounds.contains(center)
        } ?: return
        val from = favoriteKeys.indexOf(key)
        val to = favoriteKeys.indexOf(target.key)
        // The lifted favorite takes the target's place; keep it under the finger.
        offset += dragged.bounds.topLeft - target.bounds.topLeft
        onMove(from, to)
    }

    fun drop() {
        val key = draggingKey
        if (key != null && isMoving && isOverRemove) onRemove(key)
        draggingKey = null
        isMoving = false
        isOverRemove = false
        offset = Offset.Zero
    }

    private fun Size.center() = Offset(width / 2, height / 2)

    companion object {
        /** The visible items of a horizontal [LazyListState], each as tall as the row. */
        fun rowItems(listState: LazyListState): () -> List<FavoriteItemBounds> = {
            val info = listState.layoutInfo
            val height = info.viewportSize.height.toFloat()
            info.visibleItemsInfo.map {
                FavoriteItemBounds(it.key, Rect(Offset(it.offset.toFloat(), 0f), Size(it.size.toFloat(), height)))
            }
        }
    }
}

/**
 * A favorite's touch handling: a tap calls [onTap]; a long press lifts it, and then either a
 * release without moving calls [onLongPress] (the options sheet) or moving drags it.
 */
fun Modifier.favoriteGestures(
    key: Any,
    state: FavoriteDragState,
    interactionSource: MutableInteractionSource,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onLift: () -> Unit,
): Modifier = pointerInput(key, state) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val press = PressInteraction.Press(down.position)
        interactionSource.tryEmit(press)
        var longPressed = false
        val up = try {
            withTimeout(viewConfiguration.longPressTimeoutMillis) { waitForUpOrCancellation() }
        } catch (_: PointerEventTimeoutCancellationException) {
            longPressed = true
            null
        }
        if (!longPressed) {
            if (up != null) {
                up.consume()
                interactionSource.tryEmit(PressInteraction.Release(press))
                onTap()
            } else {
                interactionSource.tryEmit(PressInteraction.Cancel(press))
            }
            return@awaitEachGesture
        }

        interactionSource.tryEmit(PressInteraction.Release(press))
        state.lift(key)
        onLift()
        var total = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (change.changedToUp()) {
                change.consume()
                break
            }
            val delta = change.positionChange()
            total += delta
            if (!state.isMoving && total.getDistance() > viewConfiguration.touchSlop) state.startMoving()
            if (state.isMoving) state.drag(delta)
            change.consume()
        }
        val moved = state.isMoving
        state.drop()
        if (!moved) onLongPress()
    }
}
