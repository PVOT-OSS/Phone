/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import android.app.Application
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.Contacts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.shortcuts.ShortcutRefresher
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntryDatabaseHelper
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.prauga.phone.dialer.speeddial.loader.UiItemLoaderComponent
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.widget.FavoritesWidget

/** What the favorites tab shows. */
sealed interface SpeedDialUiState {
    data object Loading : SpeedDialUiState
    data object NeedsPermission : SpeedDialUiState
    data object Empty : SpeedDialUiState

    /** Starred contacts in their pinned order, then frequently called contacts. */
    data class Loaded(
        val favorites: List<SpeedDialUiItem>,
        val suggestions: List<SpeedDialUiItem>,
    ) : SpeedDialUiState {
        /** Every item in display order, as positions are saved. */
        val all: List<SpeedDialUiItem> get() = favorites + suggestions
    }
}

/** Splits the loaded items into favorites, ordered by their pinned position, and suggestions. */
internal fun stateOf(items: List<SpeedDialUiItem>): SpeedDialUiState {
    if (items.isEmpty()) return SpeedDialUiState.Empty
    val (favorites, suggestions) = items.partition { it.isStarred }
    return SpeedDialUiState.Loaded(
        favorites = favorites.sortedBy { it.pinnedPosition().orElse(-1) },
        suggestions = suggestions,
    )
}

/** Reads and changes favorites; the work happens off the main thread. */
interface SpeedDialRepository {
    suspend fun load(): List<SpeedDialUiItem>
    suspend fun star(contactUri: Uri): List<SpeedDialUiItem>
    suspend fun remove(item: SpeedDialUiItem): List<SpeedDialUiItem>
    suspend fun savePositions(items: List<SpeedDialUiItem>)
    suspend fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel)
}

/** [SpeedDialRepository] over the speed dial database and contacts provider. */
class DefaultSpeedDialRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpeedDialRepository {
    private val mutator get() = UiItemLoaderComponent.get(context).speedDialUiItemMutator()

    override suspend fun load(): List<SpeedDialUiItem> = mutator.loadSpeedDialUiItems().await()

    override suspend fun star(contactUri: Uri): List<SpeedDialUiItem> = mutator.starContact(contactUri).await()

    override suspend fun remove(item: SpeedDialUiItem): List<SpeedDialUiItem> =
        mutator.removeSpeedDialUiItem(item).await()

    override suspend fun savePositions(items: List<SpeedDialUiItem>) =
        withContext(ioDispatcher) { mutator.updatePinnedPosition(items) }

    override suspend fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel) = withContext(ioDispatcher) {
        val entry = SpeedDialEntry.builder()
            .setId(item.speedDialEntryId())
            .setContactId(item.contactId())
            .setLookupKey(item.lookupKey())
            .setDefaultChannel(channel)
            .build()
        SpeedDialEntryDatabaseHelper(context).update(ImmutableList.of(entry))
    }
}

/** Loads favorites and suggestions, and keeps the user's changes to them. */
class SpeedDialViewModel @JvmOverloads constructor(
    application: Application,
    private val repository: SpeedDialRepository = DefaultSpeedDialRepository(application),
    private val hasPermission: () -> Boolean = { PermissionsUtil.hasContactsReadPermissions(application) },
    private val onSaved: (List<SpeedDialUiItem>) -> Unit = { items ->
        ShortcutRefresher.refresh(application, ShortcutRefresher.speedDialUiItemsToContactEntries(items))
    },
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<SpeedDialUiState>(SpeedDialUiState.Loading)
    val state: StateFlow<SpeedDialUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /** The last position save, which a reload waits for so it cannot show the old order. */
    private var saveJob: Job? = null

    /** Set when a change already produced fresh items, so the next resume need not reload. */
    private var skipNextRefresh = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    // Registered once the permission is there: without it, registering throws.
    private var observing = false

    /** Reloads favorites and suggestions, or asks for the permission when it is missing. */
    fun refresh() {
        if (skipNextRefresh) {
            skipNextRefresh = false
            return
        }
        if (!hasPermission()) {
            loadJob?.cancel()
            _state.value = SpeedDialUiState.NeedsPermission
            return
        }
        if (!observing) {
            getApplication<Application>().contentResolver.registerContentObserver(Contacts.CONTENT_STREQUENT_URI, true, observer)
            observing = true
        }
        update { repository.load() }
    }

    /** Stars the contact the user picked, from a phone number URI. */
    fun star(contactUri: Uri) {
        skipNextRefresh = true
        update { repository.star(contactUri) }
    }

    /** Removes [item] from favorites, at once on screen and then in storage. */
    fun remove(item: SpeedDialUiItem) {
        val loaded = _state.value as? SpeedDialUiState.Loaded
        if (loaded != null) {
            val favorites = loaded.favorites.filterNot { it.contactId() == item.contactId() }
            _state.value = if (favorites.isEmpty() && loaded.suggestions.isEmpty()) {
                SpeedDialUiState.Empty
            } else {
                loaded.copy(favorites = favorites)
            }
        }
        update { repository.remove(item) }
    }

    /** Moves the favorite at [from] to [to] while the user drags it; saved by [savePositions]. */
    fun moveFavorite(from: Int, to: Int) {
        val loaded = _state.value as? SpeedDialUiState.Loaded ?: return
        if (from == to || from !in loaded.favorites.indices || to !in loaded.favorites.indices) return
        val favorites = loaded.favorites.toMutableList().apply { add(to, removeAt(from)) }
        _state.value = loaded.copy(favorites = favorites)
    }

    /** Saves the favorites' order and refreshes the launcher shortcuts, when the tab is left. */
    fun savePositions() {
        val loaded = _state.value as? SpeedDialUiState.Loaded ?: return
        if (!hasPermission()) return
        val items = loaded.all
        saveJob = viewModelScope.launch {
            try {
                repository.savePositions(items)
                onSaved(items)
                refreshWidget()
            } catch (e: Exception) {
                LogUtil.e("SpeedDialViewModel.savePositions", "could not save the order", e)
            }
        }
    }

    /** Remembers [channel] as the one to call when [item] is tapped, and shows it on the item. */
    fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel) {
        update {
            repository.setDefaultChannel(item, channel)
            repository.load()
        }
    }

    /** Redraws the home screen widget, apart from the list so a slow redraw never holds it up. */
    private fun refreshWidget() {
        viewModelScope.launch { FavoritesWidget.update(getApplication()) }
    }

    private fun update(block: suspend () -> List<SpeedDialUiItem>) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            saveJob?.join()
            _state.value = try {
                stateOf(block())
            } catch (e: SecurityException) {
                LogUtil.e("SpeedDialViewModel.update", "permission revoked while loading", e)
                SpeedDialUiState.NeedsPermission
            } catch (e: Exception) {
                LogUtil.e("SpeedDialViewModel.update", "could not load favorites", e)
                // What is on screen stays; a first load that fails shows the empty state.
                if (_state.value is SpeedDialUiState.Loading) SpeedDialUiState.Empty else _state.value
            }
            if (_state.value is SpeedDialUiState.Loaded) refreshWidget()
        }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
