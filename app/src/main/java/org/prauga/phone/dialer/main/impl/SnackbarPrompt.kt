/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.main.impl

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.postcall.PostCall

/** Shows [PostCall]'s prompts in [hostState], drawn by the screen's own snackbar host. */
class SnackbarPrompt(private val scope: CoroutineScope, private val hostState: SnackbarHostState) : PostCall.Prompt {

    override fun show(
        message: String,
        actionLabel: String,
        onAction: Runnable,
        longDuration: Boolean,
        onDone: Runnable?,
    ) {
        scope.launch {
            hostState.currentSnackbarData?.dismiss()
            val result = hostState.showSnackbar(
                message = message,
                actionLabel = actionLabel,
                duration = if (longDuration) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) onAction.run()
            onDone?.run()
        }
    }
}
