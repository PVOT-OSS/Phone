/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.graphics.drawable.toDrawable
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotDialogContent
import com.prauga.pvot.designsystem.components.PvotDialogDefaults

/**
 * A platform [android.app.Dialog] drawn as a Pvot dialog card, for code that needs a real dialog
 * window: one shown over the lock screen, handed around as a `Dialog`, or returned from
 * `DialogFragment.onCreateDialog`.
 *
 * Every action closes the dialog; a [cancels] action cancels it, as the old negative buttons did.
 */
class PvotAlertDialog @JvmOverloads constructor(
    context: Context,
    private val title: CharSequence?,
    private val message: CharSequence?,
    private val actions: List<Action>,
    private val content: (@Composable () -> Unit)? = null,
) : ComponentDialog(context) {

    /** A button of the dialog. [onClick] runs before the dialog closes. */
    class Action @JvmOverloads constructor(
        val label: CharSequence,
        val style: PvotDialogActionStyle = PvotDialogActionStyle.Neutral,
        val onClick: Runnable? = null,
        val cancels: Boolean = false,
        /** Whether the button can be tapped; read while drawing, so it may follow Compose state. */
        val enabled: () -> Boolean = { true },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        setContentView(
            ComposeView(context).apply {
                setContent {
                    PhoneScreenTheme(fillBackground = false) {
                        PvotDialogContent(
                            title = title?.toString(),
                            modifier = Modifier.padding(PvotDialogDefaults.ScreenMargin),
                            message = message?.toString(),
                            content = content?.let { { it() } },
                        ) {
                            for (action in actions) {
                                PvotDialogAction(action.label.toString(), onClick = { act(action) }, style = action.style, enabled = action.enabled())
                            }
                        }
                    }
                }
            },
        )
        window?.run {
            // The card draws its own surface; the window only dims what is behind it.
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(PvotDialogDefaults.ScrimAlpha)
        }
    }

    private fun act(action: Action) {
        action.onClick?.run()
        if (action.cancels) cancel() else dismiss()
    }
}
