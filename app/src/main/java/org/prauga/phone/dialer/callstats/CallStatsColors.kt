/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.provider.CallLog.Calls
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.prauga.pvot.designsystem.components.progress.PvotProportionBarColors
import com.prauga.pvot.designsystem.components.progress.PvotProportionBarDefaults

/** The call types a statistics bar is split into, in the order of its colors. */
internal val StatTypes = listOf(Calls.INCOMING_TYPE, Calls.OUTGOING_TYPE, Calls.MISSED_TYPE, Calls.BLOCKED_TYPE)

/** The statistics' bar colors: steps of the accent, so the parts of a bar read as one family. */
@Composable
internal fun statColors(): PvotProportionBarColors = PvotProportionBarDefaults.tonalColors()

/** The color [type] has in a bar and its legend; any type past blocked takes blocked's. */
internal fun PvotProportionBarColors.colorFor(type: Int): Color =
    segmentColors[StatTypes.indexOf(type).let { if (it < 0) StatTypes.lastIndex else it }]

/** [fractions] by call type, as a bar's segments in [StatTypes] order; types not given are 0. */
internal fun segmentsOf(fractions: Map<Int, Float>): List<Float> = StatTypes.map { fractions[it] ?: 0f }
