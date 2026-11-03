/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.content.ContentUris
import android.content.Context
import android.graphics.BitmapFactory
import android.provider.ContactsContract
import android.util.LruCache
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.core.net.toUri
import com.prauga.pvot.designsystem.components.list.PvotLetterTile
import com.prauga.pvot.designsystem.components.list.PvotLetterTileDefaults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.ui.R

/**
 * A contact's photo, or a letter tile while it loads or when there is none.
 *
 * @param name The contact's name, for the tile's letter
 * @param photoUri The contact's photo thumbnail, such as `Contacts.PHOTO_THUMBNAIL_URI`
 * @param colorKey What picks the tile's color; the lookup key keeps it stable across renames
 */
@Composable
fun ContactAvatar(
    name: String,
    photoUri: String?,
    modifier: Modifier = Modifier,
    colorKey: Any = name,
    size: Dp = PvotLetterTileDefaults.Size,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val photo by
        produceState(ContactPhotoCache.cached(photoUri, sizePx), photoUri, sizePx) {
            if (value == null && photoUri != null)
                value = ContactPhotoCache.load(context, photoUri, sizePx)
        }
    val bitmap = photo
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.size(size).clip(CircleShape),
        )
    } else if (name.none(Char::isLetter)) {
        // A number or an empty name has no initial; show a person instead.
        val colors =
            PvotLetterTileDefaults.colorsFor(colorKey, PvotLetterTileDefaults.themePalette())
        Box(
            modifier = modifier.size(size).background(colors.containerColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_user),
                contentDescription = null,
                tint = colors.contentColor,
                modifier = Modifier.size(size / 2),
            )
        }
    } else {
        PvotLetterTile(name = name, modifier = modifier, colorKey = colorKey, size = size)
    }
}

/** Contact thumbnails decoded at the size they are shown, kept while the app runs. */
internal object ContactPhotoCache {
    private val cache = LruCache<String, ImageBitmap>(200)

    fun cached(photoUri: String?, sizePx: Int): ImageBitmap? =
        photoUri?.let { cache.get(key(it, sizePx)) }

    suspend fun load(context: Context, photoUri: String, sizePx: Int): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver
                    .loadThumbnail(photoUri.toUri(), Size(sizePx, sizePx), null)
                    .asImageBitmap()
                    .also { cache.put(key(photoUri, sizePx), it) }
            } catch (e: Exception) {
                // A deleted photo or a missing permission falls back to the letter tile.
                LogUtil.w("ContactPhotoCache.load", "no photo: %s", e.javaClass.simpleName)
                null
            }
        }

    /** The thumbnail stored in a contact's photo row, if it has been loaded before. */
    fun cachedThumbnail(photoId: Long): ImageBitmap? =
        if (photoId == 0L) null else cache.get(thumbnailKey(photoId))

    /** The thumbnail stored in a contact's photo row, kept once read. */
    suspend fun loadThumbnail(context: Context, photoId: Long): ImageBitmap? =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver
                    .query(
                        ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, photoId),
                        arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
                        null,
                        null,
                        null,
                    )
                    ?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use null
                        val bytes = cursor.getBlob(0) ?: return@use null
                        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                    }
                    ?.also { cache.put(thumbnailKey(photoId), it) }
            } catch (e: Exception) {
                // A deleted contact or a missing permission falls back to the letter tile.
                LogUtil.w("ContactPhotoCache.loadThumbnail", "no photo: %s", e.javaClass.simpleName)
                null
            }
        }

    private fun key(photoUri: String, sizePx: Int) = "$sizePx:$photoUri"

    private fun thumbnailKey(photoId: Long) = "photo:$photoId"
}
