/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.prauga.pvot.designsystem.theme.PvotAppTheme

/**
 * The Pvot theme and background a full Compose screen sits on, with test tags exposed as ids.
 *
 * @param fillBackground Whether to paint the background and fill the space; off for content that
 *   sizes itself or slides over another screen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhoneScreenTheme(fillBackground: Boolean = true, content: @Composable () -> Unit) {
    PvotAppTheme {
        Surface(
            modifier = (if (fillBackground) Modifier.fillMaxSize() else Modifier).semantics { testTagsAsResourceId = true },
            color = if (fillBackground) MaterialTheme.colorScheme.background else Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            content = content,
        )
    }
}
