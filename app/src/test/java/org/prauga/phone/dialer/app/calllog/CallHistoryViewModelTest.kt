/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.prauga.phone.dialer.R
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
        val blockedLookups = mutableListOf<Set<String>>()
        var failWith: Exception? = null
        var deleteFailsWith: Exception? = null
        var blockFailsWith: Exception? = null

        /** When set, [blocked] waits on it, as the real lookup waits on the provider. */
        var blockedGate: CompletableDeferred<Unit>? = null

        override suspend fun load(callType: Int): List<CallLogGroup> {
            loadedTypes += callType
            failWith?.let { throw it }
            return groups
        }

        override suspend fun blocked(numbers: Collection<String>): Set<String> {
            blockedLookups += numbers.toSet()
            blockedGate?.await()
            return blockedNumbers.intersect(numbers.toSet())
        }

        override suspend fun delete(groups: Collection<CallLogGroup>) {
            deleteFailsWith?.let { throw it }
            val ids = groups.flatMap { it.callIds }
            deleted += ids
            this.groups = this.groups.filter { group -> group.callIds.none { it in ids } }
        }

        override suspend fun block(number: String, countryIso: String?) {
            blockFailsWith?.let { throw it }
            blockedNumbers = blockedNumbers + number
        }

        override suspend fun unblock(number: String) {
            blockedNumbers = blockedNumbers - number
        }
    }

    private val ada = callLogGroup(10, "+15550100")
    private val bea = callLogGroup(20, "+15550199", callTypes = listOf(1, 3))
    private val repository = FakeRepository(listOf(ada, bea))
    private var permitted = true
    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(callType: Int = CallLogQueryHandler.CALL_TYPE_ALL) =
        CallHistoryViewModel(
                ApplicationProvider.getApplicationContext<Application>(),
                callType,
                repository,
                hasPermission = { permitted },
            )
            .apply { refresh() }

    private val CallHistoryViewModel.loaded
        get() = state.value as CallHistoryUiState.Loaded

    private val CallHistoryViewModel.pendingDeletes
        get() = loaded.pendingDeletes

    /** The messages [CallHistoryViewModel.errors] sends, as they come. */
    private fun CallHistoryViewModel.errorsShown(): List<Int> =
        mutableListOf<Int>().also { shown ->
            CoroutineScope(Dispatchers.Main).launch { errors.collect { shown += it } }
        }

    @Test
    fun loadsTheRequestedCallType() {
        val vm = viewModel(callType = android.provider.CallLog.Calls.MISSED_TYPE)
        assertThat(repository.loadedTypes)
            .containsExactly(android.provider.CallLog.Calls.MISSED_TYPE)
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
    fun theRowsShowBeforeTheBlockedNumbersAreKnown() {
        repository.blockedNumbers = setOf("+15550199")
        val gate = CompletableDeferred<Unit>()
        repository.blockedGate = gate
        val vm = viewModel()
        assertThat(vm.loaded.groups).containsExactly(ada, bea).inOrder()
        assertThat(vm.loaded.blockedNumbers).isEmpty()

        gate.complete(Unit)

        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550199")
    }

    @Test
    fun blockedNumbersAreKeptAcrossReloadsUntilTheyChange() {
        repository.blockedNumbers = setOf("+15550199")
        val vm = viewModel()
        assertThat(repository.blockedLookups).containsExactly(setOf("+15550100", "+15550199"))

        vm.refresh()
        assertThat(repository.blockedLookups).hasSize(1)
        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550199")

        // A new number is looked up on its own; the rest are still known.
        val cy = callLogGroup(30, "+15550177")
        repository.groups = listOf(cy, ada, bea)
        vm.refresh()
        assertThat(repository.blockedLookups.last()).containsExactly("+15550177")

        vm.block("+15550177", "US")
        vm.refresh()
        assertThat(repository.blockedLookups).hasSize(2)
        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550177", "+15550199")
    }

    @Test
    fun aChangeToTheBlockedNumbersLooksThemUpAgainWithoutAFlash() {
        repository.blockedNumbers = setOf("+15550199")
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        repository.blockedGate = gate
        repository.blockedNumbers = setOf("+15550100")

        vm.blockedNumbersChanged()
        dispatcher.scheduler.advanceTimeBy(CallHistoryViewModel.CHANGE_DEBOUNCE_MS + 1)
        // Looked up again, but until that returns the rows keep what was known.
        assertThat(repository.blockedLookups).hasSize(2)
        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550199")

        gate.complete(Unit)
        assertThat(vm.loaded.blockedNumbers).containsExactly("+15550100")
    }

    @Test
    fun changesReloadOnceTheySettle() {
        val vm = viewModel()
        vm.contentChanged()
        vm.contentChanged()
        vm.contentChanged()
        assertThat(repository.loadedTypes).hasSize(1)

        dispatcher.scheduler.advanceTimeBy(CallHistoryViewModel.CHANGE_DEBOUNCE_MS + 1)

        assertThat(repository.loadedTypes).hasSize(2)
        assertThat(vm.loaded.groups).containsExactly(ada, bea).inOrder()
    }

    @Test
    fun comingBackDoesNotReloadWhatTheObserversKeptCurrent() {
        val vm = viewModel()
        vm.resumed()
        assertThat(repository.loadedTypes).hasSize(1)
    }

    @Test
    fun comingBackReloadsOnceThePermissionIsThere() {
        permitted = false
        val vm = viewModel()
        assertThat(vm.state.value).isEqualTo(CallHistoryUiState.NeedsPermission)
        permitted = true
        vm.resumed()
        assertThat(vm.loaded.groups).containsExactly(ada, bea).inOrder()
    }

    @Test
    fun deletingHidesAtOnceAndDeletesOnCommit() {
        val vm = viewModel()
        vm.deleteLater(bea)
        assertThat(vm.pendingDeletes).containsExactly(20L, 21L)
        assertThat(repository.deleted).isEmpty()

        vm.commitDelete(bea)

        assertThat(repository.deleted).containsExactly(20L, 21L)
        // Still hidden until a reload no longer has the calls, or the row would come back
        // meanwhile.
        assertThat(vm.pendingDeletes).containsExactly(20L, 21L)
        vm.refresh()
        assertThat(vm.pendingDeletes).isEmpty()
        assertThat(vm.loaded.groups).containsExactly(ada)
    }

    @Test
    fun theDeleteHappensOnceTheUndoWindowCloses() {
        val vm = viewModel()
        vm.deleteLater(bea)
        dispatcher.scheduler.advanceTimeBy(CallHistoryViewModel.UNDO_WINDOW_MS - 1)
        assertThat(repository.deleted).isEmpty()

        dispatcher.scheduler.advanceTimeBy(2)

        assertThat(repository.deleted).containsExactly(20L, 21L)
        assertThat(vm.loaded.visible).containsExactly(ada)
    }

    @Test
    fun undoWithinTheWindowKeepsTheCalls() {
        val vm = viewModel()
        vm.deleteLater(bea)
        dispatcher.scheduler.advanceTimeBy(CallHistoryViewModel.UNDO_WINDOW_MS - 1)
        vm.undoDelete(bea)
        dispatcher.scheduler.advanceTimeBy(CallHistoryViewModel.UNDO_WINDOW_MS)
        assertThat(repository.deleted).isEmpty()
        assertThat(vm.loaded.visible).containsExactly(ada, bea).inOrder()
    }

    @Test
    fun clearingDeletesWhatWasStillWaitingOnUndo() {
        val vm = viewModel()
        vm.deleteLater(ada)
        vm.deleteLater(bea)
        vm.undoDelete(ada)

        ViewModelStore().apply { put("history", vm) }.clear()

        assertThat(repository.deleted).containsExactly(20L, 21L)
    }

    @Test
    fun aFailedDeleteShowsTheCallsAgainWithAMessage() {
        val vm = viewModel()
        val errors = vm.errorsShown()
        repository.deleteFailsWith = SecurityException("not the default dialer")
        vm.deleteLater(bea)
        vm.commitDelete(bea)
        assertThat(vm.loaded.visible).containsExactly(ada, bea).inOrder()
        assertThat(errors).containsExactly(R.string.call_history_delete_failed)
    }

    @Test
    fun aFailedBlockSaysSo() {
        val vm = viewModel()
        val errors = vm.errorsShown()
        repository.blockFailsWith = SecurityException("not the default dialer")
        vm.block("+15550100", "US")
        assertThat(vm.loaded.blockedNumbers).isEmpty()
        assertThat(errors).containsExactly(R.string.call_history_block_failed)
    }

    @Test
    fun aFailedLoadKeepsTheListAndSaysSo() {
        val vm = viewModel()
        val errors = vm.errorsShown()
        repository.failWith = IllegalStateException("provider died")
        vm.refresh()
        assertThat(vm.loaded.groups).containsExactly(ada, bea).inOrder()
        assertThat(errors).containsExactly(R.string.call_history_load_failed)
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
