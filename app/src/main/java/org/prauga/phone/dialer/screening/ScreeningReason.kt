/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

/** Why the screening policy reached its decision for a call. */
sealed interface ScreeningReason {
    /** The call comes from an emergency number. */
    data object Emergency : ScreeningReason

    /** The user called emergency services recently, so a callback must get through. */
    data object RecentEmergency : ScreeningReason

    /** The number belongs to a contact. */
    data object Contact : ScreeningReason

    /** The user called this number recently. */
    data object RecentOutgoing : ScreeningReason

    /** The user asked never to screen this number. */
    data object DoNotScreen : ScreeningReason

    /** The user is in another call, so this one arrives as a waiting call. */
    data object AnotherActiveCall : ScreeningReason

    /** The caller falls into [category]. */
    data class Category(val category: CallerCategory) : ScreeningReason

    /** Nothing applied. */
    data object None : ScreeningReason
}
