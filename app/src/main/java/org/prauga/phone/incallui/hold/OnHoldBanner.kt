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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.incall.protocol.SecondaryInfo

/** Size of the circle holding the on-hold icon at the capsule's start. */
private val HoldIconCircle = 32.dp

/** A slim capsule for the call on hold: who it is and that they are on hold. */
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
            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
            .padding(start = PvotSpacing.XS, end = PvotSpacing.L, top = PvotSpacing.XS, bottom = PvotSpacing.XS)
            .testTag(TestTags.ON_HOLD_BANNER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Box(
            Modifier.size(HoldIconCircle).background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(if (info.isVideoCall()) R.drawable.ic_video else R.drawable.ic_player_pause),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp),
            )
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.incall_on_hold),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
