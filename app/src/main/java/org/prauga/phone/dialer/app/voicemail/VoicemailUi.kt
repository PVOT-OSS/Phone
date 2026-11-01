/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.voicemail

import android.text.Html
import android.text.Spanned
import android.view.View
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessage

/** A voicemail status message, such as a full inbox, with its actions. */
@Composable
fun VoicemailStatusCard(message: VoicemailErrorMessage, modifier: Modifier = Modifier) {
    val view = LocalView.current
    PvotCard(modifier = modifier.fillMaxWidth().padding(PvotSpacing.L).testTag(TestTags.VOICEMAIL_STATUS)) {
        Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            Text(text = message.title.toString(), style = MaterialTheme.typography.titleMedium)
            Text(
                text = message.description.toAnnotated(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            MessageActions(message, view)
        }
    }
}

/** The carrier's visual voicemail terms, shown instead of the voicemail list until answered. */
@Composable
fun VoicemailTermsScreen(message: VoicemailErrorMessage, modifier: Modifier = Modifier) {
    val view = LocalView.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(PvotSpacing.L)
            .testTag(TestTags.VOICEMAIL_STATUS),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        message.imageResourceId?.let { image ->
            Image(painterResource(image), contentDescription = null, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        Text(text = message.title.toString(), style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message.description.toAnnotated(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MessageActions(message, view)
    }
}

@Composable
private fun MessageActions(message: VoicemailErrorMessage, view: View) {
    val actions = message.actions.orEmpty()
    if (actions.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S), modifier = Modifier.fillMaxWidth()) {
        for (action in actions) {
            PvotButton(
                text = action.text.toString(),
                onClick = { action.listener.onClick(view) },
                style = if (action.isRaised) PvotButtonStyle.Filled else PvotButtonStyle.Text,
            )
        }
    }
}

/** Keeps the links of a styled message, such as "Learn more". */
private fun CharSequence?.toAnnotated(): AnnotatedString = when (this) {
    null -> AnnotatedString("")
    is Spanned -> AnnotatedString.fromHtml(Html.toHtml(this, Html.TO_HTML_PARAGRAPH_LINES_CONSECUTIVE))
    else -> AnnotatedString(toString())
}
