/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CopyOnWriteArrayList
import org.junit.rules.ExternalResource

/**
 * Collects the toasts shown while a test runs. Android reports each toast to accessibility as a
 * notification state change, which the test's UiAutomation can listen to.
 */
class ToastWatcher : ExternalResource() {

    private val shown = CopyOnWriteArrayList<String>()
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    override fun before() {
        automation.setOnAccessibilityEventListener { event ->
            if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED &&
                event.className == Toast::class.java.name
            ) {
                // The text holds the message and then the app's name.
                shown += event.text.map { it.toString() }
            }
        }
    }

    override fun after() = automation.setOnAccessibilityEventListener(null)

    /** Waits for a toast saying [text]. */
    fun waitFor(text: String) = CallTests.waitFor { shown.firstOrNull { it == text } }
}
