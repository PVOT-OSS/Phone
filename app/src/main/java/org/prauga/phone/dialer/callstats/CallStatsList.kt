/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.provider.CallLog.Calls
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
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
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.contactinfo.ContactInfoCache
import org.prauga.phone.dialer.app.contactinfo.ExpirableCacheHeadlessFragment
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumbercache.ContactInfo
import org.prauga.phone.dialer.phonenumbercache.ContactInfoHelper
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.telecom.TelecomUtil
import org.prauga.phone.dialer.util.ExpirableCache
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.util.UriUtils

/** One number's calls in the statistics, with the contact the call log cached for it. */
data class CallStatsEntry(val cachedInfo: ContactInfo, val details: CallStatsDetails)

/** Reads the call log into per-number statistics. */
object CallStatsLoader {

    /** Calls after [from] and before [to] (each -1 for no limit), one entry per number. */
    fun load(resolver: ContentResolver, from: Long, to: Long): List<CallStatsEntry> {
        val selection = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (from != -1L) { selection += "(${Calls.DATE} > ?)"; args += from.toString() }
        if (to != -1L) { selection += "(${Calls.DATE} < ?)"; args += to.toString() }
        return resolver.query(
            Calls.CONTENT_URI_WITH_VOICEMAIL,
            CallStatsQuery._PROJECTION,
            selection.joinToString(" AND "),
            args.toTypedArray(),
            "${Calls.NUMBER} ASC",
        )?.use(::process).orEmpty()
    }

    /** Sums each number's calls; numbers written differently but equal count as one. */
    fun process(cursor: Cursor): List<CallStatsEntry> {
        val entries = mutableListOf<CallStatsEntry>()
        var pending: CallStatsDetails? = null
        cursor.moveToPosition(-1)
        while (cursor.moveToNext()) {
            val number = cursor.getString(CallStatsQuery.NUMBER).orEmpty()
            if (pending == null || !numbersEqual(pending.number.orEmpty(), number)) {
                val info = cachedInfo(cursor)
                pending = CallStatsDetails(
                    number,
                    cursor.getInt(CallStatsQuery.NUMBER_PRESENTATION),
                    cursor.getString(CallStatsQuery.POST_DIAL_DIGITS),
                    TelecomUtil.composePhoneAccountHandle(
                        cursor.getString(CallStatsQuery.ACCOUNT_COMPONENT_NAME),
                        cursor.getString(CallStatsQuery.ACCOUNT_ID),
                    ),
                    info,
                    cursor.getString(CallStatsQuery.COUNTRY_ISO),
                    cursor.getString(CallStatsQuery.GEOCODED_LOCATION),
                    cursor.getLong(CallStatsQuery.DATE),
                )
                entries += CallStatsEntry(info, pending)
            }
            pending.addTimeOrMissed(cursor.getInt(CallStatsQuery.CALL_TYPE), cursor.getLong(CallStatsQuery.DURATION))
        }
        // Sorting by number keeps most equal numbers together; merge the rest.
        val merged = mutableListOf<CallStatsEntry>()
        for (entry in entries) {
            val same = merged.firstOrNull { numbersEqual(it.details.number.orEmpty(), entry.details.number.orEmpty()) }
            if (same != null) same.details.mergeWith(entry.details) else merged += entry
        }
        return merged
    }

    private fun cachedInfo(cursor: Cursor) = ContactInfo().apply {
        lookupUri = UriUtils.parseUriOrNull(cursor.getString(CallStatsQuery.CACHED_LOOKUP_URI))
        name = cursor.getString(CallStatsQuery.CACHED_NAME)
        type = cursor.getInt(CallStatsQuery.CACHED_NUMBER_TYPE)
        label = cursor.getString(CallStatsQuery.CACHED_NUMBER_LABEL)
        number = cursor.getString(CallStatsQuery.CACHED_MATCHED_NUMBER) ?: cursor.getString(CallStatsQuery.NUMBER)
        normalizedNumber = cursor.getString(CallStatsQuery.CACHED_NORMALIZED_NUMBER)
        formattedNumber = cursor.getString(CallStatsQuery.CACHED_FORMATTED_NUMBER)
        photoId = cursor.getLong(CallStatsQuery.CACHED_PHOTO_ID)
    }

    private fun numbersEqual(a: String, b: String): Boolean =
        if (PhoneNumberHelper.isUriNumber(a) || PhoneNumberHelper.isUriNumber(b)) {
            PhoneNumberHelper.compareSipAddresses(a, b)
        } else {
            PhoneNumberUtils.compare(a, b)
        }
}

/** Formats call counts and durations the way the statistics always have. */
object CallStatsFormat {

    @JvmStatic
    fun callCount(context: Context, count: Long): String =
        context.resources.getQuantityString(R.plurals.call, count.toInt(), count.toInt())

    /** "1 hour 5 minutes"; without seconds the minutes are rounded. */
    @JvmStatic
    fun duration(context: Context, totalSeconds: Long, includeSeconds: Boolean): String {
        var rest = totalSeconds
        var hours = (rest / 3600).toInt()
        rest -= hours * 3600L
        var minutes = (rest / 60).toInt()
        rest -= minutes * 60L
        val seconds = rest.toInt()
        if (!includeSeconds) {
            if (seconds >= 30) minutes++
            if (minutes >= 60) hours++
        }
        val showHours = hours > 0
        val showMinutes = minutes > 0 || (!includeSeconds && hours == 0)
        val showSeconds = includeSeconds && (seconds > 0 || (hours == 0 && minutes == 0))
        val res = context.resources
        val index = ((if (showHours) 4 else 0) or (if (showMinutes) 2 else 0) or (if (showSeconds) 1 else 0)) - 1
        return String.format(
            res.getStringArray(R.array.call_stats_duration)[index],
            if (showHours) res.getQuantityString(R.plurals.hour, hours, hours) else null,
            if (showMinutes) res.getQuantityString(R.plurals.minute, minutes, minutes) else null,
            if (showSeconds) res.getQuantityString(R.plurals.second, seconds, seconds) else null,
        )
    }
}

/** What the statistics show. */
sealed interface CallStatsUiState {
    data object Loading : CallStatsUiState
    data object NeedsPermission : CallStatsUiState

    /** No calls; [inRange] when a time range is set. */
    data class Empty(val inRange: Boolean) : CallStatsUiState

    data class Loaded(val entries: List<CallStatsEntry>, val total: CallStatsDetails) : CallStatsUiState
}

/** The time range and sort order the user picked. */
data class CallStatsFilter(val from: Long = -1, val to: Long = -1, val sortByDuration: Boolean = true) {
    val hasRange: Boolean get() = from != -1L || to != -1L
}

/** Loads the statistics for the user's time range and keeps their sort order. */
class CallStatsListViewModel(
    application: Application,
    private val load: (from: Long, to: Long) -> List<CallStatsEntry> = { from, to ->
        CallStatsLoader.load(application.contentResolver, from, to)
    },
    private val hasPermission: () -> Boolean = { PermissionsUtil.hasCallLogReadPermissions(application) },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AndroidViewModel(application) {

    private val _filter = MutableStateFlow(CallStatsFilter())
    val filter: StateFlow<CallStatsFilter> = _filter.asStateFlow()

    private val _state = MutableStateFlow<CallStatsUiState>(CallStatsUiState.Loading)
    val state: StateFlow<CallStatsUiState> = _state.asStateFlow()

    private val _contactInfoVersion = MutableStateFlow(0)
    val contactInfoVersion: StateFlow<Int> = _contactInfoVersion.asStateFlow()

    val contactInfoCache = ContactInfoCache(
        ExpirableCache.create(ExpirableCacheHeadlessFragment.CONTACT_INFO_CACHE_SIZE),
        ContactInfoHelper(application, GeoUtil.getCurrentCountryIso(application)),
    ) { _contactInfoVersion.value++ }

    private var loadJob: Job? = null
    private var raw: List<CallStatsEntry> = emptyList()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    init {
        if (!PermissionsUtil.hasContactsReadPermissions(application)) contactInfoCache.disableRequestProcessing()
        application.contentResolver.registerContentObserver(CallLog.CONTENT_URI, true, observer)
        application.contentResolver.registerContentObserver(ContactsContract.Contacts.CONTENT_URI, true, observer)
    }

    fun refresh() {
        if (!hasPermission()) {
            loadJob?.cancel()
            _state.value = CallStatsUiState.NeedsPermission
            return
        }
        val filter = _filter.value
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            raw = try {
                withContext(ioDispatcher) { load(filter.from, filter.to) }
            } catch (e: SecurityException) {
                LogUtil.e("CallStatsListViewModel.refresh", "permission revoked while loading", e)
                _state.value = CallStatsUiState.NeedsPermission
                return@launch
            }
            contactInfoCache.invalidate()
            publish()
        }
    }

    /** Shows calls from [from] to [to], or every call for -1 and -1. */
    fun setRange(from: Long, to: Long) {
        _filter.value = _filter.value.copy(from = from, to = to)
        refresh()
    }

    fun setSortByDuration(byDuration: Boolean) {
        _filter.value = _filter.value.copy(sortByDuration = byDuration)
        publish()
    }

    fun start() {
        if (PermissionsUtil.hasContactsReadPermissions(getApplication())) contactInfoCache.start()
    }

    fun stop() = contactInfoCache.stop()

    private fun publish() {
        val filter = _filter.value
        val total = CallStatsDetails(null, 0, null, null, null, null, null, 0)
        raw.forEach { total.mergeWith(it.details) }
        val shown = raw
            .filter { if (filter.sortByDuration) it.details.getRequestedDuration(ALL) > 0 else it.details.getRequestedCount(ALL) > 0 }
            .sortedByDescending { if (filter.sortByDuration) it.details.getRequestedDuration(ALL) else it.details.getRequestedCount(ALL).toLong() }
        _state.value = if (shown.isEmpty()) {
            CallStatsUiState.Empty(inRange = filter.hasRange)
        } else {
            CallStatsUiState.Loaded(shown, total)
        }
    }

    override fun onCleared() {
        contactInfoCache.stop()
        getApplication<Application>().contentResolver.unregisterContentObserver(observer)
    }

    companion object {
        /** Every call type. */
        const val ALL = -1
    }
}
