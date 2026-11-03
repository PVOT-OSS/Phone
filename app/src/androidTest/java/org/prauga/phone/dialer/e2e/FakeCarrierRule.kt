/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.os.Process
import android.os.UserManager
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import org.prauga.phone.dialer.testing.FakeCarrier

/** Registers and enables the fake carrier's calling account for the test. */
class FakeCarrierRule : ExternalResource() {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    override fun before() {
        FakeCarrier.register(context)
        val handle = FakeCarrier.accountHandle(context)
        val serial =
            context
                .getSystemService(UserManager::class.java)
                .getSerialNumberForUser(Process.myUserHandle())
        Shell.run(
            "telecom set-phone-account-enabled ${handle.componentName.flattenToString()} ${handle.id} $serial"
        )
    }

    override fun after() {
        FakeCarrier.unregister(context)
    }
}
