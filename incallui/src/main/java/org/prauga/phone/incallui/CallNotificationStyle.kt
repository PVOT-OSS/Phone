/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui

import android.app.Notification
import android.app.PendingIntent
import android.app.Person
import android.content.Context
import android.graphics.drawable.Icon

/**
 * Android's call notification style for the in-call notification: the system draws the caller and
 * the answer, decline or hang-up buttons itself.
 */
object CallNotificationStyle {

    /** The caller as the style shows them, or null when there is no name, which the style needs. */
    @JvmStatic
    fun person(name: CharSequence?, uri: String?): Person? =
        if (name.isNullOrBlank()) null else Person.Builder().setName(name).setUri(uri).build()

    /**
     * A ringing call, answered with [answer] (as video for a video call) or declined with
     * [decline].
     */
    @JvmStatic
    fun incoming(
        person: Person,
        decline: PendingIntent,
        answer: PendingIntent,
        isVideo: Boolean,
    ): Notification.CallStyle =
        Notification.CallStyle.forIncomingCall(person, decline, answer).setIsVideo(isVideo)

    /** A dialing, active or held call, ended with [hangUp]. */
    @JvmStatic
    fun ongoing(person: Person, hangUp: PendingIntent, isVideo: Boolean): Notification.CallStyle =
        Notification.CallStyle.forOngoingCall(person, hangUp).setIsVideo(isVideo)

    /**
     * Marks the caller as verified by the network (STIR/SHAKEN) with a check next to their name.
     */
    @JvmStatic
    fun markVerified(context: Context, style: Notification.CallStyle): Notification.CallStyle =
        style.setVerificationIcon(
            Icon.createWithResource(context, R.drawable.quantum_ic_check_circle_outline_vd_theme_24)
        )
}
