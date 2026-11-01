/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.onboarding

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import org.prauga.phone.dialer.ui.PhoneScreenTheme

/**
 * Asks to become the default phone app, then for the permissions the dialer needs, explaining
 * each. It sits over [org.prauga.phone.dialer.main.impl.MainActivity], which keeps the intent
 * that opened it, so a number from a DIAL or CALL intent is still there once this finishes.
 */
class OnboardingActivity : ComponentActivity(), OnboardingActions {

    private var state by mutableStateOf(OnboardingState())

    private val roleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val held = isRoleHeld()
        state = state.copy(isDefaultDialer = held, roleDenied = !held)
        advance()
    }

    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val asked = asking
        asking = emptyList()
        state = state.afterPermissionRequest(grantedGroups(), asked) { group ->
            group.permissions.any(::shouldShowRequestPermissionRationale)
        }
        advance()
    }

    /** The groups in the permission request in flight. */
    private var asking: List<PermissionGroup> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePvotEdgeToEdge()
        savedInstanceState?.let { saved ->
            state = state.copy(
                roleSkipped = saved.getBoolean(KEY_ROLE_SKIPPED),
                roleDenied = saved.getBoolean(KEY_ROLE_DENIED),
                blocked = saved.getStringArrayList(KEY_BLOCKED).orEmpty().map(PermissionGroup::valueOf).toSet(),
            )
            asking = saved.getStringArrayList(KEY_ASKING).orEmpty().map(PermissionGroup::valueOf)
        }
        // Leaving with back is the same as "Not now", or MainActivity would open this again.
        onBackPressedDispatcher.addCallback(this) { dismiss() }
        setPvotContent {
            PhoneScreenTheme { OnboardingScreen(state, this) }
        }
    }

    override fun onResume() {
        super.onResume()
        // The user may have changed the role or permissions in Settings meanwhile.
        state = state.copy(isDefaultDialer = isRoleHeld(), granted = grantedGroups())
        state = state.copy(blocked = state.blocked - state.granted)
        advance()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_ROLE_SKIPPED, state.roleSkipped)
        outState.putBoolean(KEY_ROLE_DENIED, state.roleDenied)
        outState.putStringArrayList(KEY_BLOCKED, ArrayList(state.blocked.map { it.name }))
        outState.putStringArrayList(KEY_ASKING, ArrayList(asking.map { it.name }))
    }

    override fun onRequestRole() {
        val intent = getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
            .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        roleRequest.launch(intent)
    }

    override fun onOpenDefaultApps() {
        open(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
    }

    override fun onRequestPermissions() {
        asking = state.requestable
        permissionRequest.launch(asking.flatMap { it.permissions }.toTypedArray())
    }

    override fun onOpenAppSettings() {
        open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    override fun onNotNow() {
        if (state.step == OnboardingStep.ROLE) {
            state = state.copy(roleSkipped = true)
            advance()
        } else {
            dismiss()
        }
    }

    /** Finishes once there is nothing left to ask. */
    private fun advance() {
        if (state.step == OnboardingStep.DONE) dismiss()
    }

    private fun dismiss() {
        OnboardingRouter.markDismissed(this)
        finish()
    }

    private fun open(intent: Intent) {
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            open(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun isRoleHeld() = isDefaultDialer(this)

    private fun grantedGroups() = PermissionGroup.entries.filter { group ->
        group.permissions.all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    }.toSet()

    companion object {
        private const val KEY_ROLE_SKIPPED = "role_skipped"
        private const val KEY_ROLE_DENIED = "role_denied"
        private const val KEY_BLOCKED = "blocked"
        private const val KEY_ASKING = "asking"

        internal fun isDefaultDialer(context: Context) =
            context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }
}
