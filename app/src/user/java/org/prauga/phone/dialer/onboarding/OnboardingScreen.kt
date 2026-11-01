/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import androidx.compose.foundation.layout.Box
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotGlassCardDefaults
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.TestTags

/** What the onboarding screen's buttons do. */
interface OnboardingActions {
    fun onRequestRole()
    fun onOpenDefaultApps()
    fun onRequestPermissions()
    fun onOpenAppSettings()
    fun onNotNow()
}

/**
 * The first-run screen of the user build: become the default phone app, then allow access. Each
 * step is one card on the plain background.
 */
@Composable
fun OnboardingScreen(state: OnboardingState, actions: OnboardingActions, modifier: Modifier = Modifier) {
    PhoneScaffold(modifier = modifier) {
        PvotScreen(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .testTag(TestTags.ONBOARDING),
            contentPadding = PvotScreenDefaults.contentPadding(top = PvotSpacing.L * 3, bottom = PvotSpacing.L),
        ) {
            // The activity finishes after the last step; nothing to show for the frame before it does.
            if (state.step != OnboardingStep.DONE) {
                item(key = state.step.name) {
                    PvotCard(modifier = Modifier.fillMaxWidth()) {
                        Box(Modifier.padding(PvotGlassCardDefaults.ContentPadding)) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
                            ) {
                                when (state.step) {
                                    OnboardingStep.ROLE -> RoleStep(state, actions)
                                    OnboardingStep.PERMISSIONS -> PermissionsStep(state, actions)
                                    OnboardingStep.DONE -> Unit
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoleStep(state: OnboardingState, actions: OnboardingActions) {
Header(R.drawable.ic_phone, R.string.onboarding_role_title, R.string.onboarding_role_body)
if (state.roleDenied) Note(R.string.onboarding_role_denied)
PvotButton(
    text = stringResource(R.string.onboarding_role_action),
    onClick = actions::onRequestRole,
    modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_PRIMARY),
)
if (state.roleDenied) {
    PvotButton(
        text = stringResource(R.string.onboarding_role_settings),
        onClick = actions::onOpenDefaultApps,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_SETTINGS),
        style = PvotButtonStyle.Tonal,
    )
}
NotNow(actions)
}

@Composable
private fun PermissionsStep(state: OnboardingState, actions: OnboardingActions) {
Header(R.drawable.ic_settings, R.string.onboarding_permissions_title, R.string.onboarding_permissions_body)
for (group in PermissionGroup.entries) {
    key(group.name) {
        PermissionRow(group, granted = group in state.granted, blocked = group in state.blocked)
    }
}
val blockedMissing = state.blocked.any { it in state.missing }
if (blockedMissing) Note(R.string.onboarding_permissions_blocked)
if (state.requestable.isNotEmpty()) {
    PvotButton(
        text = stringResource(R.string.onboarding_permissions_action),
        onClick = actions::onRequestPermissions,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_PRIMARY),
    )
}
if (blockedMissing) {
    PvotButton(
        text = stringResource(R.string.onboarding_permissions_settings),
        onClick = actions::onOpenAppSettings,
        modifier = Modifier.fillMaxWidth().testTag(TestTags.ONBOARDING_SETTINGS),
        style = if (state.requestable.isEmpty()) PvotButtonStyle.Filled else PvotButtonStyle.Tonal,
    )
}
NotNow(actions)
}

@Composable
private fun Header(icon: Int, title: Int, body: Int) {
Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
) {
    // The icon sits straight on the glass: a filled circle under it would hide the glass.
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(HeaderIconSize),
    )
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

/** Size of the icon at the top of each step. */
private val HeaderIconSize = PvotSpacing.L * 3
