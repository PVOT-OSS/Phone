/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.testing.FakeCarrier

/**
 * Dialling a home number while abroad adds the home country code. Runs where the phone has a
 * network country, using a chosen home country to make the phone count as roaming.
 */
@RunWith(AndroidJUnit4::class)
class AssistedDialingTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    private val toggleKey = context.getString(R.string.assisted_dialing_setting_toggle_key)
    private val countryKey = context.getString(R.string.assisted_dialing_setting_cc_key)
    private val networkCountry =
        context.getSystemService(TelephonyManager::class.java).networkCountryIso.uppercase()
    private val saved = prefs.all.filterKeys { it == toggleKey || it == countryKey }

    @get:Rule
    val rules: RuleChain =
        RuleChain.outerRule(DefaultDialerRule()).around(FakeCarrierRule()).around(DeviceRule())

    @Before
    fun needsANetworkCountryOtherThanTheUs() =
        assumeTrue("no network country", networkCountry.isNotEmpty() && networkCountry != "US")

    @After
    fun restorePreferences() =
        prefs.edit {
            remove(toggleKey)
            remove(countryKey)
            (saved[toggleKey] as? Boolean)?.let { putBoolean(toggleKey, it) }
            (saved[countryKey] as? String)?.let { putString(countryKey, it) }
        }

    @Test
    fun homeNumberGetsTheHomeCountryCodeWhereverThePhoneIs() {
        // The current country is not in the supported list on most test phones; only the home
        // country has to be.
        setHome("US")
        assertEquals("+16502530000", dialledAs("6502530000"))
    }

    @Test
    fun onByDefault() {
        prefs.edit {
            remove(toggleKey)
            putString(countryKey, "US")
        }
        assertEquals("+16502530000", dialledAs("6502530000"))
    }

    @Test
    fun turnedOffKeepsTheNumber() {
        setHome("US", enabled = false)
        assertEquals("6502530000", dialledAs("6502530000"))
    }

    @Test
    fun internationalNumberIsKept() {
        setHome("US")
        assertEquals("+447700900123", dialledAs("+447700900123"))
    }

    @Test
    fun unsupportedHomeCountryKeepsTheNumber() {
        setHome("DE")
        assertEquals("030901820", dialledAs("030901820"))
    }

    @Test
    fun atHomeKeepsTheNumber() {
        setHome(networkCountry)
        assertEquals("6502530000", dialledAs("6502530000"))
    }

    private fun setHome(country: String, enabled: Boolean = true) =
        prefs.edit {
            putBoolean(toggleKey, enabled)
            putString(countryKey, country)
        }

    /** Dials [number] the way the app's own screens do and returns what reached the network. */
    private fun dialledAs(number: String): String {
        val builder =
            CallIntentBuilder(number, CallInitiationType.Type.DIALPAD)
                .setPhoneAccountHandle(FakeCarrier.accountHandle(context))
                .setAllowAssistedDial(true)
        val intent =
            InCall.onMain { PreCall.getIntent(context, builder) }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        val far = CallTests.waitFor { FakeCarrier.connections.firstOrNull() }
        far.hangUpFromFarEnd()
        InCall.waitForNoCalls()
        return far.spec.number.orEmpty().filter { it.isDigit() || it == '+' }
    }
}
