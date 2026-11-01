/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.helplines

import android.annotation.SuppressLint
import android.content.res.Resources
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import org.lineageos.lib.phone.SensitivePhoneNumbers
import org.lineageos.lib.phone.spn.Item

/** Finds the helplines for the countries of the active SIMs. */
internal object HelplineLoader {

    /** The helplines for every active SIM's country, sorted by name. Reads bundled data; call off the main thread. */
    fun load(resources: Resources, subscriptions: SubscriptionManager): List<HelplineItem> =
        load(resources, activeSubscriptions(subscriptions).map { it.countryIso to it.mccString }) { mcc ->
            SensitivePhoneNumbers.getInstance().getSensitivePnInfosForMcc(mcc)
        }

    /**
     * The helplines for [sims], given as (country ISO, MCC) pairs. When a SIM's country differs from
     * the user's locale, names include the country, so a number near a border is clearly the right
     * one.
     */
    fun load(
        resources: Resources,
        sims: List<Pair<String, String?>>,
        helplinesForMcc: (String) -> List<Item>,
    ): List<HelplineItem> {
        val localeCountryIso = resources.configuration.locales[0].country.lowercase()
        var addCountryCode = false
        val seenMccs = mutableSetOf<String>()
        val helplines = mutableListOf<HelplineItem>()
        for ((countryIso, mcc) in sims) {
            if (countryIso != localeCountryIso) addCountryCode = true
            if (mcc == null || !seenMccs.add(mcc)) continue
            for (item in helplinesForMcc(mcc)) {
                helplines += HelplineItem(resources, item, if (addCountryCode) countryIso else "")
            }
        }
        return helplines.sortedBy { it.name }
    }

    @SuppressLint("MissingPermission")
    private fun activeSubscriptions(subscriptions: SubscriptionManager): List<SubscriptionInfo> =
        subscriptions.activeSubscriptionInfoList
            ?: listOfNotNull(subscriptions.getActiveSubscriptionInfo(SubscriptionManager.getDefaultVoiceSubscriptionId()))
}
