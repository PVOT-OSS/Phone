/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.os.Process
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource

/**
 * Makes this app the default phone app for the test. The role is not handed back here: losing it
 * revokes the permissions it granted, which kills the process running the tests. The host-side
 * setup script restores the previous holder after the run.
 */
class DefaultDialerRule : ExternalResource() {

    private val packageName = InstrumentationRegistry.getInstrumentation().targetContext.packageName
    private val userId = Process.myUserHandle().hashCode()

    override fun before() {
        if (holders() != listOf(packageName)) {
            Shell.run("cmd role add-role-holder --user $userId $ROLE $packageName")
        }
        check(holders() == listOf(packageName)) { "could not become the default phone app" }
    }

    private fun holders(): List<String> =
        Shell.run("cmd role get-role-holders --user $userId $ROLE").lines().filter { it.isNotBlank() }

    private companion object {
        const val ROLE = "android.app.role.DIALER"
    }
}
