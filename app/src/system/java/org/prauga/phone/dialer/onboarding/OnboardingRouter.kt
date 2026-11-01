/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.content.Context
import android.content.Intent

/** The PVOT ROM makes the system build the default phone app and grants its permissions, so it has no onboarding. */
object OnboardingRouter {

    /** Always null: the main screen shows straight away. */
    @JvmStatic
    @Suppress("UNUSED_PARAMETER", "FunctionOnlyReturningConstant")
    fun intentIfNeeded(context: Context): Intent? = null
}
