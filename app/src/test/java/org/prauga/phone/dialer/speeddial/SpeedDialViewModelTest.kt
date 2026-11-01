/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import android.app.Application
import android.net.Uri
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
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.robolectric.RobolectricTestRunner

/** Loading and changing favorites. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class SpeedDialViewModelTest {

    private class FakeRepository(var items: List<SpeedDialUiItem>) : SpeedDialRepository {
        var loads = 0
        val starred = mutableListOf<Uri>()
        val removed = mutableListOf<SpeedDialUiItem>()
        var saved: List<SpeedDialUiItem>? = null
        var defaults = mutableListOf<Pair<SpeedDialUiItem, Channel>>()
        var failWith: Exception? = null

        override suspend fun load(): List<SpeedDialUiItem> {
            loads++
            failWith?.let { throw it }
            return items
        }
        override suspend fun star(contactUri: Uri) = items.also { starred += contactUri }
        override suspend fun remove(item: SpeedDialUiItem): List<SpeedDialUiItem> {
            removed += item
            items = items.filterNot { it.contactId() == item.contactId() }
            return items
        }
        override suspend fun savePositions(items: List<SpeedDialUiItem>) { saved = items }
        override suspend fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel) { defaults += item to channel }
    }

    private val ada = speedDialItem(1, "Ada", pinned = 1)
    private val bea = speedDialItem(2, "Bea", pinned = 0)
    private val cy = speedDialItem(3, "Cy", pinned = 2)
    private val dev = speedDialItem(4, "Dev", starred = false)

    private val repository = FakeRepository(listOf(ada, dev, bea, cy))
    private var permitted = true
    private var shortcutsFor: List<SpeedDialUiItem>? = null

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = SpeedDialViewModel(
        ApplicationProvider.getApplicationContext<Application>(),
        repository,
        hasPermission = { permitted },
        onSaved = { shortcutsFor = it },
    ).apply { refresh() }

    private val SpeedDialViewModel.loaded get() = state.value as SpeedDialUiState.Loaded

    @Test
    fun favoritesFollowTheirPinnedPositionThenSuggestions() {
        val vm = viewModel()
        assertThat(vm.loaded.favorites.map { it.name() }).containsExactly("Bea", "Ada", "Cy").inOrder()
        assertThat(vm.loaded.suggestions.map { it.name() }).containsExactly("Dev")
    }

    @Test
    fun nothingToShowIsEmpty() {
        repository.items = emptyList()
        assertThat(viewModel().state.value).isEqualTo(SpeedDialUiState.Empty)
    }

    @Test
    fun missingPermissionAsksForIt() {
        permitted = false
        assertThat(viewModel().state.value).isEqualTo(SpeedDialUiState.NeedsPermission)
        assertThat(repository.loads).isEqualTo(0)
    }

    @Test
    fun revokedWhileLoadingAsksForIt() {
        repository.failWith = SecurityException("revoked")
        assertThat(viewModel().state.value).isEqualTo(SpeedDialUiState.NeedsPermission)
    }

    @Test
    fun movingAFavoriteReordersAndSavesOnLeave() {
        val vm = viewModel()
        vm.moveFavorite(0, 2)
        assertThat(vm.loaded.favorites.map { it.name() }).containsExactly("Ada", "Cy", "Bea").inOrder()

        vm.savePositions()

        assertThat(repository.saved?.map { it.name() }).containsExactly("Ada", "Cy", "Bea", "Dev").inOrder()
        assertThat(shortcutsFor).isEqualTo(repository.saved)
    }

    @Test
    fun movesOutOfRangeAreIgnored() {
        val vm = viewModel()
        vm.moveFavorite(0, 9)
        assertThat(vm.loaded.favorites.map { it.name() }).containsExactly("Bea", "Ada", "Cy").inOrder()
    }

    @Test
    fun savingWithoutPermissionDoesNothing() {
        val vm = viewModel()
        permitted = false
        vm.savePositions()
        assertThat(repository.saved).isNull()
    }

    @Test
    fun removingLeavesTheGridAtOnce() {
        val vm = viewModel()
        vm.remove(ada)
        assertThat(repository.removed).containsExactly(ada)
        assertThat(vm.loaded.favorites.map { it.name() }).containsExactly("Bea", "Cy").inOrder()
    }

    @Test
    fun starringSkipsTheNextResumeReload() {
        val vm = viewModel()
        val loadsBefore = repository.loads
        vm.star(Uri.parse("content://com.android.contacts/data/phones/7"))
        vm.refresh()
        assertThat(repository.starred).hasSize(1)
        assertThat(repository.loads).isEqualTo(loadsBefore)
        vm.refresh()
        assertThat(repository.loads).isEqualTo(loadsBefore + 1)
    }

    @Test
    fun rememberedNumberIsSaved() {
        val vm = viewModel()
        val work = channel("+15550199", "Work")
        vm.setDefaultChannel(ada, work)
        assertThat(repository.defaults).containsExactly(ada to work)
    }
}
