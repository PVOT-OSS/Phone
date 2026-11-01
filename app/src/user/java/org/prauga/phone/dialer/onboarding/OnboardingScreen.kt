/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.TestTags

/** What the onboarding screen's buttons do. */
interface OnboardingActions {
    fun onRequestRole()
    fun onOpenDefaultApps()
    fun onRequestPermissions()
    fun onOpenAppSettings()
    fun onNotNow()
}

/** The first-run screen of the user build: become the default phone app, then allow access. */
@Composable
fun OnboardingScreen(state: OnboardingState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    PvotScreen(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(TestTags.ONBOARDING),
        contentPadding = PvotScreenDefaults.contentPadding(top = PvotSpacing.L * 3, bottom = PvotSpacing.L),
    ) {
        when (state.step) {
            OnboardingStep.ROLE -> roleStep(state, actions)
            OnboardingStep.PERMISSIONS -> permissionsStep(state, actions)
            // The activity finishes; nothing to show for the frame before it does.
            OnboardingStep.DONE -> Unit
        }
    }
}

private fun LazyListScope.roleStep(state: OnboardingState, actions: OnboardingActions) {
    item(key = "header") {
        Header(R.drawable.ic_phone, R.string.onboarding_role_title, R.string.onboarding_role_body)
    }
    if (state.roleDenied) {
        item(key = "denied") { Note(R.string.onboarding_role_denied) }
    }
    item(key = "primary") {
        PvotButton(
            text = stringResource(R.string.onboarding_role_action),
            onClick = actions::onRequestRole,
            modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_PRIMARY),
        )
    }
    if (state.roleDenied) {
        item(key = "settings") {
            PvotButton(
                text = stringResource(R.string.onboarding_role_settings),
                onClick = actions::onOpenDefaultApps,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_SETTINGS),
                style = PvotButtonStyle.Tonal,
            )
        }
    }
    item(key = "not_now") { NotNow(actions) }
}

private fun LazyListScope.permissionsStep(state: OnboardingState, actions: OnboardingActions) {
    item(key = "header") {
        Header(R.drawable.ic_settings, R.string.onboarding_permissions_title, R.string.onboarding_permissions_body)
    }
    items(PermissionGroup.entries, key = { it.name }) { group ->
        PermissionRow(group, granted = group in state.granted, blocked = group in state.blocked)
    }
    if (state.blocked.any { it in state.missing }) {
        item(key = "blocked") { Note(R.string.onboarding_permissions_blocked) }
    }
    if (state.requestable.isNotEmpty()) {
        item(key = "primary") {
            PvotButton(
                text = stringResource(R.string.onboarding_permissions_action),
                onClick = actions::onRequestPermissions,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_PRIMARY),
            )
        }
    }
    if (state.blocked.any { it in state.missing }) {
        item(key = "settings") {
            PvotButton(
                text = stringResource(R.string.onboarding_permissions_settings),
                onClick = actions::onOpenAppSettings,
                modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_SETTINGS),
                style = if (state.requestable.isEmpty()) PvotButtonStyle.Filled else PvotButtonStyle.Tonal,
            )
        }
    }
    item(key = "not_now") { NotNow(actions) }
}

@Composable
private fun Header(icon: Int, title: Int, body: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        Box(
            modifier = Modifier
                .size(HeaderIconSize)
                .clip(PvotShapes.Pill)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(PvotSpacing.L * 2),
            )
        }
        Text(
            text = stringResource(title),
            modifier = Modifier.semantics { heading() },
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PermissionRow(group: PermissionGroup, granted: Boolean, blocked: Boolean) {
    PvotListRow(
        title = { Text(stringResource(group.title)) },
        modifier = Modifier.testTag(TestTags.onboardingPermission(group.name.lowercase())),
        supportingText = { Text(stringResource(group.reason)) },
        leading = {
            Icon(
                painter = painterResource(group.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        trailing = when {
            granted -> {
                {
                    Icon(
                        painter = painterResource(R.drawable.ic_check),
                        contentDescription = stringResource(R.string.onboarding_permission_granted),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            blocked -> {
                {
                    Text(
                        text = stringResource(R.string.onboarding_permission_blocked),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            else -> null
        },
    )
}

@Composable
private fun Note(text: Int) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun NotNow(actions: OnboardingActions) {
    PvotButton(
        text = stringResource(R.string.onboarding_not_now),
        onClick = actions::onNotNow,
        modifier = Modifier.testTag(TestTags.ONBOARDING_NOT_NOW),
        style = PvotButtonStyle.Text,
    )
}

private val HeaderIconSize = PvotSpacing.L * 4
