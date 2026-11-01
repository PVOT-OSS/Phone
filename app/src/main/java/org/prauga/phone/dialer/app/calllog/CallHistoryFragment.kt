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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prauga.pvot.designsystem.components.PvotSegmentedControl
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.ui.PhoneScreenTheme
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
            PhoneScreenTheme {
                // All / Missed on the Recents tab; the call history screen filters with its tabs.
                var missedOnly by rememberSaveable { mutableStateOf(false) }
                Column(Modifier.fillMaxSize()) {
                    PvotSegmentedControl(
                        options = listOf(
                            stringResource(R.string.call_log_all_title),
                            stringResource(R.string.call_log_missed_title),
                        ),
                        selectedIndex = if (missedOnly) 1 else 0,
                        onSelect = { missedOnly = it == 1 },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S)
                            .testTag(TestTags.RECENTS_FILTER),
                    )
                    key(missedOnly) {
                        CallHistoryRoute(
                            viewModel = if (missedOnly) missedViewModel else viewModel,
                            callType = if (missedOnly) Calls.MISSED_TYPE else callType,
                            modifier = Modifier.weight(1f),
                            showEmptyAction = !missedOnly && showsEmptyAction(callType, isCallLogActivity = false),
                            onShowDialpad = {
                                FragmentUtils.getParent(this@CallHistoryFragment, CallLogHost::class.java)
                                    ?.showDialpad()
                            },
                            onMissedCallRead = {
                                FragmentUtils.getParent(this@CallHistoryFragment, CallLogListener::class.java)
                                    ?.updateTabUnreadCounts()
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) onVisible()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) onVisible()
    }

    private fun onVisible() {
        FragmentUtils.getParent(this, CallLogHost::class.java)?.enableFloatingButton(true)
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
