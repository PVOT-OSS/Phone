/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.prauga.pvot.designsystem.components.PvotDock
import com.prauga.pvot.designsystem.components.PvotDockAction
import com.prauga.pvot.designsystem.components.PvotDockItem
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.WindowBackdrop

/** Puts the Compose dock into the main screen's view layout. */
object MainChrome {

    /** Binds the dock, glass over [behind], the view holding the tabs it floats over. */
    @JvmStatic
    fun bind(
        dockView: ComposeView,
        behind: View,
        navBar: MainNavBarState,
        actions: MainDockActions,
    ) {
        dockView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        dockView.setChromeContent { WindowBackdrop(behind) { MainDock(navBar, actions) } }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ComposeView.setChromeContent(content: @Composable () -> Unit) {
        setContent {
            PhoneTheme { Box(Modifier.semantics { testTagsAsResourceId = true }) { content() } }
        }
    }
}

/** What the selected tab's pill does: dial on the dialpad, search on recents, add on contacts. */
interface MainDockActions {

    /** Places a call with the dialpad's number. */
    fun onDial()

    /** Opens search. */
    fun onSearch()

    /** Creates a contact. */
    fun onAddContact()
}

/**
 * The main screen's tabs as a dock, with unread badges on recents and voicemail. The selected tab's
 * pill runs that tab's action; voicemail has none.
 */
@Composable
fun MainDock(state: MainNavBarState, actions: MainDockActions, modifier: Modifier = Modifier) {
    val tabs = state.tabs
    val items = tabs.map { tab -> dockItem(tab, state.notificationCount(tab), actions) }
    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter =
            slideInVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) { it } +
                fadeIn(tween(PvotDurations.Medium)),
        exit =
            slideOutVertically(tween(PvotDurations.Short)) { it } +
                fadeOut(tween(PvotDurations.Short)),
    ) {
        PvotDock(
            items = items,
            selectedIndex = tabs.indexOf(state.selectedTab),
            onSelect = { state.selectTab(tabs[it]) },
            modifier = Modifier.testTag(TestTags.MAIN_NAV_BAR),
        )
    }
}

@Composable
private fun dockItem(@TabIndex tab: Int, badgeCount: Int, actions: MainDockActions): PvotDockItem =
    when (tab) {
        TabIndex.DIALPAD ->
            PvotDockItem(
                icon = painterResource(R.drawable.ic_dialpad),
                label = stringResource(R.string.tab_title_dialpad),
                action =
                    PvotDockAction(
                        painterResource(R.drawable.ic_phone),
                        stringResource(R.string.dock_action_dial),
                        actions::onDial,
                    ),
            )
        TabIndex.CALL_LOG ->
            PvotDockItem(
                icon = painterResource(R.drawable.ic_history),
                label = stringResource(R.string.tab_title_call_history),
                action =
                    PvotDockAction(
                        painterResource(R.drawable.ic_search),
                        stringResource(R.string.dock_action_search),
                        actions::onSearch,
                    ),
                badgeCount = badgeCount,
            )
        TabIndex.CONTACTS ->
            PvotDockItem(
                icon = painterResource(R.drawable.ic_users),
                label = stringResource(R.string.tab_all_contacts),
                action =
                    PvotDockAction(
                        painterResource(R.drawable.ic_user_plus),
                        stringResource(R.string.dock_action_add),
                        actions::onAddContact,
                    ),
            )
        else ->
            PvotDockItem(
                icon = painterResource(R.drawable.ic_call_voicemail),
                label = stringResource(R.string.tab_title_voicemail),
                badgeCount = badgeCount,
            )
    }
