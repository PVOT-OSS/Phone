/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.Manifest.permission.READ_CALL_LOG
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.provider.CallLog
import android.provider.CallLog.Calls
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callstats.CallStatsActions
import org.prauga.phone.dialer.callstats.CallStatsDetailActivity
import org.prauga.phone.dialer.callstats.CallStatsDetails
import org.prauga.phone.dialer.callstats.CallStatsEntry
import org.prauga.phone.dialer.callstats.CallStatsListScreen
import org.prauga.phone.dialer.callstats.CallStatsListViewModel
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil
import org.prauga.phone.dialer.clipboard.ClipboardUtils
import org.prauga.phone.dialer.util.DialerUtils

/** The call history screen: all calls, missed calls and call statistics. */
class CallLogActivity : ComponentActivity() {

    private var selectedTab = TAB_ALL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePvotEdgeToEdge()
        val startTab = if (intent?.getIntExtra(CallLog.Calls.EXTRA_CALL_TYPE_FILTER, -1) == Calls.MISSED_TYPE) {
            TAB_MISSED
        } else {
            TAB_ALL
        }
        setPvotContent {
            PhoneScreenTheme {
                CallLogContent(
                    startTab = startTab,
                    onTabSelected = { tab ->
                        selectedTab = tab
                        if (tab != TAB_STATS) markMissedCallsAsRead()
                    },
                    onBack = ::goToMain,
                    onClearHistory = ::clearHistory,
                )
            }
        }
    }

    override fun onStop() {
        if (!isChangingConfigurations && selectedTab != TAB_STATS) markMissedCallsAsRead()
        super.onStop()
    }

    private fun markMissedCallsAsRead() {
        if (getSystemService(android.app.KeyguardManager::class.java).isKeyguardLocked) return
        CallLogQueryHandler(this, contentResolver, null).markMissedCallsAsRead()
        CallLogNotificationsService.cancelAllMissedCalls(this)
    }

    private fun goToMain() {
        startActivity(Intent(ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    private fun clearHistory() {
        CallLogNotificationsService.cancelAllMissedCalls(this)
        val appContext = applicationContext
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                appContext.contentResolver.delete(Calls.CONTENT_URI, null, null)
                appContext.contentResolver.notifyChange(Calls.CONTENT_URI, null)
            }
        }
    }

    companion object {
        const val TAB_ALL = 0
        const val TAB_MISSED = 1
        const val TAB_STATS = 2

        private const val ACTION_MAIN = "org.prauga.phone.dialer.main.impl.MAIN"
    }
}

@Composable
private fun CallLogContent(
    startTab: Int,
    onTabSelected: (Int) -> Unit,
    onBack: () -> Unit,
    onClearHistory: () -> Unit,
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    var tab by rememberSaveable { mutableIntStateOf(startTab) }
    var confirmClear by remember { mutableStateOf(false) }
    val allCalls: CallHistoryViewModel = viewModel(key = "all", factory = viewModelFactory {
        initializer { CallHistoryViewModel(application, CallLogQueryHandler.CALL_TYPE_ALL) }
    })
    val missedCalls: CallHistoryViewModel = viewModel(key = "missed", factory = viewModelFactory {
        initializer { CallHistoryViewModel(application, Calls.MISSED_TYPE) }
    })
    val stats: CallStatsListViewModel = viewModel(key = "stats", factory = viewModelFactory {
        initializer { CallStatsListViewModel(application) }
    })
    val allState by allCalls.state.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize()) {
        PvotTopBar(
            title = stringResource(R.string.call_log_activity_title),
            onBack = onBack,
            actions = {
                if (tab == CallLogActivity.TAB_ALL && allState is CallHistoryUiState.Loaded) {
                    PvotTopBarAction(
                        icon = painterResource(R.drawable.ic_trash),
                        contentDescription = stringResource(R.string.call_log_delete_all),
                        onClick = { confirmClear = true },
                    )
                }
            },
        )
        PvotSegmentedControl(
            options = listOf(
                stringResource(R.string.call_log_all_title),
                stringResource(R.string.call_log_missed_title),
                stringResource(R.string.call_log_stats_title),
            ),
            selectedIndex = tab,
            onSelect = {
                tab = it
                onTabSelected(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S)
                .testTag(TestTags.CALL_HISTORY_TABS),
        )
        when (tab) {
            CallLogActivity.TAB_ALL -> CallHistoryRoute(
                viewModel = allCalls,
                callType = CallLogQueryHandler.CALL_TYPE_ALL,
                showEmptyAction = showsEmptyAction(CallLogQueryHandler.CALL_TYPE_ALL, isCallLogActivity = true),
                onShowDialpad = {},
                onMissedCallRead = {},
                modifier = Modifier.weight(1f),
            )
            CallLogActivity.TAB_MISSED -> CallHistoryRoute(
                viewModel = missedCalls,
                callType = Calls.MISSED_TYPE,
                showEmptyAction = false,
                onShowDialpad = {},
                onMissedCallRead = {},
                modifier = Modifier.weight(1f),
            )
            else -> CallStatsRoute(stats, Modifier.weight(1f))
        }
    }

    if (confirmClear) {
        PvotDialog(
            visible = true,
            onDismissRequest = { confirmClear = false },
            title = stringResource(R.string.clearCallLogConfirmation_title),
            message = stringResource(R.string.clearCallLogConfirmation),
            destructive = true,
        ) {
            PvotDialogAction(
                label = stringResource(android.R.string.ok),
                onClick = { confirmClear = false; onClearHistory() },
                style = PvotDialogActionStyle.Destructive,
            )
            PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = { confirmClear = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallStatsRoute(viewModel: CallStatsListViewModel, modifier: Modifier) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val version by viewModel.contactInfoVersion.collectAsStateWithLifecycle()
    var pickingRange by remember { mutableStateOf(false) }
    val displayOrder = remember { ContactsComponent.get(context).contactDisplayPreferences().displayOrder }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) viewModel.refresh()
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        viewModel.refresh()
        onPauseOrDispose { viewModel.stop() }
    }

    val actions = remember(viewModel) {
        object : CallStatsActions {
            override fun open(entry: CallStatsEntry, total: CallStatsDetails) {
                context.startActivity(
                    Intent(context, CallStatsDetailActivity::class.java)
                        .putExtra(CallStatsDetailActivity.EXTRA_DETAILS, entry.details)
                        .putExtra(CallStatsDetailActivity.EXTRA_TOTAL, total)
                        .putExtra(CallStatsDetailActivity.EXTRA_FROM, viewModel.filter.value.from)
                        .putExtra(CallStatsDetailActivity.EXTRA_TO, viewModel.filter.value.to),
                )
            }

            override fun copyNumber(number: String) = ClipboardUtils.copyText(context, null, number, true)

            override fun editBeforeCall(number: String) =
                DialerUtils.startActivityWithErrorToast(context, Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(number)))

            override fun pickRange() { pickingRange = true }
            override fun clearRange() = viewModel.setRange(-1, -1)
            override fun sortByDuration(byDuration: Boolean) = viewModel.setSortByDuration(byDuration)
            override fun turnOnPermission() = permissionLauncher.launch(READ_CALL_LOG)
        }
    }

    CallStatsListScreen(
        state = state,
        filter = filter,
        prepare = { entry ->
            version.let {
                val details = entry.details
                if (PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation) && !details.isVoicemailNumber) {
                    viewModel.contactInfoCache.getValue(
                        details.number + details.postDialDigits, details.countryIso, entry.cachedInfo, false,
                    )?.let(details::updateFromInfo)
                }
                details.updateDisplayProperties(context, displayOrder)
                details
            }
        },
        actions = actions,
        modifier = modifier,
    )

    if (pickingRange) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = filter.from.takeIf { it != -1L }?.let(::localToUtcDay),
            initialSelectedEndDateMillis = filter.to.takeIf { it != -1L }?.let(::localToUtcDay),
        )
        DatePickerDialog(
            onDismissRequest = { pickingRange = false },
            confirmButton = {
                PvotButton(
                    text = stringResource(android.R.string.ok),
                    onClick = {
                        val start = pickerState.selectedStartDateMillis
                        if (start != null) {
                            val end = pickerState.selectedEndDateMillis ?: start
                            viewModel.setRange(startOfLocalDay(start), startOfLocalDay(end) + DAY_MILLIS - 1)
                        }
                        pickingRange = false
                    },
                    style = PvotButtonStyle.Text,
                )
            },
            dismissButton = {
                PvotButton(
                    text = stringResource(android.R.string.cancel),
                    onClick = { pickingRange = false },
                    style = PvotButtonStyle.Text,
                )
            },
        ) {
            DateRangePicker(state = pickerState, modifier = Modifier.weight(1f))
        }
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** The picker's UTC midnight for a day, as the start of that day in the phone's time zone. */
private fun startOfLocalDay(utcDayMillis: Long): Long =
    Instant.ofEpochMilli(utcDayMillis).atZone(ZoneOffset.UTC).toLocalDate()
        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** A local time, as the picker's UTC midnight of its day. */
private fun localToUtcDay(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis).atZone(ZoneId.systemDefault()).toLocalDate()
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
