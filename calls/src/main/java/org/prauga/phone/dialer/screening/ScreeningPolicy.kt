/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.telecom.Connection
import android.telecom.TelecomManager

/** Decides how an incoming call is handled from what is known about it and the user's choices. */
object ScreeningPolicy {

    /**
     * Calls that must always ring come first: emergency numbers, callbacks after an emergency call,
     * contacts, numbers the user called recently, numbers marked never to screen and calls that
     * arrive while the user is in another call. Other calls take the strictest action the user
     * chose among the categories they fall into.
     */
    fun decide(
        facts: IncomingCallFacts,
        actionFor: (CallerCategory) -> CategoryAction,
    ): ScreeningDecision {
        exemption(facts)?.let {
            return ScreeningDecision(CategoryAction.RING, it)
        }
        return categoriesOf(facts)
            .map { ScreeningDecision(actionFor(it), ScreeningReason.Category(it)) }
            .filter { it.action != CategoryAction.RING }
            .maxByOrNull { it.action.ordinal }
            ?: ScreeningDecision(CategoryAction.RING, ScreeningReason.None)
    }

    /** The categories [facts] fall into, most specific first. */
    fun categoriesOf(facts: IncomingCallFacts): List<CallerCategory> = buildList {
        val hasNumber = !facts.number.isNullOrEmpty()
        if (!hasNumber || facts.presentation != TelecomManager.PRESENTATION_ALLOWED) {
            add(CallerCategory.PRIVATE_OR_HIDDEN)
        }
        if (facts.verificationStatus == Connection.VERIFICATION_STATUS_FAILED) {
            add(CallerCategory.POSSIBLY_FAKED)
        }
        if (hasNumber && !facts.hasCallLogHistory) {
            add(CallerCategory.FIRST_TIME)
        }
        // Contacts are exempt above, so a shown number that gets here belongs to a stranger; a hidden
        // one is already covered by the first category.
        if (hasNumber && facts.presentation == TelecomManager.PRESENTATION_ALLOWED && !facts.isContact) {
            add(CallerCategory.NOT_IN_CONTACTS)
        }
    }

    private fun exemption(facts: IncomingCallFacts): ScreeningReason? =
        when {
            facts.isEmergencyNumber -> ScreeningReason.Emergency
            facts.hasRecentEmergencyCall -> ScreeningReason.RecentEmergency
            facts.isContact -> ScreeningReason.Contact
            facts.hasRecentOutgoingCall -> ScreeningReason.RecentOutgoing
            facts.isMarkedDoNotScreen -> ScreeningReason.DoNotScreen
            facts.hasActiveCall -> ScreeningReason.AnotherActiveCall
            else -> null
        }
}
