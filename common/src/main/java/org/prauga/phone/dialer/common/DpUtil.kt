/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common

import android.content.Context

/** Utility for dp to px conversion */
object DpUtil {

    @JvmStatic
    fun pxToDp(context: Context, px: Float): Float = px / context.resources.displayMetrics.density

    @JvmStatic
    fun dpToPx(context: Context, dp: Float): Float = dp * context.resources.displayMetrics.density
}
