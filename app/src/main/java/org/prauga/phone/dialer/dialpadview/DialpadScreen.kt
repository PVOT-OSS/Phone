/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.dialpadview

import android.telecom.PhoneAccountHandle
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.effects.pvotPressScale
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.i18n.LocaleUtils
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import java.text.DecimalFormat
import java.util.Locale

/** A calling account offered in the dialpad's "Call via" menu. */
data class DialpadAccount(val handle: PhoneAccountHandle, val label: String, val selected: Boolean)

/** What the dialpad shows besides the number, as Compose state the dialpad fragment sets. */
class DialpadUiState {
    /** A warning under the number, such as "emergency calling not available", or null. */
    var hint by mutableStateOf<String?>(null)
    var rateCountry by mutableStateOf<String?>(null)
    var rateText by mutableStateOf<String?>(null)
    var deleteEnabled by mutableStateOf(false)
    var menuButtonVisible by mutableStateOf(false)
    var callButtonVisible by mutableStateOf(false)

    /** Whether the number is the pretend emergency number, which turns the call button red. */
    var pseudoEmergency by mutableStateOf(false)

    var menuOpen by mutableStateOf(false)
    var menuEnabled by mutableStateOf(false)
    var callWithNoteSupported by mutableStateOf(false)
    val accounts = mutableStateListOf<DialpadAccount>()
}

/** What the dialpad does when the user acts; the dialpad fragment implements it. */
interface DialpadCallbacks {
    /** A key went down ([pressed]) or up; [keyCode] is a [KeyEvent] key code. */
    fun onKey(keyCode: Int, pressed: Boolean)

    /** A key was held; returns whether that did something, such as 0 typing a plus. */
    fun onKeyLongPress(keyCode: Int): Boolean

    fun onDelete()
    fun onDeleteLongPress()
    fun onCall()
    fun onMenuOpen()
    fun onAddPause()
    fun onAddWait()
    fun onCallWithNote()
    fun onSelectAccount(handle: PhoneAccountHandle)
}

/** One dialpad key: its key code, digit and letters. */
data class DialpadKeySpec(val keyCode: Int, val number: String, val letters: String?, val secondaryLetters: String?)

/** The dialpad: the number with its delete button, the twelve keys and the call button. */
@Composable
fun DialpadScreen(state: DialpadUiState, digits: EditText, callbacks: DialpadCallbacks, modifier: Modifier = Modifier) {
    val keys = rememberKeys()
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M)
            .testTag(TestTags.DIALPAD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        state.rateCountry?.let { country ->
            Text(
                text = "$country  ${state.rateText.orEmpty()}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp))
            AndroidView(
                factory = { digits.also { (it.parent as? ViewGroup)?.removeView(it) } },
                update = { it.setTextColor(textColor) },
                modifier = Modifier.weight(1f).height(56.dp).testTag(TestTags.DIALPAD_DIGITS),
            )
            DeleteButton(state.deleteEnabled, callbacks)
        }
        state.hint?.let { hint ->
            Text(text = hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(PvotSpacing.S))
        DialpadKeyGrid(onKey = callbacks::onKey, onKeyLongPress = callbacks::onKeyLongPress, keys = keys)
        Spacer(Modifier.height(PvotSpacing.S))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) {
                if (state.menuButtonVisible) {
                    IconButton(onClick = callbacks::onMenuOpen, modifier = Modifier.testTag(TestTags.DIALPAD_MENU)) {
                        Icon(painterResource(R.drawable.ic_dots_vertical), contentDescription = stringResource(R.string.description_dialpad_overflow))
                    }
                }
            }
            CallButton(state, callbacks)
            Box(Modifier.size(KeySize))
        }
    }
    if (state.menuOpen) DialpadMenu(state, callbacks)
}

private val KeySize = 72.dp

/**
 * The twelve dialpad keys. A key reports going down and up through [onKey], so its tone plays
 * while held; [onKeyLongPress] answers whether a held 0 or 1 did something.
 */
@Composable
fun DialpadKeyGrid(
    onKey: (keyCode: Int, pressed: Boolean) -> Unit,
    onKeyLongPress: (keyCode: Int) -> Boolean,
    modifier: Modifier = Modifier,
    longPressKeys: Boolean = true,
    keys: List<DialpadKeySpec> = rememberKeys(),
) {
    Column(modifier) {
        for (row in keys.chunked(3)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (key in row) DialpadKey(key, onKey, if (longPressKeys) onKeyLongPress else { _ -> false }, longPressKeys)
            }
        }
    }
}

@Composable
private fun rememberKeys(): List<DialpadKeySpec> {
    val context = LocalContext.current
    val star = stringResource(R.string.dialpad_star_number)
    val pound = stringResource(R.string.dialpad_pound_number)
    return remember(context, star, pound) {
        val locale = LocaleUtils.getLocale(context)
        // Persian shows Persian digits; every other language shows Western ones.
        val format = if (locale.isO3Language == "fas") DecimalFormat.getInstance(locale) else DecimalFormat.getInstance(Locale.ENGLISH)
        val primary = DialpadCharMappings.getDefaultKeyToCharsMap()
        val secondary = DialpadCharMappings.getKeyToCharsMap(context)
        val codes = listOf(
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6,
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9,
        )
        codes.mapIndexed { i, code ->
            val digit = i + 1
            DialpadKeySpec(code, format.format(digit), primary[digit], secondary?.get(digit))
        } + listOf(
            DialpadKeySpec(KeyEvent.KEYCODE_STAR, star, null, null),
            DialpadKeySpec(KeyEvent.KEYCODE_0, format.format(0), "+", null),
            DialpadKeySpec(KeyEvent.KEYCODE_POUND, pound, null, null),
        )
    }
}

/** One key: it plays its tone while held, as the old keys did, and 0 and 1 have long presses. */
@Composable
private fun DialpadKey(
    key: DialpadKeySpec,
    onKey: (Int, Boolean) -> Unit,
    onKeyLongPress: (Int) -> Boolean,
    longPressKeys: Boolean,
) {
    val source = remember { MutableInteractionSource() }
    val longPressDescription = if (!longPressKeys) null else when (key.keyCode) {
        KeyEvent.KEYCODE_1 -> stringResource(R.string.description_voicemail_button)
        KeyEvent.KEYCODE_0 -> stringResource(R.string.description_image_button_plus)
        else -> null
    }
    val description = listOfNotNull(key.number, key.letters?.takeIf { key.keyCode != KeyEvent.KEYCODE_0 }).joinToString(",")
    Column(
        modifier = Modifier
            .size(KeySize)
            .pvotPressScale(source)
            .clip(CircleShape)
            .pointerInput(key.keyCode) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val press = PressInteraction.Press(down.position)
                    source.tryEmit(press)
                    onKey(key.keyCode, true)
                    var released = false
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                            released = true
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        if (longPressDescription != null && onKeyLongPress(key.keyCode)) {
                            waitForUpOrCancellation()
                            released = true
                        }
                    }
                    if (!released) waitForUpOrCancellation()
                    source.tryEmit(PressInteraction.Release(press))
                    onKey(key.keyCode, false)
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick { onKey(key.keyCode, true); onKey(key.keyCode, false); true }
                if (longPressDescription != null) onLongClick(longPressDescription) { onKeyLongPress(key.keyCode) }
            }
            .testTag(TestTags.dialpadKey(key.number)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = key.number, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        // The "+" under 0 is a long press, so it only shows where long presses work.
        val letters = key.letters?.takeUnless { key.keyCode == KeyEvent.KEYCODE_0 && !longPressKeys }
            ?: if (key.keyCode == KeyEvent.KEYCODE_1) " " else null
        if (letters != null) {
            Text(text = letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        key.secondaryLetters?.let {
            Text(text = it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DeleteButton(enabled: Boolean, callbacks: DialpadCallbacks) {
    val description = stringResource(R.string.description_delete_button)
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown()
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            if (waitForUpOrCancellation() != null) callbacks.onDelete()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        callbacks.onDeleteLongPress()
                        waitForUpOrCancellation()
                    }
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = description
                if (enabled) {
                    onClick { callbacks.onDelete(); true }
                    onLongClick { callbacks.onDeleteLongPress(); true }
                }
            }
            .testTag(TestTags.DIALPAD_DELETE),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_backspace),
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
        )
    }
}

@Composable
private fun CallButton(state: DialpadUiState, callbacks: DialpadCallbacks) {
    val source = remember { MutableInteractionSource() }
    val container by animateColorAsState(
        if (state.pseudoEmergency) MaterialTheme.colorScheme.error else CallGreen,
        tween(PvotDurations.Medium),
        label = "callButtonColor",
    )
    Box(Modifier.size(KeySize), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = state.callButtonVisible,
            enter = scaleIn(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) + fadeIn(tween(PvotDurations.Short)),
            exit = scaleOut(tween(PvotDurations.Short)) + fadeOut(tween(PvotDurations.Short)),
        ) {
            IconButton(
                onClick = callbacks::onCall,
                interactionSource = source,
                modifier = Modifier
                    .size(KeySize)
                    .pvotPressScale(source)
                    .background(container, CircleShape)
                    .testTag(TestTags.DIALPAD_CALL),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_phone),
                    contentDescription = stringResource(R.string.description_dial_button),
                    tint = androidx.compose.ui.graphics.Color.White,
                )
            }
        }
    }
}

/** The call button keeps the green people expect of it, in light and dark. */
private val CallGreen = androidx.compose.ui.graphics.Color(0xFF1E8E3E)

@Composable
private fun DialpadMenu(state: DialpadUiState, callbacks: DialpadCallbacks) {
    PvotBottomSheet(visible = true, onDismissRequest = { state.menuOpen = false }) {
        for (account in state.accounts) {
            PvotSheetItem(
                title = account.label,
                subtitle = stringResource(R.string.call_via),
                icon = if (account.selected) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null) }
                } else null,
                enabled = state.menuEnabled,
                onClick = { state.menuOpen = false; callbacks.onSelectAccount(account.handle) },
            )
        }
        PvotSheetItem(
            title = stringResource(R.string.add_2sec_pause),
            enabled = state.menuEnabled,
            onClick = { state.menuOpen = false; callbacks.onAddPause() },
        )
        PvotSheetItem(
            title = stringResource(R.string.add_wait),
            enabled = state.menuEnabled,
            onClick = { state.menuOpen = false; callbacks.onAddWait() },
        )
        if (state.callWithNoteSupported) {
            PvotSheetItem(
                title = stringResource(R.string.call_with_a_note),
                icon = { Icon(painterResource(R.drawable.ic_call_note), contentDescription = null) },
                enabled = state.menuEnabled,
                onClick = { state.menuOpen = false; callbacks.onCallWithNote() },
            )
        }
    }
}

/** Puts the dialpad into the dialpad fragment's view. */
object DialpadScreenHost {
    @JvmStatic
    fun attach(view: ComposeView, state: DialpadUiState, digits: EditText, callbacks: DialpadCallbacks) {
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        view.setContent {
            PhoneScreenTheme(fillBackground = false) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) { DialpadScreen(state, digits, callbacks) }
            }
        }
    }
}
