/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

/** Kinds of unknown incoming caller the user can choose to ring, silence or decline. */
enum class CallerCategory {
    /**
     * The caller hides their number: restricted, unknown or payphone presentation, or no number.
     */
    PRIVATE_OR_HIDDEN,

    /** The network reported that the caller's number failed verification and may be altered. */
    POSSIBLY_FAKED,

    /** The number has no entry in the call log. */
    FIRST_TIME,

    /** The number belongs to none of the user's contacts. */
    NOT_IN_CONTACTS,
}
