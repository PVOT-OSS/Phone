/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.ui.PhoneScreenTheme

/** The Recents tab of the main screen: every call but voicemail. */
class CallHistoryFragment : Fragment() {

    private val callType: Int get() = requireArguments().getInt(ARG_CALL_TYPE)

    private val viewModel: CallHistoryViewModel by viewModels {
        viewModelFactory { initializer { CallHistoryViewModel(requireActivity().application, callType) } }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            PhoneScreenTheme {
                CallHistoryRoute(
                    viewModel = viewModel,
                    callType = callType,
                    showEmptyAction = showsEmptyAction(callType, isCallLogActivity = false),
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
