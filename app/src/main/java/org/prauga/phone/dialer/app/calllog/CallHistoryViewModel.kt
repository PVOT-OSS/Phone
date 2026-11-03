/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Application
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.BlockedNumberContract
import android.provider.BlockedNumberContract.BlockedNumbers
import android.provider.CallLog
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.calllog.calllogcache.CallLogCache
import org.prauga.phone.dialer.app.contactinfo.ContactInfoCache
import org.prauga.phone.dialer.app.contactinfo.ExpirableCacheHeadlessFragment
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumbercache.CallLogQuery
import org.prauga.phone.dialer.phonenumbercache.ContactInfoHelper
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.util.ExpirableCache
import org.prauga.phone.dialer.util.PermissionsUtil

/** What a call history list shows. */
sealed interface CallHistoryUiState {
    data object Loading : CallHistoryUiState

    data object NeedsPermission : CallHistoryUiState

    data object Empty : CallHistoryUiState

    /**
     * @param pendingDeletes Calls hidden until their delete is undone or done; part of the state so
     *   a reload publishes the rows and what hides them together
     * @param rows Each group's row, ready to show, by row id
     */
    data class Loaded(
        val groups: List<CallLogGroup>,
        val blockedNumbers: Set<String>,
        val pendingDeletes: Set<Long> = emptySet(),
        val rows: Map<Long, CallLogRowUi> = emptyMap(),
    ) : CallHistoryUiState {
        /** The rows to show: [groups] without the ones waiting on a delete. */
        val visible: List<CallLogGroup>
            get() =
                if (pendingDeletes.isEmpty()) groups
                else groups.filter { group -> group.callIds.none { it in pendingDeletes } }
    }
}

/** Reads, filters and changes the call log for a call history list. */
interface CallHistoryRepository {
    /**
     * Calls of [callType] ([CallLogQueryHandler.CALL_TYPE_ALL] for all but voicemail), newest
     * first.
     */
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
    private val resolver
        get() = application.contentResolver

    override suspend fun load(callType: Int): List<CallLogGroup> =
        withContext(ioDispatcher) {
            val query = CallLogQueryHandler.buildFetchQuery(application, callType, false, 0, -1)
            resolver
                .query(
                    query.uri,
                    CallLogQuery.getProjection(),
                    query.selection,
                    query.selectionArgs,
                    Calls.DEFAULT_SORT_ORDER,
                )
                ?.use(CallLogGrouper::group)
                .orEmpty()
        }

    override suspend fun blocked(numbers: Collection<String>): Set<String> =
        withContext(ioDispatcher) {
            // Only the default phone app may read blocked numbers.
            if (
                !TelecomUtil.isDefaultDialer(application) ||
                    !BlockedNumberContract.canCurrentUserBlockNumbers(application)
            )
                return@withContext emptySet()
            numbers.filterTo(mutableSetOf()) { number ->
                number.isNotEmpty() && BlockedNumberContract.isBlocked(application, number)
            }
        }

    override suspend fun delete(groups: Collection<CallLogGroup>) =
        withContext<Unit>(ioDispatcher) {
            val (voicemails, calls) = groups.partition { it.voicemailUri != null }
            voicemails.forEach {
                CallLogAsyncTaskUtil.deleteVoicemailSynchronous(
                    application,
                    Uri.parse(it.voicemailUri),
                )
            }
            val callIds = calls.flatMap { it.callIds }
            if (callIds.isEmpty()) return@withContext
            val where = "${CallLog.Calls._ID} IN (${callIds.joinToString(",")})"
            resolver.delete(Calls.CONTENT_URI, where, null)
        }

    override suspend fun block(number: String, countryIso: String?) =
        withContext<Unit>(ioDispatcher) {
            resolver.insert(
                BlockedNumbers.CONTENT_URI,
                ContentValues().apply { put(BlockedNumbers.COLUMN_ORIGINAL_NUMBER, number) },
            )
        }

    override suspend fun unblock(number: String) =
        withContext<Unit>(ioDispatcher) { BlockedNumberContract.unblock(application, number) }
}

/**
 * Loads one call history list (all calls, or missed calls), reloads it when calls or contacts
 * change, and looks up callers in the background through [contactInfoCache].
 *
 * The rows show as soon as they are grouped; which of them are blocked follows, from a lookup that
 * is slow (one call into the blocked numbers provider per number) and so is kept across reloads
 * until the blocked numbers change. Each row's text and actions are resolved here, off the main
 * thread, and again only when the groups, their callers or the blocked numbers change.
 *
 * @param resolveRow Turns a group, its index and whether it is blocked into its row; by default the
 *   [CallLogRowResolver] with the caller from [contactInfoCache]
 * @param resolveDispatcher Where rows are resolved: one thread at a time, as the resolver is not
 *   thread safe
 */
@OptIn(FlowPreview::class)
class CallHistoryViewModel(
    application: Application,
    private val callType: Int,
    private val repository: CallHistoryRepository = DefaultCallHistoryRepository(application),
    private val hasPermission: () -> Boolean = {
        PermissionsUtil.hasCallLogReadPermissions(application)
    },
    resolveRow: ((group: CallLogGroup, index: Int, isBlocked: Boolean) -> CallLogRowUi)? = null,
    private val resolveDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1),
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<CallHistoryUiState>(CallHistoryUiState.Loading)
    val state: StateFlow<CallHistoryUiState> = _state.asStateFlow()

    /** What went wrong, as a string resource to show once. */
    private val _errors =
        MutableSharedFlow<Int>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )
    val errors: SharedFlow<Int> = _errors.asSharedFlow()

    /** Rows deleted with a swipe, hidden until the delete is undone or committed. */
    private var pendingDeletes: Set<Long> = emptySet()

    /**
     * Swiped rows whose undo window is still open, by row id, each with the timer that commits it.
     */
    private val undoTimers = mutableMapOf<Long, PendingDelete>()

    private class PendingDelete(val group: CallLogGroup, val timer: Job)

    /**
     * Runs the deletes still pending when this is cleared. [viewModelScope] is closed by then, and
     * a delete the user did not undo must land even as the screen goes.
     */
    private val commitScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * Calls deleted from the call log that a load may still have shown, until it no longer does.
     */
    private val deleted = mutableSetOf<Long>()

    /** Whether each number looked up so far is blocked; cleared when the blocked numbers change. */
    private val knownBlocked = mutableMapOf<String, Boolean>()

    /** A change to the calls, contacts or blocked numbers; the list reloads once they settle. */
    private val changes =
        MutableSharedFlow<Unit>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST,
        )

    /**
     * Whether a load has finished since the observers were registered, so a resume can skip one.
     */
    private var loadedOnce = false

    /** Whether the list is showing, so caller lookups may run. */
    private var lookingUp = false

    val contactInfoCache =
        ContactInfoCache(
            ExpirableCache.create(ExpirableCacheHeadlessFragment.CONTACT_INFO_CACHE_SIZE),
            ContactInfoHelper(application, GeoUtil.getCurrentCountryIso(application)),
        ) {
            contactInfoChanged()
        }

    private val resolveRow: (CallLogGroup, Int, Boolean) -> CallLogRowUi =
        resolveRow
            ?: run {
                val resolver = CallLogRowResolver(application, CallLogCache(application))
                val remoteLookups = REMOTE_LOOKUP_ROWS
                ({ group, index, isBlocked ->
                    val info =
                        contactInfoCache.getValue(
                            group.number + group.postDialDigits,
                            group.countryIso,
                            group.cachedInfo,
                            index < remoteLookups,
                        )
                    resolver.resolve(group, info, isBlocked)
                })
            }

    private var loadJob: Job? = null
    private var rowsJob: Job? = null

    private val observer =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = contentChanged()
        }

    private val blockedObserver =
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = blockedNumbersChanged()
        }

    // Each observer is registered once its permission is there: without it, registering throws.
    private var observingCalls = false
    private var observingContacts = false
    private var observingBlocked = false

    init {
        if (!PermissionsUtil.hasContactsReadPermissions(application))
            contactInfoCache.disableRequestProcessing()
        // A burst of changes (a call ends, its caller is looked up and written back, a contact is
        // edited) becomes one reload once it has settled, rather than a load restarted each time.
        viewModelScope.launch { changes.debounce(CHANGE_DEBOUNCE_MS).collect { load() } }
    }

    /** The calls or contacts changed; the list reloads once the changes settle. */
    internal fun contentChanged() {
        changes.tryEmit(Unit)
    }

    /** The blocked numbers changed: they are looked up again with the next reload. */
    internal fun blockedNumbersChanged() {
        knownBlocked.clear()
        contentChanged()
    }

    /** A caller lookup finished: the rows read their callers again. */
    internal fun contactInfoChanged() = resolveRows()

    private fun observe() {
        val application = getApplication<Application>()
        if (!observingCalls) {
            application.contentResolver.registerContentObserver(CallLog.CONTENT_URI, true, observer)
            observingCalls = true
        }
        if (!observingContacts && PermissionsUtil.hasContactsReadPermissions(application)) {
            application.contentResolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer,
            )
            observingContacts = true
        }
        if (!observingBlocked) {
            try {
                application.contentResolver.registerContentObserver(
                    BlockedNumbers.CONTENT_URI,
                    true,
                    blockedObserver,
                )
                observingBlocked = true
            } catch (e: SecurityException) {
                // Not the default phone app: it cannot read blocked numbers either, so none show.
                LogUtil.i("CallHistoryViewModel.observe", "cannot watch the blocked numbers", e)
            }
        }
    }

    /**
     * The list came back into view. Reloads it unless a load already finished and the observers
     * have kept it current since, in which case the rows are already what a reload would give.
     */
    fun resumed() {
        if (loadedOnce && observingCalls && hasPermission()) return
        refresh()
    }

    /** Reloads the calls, or asks for the permission when it is missing. */
    fun refresh() {
        if (!hasPermission()) {
            loadJob?.cancel()
            loadedOnce = false
            _state.value = CallHistoryUiState.NeedsPermission
            return
        }
        observe()
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                try {
                    val groups = repository.load(callType)
                    if (groups.isEmpty()) {
                        forgetDeleted(groups)
                        _state.value = CallHistoryUiState.Empty
                        loadedOnce = true
                        return@launch
                    }
                    // The rows show now, with the blocked numbers as last known (a number not
                    // looked
                    // up yet keeps what the last list said of it); the lookup follows. The rows,
                    // what
                    // hides them and the blocked set go out together, so a row waiting on undo
                    // never
                    // shows between two states.
                    contactInfoCache.expireAll()
                    if (lookingUp) contactInfoCache.start()
                    val numbers = groups.mapTo(LinkedHashSet()) { it.number }
                    val previous =
                        (_state.value as? CallHistoryUiState.Loaded)?.blockedNumbers.orEmpty()
                    val blockedNow =
                        numbers.filterTo(HashSet()) { knownBlocked[it] ?: (it in previous) }
                    val rows = resolveRows(groups, blockedNow)
                    forgetDeleted(groups)
                    _state.value =
                        CallHistoryUiState.Loaded(groups, blockedNow, pendingDeletes, rows)
                    loadedOnce = true

                    val unknown = numbers.filter { it !in knownBlocked }
                    if (unknown.isNotEmpty()) {
                        val blocked = repository.blocked(unknown)
                        unknown.forEach { knownBlocked[it] = it in blocked }
                        updateBlocked { it - unknown.toSet() + blocked }
                    }
                } catch (e: SecurityException) {
                    LogUtil.e("CallHistoryViewModel.refresh", "permission revoked while loading", e)
                    _state.value = CallHistoryUiState.NeedsPermission
                } catch (e: Exception) {
                    // The provider can fail in other ways too; the list keeps what it had and says
                    // so.
                    LogUtil.e("CallHistoryViewModel.refresh", "loading the call log failed", e)
                    if (_state.value is CallHistoryUiState.Loading)
                        _state.value = CallHistoryUiState.Empty
                    _errors.tryEmit(R.string.call_history_load_failed)
                }
            }
    }

    /**
     * Stops hiding deleted calls once a load no longer has them. Calls still waiting on undo stay
     * hidden even when a filtered load leaves them out. Not suspending: it runs right before the
     * state that shows the result is published.
     */
    private fun forgetDeleted(groups: List<CallLogGroup>) {
        val loaded = groups.flatMapTo(HashSet()) { it.callIds }
        val gone = deleted.filterTo(HashSet()) { it !in loaded }
        deleted -= gone
        pendingDeletes -= gone
    }

    private suspend fun resolveRows(
        groups: List<CallLogGroup>,
        blocked: Set<String>,
    ): Map<Long, CallLogRowUi> =
        withContext(resolveDispatcher) {
            groups
                .mapIndexed { index, group ->
                    group.rowId to resolveRow(group, index, group.number in blocked)
                }
                .toMap()
        }

    /**
     * Resolves the loaded rows again, for a caller that was just looked up or a change to the
     * blocked numbers, and publishes them if the list has not moved on meanwhile.
     */
    private fun resolveRows() {
        val loaded = _state.value as? CallHistoryUiState.Loaded ?: return
        rowsJob?.cancel()
        rowsJob =
            viewModelScope.launch {
                val rows = resolveRows(loaded.groups, loaded.blockedNumbers)
                val current = _state.value as? CallHistoryUiState.Loaded ?: return@launch
                if (
                    current.groups === loaded.groups &&
                        current.blockedNumbers == loaded.blockedNumbers
                ) {
                    _state.value = current.copy(rows = rows)
                }
            }
    }

    /** Hides or shows calls at once, in the state the list reads. */
    private fun updatePendingDeletes(change: (Set<Long>) -> Set<Long>) {
        pendingDeletes = change(pendingDeletes)
        val loaded = _state.value as? CallHistoryUiState.Loaded ?: return
        _state.value = loaded.copy(pendingDeletes = pendingDeletes)
    }

    /** Starts caller lookups, while the list shows. */
    fun start() {
        if (!PermissionsUtil.hasContactsReadPermissions(getApplication())) return
        // Lookups were turned off if the permission was missing when this was created.
        contactInfoCache.enableRequestProcessing()
        contactInfoCache.start()
        lookingUp = true
    }

    /** Stops caller lookups, when the list is left. */
    fun stop() {
        lookingUp = false
        contactInfoCache.stop()
    }

    /**
     * Hides [group] at once and deletes it once the undo window closes, unless [undoDelete] comes
     * first. The timer runs here, not on the screen, so leaving the list, rotating or switching
     * tabs while the snackbar shows still ends in the delete.
     */
    fun deleteLater(group: CallLogGroup) {
        undoTimers.remove(group.rowId)?.timer?.cancel()
        updatePendingDeletes { it + group.callIds }
        val timer =
            viewModelScope.launch {
                delay(UNDO_WINDOW_MS)
                undoTimers -= group.rowId
                delete(listOf(group))
            }
        undoTimers[group.rowId] = PendingDelete(group, timer)
    }

    fun undoDelete(group: CallLogGroup) {
        undoTimers.remove(group.rowId)?.timer?.cancel()
        updatePendingDeletes { it - group.callIds.toSet() }
    }

    /** Deletes [group] now rather than when its undo window closes. */
    fun commitDelete(group: CallLogGroup) {
        undoTimers.remove(group.rowId)?.timer?.cancel()
        if (group.callIds.none { it in pendingDeletes }) return
        viewModelScope.launch { delete(listOf(group)) }
    }

    /** Deletes [groups] at once, for voicemails the user selected and confirmed. */
    fun deleteNow(groups: List<CallLogGroup>) {
        if (groups.isEmpty()) return
        updatePendingDeletes { it + groups.flatMap { group -> group.callIds } }
        viewModelScope.launch { delete(groups) }
    }

    /**
     * Deletes [groups]' calls. They stay hidden until a reload no longer has them; showing them
     * again as soon as the delete is done would bring the rows back until then. When the delete
     * fails they show again, with a message.
     */
    private suspend fun delete(groups: List<CallLogGroup>) {
        val ids = groups.flatMap { it.callIds }
        try {
            repository.delete(groups)
            deleted += ids
        } catch (e: Exception) {
            LogUtil.e("CallHistoryViewModel.delete", "deleting calls failed", e)
            updatePendingDeletes { it - ids.toSet() }
            _errors.tryEmit(R.string.call_history_delete_failed)
        }
    }

    fun block(number: String, countryIso: String?) {
        viewModelScope.launch {
            try {
                repository.block(number, countryIso)
                knownBlocked[number] = true
                updateBlocked { it + number }
            } catch (e: Exception) {
                LogUtil.e("CallHistoryViewModel.block", "blocking failed", e)
                _errors.tryEmit(R.string.call_history_block_failed)
            }
        }
    }

    fun unblock(number: String) {
        viewModelScope.launch {
            try {
                repository.unblock(number)
                knownBlocked[number] = false
                updateBlocked { it - number }
            } catch (e: Exception) {
                LogUtil.e("CallHistoryViewModel.unblock", "unblocking failed", e)
                _errors.tryEmit(R.string.call_history_unblock_failed)
            }
        }
    }

    private fun updateBlocked(change: (Set<String>) -> Set<String>) {
        val loaded = _state.value as? CallHistoryUiState.Loaded ?: return
        val blocked = change(loaded.blockedNumbers)
        if (blocked == loaded.blockedNumbers) return
        _state.value = loaded.copy(blockedNumbers = blocked)
        // Blocked rows read and offer different things.
        resolveRows()
    }

    override fun onCleared() {
        contactInfoCache.stop()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
        getApplication<Application>().contentResolver.unregisterContentObserver(blockedObserver)
        // Undo is gone with the screen, so what it was waiting on is deleted now.
        val pending = undoTimers.values.map { it.group }
        undoTimers.values.forEach { it.timer.cancel() }
        undoTimers.clear()
        if (pending.isNotEmpty()) {
            commitScope.launch {
                try {
                    repository.delete(pending)
                } catch (e: Exception) {
                    LogUtil.e("CallHistoryViewModel.onCleared", "deleting calls failed", e)
                }
            }
        }
    }

    companion object {
        /**
         * How long a swiped row can be brought back, in ms: the Short snackbar that offers Undo
         * shows for 4 s, plus a margin so a tap at its last moment still lands.
         */
        const val UNDO_WINDOW_MS = 4_500L

        /** How long the calls, contacts and blocked numbers must stay quiet before a reload. */
        const val CHANGE_DEBOUNCE_MS = 300L

        /** How many rows from the top also look their caller up in the remote directory. */
        private const val REMOTE_LOOKUP_ROWS = 5
    }
}
