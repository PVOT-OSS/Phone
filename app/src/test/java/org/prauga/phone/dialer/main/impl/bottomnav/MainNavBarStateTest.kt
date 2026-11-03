/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Tab selection, the voicemail tab and badges of the main screen's nav bar. */
@RunWith(RobolectricTestRunner::class)
class MainNavBarStateTest {

    private class RecordingListener : MainNavBarState.OnBottomNavTabSelectedListener {
        val selected = mutableListOf<String>()

        override fun onDialpadSelected() {
            selected += "dialpad"
        }

        override fun onCallLogSelected() {
            selected += "call_log"
        }

        override fun onContactsSelected() {
            selected += "contacts"
        }

        override fun onVoicemailSelected() {
            selected += "voicemail"
        }
    }

    private val state = MainNavBarState()
    private val listener = RecordingListener().also(state::addOnTabSelectedListener)

    @Test
    fun selectingATabTellsEveryListener() {
        val second = RecordingListener().also(state::addOnTabSelectedListener)

        state.selectTab(TabIndex.CALL_LOG)
        state.selectTab(TabIndex.CONTACTS)

        assertThat(state.selectedTab).isEqualTo(TabIndex.CONTACTS)
        assertThat(listener.selected).containsExactly("call_log", "contacts").inOrder()
        assertThat(second.selected).isEqualTo(listener.selected)
    }

    @Test
    fun reselectingATabTellsListenersAgain() {
        state.selectTab(TabIndex.DIALPAD)
        state.selectTab(TabIndex.DIALPAD)

        assertThat(listener.selected).containsExactly("dialpad", "dialpad")
    }

    @Test
    fun invalidTabIsRejected() {
        assertThrows(IllegalStateException::class.java) { state.selectTab(TabIndex.NONE) }
        assertThrows(IllegalStateException::class.java) { state.setNotificationCount(7, 1) }
    }

    @Test
    fun voicemailTabIsLastAndOnlyWhenShown() {
        assertThat(state.tabs)
            .containsExactly(TabIndex.DIALPAD, TabIndex.CALL_LOG, TabIndex.CONTACTS)
            .inOrder()

        state.showVoicemail(true)

        assertThat(state.tabs)
            .containsExactly(
                TabIndex.DIALPAD,
                TabIndex.CALL_LOG,
                TabIndex.CONTACTS,
                TabIndex.VOICEMAIL,
            )
            .inOrder()
    }

    @Test
    fun hidingTheSelectedVoicemailTabMovesToSpeedDial() {
        state.showVoicemail(true)
        state.selectTab(TabIndex.VOICEMAIL)

        state.showVoicemail(false)

        assertThat(state.selectedTab).isEqualTo(TabIndex.DIALPAD)
        assertThat(listener.selected).containsExactly("voicemail", "dialpad").inOrder()
    }

    @Test
    fun hidingVoicemailKeepsAnotherSelectedTab() {
        state.showVoicemail(true)
        state.selectTab(TabIndex.CONTACTS)

        state.showVoicemail(false)

        assertThat(state.selectedTab).isEqualTo(TabIndex.CONTACTS)
        assertThat(listener.selected).containsExactly("contacts")
    }

    @Test
    fun badgeCountsArePerTab() {
        assertThat(state.notificationCount(TabIndex.CALL_LOG)).isEqualTo(0)

        state.setNotificationCount(TabIndex.CALL_LOG, 3)
        state.setNotificationCount(TabIndex.VOICEMAIL, 1)

        assertThat(state.notificationCount(TabIndex.CALL_LOG)).isEqualTo(3)
        assertThat(state.notificationCount(TabIndex.VOICEMAIL)).isEqualTo(1)
        assertThat(state.notificationCount(TabIndex.CONTACTS)).isEqualTo(0)
    }
}
