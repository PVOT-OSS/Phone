/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.answer.impl

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prauga.pvot.designsystem.components.PvotBottomSheetDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.incallui.incalluilock.InCallUiLock

/** The quick replies for declining a call with a message, and "Write your own…". */
@Composable
fun SmsReplySheet(replies: List<CharSequence>, onPick: (CharSequence?) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PvotBottomSheetDefaults.containerColor, PvotBottomSheetDefaults.Shape)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
    ) {
        Box(
            Modifier
                .padding(bottom = PvotSpacing.S)
                .size(PvotBottomSheetDefaults.HandleWidth, PvotBottomSheetDefaults.HandleHeight)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        )
        for (reply in replies) {
            PvotSheetItem(
                title = reply.toString(),
                icon = { Icon(painterResource(R.drawable.ic_message), contentDescription = null) },
                onClick = { onPick(reply) },
            )
        }
        PvotSheetItem(
            title = stringResource(R.string.call_incoming_message_custom),
            icon = { Icon(painterResource(R.drawable.ic_pencil), contentDescription = null) },
            onClick = { onPick(null) },
        )
    }
}

/** Shows the quick replies over the incoming call; a pick declines the call with that message. */
class SmsBottomSheetFragment : BottomSheetDialogFragment() {

    private var inCallUiLock: InCallUiLock? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        FragmentUtils.checkParent(this, SmsSheetHolder::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        LogUtil.i("SmsBottomSheetFragment.onCreateDialog", null)
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        inCallUiLock = holder().acquireInCallUiLock("SmsBottomSheetFragment")
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val replies = requireArguments().getCharSequenceArrayList(ARG_OPTIONS).orEmpty()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PhoneScreenTheme(fillBackground = false) {
                    SmsReplySheet(replies, onPick = { text ->
                        holder().smsSelected(text)
                        dismiss()
                    })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The Compose sheet draws its own rounded surface.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        holder().smsDismissed()
        inCallUiLock?.release()
    }

    private fun holder() = FragmentUtils.getParentUnsafe(this, SmsSheetHolder::class.java)

    /** Callback interface for [SmsBottomSheetFragment] */
    interface SmsSheetHolder {
        fun acquireInCallUiLock(tag: String): InCallUiLock

        fun smsSelected(text: CharSequence?)

        fun smsDismissed()
    }

    companion object {
        private const val ARG_OPTIONS = "options"

        @JvmStatic
        fun newInstance(options: ArrayList<CharSequence>?) = SmsBottomSheetFragment().apply {
            arguments = Bundle().apply { putCharSequenceArrayList(ARG_OPTIONS, options) }
        }
    }
}

