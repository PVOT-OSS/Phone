/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.TelecomManager
import androidx.test.platform.app.InstrumentationRegistry
import org.prauga.phone.dialer.testing.FakeCarrier
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.DialerCall

/** Looks at and acts on the app's calls the way its call screen does, on the main thread. */
object InCall {

    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    /** The first call the app knows in [state], one of the DialerCallState constants. */
    fun callIn(state: Int): DialerCall? = onMain {
        CallList.getInstance().allCalls.firstOrNull { it.state == state }
    }

    fun waitForState(state: Int): DialerCall = CallTests.waitFor { callIn(state) }

    fun waitForNoCalls() =
        CallTests.waitFor {
            if (onMain { CallList.getInstance().allCalls.isEmpty() }) true else null
        }

    /** Dials [number] on the fake carrier, as if the user had dialled it. */
    fun place(context: Context, number: String) {
        val extras =
            Bundle().apply {
                putParcelable(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    FakeCarrier.accountHandle(context),
                )
            }
        context
            .getSystemService(TelecomManager::class.java)
            .placeCall(Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null), extras)
    }
}
