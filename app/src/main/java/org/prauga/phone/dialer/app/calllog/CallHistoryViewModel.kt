/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Application
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.content.ContentValues
import android.net.Uri
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
import org.prauga.phone.dialer.app.contactinfo.ContactInfoCache
import org.prauga.phone.dialer.app.contactinfo.ExpirableCacheHeadlessFragment
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery
import org.prauga.phone.dialer.phonenumbercache.ContactInfoHelper
import org.prauga.phone.dialer.util.ExpirableCache
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.util.PermissionsUtil

/** What a call history list shows. */
sealed interface CallHistoryUiState {
    data object Loading : CallHistoryUiState
    data object NeedsPermission : CallHistoryUiState
    data object Empty : CallHistoryUiState
    data class Loaded(val groups: List<CallLogGroup>, val blockedNumbers: Set<String>) : CallHistoryUiState
}

/** Reads, filters and changes the call log for a call history list. */
interface CallHistoryRepository {
    /** Calls of [callType] ([CallLogQueryHandler.CALL_TYPE_ALL] for all but voicemail), newest first. */
    suspend fun load(callType: Int): List<CallLogGroup>

    /** Which of [numbers] are blocked. */
    suspend fun blocked(numbers: Collection<String>): Set<String>

    /** Deletes [groups]' calls; a voicemail is also marked deleted on the server. */
    suspend fun delete(groups: Collection<CallLogGroup>)
    suspend fun block(number: String, countryIso: String?)
    suspend fun unblock(number: String)
}

/** [CallHistoryRepository] over the call log and blocked numbers providers. */
class DefaultCallHistoryRepository(
    private val application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CallHistoryRepository {
    private val resolver get() = application.contentResolver

    override suspend fun load(callType: Int): List<CallLogGroup> = withContext(ioDispatcher) {
        val query = CallLogQueryHandler.buildFetchQuery(application, callType, false, 0, -1)
        resolver.query(query.uri, CallLogQuery.getProjection(), query.selection, query.selectionArgs, Calls.DEFAULT_SORT_ORDER)
            ?.use(CallLogGrouper::group)
            .orEmpty()
    }

    override suspend fun blocked(numbers: Collection<String>): Set<String> = withContext(ioDispatcher) {
        // Only the default phone app may read blocked numbers.
        if (!TelecomUtil.isDefaultDialer(application) ||
            !BlockedNumberContract.canCurrentUserBlockNumbers(application)
        ) return@withContext emptySet()
        numbers.filterTo(mutableSetOf()) { number ->
            number.isNotEmpty() && BlockedNumberContract.isBlocked(application, number)
        }
    }

    override suspend fun delete(groups: Collection<CallLogGroup>) = withContext<Unit>(ioDispatcher) {
        val (voicemails, calls) = groups.partition { it.voicemailUri != null }
        voicemails.forEach { CallLogAsyncTaskUtil.deleteVoicemailSynchronous(application, Uri.parse(it.voicemailUri)) }
        val callIds = calls.flatMap { it.callIds }
        if (callIds.isEmpty()) return@withContext
        val where = "${CallLog.Calls._ID} IN (${callIds.joinToString(",")})"
        resolver.delete(Calls.CONTENT_URI, where, null)
    }

    override suspend fun block(number: String, countryIso: String?) = withContext<Unit>(ioDispatcher) {
        resolver.insert(
            BlockedNumbers.CONTENT_URI,
            ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
        )
    }

    override suspend fun unblock(number: String) = withContext<Unit>(ioDispatcher) {
        BlockedNumberContract.unblock(application, number)
    }
}

/**
 * Loads one call history list (all calls, or missed calls), reloads it when calls or contacts
 * change, and looks up callers in the background through [contactInfoCache].
 */
class CallHistoryViewModel(
    application: Application,
    private val callType: Int,
    private val repository: CallHistoryRepository = DefaultCallHistoryRepository(application),
    private val hasPermission: () -> Boolean = { PermissionsUtil.hasCallLogReadPermissions(application) },
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<CallHistoryUiState>(CallHistoryUiState.Loading)
    val state: StateFlow<CallHistoryUiState> = _state.asStateFlow()

    /** Bumped each time a caller lookup finishes, so rows can read their new contact. */
    private val _contactInfoVersion = MutableStateFlow(0)
    val contactInfoVersion: StateFlow<Int> = _contactInfoVersion.asStateFlow()

    /** Rows deleted with a swipe, hidden until the delete is undone or committed. */
    private val _pendingDeletes = MutableStateFlow<Set<Long>>(emptySet())

    /** Calls deleted from the call log that a load may still have shown, until it no longer does. */
    private val deleted = mutableSetOf<Long>()
    val pendingDeletes: StateFlow<Set<Long>> = _pendingDeletes.asStateFlow()

    val contactInfoCache = ContactInfoCache(
        ExpirableCache.create(ExpirableCacheHeadlessFragment.CONTACT_INFO_CACHE_SIZE),
        ContactInfoHelper(application, GeoUtil.getCurrentCountryIso(application)),
    ) { _contactInfoVersion.value++ }

    private var loadJob: Job? = null

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    // Each observer is registered once its permission is there: without it, registering throws.
    private var observingCalls = false
    private var observingContacts = false

    init {
        if (!PermissionsUtil.hasContactsReadPermissions(application)) contactInfoCache.disableRequestProcessing()
    }

    private fun observe() {
        val application = getApplication<Application>()
        if (!observingCalls) {
            application.contentResolver.registerContentObserver(CallLog.CONTENT_URI, true, observer)
            observingCalls = true
        }
        if (!observingContacts && PermissionsUtil.hasContactsReadPermissions(application)) {
            application.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
            observingContacts = true
        }
    }

    /** Reloads the calls, or asks for the permission when it is missing. */
    fun refresh() {
        if (!hasPermission()) {
            loadJob?.cancel()
            _state.value = CallHistoryUiState.NeedsPermission
            return
        }
        observe()
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = try {
                val groups = repository.load(callType)
                forgetDeleted(groups)
                if (groups.isEmpty()) {
                    CallHistoryUiState.Empty
                } else {
                    contactInfoCache.invalidate()
                    CallHistoryUiState.Loaded(groups, repository.blocked(groups.map { it.number }.toSet()))
                }
            } catch (e: SecurityException) {
                LogUtil.e("CallHistoryViewModel.refresh", "permission revoked while loading", e)
                CallHistoryUiState.NeedsPermission
            }
        }
    }

    /**
     * Stops hiding deleted calls once a load no longer has them. Calls still waiting on undo stay
     * hidden even when a filtered load leaves them out.
     */
    private fun forgetDeleted(groups: List<CallLogGroup>) {
        val loaded = groups.flatMapTo(HashSet()) { it.callIds }
        val gone = deleted.filterTo(HashSet()) { it !in loaded }
        deleted -= gone
        _pendingDeletes.value -= gone
    }

    /** Starts caller lookups, while the list shows. */
    fun start() {
        if (!PermissionsUtil.hasContactsReadPermissions(getApplication())) return
        // Lookups were turned off if the permission was missing when this was created.
        contactInfoCache.enableRequestProcessing()
        contactInfoCache.start()
    }

    /** Stops caller lookups, when the list is left. */
    fun stop() = contactInfoCache.stop()

    /** Hides [group] at once; the delete happens on [commitDelete] unless [undoDelete] comes first. */
    fun deleteLater(group: CallLogGroup) {
        _pendingDeletes.value += group.callIds
    }

    fun undoDelete(group: CallLogGroup) {
        _pendingDeletes.value -= group.callIds.toSet()
    }

    fun commitDelete(group: CallLogGroup) {
        if (group.callIds.none { it in _pendingDeletes.value }) return
        // The calls stay hidden until a reload no longer has them; showing them again as soon as
        // the delete is done would bring the row back until then.
        viewModelScope.launch {
            repository.delete(listOf(group))
            deleted += group.callIds
        }
    }

    /** Deletes [groups] at once, for voicemails the user selected and confirmed. */
    fun deleteNow(groups: List<CallLogGroup>) {
        if (groups.isEmpty()) return
        _pendingDeletes.value += groups.flatMap { it.callIds }
        viewModelScope.launch {
            repository.delete(groups)
            deleted += groups.flatMap { it.callIds }
        }
    }

    fun block(number: String, countryIso: String?) {
        viewModelScope.launch {
            repository.block(number, countryIso)
            updateBlocked { it + number }
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            repository.unblock(number)
            updateBlocked { it - number }
        }
    }

    private fun updateBlocked(change: (Set<String>) -> Set<String>) {
        val loaded = _state.value as? CallHistoryUiState.Loaded ?: return
        _state.value = loaded.copy(blockedNumbers = change(loaded.blockedNumbers))
    }

    override fun onCleared() {
        contactInfoCache.stop()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }
}
