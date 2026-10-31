/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

/**
 * What is known about one incoming call when it is screened.
 *
 * @param number The caller's number, or null when it was not delivered
 * @param presentation The handle presentation, one of the TelecomManager PRESENTATION_ constants
 * @param verificationStatus The network's caller number verification result, one of the
 * Connection VERIFICATION_STATUS_ constants
 * @param isEmergencyNumber Whether the number is an emergency number
 * @param hasRecentEmergencyCall Whether the user called emergency services recently
 * @param isContact Whether the number belongs to a contact
 * @param hasCallLogHistory Whether the call log has any call with this number
 * @param hasRecentOutgoingCall Whether the user called this number recently
 * @param isMarkedDoNotScreen Whether the user asked never to screen this number
 */
data class IncomingCallFacts(
    val number: String?,
    val presentation: Int,
    val verificationStatus: Int,
    val isEmergencyNumber: Boolean,
    val hasRecentEmergencyCall: Boolean,
    val isContact: Boolean,
    val hasCallLogHistory: Boolean,
    val hasRecentOutgoingCall: Boolean,
    val isMarkedDoNotScreen: Boolean,
)
