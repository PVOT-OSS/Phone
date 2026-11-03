/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.ContactsContract.Contacts
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.prauga.phone.dialer.speeddial.loader.UiItemLoaderComponent

/** A favorite on the home screen widget: a tile that opens [intent] to call them. */
data class FavoriteTile(val name: String, val photo: Bitmap, val intent: Intent)

/** Builds the widget's tiles from the starred contacts. */
object FavoritesWidgetData {

    /** How many favorites the widget shows at most. */
    const val MAX_TILES = 8

    /** The size each photo is drawn at. */
    private const val PHOTO_PX = 144

    /** The starred contacts, in their pinned order, as tiles. */
    suspend fun load(context: Context): List<FavoriteTile> =
        withContext(Dispatchers.IO) {
            val items =
                runCatching {
                        UiItemLoaderComponent.get(context)
                            .speedDialUiItemMutator()
                            .loadSpeedDialUiItems()
                            .await()
                    }
                    .getOrElse {
                        LogUtil.e("FavoritesWidgetData.load", "could not load favorites", it)
                        emptyList()
                    }
            tiles(context, items)
        }

    /** The tiles for [items]: the starred ones in order, each with a photo or a letter tile. */
    fun tiles(context: Context, items: List<SpeedDialUiItem>): List<FavoriteTile> =
        items
            .filter { it.isStarred }
            .sortedBy { it.pinnedPosition().orElse(Int.MAX_VALUE) }
            .take(MAX_TILES)
            .map { item ->
                val lookupUri = Contacts.getLookupUri(item.contactId(), item.lookupKey())
                FavoriteTile(
                    name = item.name(),
                    photo = photo(context, item.photoUri(), item.name(), item.lookupKey()),
                    intent =
                        Intent(CALL_CONTACT_ACTION)
                            .setClassName(context, CALL_CONTACT_ACTIVITY)
                            .setData(lookupUri),
                )
            }

    private fun photo(
        context: Context,
        photoUri: String?,
        name: String,
        lookupKey: String,
    ): Bitmap {
        val fromUri =
            photoUri?.let { uri ->
                runCatching {
                        context.contentResolver
                            .openInputStream(uri.toUri())
                            ?.use(BitmapFactory::decodeStream)
                    }
                    .getOrNull()
            }
        if (fromUri != null) return fromUri
        return LetterTileDrawable(context.resources)
            .setCanonicalDialerLetterTileDetails(
                name,
                lookupKey,
                LetterTileDrawable.SHAPE_CIRCLE,
                LetterTileDrawable.TYPE_DEFAULT,
            )
            .getBitmap(PHOTO_PX, PHOTO_PX)
    }

    private const val CALL_CONTACT_ACTION = "org.prauga.phone.dialer.shortcuts.CALL_CONTACT"
    private const val CALL_CONTACT_ACTIVITY =
        "org.prauga.phone.dialer.shortcuts.CallContactActivity"
}

/** The favorites on the home screen: a row of photos that call with a tap. */
class FavoritesWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tiles = FavoritesWidgetData.load(context)
        provideContent { GlanceTheme { Content(tiles) } }
    }

    @Composable
    private fun Content(tiles: List<FavoriteTile>) {
        Column(
            modifier =
                GlanceModifier.fillMaxSize()
                    .background(GlanceTheme.colors.widgetBackground)
                    .cornerRadius(24.dp)
                    .padding(12.dp)
        ) {
            Text(
                text = LocalContextCompat.string(R.string.quick_dial_header),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 8.dp),
            )
            if (tiles.isEmpty()) {
                Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = LocalContextCompat.string(R.string.favorites_widget_empty),
                        style =
                            TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            ),
                    )
                }
            } else {
                for (line in tiles.chunked(TILES_PER_LINE)) {
                    Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        for (tile in line) Tile(tile)
                    }
                }
            }
        }
    }

    @Composable
    private fun Tile(tile: FavoriteTile) {
        Column(
            modifier = GlanceModifier.width(TILE_WIDTH).clickable(actionStartActivity(tile.intent)),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                provider = ImageProvider(tile.photo),
                contentDescription = tile.name,
                modifier = GlanceModifier.size(48.dp).cornerRadius(24.dp),
            )
            Text(
                text = tile.name.substringBefore(' '),
                style =
                    TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                maxLines = 1,
                modifier = GlanceModifier.padding(top = 4.dp),
            )
        }
    }

    companion object {
        private const val TILES_PER_LINE = 4
        private val TILE_WIDTH = 72.dp

        /** Redraws every placed widget; call after the favorites change. */
        suspend fun update(context: Context) {
            runCatching { FavoritesWidget().updateAll(context) }
                .onFailure {
                    LogUtil.e("FavoritesWidget.update", "could not update the widget", it)
                }
        }
    }
}

/** Puts the widget on the home screen. */
class FavoritesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = FavoritesWidget()
}

/** Reads a string without a Compose resource API, which Glance does not have. */
private object LocalContextCompat {
    @Composable fun string(id: Int): String = androidx.glance.LocalContext.current.getString(id)
}
