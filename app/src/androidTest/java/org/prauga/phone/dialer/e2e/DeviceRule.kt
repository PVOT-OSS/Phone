/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.Assert.assertFalse
import org.junit.rules.ExternalResource

/** Keeps the screen on and unlocked, and fails the test if the app crashed during it. */
class DeviceRule : ExternalResource() {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)

    override fun before() {
        device.wakeUp()
        Shell.run("wm dismiss-keyguard")
        Shell.run("logcat -b crash -c")
    }

    override fun after() {
        val crashes = Shell.run("logcat -b crash -d")
        val packageName = instrumentation.targetContext.packageName
        assertFalse("app crashed:\n$crashes", crashes.contains("Process: $packageName"))
    }
}
