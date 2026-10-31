/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

/** The outcome of screening one incoming call. */
data class ScreeningDecision(val action: CategoryAction, val reason: ScreeningReason)
