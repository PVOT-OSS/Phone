/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.PvotGlassCardDefaults
import com.prauga.pvot.designsystem.components.list.PvotLetterTile
import com.prauga.pvot.designsystem.components.list.PvotListRowDefaults
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.components.slider.PvotSlider
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassIntensity
import com.prauga.pvot.designsystem.effects.PvotGlassMaterial
import com.prauga.pvot.designsystem.effects.pvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.rememberPvotGlassBackdrop
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.settings.ui.SettingsComposeFragment
import org.prauga.phone.dialer.app.settings.ui.SettingsGroup
import org.prauga.phone.dialer.app.settings.ui.SettingsGroupDefaults
import org.prauga.phone.dialer.app.settings.ui.SettingsList
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings

/** How the app looks: whether the bars, sheets and controls are glass, and how much. */
class AppearanceSettingsFragment : SettingsComposeFragment() {

    @Composable
    override fun Content() {
        AppearanceSettingsList()
    }
}

/** The appearance settings, as a list. */
@Composable
fun AppearanceSettingsList() {
    val context = LocalContext.current
    val appearance by AppearanceSettings.rememberAppearance()
    val max = PvotGlassDefaults.maxIntensity()
    SettingsList {
        item(key = "glass") {
            SettingsGroup(title = stringResource(R.string.appearance_glass_title), dividers = false) {
                PvotSwitchRow(
                    title = stringResource(R.string.appearance_glass_effects),
                    checked = appearance.glass,
                    onCheckedChange = { AppearanceSettings.setGlass(context, it) },
                    modifier = Modifier.testTag(TestTags.APPEARANCE_GLASS_SWITCH),
                    summary = stringResource(R.string.appearance_glass_effects_summary),
                )
                // Padded like the row above, so the label starts where the row's title does.
                Column(Modifier.padding(PvotListRowDefaults.ContentPadding)) {
                    Text(
                        text = stringResource(R.string.appearance_glass_amount),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(
                            alpha = if (appearance.glass) 1f else PvotListRowDefaults.DisabledAlpha,
                        ),
                    )
                    PvotSlider(
                        value = appearance.intensity.coerceAtMost(max),
                        onValueChange = { AppearanceSettings.setIntensity(context, it) },
                        modifier = Modifier.testTag(TestTags.APPEARANCE_GLASS_SLIDER),
                        enabled = appearance.glass && max > 0f,
                        valueRange = 0f..max,
                    )
                }
            }
        }
        item(key = "preview") { GlassPreview() }
        item(key = "note") {
            val reduced = PvotGlassIntensity.isReduced()
            val note = when {
                reduced -> R.string.appearance_glass_reduced
                appearance.animationsOff -> R.string.appearance_glass_animations_off
                else -> null
            }
            if (note != null) {
                Text(
                    text = stringResource(note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(
                        horizontal = SettingsGroupDefaults.CardPadding + SettingsGroupDefaults.RowPadding,
                    ),
                )
            }
        }
    }
}

/** How much of the preview's width its card takes. */
private const val PreviewCardWidthFraction = 0.7f

/** A small piece of the floating layer, so the slider has something to show. */
@Composable
private fun GlassPreview() {
    val backdrop = rememberPvotGlassBackdrop()
    val intensity = PvotGlassDefaults.currentIntensity()
    val label = stringResource(R.string.appearance_glass_preview)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PvotShapes.Card)
            .testTag(TestTags.APPEARANCE_GLASS_PREVIEW),
    ) {
        // Glass sits over the content that scrolls under it, so the preview's card sits over rows.
        PreviewRows(Modifier.fillMaxWidth().pvotGlassBackdrop(backdrop))
        val cardModifier = Modifier.align(Alignment.Center).fillMaxWidth(PreviewCardWidthFraction)
        // Both cards pad their text the same and share a corner, so the text holds still when the
        // glass is switched on or off.
        if (intensity > PvotGlassDefaults.SolidIntensity) {
            PvotGlassCard(
                backdrop = backdrop,
                modifier = cardModifier,
                style = PvotGlassDefaults.materialStyle(PvotGlassMaterial.Regular),
                contentPadding = PvotGlassCardDefaults.ContentPadding,
            ) { PreviewText(label) }
        } else {
            PvotCard(modifier = cardModifier, shape = RoundedCornerShape(PvotGlassCardDefaults.CornerRadius)) {
                Column(Modifier.padding(PvotGlassCardDefaults.ContentPadding)) { PreviewText(label) }
            }
        }
    }
}

/** Stand-in list rows for the preview's glass to refract: a letter tile and two lines each. */
@Composable
private fun PreviewRows(modifier: Modifier) {
    val line = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(PvotSpacing.L),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        PreviewRowNames.forEachIndexed { index, name ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                PvotLetterTile(name = name, colorKey = index)
                Column(
                    modifier = Modifier.padding(start = PvotSpacing.M),
                    verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
                ) {
                    Box(Modifier.fillMaxWidth(PreviewTitleFraction).height(PvotSpacing.M).background(line, PvotShapes.Pill))
                    Box(Modifier.fillMaxWidth(PreviewSubtitleFraction).height(PvotSpacing.S).background(line, PvotShapes.Pill))
                }
            }
        }
    }
}

/** Initials of the preview's stand-in rows; not shown as text. */
private val PreviewRowNames = listOf("A", "M", "S")

private const val PreviewTitleFraction = 0.6f
private const val PreviewSubtitleFraction = 0.4f

@Composable
private fun PreviewText(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
