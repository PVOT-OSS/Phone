/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.net.Uri
import android.util.Size
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.common.LogUtil

/**
 * A picture from [uri], such as one sent with a call, cropped to a [size] square with rounded
 * corners. Loads off the main thread and shows an empty tile until it is ready or if it can't be read.
 */
@Composable
fun UriThumbnail(uri: Uri, contentDescription: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val picture by produceState<ImageBitmap?>(null, uri, sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(sizePx, sizePx), null).asImageBitmap() }
                .onFailure { LogUtil.w("UriThumbnail", "no picture: %s", it.javaClass.simpleName) }
                .getOrNull()
        }
    }
    val shape = RoundedCornerShape(PvotSpacing.S)
    val tile = modifier.size(size).clip(shape)
    val image = picture
    if (image == null) {
        Box(tile.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    } else {
        Image(image, contentDescription, tile, contentScale = ContentScale.Crop)
    }
}
