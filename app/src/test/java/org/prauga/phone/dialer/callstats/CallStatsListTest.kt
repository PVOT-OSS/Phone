/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.app.Application
import android.content.Context
import android.database.MatrixCursor
import android.provider.CallLog.Calls
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Per-number call statistics: loading, formatting, sorting and time ranges. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS")
class CallStatsListTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun cursor(vararg calls: Triple<String, Int, Long>) = MatrixCursor(CallStatsQuery._PROJECTION).apply {
        calls.forEachIndexed { i, (number, type, duration) ->
            val row = arrayOfNulls<Any?>(CallStatsQuery._PROJECTION.size)
            row[CallStatsQuery.ID] = i.toLong()
            row[CallStatsQuery.NUMBER] = number
            row[CallStatsQuery.DATE] = 1_000L * i
            row[CallStatsQuery.DURATION] = duration
            row[CallStatsQuery.CALL_TYPE] = type
            row[CallStatsQuery.NUMBER_PRESENTATION] = Calls.PRESENTATION_ALLOWED
            row[CallStatsQuery.POST_DIAL_DIGITS] = ""
            addRow(row)
        }
    }

    @Test
    fun callsAddUpPerNumber() {
        val entries = cursor(
            Triple("+15550100", Calls.INCOMING_TYPE, 60),
            Triple("+15550100", Calls.OUTGOING_TYPE, 120),
            Triple("+15550100", Calls.MISSED_TYPE, 0),
            Triple("+15550199", Calls.OUTGOING_TYPE, 30),
        ).use(CallStatsLoader::process)

        val ada = entries.single { it.details.number == "+15550100" }.details
        assertThat(ada.inDuration).isEqualTo(60)
        assertThat(ada.outDuration).isEqualTo(120)
        assertThat(ada.missedCount).isEqualTo(1)
        assertThat(ada.getTotalCount()).isEqualTo(3)
        assertThat(entries).hasSize(2)
    }

    @Test
    fun oneNumberWrittenTwoWaysCountsOnce() {
        val entries = cursor(
            Triple("(555) 010-0000", Calls.INCOMING_TYPE, 60),
            Triple("+15550199", Calls.OUTGOING_TYPE, 30),
            Triple("5550100000", Calls.OUTGOING_TYPE, 60),
        ).use(CallStatsLoader::process)

        assertThat(entries).hasSize(2)
        assertThat(entries.first().details.getFullDuration()).isEqualTo(120)
    }

    @Test
    fun durationsReadNaturally() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertThat(CallStatsFormat.duration(context, 3_725, includeSeconds = true)).isEqualTo("1 hr 2 mins 5 secs")
        assertThat(CallStatsFormat.duration(context, 95, includeSeconds = false)).isEqualTo("2 mins")
        assertThat(CallStatsFormat.callCount(context, 1)).isEqualTo("1 call")
    }

    private fun entry(number: String, inSeconds: Long, outCalls: Int): CallStatsEntry {
        val details = CallStatsDetails(number, Calls.PRESENTATION_ALLOWED, "", null, null, "US", null, 0)
        details.addTimeOrMissed(Calls.INCOMING_TYPE, inSeconds)
        repeat(outCalls) { details.addTimeOrMissed(Calls.OUTGOING_TYPE, 0) }
        return CallStatsEntry(org.prauga.phone.dialer.phonenumbercache.ContactInfo(), details)
    }

    private var permitted = true
    private val loads = mutableListOf<Pair<Long, Long>>()
    private var data = listOf(entry("+15550100", 600, 1), entry("+15550199", 60, 5))

    private fun viewModel() = CallStatsListViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        load = { from, to -> loads += from to to; data },
        hasPermission = { permitted },
        ioDispatcher = dispatcher,
    ).apply { refresh() }

    private val CallStatsListViewModel.numbers
        get() = (state.value as CallStatsUiState.Loaded).entries.map { it.details.number }

    @Test
    fun sortsByDurationThenByCount() {
        val vm = viewModel()
        assertThat(vm.numbers).containsExactly("+15550100", "+15550199").inOrder()

        vm.setSortByDuration(false)

        assertThat(vm.numbers).containsExactly("+15550199", "+15550100").inOrder()
    }

    @Test
    fun theTotalSumsEveryNumber() {
        val total = (viewModel().state.value as CallStatsUiState.Loaded).total
        assertThat(total.getFullDuration()).isEqualTo(660)
        assertThat(total.getTotalCount()).isEqualTo(8)
    }

    @Test
    fun aTimeRangeReloadsAndSaysWhenEmpty() {
        val vm = viewModel()
        data = emptyList()
        vm.setRange(1_000, 2_000)
        assertThat(loads.last()).isEqualTo(1_000L to 2_000L)
        assertThat(vm.state.value).isEqualTo(CallStatsUiState.Empty(inRange = true))
    }

    @Test
    fun missingPermissionAsksForIt() {
        permitted = false
        assertThat(viewModel().state.value).isEqualTo(CallStatsUiState.NeedsPermission)
        assertThat(loads).isEmpty()
    }
}
