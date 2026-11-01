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
import android.telephony.PhoneNumberUtils
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.dialpadview.DialpadKeyGrid
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.DialpadPresenter.DialpadUi
import org.prauga.phone.incallui.baseui.BaseFragment

/** The digit [keyCode] sends as a tone, or null for a key that sends none. */
internal fun dtmfCharOf(keyCode: Int): Char? =
    when (keyCode) {
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0' + (keyCode - KeyEvent.KEYCODE_0)
        KeyEvent.KEYCODE_STAR -> '*'
        KeyEvent.KEYCODE_POUND -> '#'
        else -> null
    }

/** The dialpad shown during a call: each key sends its tone while held and shows the digit. */
class DialpadFragment : BaseFragment<DialpadPresenter, DialpadUi>(), DialpadUi {

    private var dtmfDialerField: EditText? = null
    private var dtmfKeyListener: DtmfKeyListener? = null
    private var showEndCallSpace by mutableStateOf(true)

    override fun createPresenter() = DialpadPresenter()

    override fun getUi(): DialpadUi = this

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val field = inflater.inflate(R.layout.dialpad_digits, container, false) as EditText
        LogUtil.i("DialpadFragment.onCreateView", "creating dtmfKeyListener")
        val listener = DtmfKeyListener(presenter)
        field.keyListener = listener
        field.isLongClickable = false
        field.isElegantTextHeight = false
        dtmfDialerField = field
        dtmfKeyListener = listener
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PhoneScreenTheme(fillBackground = false) {
                    InCallDialpad(
                        digits = field,
                        showEndCallSpace = showEndCallSpace,
                        onKey = ::onKey,
                        onBack = { requireActivity().onBackPressedDispatcher.onBackPressed() },
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        dtmfKeyListener = null
        super.onDestroyView()
    }

    private fun onKey(keyCode: Int, pressed: Boolean) {
        if (pressed) {
            dtmfCharOf(keyCode)?.let(presenter::processDtmf)
        } else {
            presenter.stopDtmf()
        }
    }

    fun getDtmfText(): String = dtmfDialerField?.text?.toString().orEmpty()

    fun setDtmfText(text: String) {
        dtmfDialerField?.setText(PhoneNumberUtils.createTtsSpannable(text))
    }

    override fun appendDigitsToField(digit: Char) {
        dtmfDialerField?.text?.append(digit)
    }

    /** A hardware key went down while the dialpad shows; returns whether it sent a tone. */
    fun onDialerKeyDown(event: KeyEvent?): Boolean = dtmfKeyListener?.onKeyDown(event) ?: false

    fun onDialerKeyUp(event: KeyEvent?): Boolean = dtmfKeyListener?.onKeyUp(event) ?: false

    /** Whether to keep room at the bottom for the call screen's end call button. */
    fun setShouldShowEndCallSpace(show: Boolean) {
        showEndCallSpace = show
    }
}

/** The in-call dialpad: the tones sent so far, the keys, and room for the end call button. */
@Composable
fun InCallDialpad(
    digits: EditText,
    showEndCallSpace: Boolean,
    onKey: (Int, Boolean) -> Unit,
    onBack: () -> Unit,
) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier =
                Modifier.fillMaxWidth()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .navigationBarsPadding()
                    .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M)
                    .testTag(TestTags.INCALL_DIALPAD)
        ) {
            Row(
                Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag(TestTags.INCALL_DIALPAD_BACK),
                ) {
                    Icon(
                        painterResource(R.drawable.ic_x),
                        stringResource(R.string.description_dialpad_back),
                    )
                }
                AndroidView(
                    factory = { digits.also { (it.parent as? ViewGroup)?.removeView(it) } },
                    update = { it.setTextColor(textColor) },
                    modifier = Modifier.weight(1f).height(56.dp),
                )
                Spacer(Modifier.size(48.dp))
            }
            Spacer(Modifier.height(PvotSpacing.S))
            DialpadKeyGrid(onKey = onKey, onKeyLongPress = { false }, longPressKeys = false)
            if (showEndCallSpace)
                Spacer(Modifier.height(dimensionResource(R.dimen.dialpad_bottom_space_height)))
        }
    }
}
