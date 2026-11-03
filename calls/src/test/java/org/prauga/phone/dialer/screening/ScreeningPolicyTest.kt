/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.screening

import android.telecom.Connection
import android.telecom.TelecomManager
import org.junit.Assert.assertEquals
import org.junit.Test

class ScreeningPolicyTest {

    private val knownCaller =
        IncomingCallFacts(
            number = "+919000000000",
            presentation = TelecomManager.PRESENTATION_ALLOWED,
            verificationStatus = Connection.VERIFICATION_STATUS_NOT_VERIFIED,
            isEmergencyNumber = false,
            hasRecentEmergencyCall = false,
            isContact = false,
            hasCallLogHistory = true,
            hasRecentOutgoingCall = false,
            isMarkedDoNotScreen = false,
            hasActiveCall = false,
        )

    private val declineAll: (CallerCategory) -> CategoryAction = { CategoryAction.DECLINE }
    private val ringAll: (CallerCategory) -> CategoryAction = { CategoryAction.RING }

    private fun decide(facts: IncomingCallFacts, actions: Map<CallerCategory, CategoryAction>) =
        ScreeningPolicy.decide(facts) { actions[it] ?: CategoryAction.RING }

    @Test
    fun ringsWhenEveryCategoryRings() {
        val hidden =
            knownCaller.copy(number = null, presentation = TelecomManager.PRESENTATION_RESTRICTED)
        assertEquals(
            ScreeningDecision(CategoryAction.RING, ScreeningReason.None),
            ScreeningPolicy.decide(hidden, ringAll),
        )
    }

    @Test
    fun ringsWhenNoCategoryApplies() {
        assertEquals(
            ScreeningDecision(CategoryAction.RING, ScreeningReason.None),
            ScreeningPolicy.decide(knownCaller, declineAll),
        )
    }

    @Test
    fun hiddenNumbersArePrivate() {
        for (presentation in
            listOf(
                TelecomManager.PRESENTATION_RESTRICTED,
                TelecomManager.PRESENTATION_UNKNOWN,
                TelecomManager.PRESENTATION_PAYPHONE,
            )) {
            val facts = knownCaller.copy(presentation = presentation)
            assertEquals(
                listOf(CallerCategory.PRIVATE_OR_HIDDEN),
                ScreeningPolicy.categoriesOf(facts),
            )
        }
        assertEquals(
            listOf(CallerCategory.PRIVATE_OR_HIDDEN),
            ScreeningPolicy.categoriesOf(knownCaller.copy(number = "")),
        )
    }

    @Test
    fun failedVerificationIsPossiblyFaked() {
        val facts = knownCaller.copy(verificationStatus = Connection.VERIFICATION_STATUS_FAILED)
        assertEquals(listOf(CallerCategory.POSSIBLY_FAKED), ScreeningPolicy.categoriesOf(facts))
        val passed = knownCaller.copy(verificationStatus = Connection.VERIFICATION_STATUS_PASSED)
        assertEquals(emptyList<CallerCategory>(), ScreeningPolicy.categoriesOf(passed))
    }

    @Test
    fun numbersMissingFromTheCallLogAreFirstTime() {
        val facts = knownCaller.copy(hasCallLogHistory = false)
        assertEquals(listOf(CallerCategory.FIRST_TIME), ScreeningPolicy.categoriesOf(facts))
    }

    @Test
    fun hiddenCallersAreNotFirstTime() {
        val facts = knownCaller.copy(number = null, hasCallLogHistory = false)
        assertEquals(listOf(CallerCategory.PRIVATE_OR_HIDDEN), ScreeningPolicy.categoriesOf(facts))
    }

    @Test
    fun appliesTheChosenAction() {
        val facts = knownCaller.copy(hasCallLogHistory = false)
        assertEquals(
            ScreeningDecision(
                CategoryAction.SILENCE,
                ScreeningReason.Category(CallerCategory.FIRST_TIME),
            ),
            decide(facts, mapOf(CallerCategory.FIRST_TIME to CategoryAction.SILENCE)),
        )
    }

    @Test
    fun strictestActionWinsAcrossCategories() {
        val facts =
            knownCaller.copy(
                verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
                hasCallLogHistory = false,
            )
        val actions =
            mapOf(
                CallerCategory.POSSIBLY_FAKED to CategoryAction.SILENCE,
                CallerCategory.FIRST_TIME to CategoryAction.DECLINE,
            )
        assertEquals(
            ScreeningDecision(
                CategoryAction.DECLINE,
                ScreeningReason.Category(CallerCategory.FIRST_TIME),
            ),
            decide(facts, actions),
        )
    }

    @Test
    fun moreSpecificCategoryExplainsATie() {
        val facts =
            knownCaller.copy(
                verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
                hasCallLogHistory = false,
            )
        assertEquals(
            ScreeningDecision(
                CategoryAction.DECLINE,
                ScreeningReason.Category(CallerCategory.POSSIBLY_FAKED),
            ),
            ScreeningPolicy.decide(facts, declineAll),
        )
    }

    @Test
    fun exemptionsAlwaysRing() {
        val stranger =
            knownCaller.copy(
                hasCallLogHistory = false,
                verificationStatus = Connection.VERIFICATION_STATUS_FAILED,
            )
        val cases =
            mapOf(
                stranger.copy(isEmergencyNumber = true) to ScreeningReason.Emergency,
                stranger.copy(hasRecentEmergencyCall = true) to ScreeningReason.RecentEmergency,
                stranger.copy(isContact = true) to ScreeningReason.Contact,
                stranger.copy(hasRecentOutgoingCall = true) to ScreeningReason.RecentOutgoing,
                stranger.copy(isMarkedDoNotScreen = true) to ScreeningReason.DoNotScreen,
                stranger.copy(hasActiveCall = true) to ScreeningReason.AnotherActiveCall,
            )
        for ((facts, reason) in cases) {
            assertEquals(
                ScreeningDecision(CategoryAction.RING, reason),
                ScreeningPolicy.decide(facts, declineAll),
            )
        }
    }

    @Test
    fun emergencyOutranksOtherExemptions() {
        val facts = knownCaller.copy(isEmergencyNumber = true, isContact = true)
        assertEquals(ScreeningReason.Emergency, ScreeningPolicy.decide(facts, declineAll).reason)
    }
}
