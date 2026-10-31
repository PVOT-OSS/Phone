/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import android.content.Context
import android.util.AttributeSet
import androidx.annotation.IntDef
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AbstractComposeView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.components.PvotGlassIconButton
import com.prauga.pvot.designsystem.navigation.LocalPvotNavBarSizes
import com.prauga.pvot.designsystem.navigation.PvotNavBar
import com.prauga.pvot.designsystem.navigation.PvotTabItem
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.LogUtil

/** Dialer bottom navigation bar for the main activity, drawn with PvotLib's [PvotNavBar]. */
class BottomNavBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : AbstractComposeView(context, attrs) {

    /** Index for each tab in the bottom nav. */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TabIndex.NONE, TabIndex.CALL_LOG, TabIndex.CONTACTS, TabIndex.VOICEMAIL)
    annotation class TabIndex {
        companion object {
            const val NONE = -1
            const val CALL_LOG = 1
            const val CONTACTS = 2
            const val VOICEMAIL = 3
        }
    }

    /** Listener for bottom nav tab's on click events. */
    interface OnBottomNavTabSelectedListener {
        /** Call Log tab was clicked. */
        fun onCallLogSelected()

        /** Contacts tab was clicked. */
        fun onContactsSelected()

        /** Voicemail tab was clicked. */
        fun onVoicemailSelected()
    }

    /** Listener for the bar's actions that open something over the tabs. */
    interface OnBottomNavActionListener {
        /** The keypad item was clicked. */
        fun onKeypadClicked()

        /** The search button was clicked. */
        fun onSearchClicked()
    }

    private val listeners = mutableListOf<OnBottomNavTabSelectedListener>()
    private var actionListener: OnBottomNavActionListener? = null

    private var selected by mutableIntStateOf(TabIndex.CALL_LOG)
    private var voicemailShown by mutableStateOf(false)
    private var callLogCount by mutableIntStateOf(0)
    private var voicemailCount by mutableIntStateOf(0)

    @Composable
    override fun Content() {
        PvotAppTheme {
            val tabs = buildList {
                add(Tab(TabIndex.CALL_LOG, R.drawable.ic_tab_recents, R.string.tab_title_call_history, callLogCount))
                add(Tab(TabIndex.CONTACTS, R.drawable.ic_tab_contacts, R.string.tab_all_contacts, 0))
                if (voicemailShown) {
                    add(Tab(TabIndex.VOICEMAIL, R.drawable.ic_tab_voicemail, R.string.tab_title_voicemail, voicemailCount))
                }
                add(Tab(KEYPAD, R.drawable.ic_tab_keypad, R.string.tab_title_keypad, 0))
            }
            val sizes = LocalPvotNavBarSizes.current.copy(
                fillWidth = true,
                horizontalPadding = PvotSpacing.M,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(end = PvotSpacing.M),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                PvotNavBar(
                    selectedTab = tabs.indexOfFirst { it.index == selected },
                    onTabClick = { position -> onTabClick(tabs[position].index) },
                    tabs = tabs.map {
                        PvotTabItem(
                            iconRes = it.iconRes,
                            labelRes = it.labelRes,
                            contentDescriptionRes = it.labelRes,
                            badgeCount = it.badgeCount,
                        )
                    },
                    modifier = Modifier.weight(1f),
                    sizes = sizes,
                )
                PvotGlassIconButton(
                    onClick = { actionListener?.onSearchClicked() },
                    backdrop = null,
                    size = sizes.barHeight,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_search),
                        contentDescription = stringResource(R.string.description_search_button),
                    )
                }
            }
        }
    }

    private fun onTabClick(index: Int) {
        if (index == KEYPAD) {
            actionListener?.onKeypadClicked()
        } else {
            selectTab(index)
        }
    }

    /** Select tab for user and non-user click. */
    fun selectTab(@TabIndex tab: Int) {
        check(tab == TabIndex.CALL_LOG || tab == TabIndex.CONTACTS || tab == TabIndex.VOICEMAIL) {
            "Invalid tab: $tab"
        }
        selected = tab
        for (listener in listeners) {
            when (tab) {
                TabIndex.CALL_LOG -> listener.onCallLogSelected()
                TabIndex.CONTACTS -> listener.onContactsSelected()
                TabIndex.VOICEMAIL -> listener.onVoicemailSelected()
            }
        }
    }

    /**
     * Displays or hides the voicemail tab.
     *
     * In the event that the voicemail tab was earlier visible but is now no longer visible, we
     * move to the call log tab.
     */
    fun showVoicemail(showTab: Boolean) {
        LogUtil.i("OldMainActivityPeer.showVoicemail", "showing Tab:%b", showTab)
        val wasShown = voicemailShown
        voicemailShown = showTab
        if (wasShown && !showTab && selected == TabIndex.VOICEMAIL) {
            LogUtil.i("OldMainActivityPeer.showVoicemail", "hid VM tab and moved to call log tab")
            selectTab(TabIndex.CALL_LOG)
        }
    }

    fun setNotificationCount(@TabIndex tab: Int, count: Int) {
        require(count >= 0) { "Invalid count: $count" }
        when (tab) {
            TabIndex.CALL_LOG -> callLogCount = count
            TabIndex.VOICEMAIL -> voicemailCount = count
            TabIndex.CONTACTS -> Unit
            else -> throw IllegalStateException("Invalid tab: $tab")
        }
    }

    fun addOnTabSelectedListener(listener: OnBottomNavTabSelectedListener) {
        listeners.add(listener)
    }

    fun setOnActionListener(listener: OnBottomNavActionListener?) {
        actionListener = listener
    }

    @TabIndex
    fun getSelectedTab(): Int = selected

    private data class Tab(val index: Int, val iconRes: Int, val labelRes: Int, val badgeCount: Int)

    private companion object {
        const val KEYPAD = 100
    }
}
