/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.fragment.app.Fragment
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.list.PvotListGroupDefaults
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags

/**
 * A settings screen drawn in Compose, hosted by DialerSettingsActivity in its glass scaffold, under
 * its top bar. Outside that activity (or in a test) it paints its own background.
 */
abstract class SettingsComposeFragment : Fragment() {

    /** The screen's content. */
    @Composable
    abstract fun Content()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        val chrome = (activity as? SettingsChromeHost)?.settingsChrome
        setContent {
            // Inside the scaffold the background is the scaffold's, so the content stays see-through.
            SettingsTheme(fillBackground = chrome == null) {
                CompositionLocalProvider(LocalSettingsChrome provides chrome) {
                    // Qualified: inside apply, a bare Content() is ComposeView.Content and recurses forever.
                    this@SettingsComposeFragment.Content()
                }
            }
        }
    }
}

/** An activity that draws the settings chrome (the top bar and background) around its screens. */
interface SettingsChromeHost {
    val settingsChrome: SettingsChrome
}

/**
 * What the settings chrome and the screen under it tell each other: the padding that keeps the
 * screen's first row clear of the top bar and its last clear of the navigation bar, and whether
 * the screen has scrolled, which folds the top bar into its glass row.
 */
@Stable
class SettingsChrome {
    /** The padding the screen's list needs around its rows. */
    var contentPadding by mutableStateOf(PaddingValues())

    /** Whether the screen's list has scrolled away from its top. */
    var compact by mutableStateOf(false)
}

/** The [SettingsChrome] of the scaffold a settings screen sits in, or null outside one. */
val LocalSettingsChrome = staticCompositionLocalOf<SettingsChrome?> { null }

/** The theme every Compose settings screen sits in; [fillBackground] paints its background. */
@Composable
fun SettingsTheme(fillBackground: Boolean = true, content: @Composable () -> Unit) =
    PhoneScreenTheme(fillBackground = fillBackground, content = content)

/**
 * The frame of a settings screen: the glass scaffold, with a back button and [title] in a top bar
 * that turns to glass once the screen's [SettingsList] scrolls under it.
 *
 * @param chrome Shared with the screen in [content], directly or through a hosted fragment
 * @param content The screen, typically a [SettingsList]
 */
@Composable
fun SettingsScaffold(
    title: String,
    onBack: () -> Unit,
    chrome: SettingsChrome = remember { SettingsChrome() },
    content: @Composable () -> Unit,
) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val direction = LocalLayoutDirection.current
    PhoneScaffold(
        topBar = {
            PvotTopBar(
                title = title,
                modifier = Modifier.testTag(TestTags.SETTINGS_TOP_BAR),
                onBack = onBack,
                compact = chrome.compact,
            )
        },
    ) { padding ->
        val contentPadding = PaddingValues(
            start = navigationBar.calculateStartPadding(direction),
            end = navigationBar.calculateEndPadding(direction),
            top = padding.calculateTopPadding(),
            bottom = navigationBar.calculateBottomPadding(),
        )
        SideEffect { chrome.contentPadding = contentPadding }
        CompositionLocalProvider(LocalSettingsChrome provides chrome) { content() }
    }
}

/**
 * A scrolling list of settings groups, clear of the bars around it. Inside a [SettingsScaffold] it
 * starts under the top bar and tells the bar when it has scrolled.
 */
@Composable
fun SettingsList(content: LazyListScope.() -> Unit) {
    val chrome = LocalSettingsChrome.current
    val direction = LocalLayoutDirection.current
    val bars = chrome?.contentPadding ?: WindowInsets.navigationBars.asPaddingValues()
    val state = rememberLazyListState()
    if (chrome != null) {
        val compact = rememberPvotTopBarCompact(state)
        SideEffect { chrome.compact = compact }
        // The next screen starts at its top, with the bar unfolded.
        DisposableEffect(chrome) { onDispose { chrome.compact = false } }
    }
    PvotScreen(
        modifier = Modifier.fillMaxSize().testTag(TestTags.SETTINGS_LIST),
        state = state,
        contentPadding = PaddingValues(
            start = PvotScreenDefaults.HorizontalPadding + bars.calculateStartPadding(direction),
            end = PvotScreenDefaults.HorizontalPadding + bars.calculateEndPadding(direction),
            top = PvotSpacing.S + bars.calculateTopPadding(),
            bottom = PvotScreenDefaults.BottomSpacing + bars.calculateBottomPadding(),
        ),
        content = content,
    )
}

/**
 * A group of settings rows on one card, under an optional [title], with a hairline between rows
 * as a [PvotListGroup] draws them. Rows keep their own padding, so their text lines up with
 * anything else in the group padded by [SettingsGroupDefaults.RowPadding].
 *
 * @param dividers Whether to draw the hairlines; off for a group that is one control, not rows
 */
@Composable
fun SettingsGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    dividers: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(modifier.fillMaxWidth()) {
        if (title != null) SettingsSectionHeader(title)
        PvotCard(Modifier.fillMaxWidth()) {
            DividedColumn(
                dividers = dividers,
                color = PvotListGroupDefaults.colors().dividerColor,
                modifier = Modifier.padding(SettingsGroupDefaults.CardPadding),
                content = content,
            )
        }
    }
}

/** Children stacked like a column, with a hairline between each two that have any height. */
@Composable
private fun DividedColumn(dividers: Boolean, color: Color, modifier: Modifier, content: @Composable () -> Unit) {
    // Where each line goes, worked out while placing and read when drawing, in the same frame.
    val lines = remember { mutableListOf<Int>() }
    val inset = with(LocalDensity.current) { PvotListGroupDefaults.DividerInset.toPx() }
    val thickness = with(LocalDensity.current) { PvotListGroupDefaults.DividerThickness.toPx() }
    Layout(
        content = content,
        modifier = modifier.drawBehind {
            if (dividers) {
                for (y in lines) {
                    drawRect(color, topLeft = Offset(inset, y - thickness / 2f), size = Size(size.width - inset * 2, thickness))
                }
            }
        },
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
        val width = placeables.maxOfOrNull { it.width }?.coerceAtLeast(constraints.minWidth) ?: constraints.minWidth
        val height = placeables.sumOf { it.height }
        layout(width, height) {
            lines.clear()
            var y = 0
            var placedAny = false
            for (placeable in placeables) {
                if (placeable.height > 0 && placedAny) lines += y
                placeable.placeRelative(0, y)
                y += placeable.height
                if (placeable.height > 0) placedAny = true
            }
        }
    }
}

/** A settings row's leading icon, bare and tinted as grouped rows show them; null for none. */
fun settingsIcon(@DrawableRes icon: Int?): (@Composable () -> Unit)? =
    icon?.let { res -> { Icon(painterResource(res), contentDescription = null) } }

/** Measurements shared by the settings groups. */
object SettingsGroupDefaults {
    /** The gap between a group's card and its rows. */
    val CardPadding: Dp = PvotSpacing.XS

    /** Horizontal padding of content in a group that is not a row, matching the rows' own. */
    val RowPadding: Dp = PvotSpacing.M
}

/** A group title over a [SettingsGroup], aligned with the rows' text. */
@Composable
fun SettingsSectionHeader(title: String) {
    PvotSectionHeader(
        title = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = SettingsGroupDefaults.CardPadding + SettingsGroupDefaults.RowPadding,
                end = SettingsGroupDefaults.RowPadding,
                top = PvotSpacing.S,
                bottom = PvotSpacing.S,
            ),
    )
}
