/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.binary.common

import android.content.Context
import androidx.window.embedding.RuleController
import org.prauga.phone.dialer.R

/** On a wide window, opens call details and call statistics beside the main screen. */
object WideWindowSplits {
    @JvmStatic
    fun apply(context: Context) {
        val controller = RuleController.getInstance(context)
        controller.setRules(RuleController.parseRules(context, R.xml.wide_window_splits))
    }
}
