/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.KeyguardManager
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.VoicemailContract
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.app.voicemail.VoicemailAudioManager
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackPresenter
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.VoicemailStatusMonitor
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.dockClearance
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessageCreator
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailStatus
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailStatusWorker

/** The Voicemail tab of the main screen: visual voicemails with playback in the open row. */
class VoicemailFragment : Fragment() {

    private val viewModel: CallHistoryViewModel by viewModels {
        viewModelFactory { initializer { CallHistoryViewModel(requireActivity().application, Calls.VOICEMAIL_TYPE) } }
    }

    private lateinit var presenter: VoicemailPlaybackPresenter
    private lateinit var statusMonitor: VoicemailStatusMonitor
    private val playbackState = VoicemailPlaybackState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        presenter = VoicemailPlaybackPresenter.getInstance(requireActivity() as AppCompatActivity, savedInstanceState)
        statusMonitor = VoicemailStatusMonitor(requireContext())
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            val statusMessage by statusMonitor.message.collectAsStateWithLifecycle()
            PhoneScreenTheme {
                CallHistoryRoute(
                    viewModel = viewModel,
                    callType = Calls.VOICEMAIL_TYPE,
                    showEmptyAction = false,
                    bottomPadding = dockClearance(),
                    onShowDialpad = {},
                    onMissedCallRead = { listener()?.updateTabUnreadCounts() },
                    voicemail = VoicemailParts(presenter, playbackState, statusMessage),
                    onSelectionModeChanged = { enabled, exit -> listener()?.onSelectionModeChanged(enabled, exit) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.onResume()
        statusMonitor.onResume()
        listener()?.updateTabUnreadCounts()
        if (!isHidden) onVisible()
    }

    override fun onPause() {
        presenter.onPause()
        statusMonitor.onPause()
        val audioManager = requireContext().getSystemService(AudioManager::class.java)
        @Suppress("DEPRECATION")
        if (audioManager.isSpeakerphoneOn) audioManager.isSpeakerphoneOn = false
        if (!isHidden) onNotVisible()
        super.onPause()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) {
            presenter.pausePresenter(true)
            onNotVisible()
        } else {
            onVisible()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        presenter.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        presenter.onDestroy()
        statusMonitor.destroy()
        super.onDestroy()
    }

    private fun listener(): CallLogListener? = FragmentUtils.getParent(this, CallLogListener::class.java)

    private fun onVisible() {
        requireActivity().volumeControlStream = VoicemailAudioManager.PLAYBACK_STREAM
        syncUnlessBlocked()
    }

    private fun onNotVisible() {
        val activity = activity ?: return
        activity.volumeControlStream = AudioManager.USE_DEFAULT_STREAM_TYPE
        if (!activity.getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            LogUtil.i("VoicemailFragment.onNotVisible", "clearing all new voicemails")
            CallLogNotificationsService.markAllNewVoicemailsAsOld(activity)
        }
    }

    /** Asks the voicemail service to sync, unless an account's status says syncing would fail. */
    private fun syncUnlessBlocked() {
        val context = requireContext().applicationContext
        viewLifecycleOwnerLiveData.value ?: return
        lifecycleScope.launch {
            val statuses: List<VoicemailStatus> = withContext(Dispatchers.IO) {
                VoicemailStatusWorker().doInBackground(context).orEmpty()
            }
            val creator = VoicemailErrorMessageCreator()
            val blocked = statuses.any { it.isActive(context) && creator.isSyncBlockingError(it) }
            if (blocked) {
                LogUtil.i("VoicemailFragment.syncUnlessBlocked", "auto-sync blocked")
                return@launch
            }
            context.sendBroadcast(Intent(VoicemailContract.ACTION_SYNC_VOICEMAIL).setPackage(context.packageName))
        }
    }

    companion object {
        @JvmStatic
        fun newInstance(): VoicemailFragment = VoicemailFragment()
    }
}
