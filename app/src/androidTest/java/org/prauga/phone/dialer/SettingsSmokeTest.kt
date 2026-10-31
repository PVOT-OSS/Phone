/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.settings.DialerSettingsActivity

@RunWith(AndroidJUnit4::class)
class SettingsSmokeTest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val device = UiDevice.getInstance(instrumentation)

    @Test
    fun settingsListUnknownCallers() {
        device.wakeUp()
        context.startActivity(
            Intent(context, DialerSettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val title = context.getString(R.string.screening_settings_title)
        assertNotNull(device.wait(Until.findObject(By.text(title)), TIMEOUT_MILLIS))
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
