/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.compat.android.support.design.bottomsheet

import com.google.android.material.bottomsheet.BottomSheetBehavior

/** Provides access to bottom sheet states. */
object BottomSheetStateCompat {

    /** The bottom sheet is dragging. */
    const val STATE_DRAGGING = BottomSheetBehavior.STATE_DRAGGING

    /** The bottom sheet is settling. */
    const val STATE_SETTLING = BottomSheetBehavior.STATE_SETTLING

    /** The bottom sheet is expanded. */
    const val STATE_EXPANDED = BottomSheetBehavior.STATE_EXPANDED

    /** The bottom sheet is collapsed. */
    const val STATE_COLLAPSED = BottomSheetBehavior.STATE_COLLAPSED

    /** The bottom sheet is hidden. */
    const val STATE_HIDDEN = BottomSheetBehavior.STATE_HIDDEN

    /** The bottom sheet is half-expanded (not public yet). */
    const val STATE_HALF_EXPANDED = 6
}
