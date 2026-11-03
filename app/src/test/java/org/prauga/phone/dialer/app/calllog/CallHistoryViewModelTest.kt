/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.robolectric.RobolectricTestRunner

/** Loading and changing a call history list. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CallHistoryViewModelTest {

    private class FakeRepository(var groups: List<CallLogGroup>) : CallHistoryRepository {
        var blockedNumbers = setOf<String>()
        val deleted = mutableListOf<Long>()
        val loadedTypes = mutableListOf<Int>()
        var failWith: Exception? = null

        /** When set, [blocked] waits on it, as the real lookup waits on the provider. */
        var blockedGate: CompletableDeferred<Unit>? = null
        override suspend fun load(callType: Int): List<CallLogGroup> {
            loadedTypes += callType
            failWith?.let { throw it }
            return groups
        }
        override suspend fun blocked(numbers: Collection<String>): Set<String> {
            blockedGate?.await()
            return blockedNumbers.intersect(numbers.toSet())
        }
        override suspend fun delete(groups: Collection<CallLogGroup>) {
            val ids = groups.flatMap { it.callIds }
            deleted += ids
            this.groups = this.groups.filter { group -> group.callIds.none { it in ids } }
        }
        override suspend fun block(number: String, countryIso: String?) { blockedNumbers = blockedNumbers + number }
        override suspend fun unblock(number: String) { blockedNumbers = blockedNumbers - number }
    }

    private val ada = callLogGroup(10, "+15550100")
    private val bea = callLogGroup(20, "+15550199", callTypes = listOf(1, 3))
    private val repository = FakeRepository(listOf(ada, bea))
    private var permitted = true

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(callType: Int = CallLogQueryHandler.CALL_TYPE_ALL) = CallHistoryViewModel(
        ApplicationProvider.getApplicationContext<Application>(), callType, repository, hasPermission = { permitted },
    ).apply { refresh() }

    private val CallHistoryViewModel.loaded get() = state.value as CallHistoryUiState.Loaded
    private val CallHistoryViewModel.pendingDeletes get() = loaded.pendingDeletes

    @Test
    fun loadsTheRequestedCallType() {
        val vm = viewModel(callType = android.provider.CallLog.Calls.MISSED_TYPE)
        assertThat(repository.loadedTypes).containsExactly(android.provider.CallLog.Calls.MISSED_TYPE)
        assertThat(vm.loaded.groups).containsExactly(ada, bea).inOrder()
    }

    @Test
    fun noCallsIsEmpty() {
        repository.groups = emptyList()
        assertThat(viewModel().state.value).isEqualTo(CallHistoryUiState.Empty)
    }

    @Test
    fun missingPermissionAsksForIt() {
        permitted = false
        assertThat(viewModel().state.value).isEqualTo(CallHistoryUiState.NeedsPermission)
        assertThat(repository.loadedTypes).isEmpty()
    }

    @Test
    fun revokedWhileLoadingAsksForIt() {
        repository.failWith = SecurityException("revoked")
        assertThat(viewModel().state.value).isEqualTo(CallHistoryUiState.NeedsPermission)
    }

    @Test
    fun blockedNumbersAreKnown() {
        repository.blockedNumbers = setOf("+15550199")
        assertThat(viewModel().loaded.blockedNumbers).containsExactly("+15550199")
    }

    @Test
    fun deletingHidesAtOnceAndDeletesOnCommit() {
        val vm = viewModel()
        vm.deleteLater(bea)
        assertThat(vm.pendingDeletes).containsExactly(20L, 21L)
        assertThat(repository.deleted).isEmpty()

        vm.commitDelete(bea)

        assertThat(repository.deleted).containsExactly(20L, 21L)
        // Still hidden until a reload no longer has the calls, or the row would come back meanwhile.
        assertThat(vm.pendingDeletes).containsExactly(20L, 21L)
        vm.refresh()
        assertThat(vm.pendingDeletes).isEmpty()
        assertThat(vm.loaded.groups).containsExactly(ada)
    }

    @Test
    fun aFilteredReloadKeepsCallsWaitingOnUndoHidden() {
        val vm = viewModel()
        vm.deleteLater(bea)
        repository.groups = listOf(ada)
        vm.refresh()
        assertThat(vm.pendingDeletes).containsExactly(20L, 21L)
    }

    @Test
    fun aDeletedRowStaysHiddenWhileAReloadIsInFlight() {
        val vm = viewModel()
        vm.deleteLater(bea)
        vm.commitDelete(bea)
        assertThat(vm.loaded.visible).containsExactly(ada)

        // The reload no longer has the deleted calls, but is still waiting on the blocked
        // numbers: the old rows must not show again meanwhile.
        val gate = CompletableDeferred<Unit>()
        repository.blockedGate = gate
        vm.refresh()
        assertThat(vm.loaded.visible).containsExactly(ada)

        gate.complete(Unit)
        assertThat(vm.loaded.visible).containsExactly(ada)
        assertThat(vm.pendingDeletes).isEmpty()
    }

    @Test
    fun deletingSelectedVoicemailsHappensAtOnce() {
        val vm = viewModel()
        vm.deleteNow(listOf(ada, bea))
        assertThat(repository.deleted).containsExactly(10L, 20L, 21L)
        // Nothing is left: the list is empty, not a list of hidden rows.
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(CallHistoryUiState.Empty)
    }

    @Test
    fun aReloadWithoutTheDeletedCallsShowsTheRest() {
        val vm = viewModel()
        vm.deleteNow(listOf(bea))
        vm.refresh()
        assertThat(vm.pendingDeletes).isEmpty()
        assertThat(vm.loaded.groups).containsExactly(ada)
    }

    @Test
    fun undoKeepsTheCalls() {
        val vm = viewModel()
        vm.deleteLater(ada)
        vm.undoDelete(ada)
        vm.commitDelete(ada)
        assertThat(repository.deleted).isEmpty()
        assertThat(vm.pendingDeletes).isEmpty()
    }

    @Test
    fun blockingAndUnblockingUpdateTheList() {
        val vm = viewModel()
        vm.block("+15550100", "US")
        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550100")
        vm.unblock("+15550100")
        assertThat(vm.loaded.blockedNumbers).isEmpty()
    }
}
