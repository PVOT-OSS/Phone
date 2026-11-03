/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotSectionHeader
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.effects.pvotPressScale
import com.prauga.pvot.designsystem.motion.PvotDurations
import com.prauga.pvot.designsystem.motion.PvotMotion
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.TabHeader
import org.prauga.phone.dialer.ui.TestTags

/** What the favorites tab does when the user acts. */
interface SpeedDialActions {
    /** Calls [channel]; [fromChooser] when the user picked it in the number chooser. */
    fun call(channel: Channel, fromChooser: Boolean = false)
    fun message(number: String)
    fun openContact(item: SpeedDialUiItem)
    fun addFavorite()
    fun star(item: SpeedDialUiItem)
    fun remove(item: SpeedDialUiItem)
    fun move(from: Int, to: Int)
    fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel)
    fun turnOnPermission()

    /** Opens the app menu, from the avatar beside the quick dial title. */
    fun openAppMenu()
}

private val FavoriteAvatarSize = 72.dp
private val SuggestionAvatarSize = 56.dp
/** As wide as a favorite's avatar, so the tiles' edges line up with the margins. */
private val TileWidth = FavoriteAvatarSize

/** Side margin of the quick dial rows, wider than the screen padding so the tiles sit inset. */
private val QuickDialMargin = PvotSpacing.L + PvotSpacing.S

/**
 * The dialpad tab's quick dial, at the top above the keypad: starred contacts in a row the user
 * can reorder, ending with a tile to add one, then frequent contacts in a row of their own.
 */
@Composable
fun SpeedDialScreen(
    state: SpeedDialUiState,
    actions: SpeedDialActions,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
) {
    Column(modifier.fillMaxSize()) {
        TabHeader(title = stringResource(R.string.quick_dial_header), onOpenMenu = actions::openAppMenu)
        when (state) {
            SpeedDialUiState.Loading -> Unit
            SpeedDialUiState.NeedsPermission -> PvotEmptyContent(
                message = stringResource(R.string.speed_dial_contacts_permission_description),
                modifier = Modifier.fillMaxWidth().padding(PvotSpacing.L),
                icon = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
                actionLabel = stringResource(R.string.speed_dial_turn_on_contacts_permission),
                onAction = actions::turnOnPermission,
            )
            SpeedDialUiState.Empty -> QuickDial(SpeedDialUiState.Loaded(emptyList(), emptyList()), actions, listState)
            is SpeedDialUiState.Loaded -> QuickDial(state, actions, listState)
        }
    }
}

@Composable
private fun QuickDial(state: SpeedDialUiState.Loaded, actions: SpeedDialActions, listState: LazyListState) {
    var menuFor by remember { mutableStateOf<SpeedDialUiItem?>(null) }
    var suggestionMenuFor by remember { mutableStateOf<SpeedDialUiItem?>(null) }
    var chooserFor by remember { mutableStateOf<SpeedDialUiItem?>(null) }
    val haptics = LocalHapticFeedback.current
    val favoritesByKey by rememberUpdatedState(state.favorites.associateBy(::favoriteKey))
    val currentActions by rememberUpdatedState(actions)
    val dragState = remember(listState) {
        FavoriteDragState(
            items = FavoriteDragState.rowItems(listState),
            onMove = { from, to -> currentActions.move(from, to) },
            onRemove = { key -> favoritesByKey[key]?.let(currentActions::remove) },
        )
    }
    dragState.favoriteKeys = state.favorites.map(::favoriteKey)

    Box(Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth()) {
            LazyRow(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(TestTags.SPEED_DIAL_GRID)
                    .onGloballyPositioned { dragState.rowBounds = it.boundsInRoot() },
                contentPadding = PaddingValues(horizontal = QuickDialMargin),
                horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
            ) {
                items(state.favorites, key = ::favoriteKey) { item ->
                    val key = favoriteKey(item)
                    val dragging = dragState.draggingKey == key
                    // The gesture handler keeps its first callbacks, so they read the latest item here.
                    val current by rememberUpdatedState(item)
                    FavoriteTile(
                        item = item,
                        lifted = dragging,
                        overRemove = dragging && dragState.isOverRemove,
                        modifier = Modifier
                            // The same modifiers either way, so lifting does not restart the gesture.
                            .zIndex(if (dragging) 1f else 0f)
                            .animateItem(placementSpec = if (dragging) null else spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = IntOffset.VisibilityThreshold))
                            .graphicsLayer {
                                if (dragging) {
                                    translationX = dragState.offset.x
                                    translationY = dragState.offset.y
                                }
                            }
                            .testTag(TestTags.speedDialFavorite(item.contactId())),
                        gestures = { source ->
                            Modifier.favoriteGestures(
                                key = key,
                                state = dragState,
                                interactionSource = source,
                                onTap = { onFavoriteTap(current, currentActions) { chooserFor = current } },
                                onLongPress = { menuFor = current },
                                onLift = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                            )
                        },
                        onTapForAccessibility = { onFavoriteTap(item, actions) { chooserFor = item } },
                        onLongPressForAccessibility = { menuFor = item },
                    )
                }
                item(key = "add") { AddTile(onClick = actions::addFavorite) }
            }
            if (state.suggestions.isNotEmpty()) {
                PvotSectionHeader(
                    title = stringResource(R.string.suggestions_header),
                    modifier = Modifier.padding(start = QuickDialMargin, end = QuickDialMargin, top = PvotSpacing.M),
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = QuickDialMargin),
                    horizontalArrangement = Arrangement.spacedBy(PvotSpacing.M),
                ) {
                    items(state.suggestions, key = { "suggestion_${it.contactId()}" }) { item ->
                        SuggestionTile(
                            item = item,
                            onClick = { item.defaultChannel()?.let { actions.call(it) } },
                            onMore = { suggestionMenuFor = item },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = dragState.isMoving,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = PvotSpacing.S),
            enter = slideInVertically(tween(PvotDurations.Medium, easing = PvotMotion.Settle)) { -it } +
                fadeIn(tween(PvotDurations.Short)),
            exit = slideOutVertically(tween(PvotDurations.Short)) { -it } + fadeOut(tween(PvotDurations.Short)),
        ) {
            RemoveTarget(
                active = dragState.isOverRemove,
                modifier = Modifier.onGloballyPositioned { dragState.removeBounds = it.boundsInRoot() },
            )
        }
    }

    menuFor?.let { item -> FavoriteMenu(item, actions, onDismiss = { menuFor = null }) }
    suggestionMenuFor?.let { item -> SuggestionMenu(item, actions, onDismiss = { suggestionMenuFor = null }) }
    chooserFor?.let { item -> NumberChooser(item, actions, onDismiss = { chooserFor = null }) }
}

/** The tile at the end of the favorites that adds one. */
@Composable
private fun AddTile(onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val label = stringResource(R.string.speed_dial_add_button_text)
    Column(
        modifier = Modifier
            .width(TileWidth)
            .pvotPressScale(source)
            .clickable(interactionSource = source, indication = null, role = Role.Button, onClick = onClick)
            .padding(vertical = PvotSpacing.S)
            .testTag(TestTags.SPEED_DIAL_ADD),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(FavoriteAvatarSize).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_plus), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = PvotSpacing.S),
        )
    }
}

internal fun favoriteKey(item: SpeedDialUiItem): Any = "favorite_${item.contactId()}"

/** Calls the favorite's chosen number, its only number, or asks which number to call. */
private fun onFavoriteTap(item: SpeedDialUiItem, actions: SpeedDialActions, choose: () -> Unit) {
    val channel = item.defaultChannel() ?: item.channels().singleOrNull()
    when {
        channel != null -> actions.call(channel)
        item.channels().isEmpty() -> actions.openContact(item)
        else -> choose()
    }
}

@Composable
private fun FavoriteTile(
    item: SpeedDialUiItem,
    lifted: Boolean,
    overRemove: Boolean,
    modifier: Modifier,
    gestures: (MutableInteractionSource) -> Modifier,
    onTapForAccessibility: () -> Unit,
    onLongPressForAccessibility: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val scale by animateFloatAsState(
        targetValue = when {
            overRemove -> 0.5f
            lifted -> 1.1f
            else -> 1f
        },
        animationSpec = tween(PvotDurations.Short, easing = PvotMotion.Settle),
        label = "favoriteScale",
    )
    val channel = item.defaultChannel() ?: item.getDefaultVoiceChannel()
    val callLabel = stringResource(R.string.disambig_option_voice_call)
    val moreLabel = stringResource(R.string.speed_dial_more_options)
    Column(
        modifier = modifier
            .width(TileWidth)
            .pvotPressScale(source)
            .then(gestures(source))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                onClick(callLabel) { onTapForAccessibility(); true }
                onLongClick(moreLabel) { onLongPressForAccessibility(); true }
            }
            .padding(vertical = PvotSpacing.S),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (overRemove) 0.5f else 1f
            },
        ) {
            ContactAvatar(
                name = item.name(),
                photoUri = item.photoUri(),
                colorKey = item.lookupKey() ?: item.contactId(),
                size = FavoriteAvatarSize,
            )
            if (channel?.isVideoTechnology == true) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_video),
                        contentDescription = stringResource(R.string.disambig_option_video_call),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        // The name steps aside while the favorite is lifted.
        Text(
            text = item.name(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.alpha(if (lifted) 0f else 1f).padding(top = PvotSpacing.S),
        )
    }
}

/** A frequent contact: tap calls them, long press or the more action opens their options. */
@Composable
private fun SuggestionTile(item: SpeedDialUiItem, onClick: () -> Unit, onMore: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val callLabel = stringResource(R.string.disambig_option_voice_call)
    val moreLabel = stringResource(R.string.speed_dial_more_options)
    Column(
        modifier = Modifier
            .width(TileWidth)
            .pvotPressScale(source)
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = Role.Button,
                onClickLabel = callLabel,
                onLongClickLabel = moreLabel,
                onLongClick = onMore,
                onClick = onClick,
            )
            .padding(vertical = PvotSpacing.S)
            .testTag(TestTags.speedDialSuggestion(item.contactId())),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ContactAvatar(
            name = item.name(),
            photoUri = item.photoUri(),
            colorKey = item.lookupKey() ?: item.contactId(),
            size = SuggestionAvatarSize,
        )
        Text(
            text = item.name(),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = PvotSpacing.S),
        )
    }
}

/** "Mobile (650) 555-1212", or the number alone when it has no type. */
@Composable
private fun channelSummary(channel: Channel): String {
    val context = LocalContext.current
    val number = remember(channel.number()) {
        PhoneNumberHelper.formatNumber(context, channel.number(), GeoUtil.getCurrentCountryIso(context))
            ?: channel.number()
    }
    return if (channel.label().isNullOrEmpty()) {
        number
    } else {
        stringResource(R.string.call_subject_type_and_number, channel.label(), number)
    }
}

@Composable
private fun RemoveTarget(active: Boolean, modifier: Modifier = Modifier) {
    val container by animateColorAsState(
        if (active) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        tween(PvotDurations.Short),
        label = "removeTargetColor",
    )
    val content = if (active) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = modifier
            .testTag(TestTags.SPEED_DIAL_REMOVE_TARGET)
            .background(container, PvotShapes.Pill)
            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M),
        horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(R.drawable.ic_trash), contentDescription = null, tint = content)
        Text(
            text = stringResource(R.string.speed_dial_remove_favorites),
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

@Composable
private fun FavoriteMenu(item: SpeedDialUiItem, actions: SpeedDialActions, onDismiss: () -> Unit) {
    val voice = item.getDefaultVoiceChannel()
    val video = item.getDefaultVideoChannel()
    PvotBottomSheet(visible = true, onDismissRequest = onDismiss, title = item.name()) {
        if (voice != null) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_voice_call),
                subtitle = channelSummary(voice),
                icon = { Icon(painterResource(R.drawable.ic_phone), contentDescription = null) },
                onClick = { onDismiss(); actions.call(voice) },
            )
        }
        if (video != null) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_video_call),
                icon = { Icon(painterResource(R.drawable.ic_video), contentDescription = null) },
                onClick = { onDismiss(); actions.call(video) },
            )
        }
        if (voice != null) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_message),
                icon = { Icon(painterResource(R.drawable.ic_message), contentDescription = null) },
                onClick = { onDismiss(); actions.message(voice.number()) },
            )
        }
        PvotSheetItem(
            title = stringResource(R.string.contact_menu_contact_info),
            icon = { Icon(painterResource(R.drawable.ic_user), contentDescription = null) },
            onClick = { onDismiss(); actions.openContact(item) },
        )
        PvotSheetItem(
            title = stringResource(R.string.speed_dial_remove_favorites),
            modifier = Modifier.testTag(TestTags.SPEED_DIAL_MENU_REMOVE),
            icon = { Icon(painterResource(R.drawable.ic_trash), contentDescription = null) },
            destructive = true,
            onClick = { onDismiss(); actions.remove(item) },
        )
    }
}

@Composable
private fun SuggestionMenu(item: SpeedDialUiItem, actions: SpeedDialActions, onDismiss: () -> Unit) {
    val voice = item.getDefaultVoiceChannel()
    val video = item.getDefaultVideoChannel()
    val number = item.defaultChannel()?.number().orEmpty()
    PvotBottomSheet(visible = true, onDismissRequest = onDismiss, title = item.name()) {
        if (voice != null) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_voice_call),
                subtitle = channelSummary(voice),
                icon = { Icon(painterResource(R.drawable.ic_phone), contentDescription = null) },
                onClick = { onDismiss(); actions.call(voice) },
            )
        }
        if (video != null) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_video_call),
                icon = { Icon(painterResource(R.drawable.ic_video), contentDescription = null) },
                onClick = { onDismiss(); actions.call(video) },
            )
        }
        if (number.isNotEmpty()) {
            PvotSheetItem(
                title = stringResource(R.string.contact_menu_message),
                icon = { Icon(painterResource(R.drawable.ic_message), contentDescription = null) },
                onClick = { onDismiss(); actions.message(number) },
            )
        }
        PvotSheetItem(
            title = stringResource(R.string.suggested_contact_bottom_sheet_add_favorite_option),
            modifier = Modifier.testTag(TestTags.SPEED_DIAL_MENU_STAR),
            icon = { Icon(painterResource(R.drawable.ic_star), contentDescription = null) },
            onClick = { onDismiss(); actions.star(item) },
        )
        PvotSheetItem(
            title = stringResource(R.string.contact_menu_contact_info),
            icon = { Icon(painterResource(R.drawable.ic_user), contentDescription = null) },
            onClick = { onDismiss(); actions.openContact(item) },
        )
    }
}

/** Asks which of a favorite's numbers to call, and can remember the answer. */
@Composable
private fun NumberChooser(item: SpeedDialUiItem, actions: SpeedDialActions, onDismiss: () -> Unit) {
    var remember by rememberSaveable { mutableStateOf(false) }
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = stringResource(R.string.speed_dial_disambig_dialog_title),
        content = {
            for (channel in item.channels()) {
                PvotListRow(
                    title = channel.label().ifEmpty { channel.number() },
                    supportingText = channel.number(),
                    modifier = Modifier.testTag(TestTags.speedDialChannel(channel.number(), channel.isVideoTechnology)),
                    leading = {
                        Icon(
                            painter = painterResource(if (channel.isVideoTechnology) R.drawable.ic_video else R.drawable.ic_phone),
                            contentDescription = stringResource(
                                if (channel.isVideoTechnology) R.string.disambig_option_video_call
                                else R.string.disambig_option_voice_call,
                            ),
                        )
                    },
                    onClick = {
                        if (remember) actions.setDefaultChannel(item, channel)
                        onDismiss()
                        actions.call(channel, fromChooser = true)
                    },
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = remember, role = Role.Checkbox, onValueChange = { remember = it })
                    .padding(vertical = PvotSpacing.S),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = remember, onCheckedChange = null)
                Text(
                    text = stringResource(R.string.speed_dial_remember_this_choice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = PvotSpacing.S),
                )
            }
        },
        actions = {
            PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = onDismiss)
        },
    )
}
