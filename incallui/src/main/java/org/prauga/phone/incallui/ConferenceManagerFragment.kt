/*
 * Copyright (C) 2013 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package org.prauga.phone.incallui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.incallui.ConferenceManagerPresenter.ConferenceManagerUi
import org.prauga.phone.incallui.baseui.BaseFragment
import org.prauga.phone.incallui.call.CallList
import org.prauga.phone.incallui.call.DialerCall

/** Fragment that allows the user to manage a conference call. */
class ConferenceManagerFragment :
    BaseFragment<ConferenceManagerPresenter, ConferenceManagerUi>(), ConferenceManagerUi {

    private lateinit var participants: ConferenceParticipants

    override fun createPresenter() = ConferenceManagerPresenter()

    override fun getUi(): ConferenceManagerUi = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        participants = ConferenceParticipants(requireContext().applicationContext)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PhoneScreenTheme {
                    ConferenceManagerScreen(
                        participants = participants.participants,
                        onSeparate = { id -> callById(id)?.splitFromConference() },
                        onDisconnect = { id -> callById(id)?.disconnect() },
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        presenter.init(CallList.getInstance())
    }

    override fun isFragmentVisible(): Boolean = isVisible

    override fun update(participants: List<DialerCall>, parentCanSeparate: Boolean) {
        this.participants.update(participants, parentCanSeparate)
    }

    override fun refreshCall(call: DialerCall) {
        participants.refresh(call)
    }

    private fun callById(id: String): DialerCall? =
        CallList.getInstance().getCallById(id).also {
            LogUtil.i("ConferenceManagerFragment.callById", "call: %s", it)
        }
}
