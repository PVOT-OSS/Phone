/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.booleanResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.components.button.PvotIconButton
import com.prauga.pvot.designsystem.components.button.PvotIconButtonDefaults
import com.prauga.pvot.designsystem.navigation.LocalPvotNavBarSizes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.ui.R

/** Sizes shared by the main tabs' headers, so every tab's title and options button line up. */
object TabHeaderDefaults {
    /** Height of the header row. */
    val Height: Dp = 64.dp
}

/**
 * A main tab's title with an options button at its end that opens the app menu. The title starts at
 * the screen padding, like the titles of the other screens' top bars. [actions], such as search,
 * sit before the options button.
 */
@Composable
fun TabHeader(
    title: String,
    onOpenMenu: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(TabHeaderDefaults.Height)
                .padding(start = PvotSpacing.L, end = PvotSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        actions()
        OptionsButton(onClick = onOpenMenu)
    }
}

/** The options button that opens the app menu. */
@Composable
fun OptionsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PvotIconButton(
        icon = painterResource(R.drawable.ic_dots_vertical),
        contentDescription = stringResource(R.string.action_menu_overflow_description),
        onClick = onClick,
        modifier = modifier.testTag(TestTags.APP_MENU),
        colors = PvotIconButtonDefaults.plainColors(),
    )
}

/**
 * Bottom padding for a main tab's list: the nav bar floats over the tabs, so the end of the list
 * scrolls up clear of it, the space round it and the navigation bar. With the rail of a wide window
 * at the side, only the navigation bar is left to clear.
 */
@Composable
fun navBarClearance(): Dp {
    val system = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (booleanResource(R.bool.main_nav_rail)) return system + PvotSpacing.M
    return LocalPvotNavBarSizes.current.barHeight + PvotSpacing.M * 2 + system
}
