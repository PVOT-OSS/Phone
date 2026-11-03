/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog.Calls
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.car.CarCallLists

/** Finding numbers that aren't contacts, and the lists shown in Android Auto. */
@RunWith(AndroidJUnit4::class)
class SearchAndCarTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val deviceRule = DeviceRule()
    private val numbers = mutableListOf<String>()

    @get:Rule val rules: RuleChain = RuleChain.outerRule(DefaultDialerRule()).around(deviceRule)

    @After
    fun removeCalls() {
        for (number in numbers) context.contentResolver.delete(
            Calls.CONTENT_URI,
            "${Calls.NUMBER} = ?",
            arrayOf(number),
        )
    }

    @Test
    fun searchFindsANumberThatIsNotAContact() {
        val number = addCall()
        context.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number.takeLast(7)))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )

        assertNotNull(
            "no \"Not in your contacts\" section",
            deviceRule.device.wait(
                Until.findObject(
                    By.text(context.getString(R.string.call_log_search_result_header))
                ),
                CallTests.TIMEOUT_MILLIS,
            ),
        )
    }

    @Test
    fun carRecentsListTheNewestCallFirst() {
        val number = addCall()
        val recents = CarCallLists.recents(context)
        assertEquals(number, recents.first().number)
    }

    @Test
    fun appIsACarCallingApp() {
        val intent =
            Intent("androidx.car.app.CarAppService")
                .addCategory("androidx.car.app.category.CALLING")
                .setPackage(context.packageName)
        val services =
            context.packageManager.queryIntentServices(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        assertEquals(1, services.size)
    }

    /** Adds a call from a fresh number with no contact, a second ago; returns the number. */
    private fun addCall(): String {
        val number = CallTests.freshNumber()
        numbers += number
        val values =
            ContentValues().apply {
                put(Calls.NUMBER, number)
                put(Calls.TYPE, Calls.INCOMING_TYPE)
                put(Calls.DATE, System.currentTimeMillis() - 1_000)
                put(Calls.DURATION, 30)
            }
        checkNotNull(context.contentResolver.insert(Calls.CONTENT_URI, values))
        return number
    }
}
