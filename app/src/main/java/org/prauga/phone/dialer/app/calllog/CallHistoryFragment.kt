/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.os.Bundle
import android.provider.CallLog.Calls
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prauga.pvot.designsystem.components.PvotTabRow
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.navBarClearance
import org.prauga.phone.dialer.ui.TestTags

/** The Recents tab of the main screen: every call but voicemail, or only missed ones. */
class CallHistoryFragment : Fragment() {

    private val callType: Int get() = requireArguments().getInt(ARG_CALL_TYPE)

    private val viewModel: CallHistoryViewModel by viewModels {
        viewModelFactory { initializer { CallHistoryViewModel(requireActivity().application, callType) } }
    }

    // Keyed apart from the default one, which has the same class.
    private val missedViewModel: CallHistoryViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory { initializer { CallHistoryViewModel(requireActivity().application, Calls.MISSED_TYPE) } },
        )["missed", CallHistoryViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            // Scrolls reach the main screen, which shrinks the nav bar while a list scrolls up.
            PhoneScreenTheme(modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())) {
                // All, Missed, Contacts and Blocked on the Recents tab; the call history screen
                // also has Statistics.
                var filter by rememberSaveable { mutableStateOf(CallLogFilter.ALL) }
                val missedOnly = filter == CallLogFilter.MISSED
                Column(Modifier.fillMaxSize()) {
                    PvotTabRow(
                        tabs = recentsFilterLabels(),
                        selectedIndex = filter.ordinal,
                        onSelect = { filter = CallLogFilter.entries[it] },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L)
                            .testTag(TestTags.RECENTS_FILTER),
                    )
                    key(missedOnly) {
                        CallHistoryRoute(
                            viewModel = if (missedOnly) missedViewModel else viewModel,
                            callType = if (missedOnly) Calls.MISSED_TYPE else callType,
                            modifier = Modifier.weight(1f),
                            bottomPadding = navBarClearance(),
                            showEmptyAction = filter == CallLogFilter.ALL && showsEmptyAction(callType, isCallLogActivity = false),
                            filter = filter,
                            onShowDialpad = {
                                FragmentUtils.getParent(this@CallHistoryFragment, CallLogHost::class.java)
                                    ?.showDialpad()
                            },
                            onMissedCallRead = {
                                FragmentUtils.getParent(this@CallHistoryFragment, CallLogListener::class.java)
                                    ?.updateTabUnreadCounts()
                            },
                            // The main screen hides the nav bar and title while calls are selected.
                            onSelectionModeChanged = { enabled, exit ->
                                FragmentUtils.getParent(this@CallHistoryFragment, CallLogListener::class.java)
                                    ?.onSelectionModeChanged(enabled, exit)
                            },
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val ARG_CALL_TYPE = "call_type"

        /** A list of [callType] calls, [CallLogQueryHandler.CALL_TYPE_ALL] by default. */
        @JvmStatic
        @JvmOverloads
        fun newInstance(callType: Int = CallLogQueryHandler.CALL_TYPE_ALL): CallHistoryFragment =
            CallHistoryFragment().apply { arguments = bundleOf(ARG_CALL_TYPE to callType) }
    }
}

/** The labels of [CallLogFilter]'s tabs, in its order. */
@androidx.compose.runtime.Composable
internal fun recentsFilterLabels(): List<String> = listOf(
    stringResource(R.string.call_log_all_title),
    stringResource(R.string.call_log_missed_title),
    stringResource(R.string.call_log_contacts_title),
    stringResource(R.string.call_log_blocked_title),
)
