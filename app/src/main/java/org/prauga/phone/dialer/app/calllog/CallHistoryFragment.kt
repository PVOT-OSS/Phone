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
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prauga.pvot.designsystem.components.PvotTabRow
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.flow.MutableStateFlow
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackPresenter
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.VoicemailStatusMonitor
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.main.impl.OldMainActivityPeer
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

    private val voicemailViewModel: CallHistoryViewModel by lazy {
        ViewModelProvider(
            this,
            viewModelFactory { initializer { CallHistoryViewModel(requireActivity().application, Calls.VOICEMAIL_TYPE) } },
        )["voicemail", CallHistoryViewModel::class.java]
    }

    /** Playback and status for the Voicemail filter; absent when the device has no visual voicemail. */
    private var voicemail: VoicemailSupport? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OldMainActivityPeer.canVoicemailTabBeShown(requireContext())) {
            voicemail = VoicemailSupport(
                VoicemailPlaybackPresenter.getInstance(requireActivity() as AppCompatActivity, savedInstanceState),
                VoicemailStatusMonitor(requireContext()),
            )
        }
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
                // All, Missed, Voicemail (with visual voicemail), Contacts and Blocked on the
                // Recents tab; the call history screen also has Statistics.
                val support = voicemail
                val filters = remember(support) { CallLogFilter.entries.filter { it != CallLogFilter.VOICEMAIL || support != null } }
                var filter by rememberSaveable { mutableStateOf(CallLogFilter.ALL) }
                if (filter !in filters) filter = CallLogFilter.ALL
                val none = remember { MutableStateFlow(null) }
                val statusMessage by (support?.statusMonitor?.message ?: none).collectAsStateWithLifecycle()
                Column(Modifier.fillMaxSize()) {
                    PvotTabRow(
                        tabs = recentsFilterLabels(filters),
                        selectedIndex = filters.indexOf(filter),
                        onSelect = { filter = filters[it] },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = PvotSpacing.L)
                            .testTag(TestTags.RECENTS_FILTER),
                    )
                    // Each query has its own view model and list; the filters that narrow share the first.
                    val source = when (filter) {
                        CallLogFilter.MISSED -> CallLogFilter.MISSED
                        CallLogFilter.VOICEMAIL -> CallLogFilter.VOICEMAIL
                        else -> CallLogFilter.ALL
                    }
                    key(source) {
                        CallHistoryRoute(
                            viewModel = when (source) {
                                CallLogFilter.MISSED -> missedViewModel
                                CallLogFilter.VOICEMAIL -> voicemailViewModel
                                else -> viewModel
                            },
                            callType = when (source) {
                                CallLogFilter.MISSED -> Calls.MISSED_TYPE
                                CallLogFilter.VOICEMAIL -> Calls.VOICEMAIL_TYPE
                                else -> callType
                            },
                            modifier = Modifier.weight(1f),
                            bottomPadding = navBarClearance(),
                            showEmptyAction = filter == CallLogFilter.ALL && showsEmptyAction(callType, isCallLogActivity = false),
                            filter = filter,
                            voicemail = if (source == CallLogFilter.VOICEMAIL && support != null) {
                                VoicemailParts(support.presenter, support.playbackState, statusMessage)
                            } else null,
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

    override fun onResume() {
        super.onResume()
        voicemail?.let {
            it.presenter.onResume()
            it.statusMonitor.onResume()
        }
    }

    override fun onPause() {
        voicemail?.let {
            it.presenter.onPause()
            it.statusMonitor.onPause()
        }
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) voicemail?.presenter?.pausePresenter(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        voicemail?.presenter?.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        voicemail?.let {
            it.presenter.onDestroy()
            it.statusMonitor.destroy()
        }
        super.onDestroy()
    }

    /** The voicemail player and the status of the voicemail source, kept for the fragment's life. */
    private class VoicemailSupport(val presenter: VoicemailPlaybackPresenter, val statusMonitor: VoicemailStatusMonitor) {
        val playbackState = VoicemailPlaybackState()
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

/** The labels of [filters]' tabs, in their order. */
@androidx.compose.runtime.Composable
internal fun recentsFilterLabels(filters: List<CallLogFilter> = CallLogFilter.entries): List<String> = filters.map {
    stringResource(
        when (it) {
            CallLogFilter.ALL -> R.string.call_log_all_title
            CallLogFilter.MISSED -> R.string.call_log_missed_title
            CallLogFilter.VOICEMAIL -> R.string.tab_title_voicemail
            CallLogFilter.CONTACTS -> R.string.call_log_contacts_title
            CallLogFilter.BLOCKED -> R.string.call_log_blocked_title
        },
    )
}
