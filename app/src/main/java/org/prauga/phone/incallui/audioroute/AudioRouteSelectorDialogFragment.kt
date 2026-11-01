/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.audioroute

import android.app.Dialog
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.telecom.CallAudioState
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.os.BundleCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.prauga.pvot.designsystem.components.PvotBottomSheetDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.incallui.call.TelecomAdapter
import org.prauga.phone.incallui.util.BluetoothUtil

/** One place the call's audio can go: a Bluetooth device, the speaker, a headset or the earpiece. */
data class AudioRoute(
    val route: Int,
    val label: String?,
    @DrawableRes val icon: Int,
    val selected: Boolean,
    val device: BluetoothDevice? = null,
)

/**
 * The routes [state] supports, Bluetooth devices first. A null [AudioRoute.label] means the
 * route's usual name; [bluetoothName] gives a device's name, or null when it has none.
 */
fun audioRoutesOf(state: CallAudioState, bluetoothName: (BluetoothDevice) -> String?): List<AudioRoute> {
    val devices = state.supportedBluetoothDevices
    // The old sheet put each device on top of the previous one, so the last device came first.
    val bluetooth = devices.reversed().map { device ->
        AudioRoute(
            route = CallAudioState.ROUTE_BLUETOOTH,
            label = bluetoothName(device)?.takeIf { it.isNotEmpty() },
            icon = R.drawable.ic_bluetooth,
            selected = state.route == CallAudioState.ROUTE_BLUETOOTH &&
                (devices.size == 1 || device == state.activeBluetoothDevice),
            device = device,
        )
    }
    val others = listOf(
        CallAudioState.ROUTE_SPEAKER to R.drawable.ic_volume,
        CallAudioState.ROUTE_WIRED_HEADSET to R.drawable.ic_headphones,
        CallAudioState.ROUTE_EARPIECE to R.drawable.ic_phone,
    ).filter { (route, _) -> state.supportedRouteMask and route != 0 }
        .map { (route, icon) -> AudioRoute(route, null, icon, selected = state.route == route) }
    return bluetooth + others
}

/** The list of audio routes in the picker; tapping one picks it. */
@Composable
fun AudioRouteSheet(routes: List<AudioRoute>, onSelect: (AudioRoute) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(PvotBottomSheetDefaults.containerColor, PvotBottomSheetDefaults.Shape)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = PvotSpacing.S, vertical = PvotSpacing.M),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .padding(bottom = PvotSpacing.S)
                .size(PvotBottomSheetDefaults.HandleWidth, PvotBottomSheetDefaults.HandleHeight)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
        )
        for (route in routes) {
            PvotListRow(
                title = route.label ?: stringResource(defaultLabel(route.route)),
                modifier = Modifier.testTag(TestTags.audioRoute(route.route, route.label)),
                leading = { Icon(painterResource(route.icon), contentDescription = null) },
                trailing = if (route.selected) {
                    { Icon(painterResource(R.drawable.ic_check), contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
                } else null,
                selected = route.selected,
                onClick = { onSelect(route) },
            )
        }
    }
}

private fun defaultLabel(route: Int) = when (route) {
    CallAudioState.ROUTE_BLUETOOTH -> R.string.audioroute_bluetooth
    CallAudioState.ROUTE_SPEAKER -> R.string.audioroute_speaker
    CallAudioState.ROUTE_WIRED_HEADSET -> R.string.audioroute_headset
    else -> R.string.audioroute_phone
}

/** Shows picker for audio routes */
class AudioRouteSelectorDialogFragment : BottomSheetDialogFragment() {

    /** Called when an audio route is picked */
    interface AudioRouteSelectorPresenter {
        fun onAudioRouteSelected(audioRoute: Int)

        fun onAudioRouteSelectorDismiss()
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        FragmentUtils.checkParent(this, AudioRouteSelectorPresenter::class.java)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        LogUtil.i("AudioRouteSelectorDialogFragment.onCreateDialog", null)
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.window?.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        if (Settings.canDrawOverlays(requireContext())) {
            dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        }
        return dialog
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val state = BundleCompat.getParcelable(requireArguments(), ARG_AUDIO_STATE, CallAudioState::class.java)!!
        val routes = audioRoutesOf(state, BluetoothUtil::getAliasName)
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                PhoneScreenTheme(fillBackground = false) { AudioRouteSheet(routes, ::select) }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // The Compose sheet draws its own rounded surface.
        dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.setBackgroundColor(Color.TRANSPARENT)
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        presenter().onAudioRouteSelectorDismiss()
    }

    private fun select(route: AudioRoute) {
        presenter().onAudioRouteSelected(route.route)
        // Picking a Bluetooth device also makes it the active one.
        route.device?.let { TelecomAdapter.getInstance().requestBluetoothAudio(it) }
        dismiss()
    }

    private fun presenter() = FragmentUtils.getParentUnsafe(this, AudioRouteSelectorPresenter::class.java)

    companion object {
        const val TAG = "AudioRouteSelectorDialogFragment"
        private const val ARG_AUDIO_STATE = "audio_state"

        @JvmStatic
        fun newInstance(audioState: CallAudioState): AudioRouteSelectorDialogFragment =
            AudioRouteSelectorDialogFragment().apply {
                arguments = Bundle().apply { putParcelable(ARG_AUDIO_STATE, audioState) }
            }
    }
}
