/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.fragment.app.Fragment
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags

/** A settings screen drawn in Compose, hosted by DialerSettingsActivity under its top bar. */
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
        // Qualified: inside apply, a bare Content() is ComposeView.Content and recurses forever.
        setContent { SettingsTheme { this@SettingsComposeFragment.Content() } }
    }
}

/** The theme and background every Compose settings screen sits on. */
@Composable
fun SettingsTheme(content: @Composable () -> Unit) = PhoneScreenTheme(content = content)

/** A scrolling list of settings rows, padded to clear the system navigation bar. */
@Composable
fun SettingsList(content: LazyListScope.() -> Unit) {
    val navigationBar = WindowInsets.navigationBars.asPaddingValues()
    val direction = LocalLayoutDirection.current
    PvotScreen(
        modifier = Modifier.fillMaxSize().testTag(TestTags.SETTINGS_LIST),
        contentPadding = PaddingValues(
            start = PvotScreenDefaults.HorizontalPadding + navigationBar.calculateStartPadding(direction),
            end = PvotScreenDefaults.HorizontalPadding + navigationBar.calculateEndPadding(direction),
            top = PvotSpacing.S,
            bottom = PvotScreenDefaults.BottomSpacing + navigationBar.calculateBottomPadding(),
        ),
        content = content,
    )
}

/** A group title in a [SettingsList], aligned with the rows' text. */
@Composable
fun SettingsSectionHeader(title: String) {
    PvotSectionHeader(
        title = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = PvotSpacing.M, end = PvotSpacing.M, top = PvotSpacing.L),
    )
}
