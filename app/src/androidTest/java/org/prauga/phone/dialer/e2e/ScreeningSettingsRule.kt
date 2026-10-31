/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.prauga.phone.dialer.screening.CallerCategory
import org.prauga.phone.dialer.screening.CategoryAction
import org.prauga.phone.dialer.screening.ScreeningSettings

/** Lets a test choose screening actions and puts every category back to ringing afterwards. */
class ScreeningSettingsRule : ExternalResource() {

    private val settings = ScreeningSettings.from(InstrumentationRegistry.getInstrumentation().targetContext)

    fun set(category: CallerCategory, action: CategoryAction) = settings.setAction(category, action)

    override fun before() = reset()

    override fun after() = reset()

    private fun reset() = CallerCategory.entries.forEach { settings.setAction(it, CategoryAction.RING) }
}
