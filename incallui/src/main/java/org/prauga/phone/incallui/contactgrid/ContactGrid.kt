/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.contactgrid

import android.content.Context
import android.graphics.drawable.Drawable
import android.telephony.PhoneNumberUtils
import android.text.BidiFormatter
import android.text.TextDirectionHeuristics
import android.text.format.DateUtils
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.delay
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.incall.protocol.ContactPhotoType
import org.prauga.phone.incallui.incall.protocol.PrimaryCallState
import org.prauga.phone.incallui.incall.protocol.PrimaryInfo

/**
 * Who the call is with and how it is going, as Compose state. The same rules as the old contact
 * grid decide what shows: [TopRow] for the status line, [BottomRow] for the line under the name.
 */
class ContactGridState(private val context: Context) {

    private var primaryInfo = PrimaryInfo.empty()
    private var callState = PrimaryCallState.empty()

    /** "Calling via …", "On hold", "Call ended" and similar, or empty. */
    var status by mutableStateOf<CharSequence>("")
        private set

    var statusIcon by mutableStateOf<Drawable?>(null)
        private set

    var name by mutableStateOf<String?>(null)
        private set

    var nameIsNumber by mutableStateOf(false)
        private set

    var photoUri by mutableStateOf<String?>(null)
        private set

    var colorKey by mutableStateOf("")
        private set

    var bottom by mutableStateOf<BottomRow.Info?>(null)
        private set

    /** When the call connected, in wall-clock millis, while the timer shows; otherwise null. */
    var connectTimeMillis by mutableStateOf<Long?>(null)
        private set

    /** "Your number is …" for emergency callbacks, or null. */
    var callbackNumber by mutableStateOf<String?>(null)
        private set

    var isInMultiWindowMode by mutableStateOf(false)

    fun setPrimary(primaryInfo: PrimaryInfo) {
        this.primaryInfo = primaryInfo
        update()
    }

    fun setCallState(callState: PrimaryCallState) {
        this.callState = callState
        update()
    }

    /**
     * What TalkBack announces for the screen: the status, the name and, when it matters, the line
     * under it.
     */
    fun accessibilityText(): List<CharSequence?> {
        val info = BottomRow.getInfo(context, callState, primaryInfo)
        return listOfNotNull(
            status,
            name,
            info.label.takeIf { info.shouldPopulateAccessibilityEvent },
        )
    }

    private fun update() {
        val top = TopRow.getInfo(context, callState, primaryInfo)
        status = top.label ?: ""
        statusIcon = top.icon
        name = primaryInfo.name()?.takeIf { it.isNotEmpty() }
        nameIsNumber = primaryInfo.nameIsNumber()
        photoUri =
            primaryInfo.photoUri()?.toString()?.takeIf {
                primaryInfo.photoType() == ContactPhotoType.CONTACT
            }
        colorKey = primaryInfo.contactInfoLookupKey() ?: primaryInfo.number().orEmpty()
        val info = BottomRow.getInfo(context, callState, primaryInfo)
        bottom = info
        val wasTiming = connectTimeMillis != null
        connectTimeMillis = if (info.isTimerVisible) callState.connectTimeMillis() else null
        if (!wasTiming && connectTimeMillis != null)
            LogUtil.i("ContactGridState.update", "starting timer")
        callbackNumber =
            callState
                .callbackNumber()
                ?.takeIf { it.isNotEmpty() }
                ?.let {
                    context.getString(
                        R.string.contact_grid_callback_number,
                        BidiFormatter.getInstance().unicodeWrap(it, TextDirectionHeuristics.LTR),
                    )
                }
    }
}

/** The top of the call screen: status, photo, name, and the timer or details under it. */
@Composable
fun ContactGrid(
    state: ContactGridState,
    modifier: Modifier = Modifier,
    avatarSize: androidx.compose.ui.unit.Dp = 96.dp,
) {
    Column(
        modifier = modifier.fillMaxWidth().testTag(TestTags.CONTACT_GRID),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
        ) {
            state.statusIcon?.let { icon ->
                val bitmap = remember(icon) { icon.toBitmap().asImageBitmap() }
                Image(bitmap, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Text(
                text = state.status.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
        ContactAvatar(
            name = state.name.orEmpty(),
            photoUri = state.photoUri,
            colorKey = state.colorKey,
            size = avatarSize,
            modifier = Modifier.padding(vertical = PvotSpacing.S),
        )
        Text(
            text =
                state.name
                    ?.let {
                        if (state.nameIsNumber)
                            PhoneNumberUtils.createTtsSpannable(
                                    BidiFormatter.getInstance()
                                        .unicodeWrap(it, TextDirectionHeuristics.LTR)
                                )
                                .toString()
                        else it
                    }
                    .orEmpty(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.padding(horizontal = PvotSpacing.L).testTag(TestTags.CONTACT_GRID_NAME),
        )
        state.bottom?.let { BottomLine(it, state.connectTimeMillis) }
        if (!state.isInMultiWindowMode) {
            state.callbackNumber?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BottomLine(info: BottomRow.Info, connectTimeMillis: Long?) {
    val elapsed by
        produceState<String?>(null, connectTimeMillis) {
            val connected = connectTimeMillis ?: return@produceState
            while (true) {
                value =
                    DateUtils.formatElapsedTime(
                        ((System.currentTimeMillis() - connected) / 1000).coerceAtLeast(0)
                    )
                delay(1000)
            }
        }
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
    ) {
        if (info.isSpamIconVisible)
            Icon(
                painterResource(R.drawable.ic_alert_triangle),
                null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp),
            )
        if (info.isWorkIconVisible)
            Icon(
                painterResource(R.drawable.ic_briefcase),
                null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        if (info.isHdIconVisible || info.isHdAttemptingIconVisible) {
            Text(
                stringResource(R.string.incall_hd_badge),
                style = MaterialTheme.typography.labelSmall,
                color = if (info.isHdIconVisible) MaterialTheme.colorScheme.primary else color,
            )
        }
        if (info.isForwardIconVisible)
            Icon(
                painterResource(R.drawable.ic_arrow_forward_up),
                null,
                tint = color,
                modifier = Modifier.size(16.dp),
            )
        val label = info.label?.toString().orEmpty()
        val text =
            when {
                info.isTimerVisible && info.isForwardIconVisible && label.isNotEmpty() ->
                    stringResource(R.string.incall_label_and_timer, label, elapsed.orEmpty())
                info.isTimerVisible && info.isForwardIconVisible -> elapsed.orEmpty()
                info.isTimerVisible -> elapsed.orEmpty()
                else -> label
            }
        Text(
            text = if (info.isSpamIconVisible) text.uppercase() else text,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            modifier = Modifier.testTag(TestTags.CONTACT_GRID_BOTTOM),
        )
    }
}
