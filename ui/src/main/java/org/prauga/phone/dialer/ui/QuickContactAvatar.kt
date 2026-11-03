/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.graphics.Rect
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.QuickContact
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.prauga.pvot.designsystem.components.list.PvotAvatar
import org.prauga.phone.ui.R
import org.prauga.phone.dialer.util.UriUtils

/**
 * A contact's photo, or their letter tile, that opens Quick Contact when tapped, as the
 * `QuickContactBadge` it replaces did. Without a [contactUri] it is only a picture.
 *
 * @param name The name or number, for the tile's letter and for TalkBack
 * @param contactUri The contact's lookup URI, or null for a number that is not a contact
 * @param photoId The contact's thumbnail row, or 0
 * @param photoUri The contact's (or a directory's) photo, preferred over [photoId]
 * @param colorKey What picks the tile's color; by default the contact's lookup key, else [name]
 */
@Composable
fun QuickContactAvatar(
    name: String,
    contactUri: Uri?,
    photoId: Long,
    photoUri: Uri?,
    size: Dp,
    modifier: Modifier = Modifier,
    colorKey: Any? = null,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    // Start from the cache, so a hero that scrolls back in shows its photo, not a letter tile first.
    val cached = when {
        photoUri != null -> ContactPhotoCache.cached(photoUri.toString(), sizePx)
        else -> ContactPhotoCache.cachedThumbnail(photoId)
    }
    val photo by produceState(cached, photoUri, photoId, sizePx) {
        if (value == null) {
            value = when {
                photoUri != null -> ContactPhotoCache.load(context, photoUri.toString(), sizePx)
                photoId != 0L -> ContactPhotoCache.loadThumbnail(context, photoId)
                else -> null
            }
        }
    }
    val painter = photo?.let { remember(it) { BitmapPainter(it) } }
    val tileKey = colorKey ?: remember(contactUri, name) { contactUri?.let(UriUtils::getLookupKeyFromUri) ?: name }
    val tapModifier = modifier
        .clip(CircleShape)
        .then(
            if (contactUri != null) {
                Modifier.clickable {
                    QuickContact.showQuickContact(context, null as Rect?, contactUri, null, Phone.CONTENT_ITEM_TYPE)
                }
            } else {
                Modifier
            },
        )
    if (painter == null && name.none(Char::isLetter)) {
        // A number has no initial: show the same person tile as the lists do.
        ContactAvatar(name = name, photoUri = null, modifier = tapModifier, colorKey = tileKey, size = size)
        return
    }
    PvotAvatar(
        name = name,
        modifier = tapModifier,
        painter = painter,
        colorKey = tileKey,
        size = size,
        // Only a picture that opens Quick Contact is announced; otherwise it is decoration.
        contentDescription = if (contactUri != null) stringResource(R.string.description_quick_contact_for, name) else null,
    )
}
