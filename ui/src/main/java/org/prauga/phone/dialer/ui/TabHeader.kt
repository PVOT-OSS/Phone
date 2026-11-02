/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.prauga.pvot.designsystem.theme.PvotSpacing
import com.prauga.pvot.designsystem.navigation.LocalPvotNavBarSizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.ui.R

/** Sizes shared by the main tabs' headers, so every tab's title and avatar line up. */
object TabHeaderDefaults {
    /** Height of the header row. */
    val Height: Dp = 64.dp

    /** The avatar's touch target. */
    val AvatarTouchSize: Dp = 48.dp

    /** The avatar itself. */
    val AvatarSize: Dp = 36.dp
}

/**
 * A main tab's title, large, with the user's avatar at its end that opens the app menu. The title
 * starts at the screen padding, like the large titles of the other screens' top bars.
 */
@Composable
fun TabHeader(title: String, onOpenMenu: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(TabHeaderDefaults.Height)
            .padding(start = PvotSpacing.L, end = PvotSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        ProfileButton(onClick = onOpenMenu)
    }
}

/** The user's photo or initial from their profile card, or a person icon; tapping opens the app menu. */
@Composable
fun ProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val profile by produceState<Profile?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) { loadProfile(context) }
    }
    Box(
        modifier = modifier
            .size(TabHeaderDefaults.AvatarTouchSize)
            .clip(CircleShape)
            .clickable(
                onClickLabel = stringResource(R.string.action_menu_overflow_description),
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(TestTags.APP_MENU),
        contentAlignment = Alignment.Center,
    ) {
        val name = profile?.name
        if (name != null) {
            ContactAvatar(name = name, photoUri = profile?.photoUri, size = TabHeaderDefaults.AvatarSize)
        } else {
            Box(
                Modifier
                    .size(TabHeaderDefaults.AvatarSize)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_user),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Bottom padding for a main tab's list: the nav bar floats over the tabs, so the end of the list
 * scrolls up clear of it, the space round it and the navigation bar.
 */
@Composable
fun dockClearance(): Dp =
    LocalPvotNavBarSizes.current.barHeight + PvotSpacing.M * 2 +
        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

/** The user's own name and photo, from the "Me" card in contacts. */
@Immutable
private data class Profile(val name: String, val photoUri: String?)

private fun loadProfile(context: Context): Profile? {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
        return null
    }
    val projection = arrayOf(ContactsContract.Profile.DISPLAY_NAME_PRIMARY, ContactsContract.Profile.PHOTO_THUMBNAIL_URI)
    return runCatching {
        context.contentResolver.query(ContactsContract.Profile.CONTENT_URI, projection, null, null, null)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val name = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: return@use null
            Profile(name, cursor.getString(1))
        }
    }.getOrNull()
}
