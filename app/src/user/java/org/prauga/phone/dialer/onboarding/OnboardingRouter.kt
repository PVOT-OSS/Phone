/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.content.Context
import android.content.Intent

/**
 * Sends a user build that isn't the default phone app to [OnboardingActivity], until the user
 * finishes it or says "Not now". After that, Android's Default apps settings are the way back.
 */
object OnboardingRouter {

    private const val PREFS = "onboarding"
    private const val KEY_DISMISSED = "dismissed"

    /** The onboarding to show over the main screen, or null to show the main screen. */
    @JvmStatic
    fun intentIfNeeded(context: Context): Intent? {
        if (OnboardingActivity.isDefaultDialer(context) || isDismissed(context)) return null
        return Intent(context, OnboardingActivity::class.java)
    }

    internal fun isDismissed(context: Context) = prefs(context).getBoolean(KEY_DISMISSED, false)

    internal fun markDismissed(context: Context) {
        prefs(context).edit().putBoolean(KEY_DISMISSED, true).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
