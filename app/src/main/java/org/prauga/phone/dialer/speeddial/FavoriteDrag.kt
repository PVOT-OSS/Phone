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
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize

/**
 * Drag state of the favorites grid: which favorite is lifted, how far it has moved, and whether
 * it is over the remove target.
 *
 * Favorites are keyed by [favoriteKey]. While dragging, the lifted favorite swaps places with the
 * one under its center through [onMove]; dropping it on the remove target calls [onRemove].
 */
class FavoriteDragState(
    private val gridState: LazyGridState,
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

    /** The grid's and the remove target's bounds in the root, set from layout. */
    var gridBounds: Rect = Rect.Zero
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
        val dragged = itemInfo(key) ?: return
        val center = dragged.offset.toOffset() + offset + dragged.size.toSize().center()
        isOverRemove = removeBounds.contains(gridBounds.topLeft + center)
        if (isOverRemove) return
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull {
            it.key != key && it.key in favoriteKeys && Rect(it.offset.toOffset(), it.size.toSize()).contains(center)
        } ?: return
        val from = favoriteKeys.indexOf(key)
        val to = favoriteKeys.indexOf(target.key)
        // The lifted favorite takes the target's place; keep it under the finger.
        offset += dragged.offset.toOffset() - target.offset.toOffset()
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

    private fun itemInfo(key: Any): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    private fun androidx.compose.ui.geometry.Size.center() = Offset(width / 2, height / 2)
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
