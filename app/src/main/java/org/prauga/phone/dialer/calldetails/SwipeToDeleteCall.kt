/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.calldetails

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/** Lets the user swipe a single call off a number's call details, either way. */
class SwipeToDeleteCall(private val onSwiped: Listener) :
    ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {

    /**
     * Told which call was swiped away; [restore] puts the row back if the user changes their mind.
     */
    fun interface Listener {
        fun onCallSwiped(entryIndex: Int, restore: Runnable)
    }

    override fun getSwipeDirs(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
    ): Int =
        if (viewHolder is CallDetailsEntryViewHolder) super.getSwipeDirs(recyclerView, viewHolder)
        else 0

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder,
    ): Boolean = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        val position = viewHolder.bindingAdapterPosition
        val adapter = viewHolder.bindingAdapter ?: return
        // The header comes first, so call entries start at position 1.
        onSwiped.onCallSwiped(position - 1) { adapter.notifyItemChanged(position) }
    }

    companion object {
        @JvmStatic
        fun attach(recyclerView: RecyclerView, listener: Listener) {
            ItemTouchHelper(SwipeToDeleteCall(listener)).attachToRecyclerView(recyclerView)
        }
    }
}
