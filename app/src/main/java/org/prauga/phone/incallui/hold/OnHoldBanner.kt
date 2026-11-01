/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.hold

import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo

/** A slim banner for the call on hold: who it is and that they are on hold. */
@Composable
fun OnHoldBanner(info: SecondaryInfo, modifier: Modifier = Modifier) {
    val name = if (info.nameIsNumber()) {
        PhoneNumberUtils.createTtsSpannable(BidiFormatter.getInstance().unicodeWrap(info.name(), TextDirectionHeuristics.LTR)).toString()
    } else {
        info.name().orEmpty()
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer, PvotShapes.Card)
            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S)
            .testTag(TestTags.ON_HOLD_BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Icon(
            painter = painterResource(if (info.isVideoCall()) R.drawable.ic_video else R.drawable.ic_player_pause),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.incall_on_hold),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

/** Builds the on-hold banner as a view, for screens that still place it as a fragment. */
object OnHoldBannerHost {
    @JvmStatic
    fun create(context: android.content.Context, info: SecondaryInfo): android.view.View =
        androidx.compose.ui.platform.ComposeView(context).apply {
            setViewCompositionStrategy(androidx.compose.ui.platform.ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                org.prauga.phone.dialer.ui.PhoneScreenTheme(fillBackground = false) {
                    OnHoldBanner(info, Modifier.padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.XS))
                }
            }
        }
}
