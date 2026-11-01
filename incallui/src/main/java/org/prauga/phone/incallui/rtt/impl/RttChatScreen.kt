/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.rtt.impl

import android.telecom.CallAudioState
import android.text.format.DateUtils
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.components.list.PvotSwitchRow
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.delay
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.R
import org.prauga.phone.incallui.audioroute.audioRoutesOf
import org.prauga.phone.incallui.hold.OnHoldBanner
import org.prauga.phone.incallui.speakerbuttonlogic.SpeakerButtonInfo
import org.prauga.phone.incallui.util.BluetoothUtil

/** What the RTT chat screen does when the user acts; the RTT fragment implements it. */
interface RttChatActions {
    fun onSubmit()

    fun onEndCall()

    fun onMenuOpen()

    fun onMute(muted: Boolean)

    /** Turns the speaker on or off; with Bluetooth connected it opens the audio routes instead. */
    fun onSpeaker()

    fun onDialpad(show: Boolean)

    fun onAddCall()

    fun onSwap()

    /** Turns the RTT call into a voice call. */
    fun onSwitchToVoice()

    fun onAudioRoute(route: Int)

    /** The user scrolled back through the chat, which hides the keyboard. */
    fun onScrollBack()
}

/**
 * The RTT call screen: who and how long at the top, the conversation, and the input. [input] is the
 * fragment's text field, kept as a view so every keystroke reaches the other side as before.
 */
@Composable
fun RttChatScreen(
    state: RttChatUiState,
    messages: RttChatMessages,
    input: EditText,
    actions: RttChatActions,
) {
    val listState = rememberLazyListState()
    val bubbles = messages.bubbles
    val count = bubbles.size + if (messages.showAdvisory) 1 else 0
    LaunchedEffect(messages.localUpdates) {
        if (count > 0) listState.animateScrollToItem(count - 1)
    }
    LaunchedEffect(messages.remoteUpdates) {
        // Follow the other side only when the user is already at the end.
        if (count > 0 && !listState.canScrollForward) listState.animateScrollToItem(count - 1)
    }
    val scrollBack =
        remember(actions) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (available.y > 0 && source == NestedScrollSource.UserInput)
                        actions.onScrollBack()
                    return Offset.Zero
                }
            }
        }
    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        Header(state, actions)
        state.statusBanner?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier =
                    Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.tertiaryContainer)
                        .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S)
                        .testTag(TestTags.RTT_STATUS_BANNER),
            )
        }
        state.secondary?.let {
            OnHoldBanner(
                it,
                Modifier.padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.XS),
            )
        }
        LazyColumn(
            state = listState,
            modifier =
                Modifier.weight(1f)
                    .fillMaxWidth()
                    .nestedScroll(scrollBack)
                    .testTag(TestTags.RTT_CHAT),
            contentPadding = PaddingValues(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
            verticalArrangement = Arrangement.Bottom,
        ) {
            if (messages.showAdvisory) {
                item(key = "advisory") {
                    Text(
                        text = stringResource(R.string.rtt_transcript_advisory),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = PvotSpacing.M),
                    )
                }
            }
            itemsIndexed(bubbles) { i, bubble ->
                val sameGroup = i > 0 && bubbles[i - 1].isRemote == bubble.isRemote
                Bubble(bubble, sameGroup, state)
            }
        }
        if (state.inputVisible) InputBar(state, input, actions)
    }
    if (state.menuOpen) OverflowMenu(state, actions)
    if (state.audioMenuOpen) AudioMenu(state, actions)
}

@Composable
private fun Header(state: RttChatUiState, actions: RttChatActions) {
    val elapsed by
        produceState<String?>(null, state.connectTimeMillis) {
            val connected = state.connectTimeMillis ?: return@produceState
            while (true) {
                value =
                    DateUtils.formatElapsedTime(
                        ((System.currentTimeMillis() - connected) / 1000).coerceAtLeast(0)
                    )
                delay(1000)
            }
        }
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        ContactAvatar(name = state.name, photoUri = state.photoUri, colorKey = state.colorKey)
        Column(Modifier.weight(1f)) {
            Text(
                state.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            elapsed?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = actions::onMenuOpen, modifier = Modifier.testTag(TestTags.RTT_MENU)) {
            Icon(
                painterResource(R.drawable.ic_dots_vertical),
                stringResource(R.string.content_description_overflow),
            )
        }
        IconButton(
            onClick = actions::onEndCall,
            modifier =
                Modifier.size(48.dp)
                    .background(MaterialTheme.colorScheme.error, CircleShape)
                    .testTag(TestTags.RTT_END_CALL),
        ) {
            Icon(
                painterResource(R.drawable.ic_phone_off),
                stringResource(R.string.incall_content_description_end_call),
                tint = MaterialTheme.colorScheme.onError,
            )
        }
    }
}

@Composable
private fun Bubble(bubble: RttBubble, sameGroup: Boolean, state: RttChatUiState) {
    val remote = bubble.isRemote
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = if (sameGroup) 2.dp else PvotSpacing.M),
        horizontalArrangement = if (remote) Arrangement.Start else Arrangement.End,
        verticalAlignment = Alignment.Top,
    ) {
        if (remote) {
            Box(Modifier.size(AvatarSize)) {
                if (!sameGroup)
                    ContactAvatar(
                        name = state.name,
                        photoUri = state.photoUri,
                        colorKey = state.colorKey,
                        size = AvatarSize,
                    )
            }
            Box(Modifier.size(PvotSpacing.S))
        }
        Text(
            text = bubble.content,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (remote) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onPrimaryContainer,
            modifier =
                Modifier.widthIn(max = 280.dp)
                    .background(
                        if (remote) MaterialTheme.colorScheme.surfaceContainerHigh
                        else MaterialTheme.colorScheme.primaryContainer,
                        PvotShapes.Card,
                    )
                    .padding(horizontal = PvotSpacing.M, vertical = PvotSpacing.S),
        )
    }
}

@Composable
private fun InputBar(state: RttChatUiState, input: EditText, actions: RttChatActions) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val hintColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    Row(
        modifier =
            Modifier.fillMaxWidth()
                .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, PvotShapes.Pill)
                .padding(start = PvotSpacing.L, end = PvotSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AndroidView(
            factory = { input.also { (it.parent as? ViewGroup)?.removeView(it) } },
            update = {
                it.setTextColor(textColor)
                it.setHintTextColor(hintColor)
            },
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag(TestTags.RTT_INPUT),
        )
        IconButton(
            onClick = actions::onSubmit,
            enabled = state.submitEnabled,
            modifier = Modifier.testTag(TestTags.RTT_SUBMIT),
        ) {
            Icon(
                painterResource(R.drawable.ic_send),
                stringResource(R.string.content_description_rtt_check_button),
                tint =
                    if (state.submitEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            )
        }
    }
}

@Composable
private fun OverflowMenu(state: RttChatUiState, actions: RttChatActions) {
    val speaker = state.audioState?.let(::SpeakerButtonInfo)
    PvotBottomSheet(visible = true, onDismissRequest = { state.menuOpen = false }) {
        PvotSwitchRow(
            title = stringResource(R.string.incall_label_mute),
            checked = state.muted,
            onCheckedChange = actions::onMute,
            icon = {
                Icon(painterResource(R.drawable.ic_microphone_off), contentDescription = null)
            },
        )
        if (speaker == null || speaker.nonBluetoothMode) {
            PvotSwitchRow(
                title = stringResource(R.string.incall_label_speaker),
                checked = speaker?.isChecked == true,
                onCheckedChange = { actions.onSpeaker() },
                icon = { Icon(painterResource(R.drawable.ic_volume), contentDescription = null) },
            )
        } else {
            PvotSheetItem(
                title = stringResource(speaker.label),
                icon = {
                    Icon(painterResource(R.drawable.ic_bluetooth), contentDescription = null)
                },
                onClick = {
                    state.menuOpen = false
                    actions.onSpeaker()
                },
            )
        }
        PvotSwitchRow(
            title = stringResource(R.string.incall_label_dialpad),
            checked = state.dialpadShowing,
            onCheckedChange = actions::onDialpad,
            icon = { Icon(painterResource(R.drawable.ic_dialpad), contentDescription = null) },
        )
        PvotSheetItem(
            title = stringResource(R.string.incall_label_add_call),
            icon = { Icon(painterResource(R.drawable.ic_user_plus), contentDescription = null) },
            onClick = {
                state.menuOpen = false
                actions.onAddCall()
            },
        )
        if (state.swapEnabled || state.switchToSecondaryEnabled) {
            PvotSheetItem(
                title = stringResource(R.string.incall_label_swap),
                icon = {
                    Icon(painterResource(R.drawable.ic_arrows_exchange), contentDescription = null)
                },
                onClick = {
                    state.menuOpen = false
                    actions.onSwap()
                },
            )
        }
        PvotSheetItem(
            title = stringResource(R.string.rtt_button_downgrade),
            icon = { Icon(painterResource(R.drawable.ic_phone), contentDescription = null) },
            onClick = {
                state.menuOpen = false
                actions.onSwitchToVoice()
            },
        )
    }
}

@Composable
private fun AudioMenu(state: RttChatUiState, actions: RttChatActions) {
    val audioState =
        state.audioState
            ?: CallAudioState(false, CallAudioState.ROUTE_EARPIECE, CallAudioState.ROUTE_EARPIECE)
    // One row per kind of route, as the old menu had; the active Bluetooth device is the one used.
    val routes = audioRoutesOf(audioState, BluetoothUtil::getAliasName).distinctBy { it.route }
    PvotBottomSheet(visible = true, onDismissRequest = { state.audioMenuOpen = false }) {
        for (route in routes) {
            PvotListRow(
                title =
                    route.label
                        ?: stringResource(
                            when (route.route) {
                                CallAudioState.ROUTE_BLUETOOTH -> R.string.audioroute_bluetooth
                                CallAudioState.ROUTE_SPEAKER -> R.string.audioroute_speaker
                                CallAudioState.ROUTE_WIRED_HEADSET -> R.string.audioroute_headset
                                else -> R.string.audioroute_phone
                            }
                        ),
                leading = { Icon(painterResource(route.icon), contentDescription = null) },
                selected = audioState.route == route.route,
                onClick = { actions.onAudioRoute(route.route) },
            )
        }
    }
}

private val AvatarSize = PvotLetterTileDefaults.Size * 0.75f

/**
 * Builds the RTT fragment's view: the Compose screen, with the in-call dialpad's container over its
 * bottom.
 */
object RttChatHost {
    /** The chat's text field: borderless, one line, sending on the keyboard's action key. */
    @JvmStatic
    fun createInput(context: android.content.Context): EditText =
        EditText(context).apply {
            id = R.id.rtt_chat_input
            background = null
            setHint(R.string.rtt_input_hint)
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
        }

    @JvmStatic
    fun createView(
        context: android.content.Context,
        state: RttChatUiState,
        messages: RttChatMessages,
        input: EditText,
        actions: RttChatActions,
    ): android.view.View {
        val compose =
            androidx.compose.ui.platform.ComposeView(context).apply {
                setViewCompositionStrategy(
                    androidx.compose.ui.platform.ViewCompositionStrategy
                        .DisposeOnViewTreeLifecycleDestroyed
                )
                setContent {
                    org.prauga.phone.dialer.ui.PhoneScreenTheme {
                        RttChatScreen(state, messages, input, actions)
                    }
                }
            }
        val dialpad =
            android.widget.FrameLayout(context).apply {
                id = R.id.incall_dialpad_container
                clipChildren = false
                clipToPadding = false
            }
        return android.widget.FrameLayout(context).apply {
            addView(
                compose,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(
                dialpad,
                android.widget.FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    android.view.Gravity.BOTTOM,
                ),
            )
        }
    }
}
