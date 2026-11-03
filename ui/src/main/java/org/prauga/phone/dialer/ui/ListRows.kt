/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.prauga.pvot.designsystem.theme.PvotSpacing

/**
 * Inset for a list row that spans the screen. The row pads its own content by M, so this brings its
 * avatar and title in line with the screen padding (L) that titles and section headers use, and
 * keeps the row's rounded press and selection fill off the screen edges.
 */
val FullWidthRowInset: Dp = PvotSpacing.L - PvotSpacing.M

/** Insets a full-width list row by [FullWidthRowInset] on both sides. */
fun Modifier.fullWidthRow(): Modifier = padding(horizontal = FullWidthRowInset)
