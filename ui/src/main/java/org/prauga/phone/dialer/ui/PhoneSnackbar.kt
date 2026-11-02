/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.prauga.pvot.designsystem.components.PvotSnackbar
import com.prauga.pvot.designsystem.components.PvotSnackbarColors
import com.prauga.pvot.designsystem.components.PvotSnackbarDefaults
import com.prauga.pvot.designsystem.components.PvotSnackbarHost
import com.prauga.pvot.designsystem.theme.PvotSpacing

/**
 * Phone's snackbars: a raised surface like its cards, with the action in the accent, inset from the
 * screen's edges. Place it above the nav bar so it doesn't cover it.
 */
@Composable
fun PhoneSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = phoneSnackbarColors()
    PvotSnackbarHost(hostState = hostState, modifier = modifier.padding(horizontal = PvotSpacing.L)) { data ->
        PvotSnackbar(data, colors = colors)
    }
}

/** The theme's raised surface and accent, rather than the inverse colors, so it reads as Phone's. */
@Composable
private fun phoneSnackbarColors(): PvotSnackbarColors {
    val scheme = MaterialTheme.colorScheme
    return PvotSnackbarDefaults.colors(
        colorScheme = scheme,
        containerColor = scheme.surfaceContainerHighest,
        contentColor = scheme.onSurface,
        actionColor = scheme.primary,
    )
}
