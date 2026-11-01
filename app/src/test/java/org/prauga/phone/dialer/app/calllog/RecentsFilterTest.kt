/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.net.Uri
import android.provider.CallLog.Calls
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.robolectric.RobolectricTestRunner

/** Which calls the Recents filters keep. */
@RunWith(RobolectricTestRunner::class)
class RecentsFilterTest {

    private fun info(lookupKey: String? = null, lookupUri: String? = null) = ContactInfo().apply {
        this.lookupKey = lookupKey
        this.lookupUri = lookupUri?.let(Uri::parse)
    }

    @Test
    fun contactsKeepsCallsWithAContact() {
        val byKey = callLogGroup(1, cachedInfo = info(lookupKey = "0r1-ABC"))
        val byUri = callLogGroup(2, cachedInfo = info(lookupUri = "content://com.android.contacts/contacts/lookup/0r1-ABC/7"))
        assertThat(CallLogFilter.CONTACTS.matches(byKey, emptySet())).isTrue()
        assertThat(CallLogFilter.CONTACTS.matches(byUri, emptySet())).isTrue()
    }

    @Test
    fun contactsDropsUnknownNumbers() {
        val unknown = callLogGroup(1)
        val temporary = callLogGroup(
            2,
            cachedInfo = info(lookupUri = "content://com.android.contacts/contacts/lookup/encoded?directory=1#{}"),
        )
        assertThat(CallLogFilter.CONTACTS.matches(unknown, emptySet())).isFalse()
        assertThat(CallLogFilter.CONTACTS.matches(temporary, emptySet())).isFalse()
    }

    @Test
    fun blockedKeepsBlockedCallsAndBlockedNumbers() {
        val blockedCall = callLogGroup(1, callTypes = listOf(Calls.BLOCKED_TYPE))
        val blockedNumber = callLogGroup(2, number = "+15550111")
        val other = callLogGroup(3, number = "+15550122")
        val blocked = setOf("+15550111")
        assertThat(CallLogFilter.BLOCKED.matches(blockedCall, blocked)).isTrue()
        assertThat(CallLogFilter.BLOCKED.matches(blockedNumber, blocked)).isTrue()
        assertThat(CallLogFilter.BLOCKED.matches(other, blocked)).isFalse()
    }

    @Test
    fun allAndMissedLeaveTheChoiceToTheQuery() {
        val group = callLogGroup(1)
        assertThat(CallLogFilter.ALL.matches(group, emptySet())).isTrue()
        assertThat(CallLogFilter.MISSED.matches(group, emptySet())).isTrue()
        assertThat(CallLogFilter.ALL.narrows).isFalse()
        assertThat(CallLogFilter.CONTACTS.narrows).isTrue()
    }
}
