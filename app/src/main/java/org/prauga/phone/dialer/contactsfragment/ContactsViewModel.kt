/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract.Contacts
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.util.PermissionsUtil

/** What the contacts tab shows. */
sealed interface ContactsUiState {
    data object Loading : ContactsUiState

    data object NeedsPermission : ContactsUiState

    data object Empty : ContactsUiState

    data class Loaded(val sections: List<ContactSection>) : ContactsUiState
}

/** Loads the contacts tab, and reloads it when contacts change. */
class ContactsViewModel
@JvmOverloads
constructor(
    application: Application,
    private val hasPermission: () -> Boolean = {
        PermissionsUtil.hasContactsReadPermissions(application)
    },
    private val loadContacts: () -> List<ContactSection> = { ContactsQuery.load(application) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<ContactsUiState>(ContactsUiState.Loading)
    val state: StateFlow<ContactsUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = refresh()
        }

    // Registered once the permission is there: without it, registering throws.
    private var observing = false

    /** Reloads the contacts, or asks for the permission when it is missing. */
    fun refresh() {
        if (!hasPermission()) {
            loadJob?.cancel()
            _state.value = ContactsUiState.NeedsPermission
            return
        }
        if (!observing) {
            getApplication<Application>()
                .contentResolver
                .registerContentObserver(Contacts.CONTENT_URI, true, observer)
            observing = true
        }
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val sections =
                    try {
                        withContext(ioDispatcher) { loadContacts() }
                    } catch (e: SecurityException) {
                        LogUtil.e(
                            "ContactsViewModel.refresh",
                            "permission revoked while loading",
                            e,
                        )
                        null
                    }
                _state.value =
                    when {
                        sections == null -> ContactsUiState.NeedsPermission
                        sections.isEmpty() -> ContactsUiState.Empty
                        else -> ContactsUiState.Loaded(sections)
                    }
            }
    }

    override fun onCleared() {
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
