/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

/** What happens to an incoming call that falls into a [CallerCategory]. */
enum class CategoryAction {
    /** The call rings as usual. */
    RING,

    /** The call arrives without ringing or vibrating; it can still be answered and is notified. */
    SILENCE,

    /** The call is declined without a notification; it stays in the call log. */
    DECLINE,
}
