/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.provider.CallLog.Calls
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.app.calllog.calllogcache.CallLogCache
import org.prauga.phone.dialer.contacts.displaypreference.ContactDisplayPreferences.DisplayOrder
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The text and actions of a call history row. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class CallLogRowResolverTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver =
        CallLogRowResolver(context, CallLogCache(context), displayOrder = { DisplayOrder.PRIMARY })

    private fun contact(name: String) =
        ContactInfo().apply {
            this.name = name
            lookupUri = Uri.parse("content://com.android.contacts/contacts/lookup/key/1")
            lookupKey = "key"
            number = "+15550100"
        }

    /** Another app's calling account, as a messaging app registers one. */
    private fun registerApp(label: String, selfManaged: Boolean = true): PhoneAccountHandle {
        val handle = PhoneAccountHandle(ComponentName("com.example.chat", "Calls"), "chat")
        val account =
            PhoneAccount.builder(handle, label)
                .setCapabilities(
                    if (selfManaged) PhoneAccount.CAPABILITY_SELF_MANAGED
                    else PhoneAccount.CAPABILITY_CALL_PROVIDER
                )
                .build()
        context.getSystemService(TelecomManager::class.java).registerPhoneAccount(account)
        return handle
    }

    private val listingResolver =
        CallLogRowResolver(
            context,
            CallLogCache(context),
            { DisplayOrder.PRIMARY },
            OtherAppsCalls(context) { true },
        )

    @Test
    fun anotherAppsCallIsNamedAfterTheAppAndCallsBackThroughIt() {
        val app = registerApp("Chat")
        val row = listingResolver.resolve(callLogGroup(1, account = app), ContactInfo.EMPTY, false)
        assertThat(row.otherApp).isEqualTo("Chat")
        assertThat(row.accountLabel).isEqualTo("Chat")
        assertThat(row.primaryAction).isEqualTo(PrimaryAction.CALL_IN_APP)
        assertThat(row.actions).containsExactly(RowAction.DELETE)
    }

    @Test
    fun aCallingAccountThatIsNotAnAppsOwnIsThePhones() {
        val sim = registerApp("Carrier", selfManaged = false)
        val row = listingResolver.resolve(callLogGroup(1, account = sim), ContactInfo.EMPTY, false)
        assertThat(row.otherApp).isNull()
        assertThat(row.primaryAction).isEqualTo(PrimaryAction.CALL)
    }

    @Test
    fun withoutOtherAppsInTheCallLogEveryCallIsThePhones() {
        val app = registerApp("Chat")
        val row = resolver.resolve(callLogGroup(1, account = app), ContactInfo.EMPTY, false)
        assertThat(row.otherApp).isNull()
    }

    @Test
    fun aCallLogEntryIsAnAddressTelecomCanCall() {
        assertThat(TelecomUtil.callLogEntryAddress(42).toString())
            .isEqualTo("content://call_log/calls/42?limit=1&include_voip_calls=true")
    }

    @Test
    fun aContactShowsItsName() {
        val row = resolver.resolve(callLogGroup(1), contact("Ada Lovelace"), isBlocked = false)
        assertThat(row.title).isEqualTo("Ada Lovelace")
        assertThat(row.titleIsNumber).isFalse()
    }

    @Test
    fun anUnknownCallerShowsTheNumberLeftToRight() {
        val row = resolver.resolve(callLogGroup(1), ContactInfo.EMPTY, isBlocked = false)
        assertThat(row.title).contains("555")
        assertThat(row.titleIsNumber).isTrue()
    }

    @Test
    fun aPrivateNumberCannotBeCalledBack() {
        val row =
            resolver.resolve(
                callLogGroup(1, number = "", presentation = Calls.PRESENTATION_RESTRICTED),
                ContactInfo.EMPTY,
                isBlocked = false,
            )
        assertThat(row.primaryAction).isEqualTo(PrimaryAction.NONE)
        assertThat(row.actions).doesNotContain(RowAction.SEND_MESSAGE)
    }

    @Test
    fun aCallableNumberOffersCallBackMessageAndDetails() {
        val row = resolver.resolve(callLogGroup(1), ContactInfo.EMPTY, isBlocked = false)
        assertThat(row.primaryAction).isEqualTo(PrimaryAction.CALL)
        assertThat(row.actions).containsAtLeast(RowAction.SEND_MESSAGE, RowAction.DETAILS).inOrder()
    }

    @Test
    fun aBlockedNumberOffersNoMessageOrNewContact() {
        val row = resolver.resolve(callLogGroup(1), ContactInfo.EMPTY, isBlocked = true)
        assertThat(row.actions)
            .containsNoneOf(RowAction.SEND_MESSAGE, RowAction.CREATE_CONTACT, RowAction.BLOCK)
        assertThat(row.details).startsWith("Blocked")
    }

    @Test
    fun moreThanThreeCallsShowTheirCount() {
        val group = callLogGroup(1, callTypes = List(5) { Calls.INCOMING_TYPE })
        assertThat(resolver.resolve(group, ContactInfo.EMPTY, isBlocked = false).details)
            .startsWith("(5)")
    }

    @Test
    fun aVoicemailHasNoDetailsButCanBeCalledBack() {
        val group =
            callLogGroup(
                1,
                callTypes = listOf(Calls.VOICEMAIL_TYPE),
                voicemailUri = "content://voicemail/1",
            )
        val row = resolver.resolve(group, ContactInfo.EMPTY, isBlocked = false)
        assertThat(row.primaryAction).isEqualTo(PrimaryAction.PLAY_VOICEMAIL)
        assertThat(row.actions).contains(RowAction.CALL)
        assertThat(row.actions).doesNotContain(RowAction.DETAILS)
    }

    @Test
    fun anEmergencyNumberOffersOnlyDetails() {
        val row =
            resolver.resolve(callLogGroup(1, number = "911"), ContactInfo.EMPTY, isBlocked = false)
        assertThat(row.isEmergency).isTrue()
        assertThat(row.actions).containsExactly(RowAction.DETAILS)
    }
}
