/*
 * Copyright (C) 2017 The Android Open Source Project
 * Copyright (C) 2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prauga.phone.dialer.contactsfragment

import android.Manifest.permission.READ_CONTACTS
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.common.FragmentUtils
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.util.DialerUtils
import org.prauga.phone.dialer.util.IntentUtil
import org.prauga.phone.dialer.util.PermissionsUtil

/** The contacts tab of the main screen: a list of every contact on the device. */
class ContactsFragment : Fragment() {

    private val viewModel: ContactsViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results[READ_CONTACTS] != true) return@registerForActivityResult
            // Tells this and every other screen that waits for the permission to reload.
            PermissionsUtil.notifyPermissionGranted(requireContext(), READ_CONTACTS)
        }

    /** Reloads when the contacts permission is granted from another screen. */
    private val permissionGrantedReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = viewModel.refresh()
        }

    private val actions =
        object : ContactsActions {
            override fun onContactClick(contact: ContactEntry) {
                FragmentUtils.getParentUnsafe(
                        this@ContactsFragment,
                        OnContactSelectedListener::class.java,
                    )
                    .onContactSelected(contact.contactUri, contact.id)
            }

            override fun onAddContact() {
                DialerUtils.startActivityWithErrorToast(
                    requireContext(),
                    IntentUtil.getNewContactIntent(),
                    R.string.add_contact_not_available,
                )
            }

            override fun onTurnOnPermission() {
                val denied =
                    PermissionsUtil.getPermissionsCurrentlyDenied(
                        requireContext(),
                        PermissionsUtil.allContactsGroupPermissionsUsedInDialer,
                    )
                if (denied.isNotEmpty()) {
                    LogUtil.i(
                        "ContactsFragment.onTurnOnPermission",
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
                // Scrolls reach the main screen, which shrinks the nav bar while a list scrolls up.
                PhoneScreenTheme(
                    modifier = Modifier.nestedScroll(rememberNestedScrollInteropConnection())
                ) {
                    ContactsScreen(
                        state = state,
                        showAddContact = requireArguments().getBoolean(ARG_SHOW_ADD_CONTACT),
                        actions = actions,
                    )
                }
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
        // Display and sort order may have changed in settings.
        viewModel.refresh()
    }

    override fun onStop() {
        PermissionsUtil.unregisterPermissionReceiver(requireActivity(), permissionGrantedReceiver)
        super.onStop()
    }

    /** Told when the user taps a contact. */
    fun interface OnContactSelectedListener {
        fun onContactSelected(contactUri: Uri, contactId: Long)
    }

    companion object {
        private const val ARG_SHOW_ADD_CONTACT = "show_add_contact"

        /** A contacts list, with a "Create new contact" row at the top if [showAddContact]. */
        @JvmStatic
        fun newInstance(showAddContact: Boolean): ContactsFragment =
            ContactsFragment().apply {
                arguments = bundleOf(ARG_SHOW_ADD_CONTACT to showAddContact)
            }
    }
}
