/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.prauga.phone.dialer.common.LogUtil

/** State of the main screen's nav bar: the selected tab, the voicemail tab and unread badges. */
class MainNavBarState {

    /** The selected [TabIndex], or [TabIndex.NONE] before the first selection. */
    @get:TabIndex
    var selectedTab by mutableIntStateOf(TabIndex.NONE)
        private set

    /** Whether the voicemail tab is shown. */
    var isVoicemailShown by mutableStateOf(false)
        private set

    /** Whether the bar is shown; it hides while searching or selecting call log entries. */
    var isVisible by mutableStateOf(true)

    private val counts = mutableStateMapOf<Int, Int>()
    private val listeners = mutableListOf<OnBottomNavTabSelectedListener>()

    /** The tabs in the bar, in order. */
    val tabs: List<Int>
        get() = buildList {
            add(TabIndex.SPEED_DIAL)
            add(TabIndex.CALL_LOG)
            add(TabIndex.CONTACTS)
            if (isVoicemailShown) add(TabIndex.VOICEMAIL)
        }

    /** Selects [tab], for a tap or a programmatic change, and tells the listeners. */
    fun selectTab(@TabIndex tab: Int) {
        check(tab in ALL_TABS) { "Invalid tab: $tab" }
        selectedTab = tab
        listeners.forEach { listener ->
            when (tab) {
                TabIndex.SPEED_DIAL -> listener.onSpeedDialSelected()
                TabIndex.CALL_LOG -> listener.onCallLogSelected()
                TabIndex.CONTACTS -> listener.onContactsSelected()
                TabIndex.VOICEMAIL -> listener.onVoicemailSelected()
                TabIndex.NONE -> Unit
            }
        }
    }

    /**
     * Shows or hides the voicemail tab. Hiding it while it is selected moves to the speed dial tab.
     */
    fun showVoicemail(show: Boolean) {
        LogUtil.i("MainNavBarState.showVoicemail", "showing tab: %b", show)
        val wasShown = isVoicemailShown
        isVoicemailShown = show
        if (wasShown && !show && selectedTab == TabIndex.VOICEMAIL) {
            LogUtil.i("MainNavBarState.showVoicemail", "hid voicemail tab, moving to speed dial")
            selectTab(TabIndex.SPEED_DIAL)
        }
    }

    /** Sets the unread count shown on [tab]'s badge; 0 hides the badge. */
    fun setNotificationCount(@TabIndex tab: Int, count: Int) {
        check(tab in ALL_TABS) { "Invalid tab: $tab" }
        counts[tab] = count
    }

    /** The unread count shown on [tab]'s badge. */
    fun notificationCount(@TabIndex tab: Int): Int = counts[tab] ?: 0

    fun addOnTabSelectedListener(listener: OnBottomNavTabSelectedListener) {
        listeners += listener
    }

    /** Listener for tab selections. */
    interface OnBottomNavTabSelectedListener {

        /** Speed dial tab was selected. */
        fun onSpeedDialSelected()

        /** Call log tab was selected. */
        fun onCallLogSelected()

        /** Contacts tab was selected. */
        fun onContactsSelected()

        /** Voicemail tab was selected. */
        fun onVoicemailSelected()
    }

    private companion object {
        val ALL_TABS =
            setOf(TabIndex.SPEED_DIAL, TabIndex.CALL_LOG, TabIndex.CONTACTS, TabIndex.VOICEMAIL)
    }
}

/** State of the main screen's dialpad button. */
class MainFabState {

    /** Whether the button is shown. */
    var isShown by mutableStateOf(true)
        private set

    fun show() {
        isShown = true
    }

    fun hide() {
        isShown = false
    }
}
