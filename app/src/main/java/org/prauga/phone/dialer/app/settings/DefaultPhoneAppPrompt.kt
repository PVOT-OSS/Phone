/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.settings

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import org.prauga.phone.dialer.common.LogUtil

/**
 * The row asking to make Phone the default phone app, shown while it isn't. It is the way back
 * after "Not now" on the first-run prompt.
 *
 * @property denied Whether the user turned down Android's dialog here. Android may stop showing the
 *   dialog after that, so [onClick] then opens Default apps, where Phone can always be chosen
 */
internal class DefaultPhoneAppPrompt(val denied: Boolean, val onClick: () -> Unit)

/** The prompt to show at the top of settings, or null while Phone is the default phone app. */
@Composable
internal fun rememberDefaultPhoneAppPrompt(): DefaultPhoneAppPrompt? {
    val context = LocalContext.current
    val roleManager =
        remember(context) {
            context.getSystemService(RoleManager::class.java)?.takeIf {
                it.isRoleAvailable(RoleManager.ROLE_DIALER)
            }
        }
    var held by remember {
        mutableStateOf(roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true)
    }
    var denied by rememberSaveable { mutableStateOf(false) }
    val roleRequest =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            held = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true
            denied = !held
        }
    // Default apps may have been changed in Android's settings meanwhile.
    LifecycleResumeEffect(roleManager) {
        held = roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) ?: true
        onPauseOrDispose {}
    }
    if (roleManager == null || held) return null
    return DefaultPhoneAppPrompt(denied) {
        if (denied) {
            openDefaultApps(context)
        } else {
            roleRequest.launch(
                roleManager
                    .createRequestRoleIntent(RoleManager.ROLE_DIALER)
                    .putExtra(
                        TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME,
                        context.packageName,
                    )
            )
        }
    }
}

private fun openDefaultApps(context: Context) {
    for (action in listOf(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS, Settings.ACTION_SETTINGS)) {
        try {
            context.startActivity(Intent(action))
            return
        } catch (e: ActivityNotFoundException) {
            LogUtil.e("DefaultPhoneAppPrompt.openDefaultApps", "nothing handles $action", e)
        }
    }
}
