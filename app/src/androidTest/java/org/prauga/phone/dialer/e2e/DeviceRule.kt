/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertFalse
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.prauga.phone.dialer.testing.FakeCarrier

/**
 * Keeps the screen on and unlocked, and fails the test if the app crashed during it. When a test
 * fails it logs what was on screen (tag `DeviceRule`), so the failure can be read from logcat.
 */
class DeviceRule : TestRule {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice = UiDevice.getInstance(instrumentation)

    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                before()
                try {
                    base.evaluate()
                } catch (e: Throwable) {
                    logScreen(description)
                    throw e
                }
                after()
            }
        }

    private fun before() {
        device.wakeUp()
        Shell.run("wm dismiss-keyguard")
        // Android's one-time "Viewing full screen" hint would cover the top of the call screen.
        Shell.run("settings put secure immersive_mode_confirmations confirmed")
        Shell.run("logcat -b crash -c")
        // Start from a quiet phone: a call a failed test left on the fake carrier ends now, and a
        // call from the previous test may still be ending.
        FakeCarrier.connections.forEach { it.hangUpFromFarEnd() }
        InCall.waitForNoCalls()
    }

    private fun after() {
        val crashes = Shell.run("logcat -b crash -d")
        val packageName = instrumentation.targetContext.packageName
        assertFalse("app crashed:\n$crashes", crashes.contains("Process: $packageName"))
    }

    /** What the screen showed: text, descriptions and ids, one line per screen element. */
    private fun logScreen(description: Description) {
        val out = ByteArrayOutputStream()
        runCatching { device.dumpWindowHierarchy(out) }
        val nodes =
            Regex("<node [^>]*>")
                .findAll(out.toString())
                .mapNotNull { node ->
                    Regex("""(text|content-desc|resource-id)="([^"]+)"""")
                        .findAll(node.value)
                        .joinToString(" ") { "${it.groupValues[1]}=${it.groupValues[2]}" }
                        .takeIf { it.isNotEmpty() }
                }
                .toList()
        Log.w(TAG, "${description.displayName} failed; the screen showed:")
        nodes.forEach { Log.w(TAG, "  $it") }
    }

    private companion object {
        const val TAG = "DeviceRule"
    }
}
