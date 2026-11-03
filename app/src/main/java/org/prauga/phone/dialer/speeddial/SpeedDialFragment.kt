/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.speeddial

import android.Manifest.permission.READ_CONTACTS
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Contacts
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.speeddial.database.SpeedDialEntry.Channel
import org.prauga.phone.dialer.speeddial.loader.SpeedDialUiItem
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.util.IntentUtil
import org.prauga.phone.dialer.util.PermissionsUtil

/** The favorites tab of the main screen: starred contacts, then frequently called contacts. */
class SpeedDialFragment : Fragment() {

    private val viewModel: SpeedDialViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[READ_CONTACTS] == true) {
                PermissionsUtil.notifyPermissionGranted(requireContext(), READ_CONTACTS)
                viewModel.refresh()
            }
        }

    private val pickContactLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == Activity.RESULT_OK && uri != null) viewModel.star(uri)
        }

    /** Reloads when the contacts permission is granted from another screen. */
    private val permissionGrantedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = viewModel.refresh()
        }

    private val actions =
        object : SpeedDialActions {
            override fun call(channel: Channel, fromChooser: Boolean) {
                val type =
                    if (fromChooser) {
                        CallInitiationType.Type.SPEED_DIAL_DISAMBIG_DIALOG
                    } else {
                        CallInitiationType.Type.SPEED_DIAL
                    }
                PreCall.start(
                    requireContext(),
                    CallIntentBuilder(channel.number(), type)
                        .setAllowAssistedDial(true)
                        .setIsVideoCall(channel.isVideoTechnology),
                )
            }

            override fun message(number: String) =
                startActivity(IntentUtil.getSendSmsIntent(number))

            override fun openContact(item: SpeedDialUiItem) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.withAppendedPath(Contacts.CONTENT_URI, item.contactId().toString()),
                    )
                )
            }

            override fun addFavorite() =
                pickContactLauncher.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))

            override fun star(item: SpeedDialUiItem) {
                val number = item.defaultChannel()?.number() ?: return
                viewModel.star(Uri.withAppendedPath(Phone.CONTENT_FILTER_URI, number))
            }

            override fun remove(item: SpeedDialUiItem) = viewModel.remove(item)

            override fun move(from: Int, to: Int) = viewModel.moveFavorite(from, to)

            override fun setDefaultChannel(item: SpeedDialUiItem, channel: Channel) =
                viewModel.setDefaultChannel(item, channel)

            override fun openAppMenu() {
                FragmentUtils.getParent(this@SpeedDialFragment, HostInterface::class.java)
                    ?.openAppMenu()
            }

            override fun turnOnPermission() {
                val denied =
                    PermissionsUtil.getPermissionsCurrentlyDenied(
                        requireContext(),
                        PermissionsUtil.allContactsGroupPermissionsUsedInDialer,
                    )
                if (denied.isNotEmpty()) {
                    LogUtil.i(
                        "SpeedDialFragment.turnOnPermission",
                        "Requesting permissions: %s",
                        denied.joinToString(),
                    )
                    permissionLauncher.launch(denied)
                }
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                val state by viewModel.state.collectAsStateWithLifecycle()
                // The toolbar offers "Clear frequents" only while there are some.
                LaunchedEffect(state) {
                    val hasFrequents =
                        (state as? SpeedDialUiState.Loaded)?.suggestions?.isNotEmpty() == true
                    FragmentUtils.getParent(this@SpeedDialFragment, HostInterface::class.java)
                        ?.setHasFrequents(hasFrequents)
                }
                PhoneScreenTheme { SpeedDialScreen(state = state, actions = actions) }
            }
        }

    override fun onStart() {
        super.onStart()
        PermissionsUtil.registerPermissionReceiver(
            requireActivity(),
            permissionGrantedReceiver,
            READ_CONTACTS,
        )
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    override fun onPause() {
        super.onPause()
        viewModel.savePositions()
    }

    override fun onStop() {
        PermissionsUtil.unregisterPermissionReceiver(requireActivity(), permissionGrantedReceiver)
        super.onStop()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (hidden) viewModel.savePositions() else viewModel.refresh()
    }

    /** What the favorites tab needs from the screen that hosts it. */
    interface HostInterface {
        /** Whether there are frequent contacts, which the toolbar can clear. */
        fun setHasFrequents(hasFrequents: Boolean)

        /** Opens the app menu, from the avatar in the quick dial header. */
        fun openAppMenu()
    }

    companion object {
        @JvmStatic fun newInstance(): SpeedDialFragment = SpeedDialFragment()
    }
}
