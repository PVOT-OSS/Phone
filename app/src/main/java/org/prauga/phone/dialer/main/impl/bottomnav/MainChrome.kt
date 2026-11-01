/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl.bottomnav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
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
import com.prauga.pvot.designsystem.components.button.PvotFab
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.navigation.PvotNavBar
import com.prauga.pvot.designsystem.navigation.PvotTabItem
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags

/** Puts the Compose nav bar and dialpad button into the main screen's view layout. */
object MainChrome {

    @JvmStatic
    fun bind(
        navBarView: ComposeView,
        fabView: ComposeView,
        navBar: MainNavBarState,
        fab: MainFabState,
        onFabClick: Runnable,
    ) {
        navBarView.setChromeContent { MainNavBar(navBar) }
        fabView.setChromeContent { MainFab(fab, onClick = onFabClick::run) }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private fun ComposeView.setChromeContent(content: @Composable () -> Unit) {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PvotAppTheme {
                Box(Modifier.semantics { testTagsAsResourceId = true }) { content() }
            }
        }
    }
}

/** The main screen's tabs, with unread badges on recents and voicemail. */
@Composable
fun MainNavBar(state: MainNavBarState, modifier: Modifier = Modifier) {
    val tabs = state.tabs
    AnimatedVisibility(
        visible = state.isVisible,
        modifier = modifier,
        enter = slideInVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) { it } +
            fadeIn(tween(PvotDurations.Medium)),
        exit = slideOutVertically(tween(PvotDurations.Short)) { it } +
            fadeOut(tween(PvotDurations.Short)),
    ) {
        PvotNavBar(
            selectedTab = tabs.indexOf(state.selectedTab),
            onTabClick = { state.selectTab(tabs[it]) },
            tabs = tabs.map { tab -> tabItem(tab, state.notificationCount(tab)) },
            modifier = Modifier.testTag(TestTags.MAIN_NAV_BAR),
        )
    }
}

/** The dialpad button, which scales away while the dialpad or search is open. */
@Composable
fun MainFab(state: MainFabState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = state.isShown,
        modifier = modifier,
        enter = scaleIn(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) +
            fadeIn(tween(PvotDurations.Medium)),
        exit = scaleOut(tween(PvotDurations.Short)) + fadeOut(tween(PvotDurations.Short)),
    ) {
        // Padding inside the view leaves room for the shadow, which the view would clip.
        PvotFab(
            icon = painterResource(R.drawable.ic_dialpad),
            onClick = onClick,
            contentDescription = stringResource(R.string.dialpad_button_content_description),
            modifier = Modifier.padding(PvotSpacing.L).testTag(TestTags.MAIN_FAB),
        )
    }
}

private fun tabItem(@TabIndex tab: Int, badgeCount: Int): PvotTabItem {
    val (icon, label) = when (tab) {
        TabIndex.SPEED_DIAL -> R.drawable.ic_star to R.string.tab_title_speed_dial
        TabIndex.CALL_LOG -> R.drawable.ic_history to R.string.tab_title_call_history
        TabIndex.CONTACTS -> R.drawable.ic_users to R.string.tab_all_contacts
        else -> R.drawable.ic_call_voicemail to R.string.tab_title_voicemail
    }
    return PvotTabItem(
        iconRes = icon,
        labelRes = label,
        contentDescriptionRes = label,
        badgeCount = badgeCount,
    )
}
