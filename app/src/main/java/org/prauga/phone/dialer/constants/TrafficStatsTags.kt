/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.constants

/** Registry of tags for [android.net.TrafficStats.setThreadStatsTag] */
object TrafficStatsTags {
    const val CONTACT_PHOTO_DOWNLOAD_TAG = 0x00000001
    const val NEARBY_PLACES_TAG = 0x00000002
    const val REVERSE_LOOKUP_CONTACT_TAG = 0x00000003
    const val REVERSE_LOOKUP_IMAGE_TAG = 0x00000004
    const val DOWNLOAD_LOCATION_MAP_TAG = 0x00000005
    const val REVERSE_GEOCODE_TAG = 0x00000006
    const val VISUAL_VOICEMAIL_TAG = 0x00000007

    // 0xfffffe00 to 0xffffff00 reserved for proprietary extensions to the dialer app.

    // 0xffffff00 to 0xffffffff reserved by the system (see TrafficStats#getAndSetThreadStatsTag)
}
