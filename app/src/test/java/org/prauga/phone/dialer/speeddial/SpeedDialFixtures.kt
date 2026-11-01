/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import com.google.common.collect.ImmutableList
import java.util.Optional
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem

internal fun channel(number: String, label: String = "Mobile", video: Boolean = false): Channel =
    Channel.builder()
        .setNumber(number)
        .setPhoneType(2)
        .setLabel(label)
        .setTechnology(if (video) Channel.IMS_VIDEO else Channel.VOICE)
        .build()

internal fun speedDialItem(
    id: Long,
    name: String,
    starred: Boolean = true,
    pinned: Int? = null,
    channels: List<Channel> = listOf(channel("+1555020$id")),
    defaultChannel: Channel? = channels.singleOrNull(),
): SpeedDialUiItem = SpeedDialUiItem.builder()
    .setSpeedDialEntryId(if (starred) id else null)
    .setPinnedPosition(Optional.ofNullable(pinned))
    .setName(name)
    .setContactId(id)
    .setLookupKey("key$id")
    .setIsStarred(starred)
    .setPhotoId(0)
    .setPhotoUri("")
    .setChannels(ImmutableList.copyOf(channels))
    .setDefaultChannel(defaultChannel)
    .build()
