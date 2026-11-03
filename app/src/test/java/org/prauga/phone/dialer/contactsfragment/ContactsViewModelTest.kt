/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.contactsfragment

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Loading, permission and empty states of the contacts tab. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ContactsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val sections = listOf(ContactSection("A", listOf(ContactEntry(1, "k", "Ada", null))))

    @Before fun setUp() = Dispatchers.setMain(dispatcher)

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(permitted: () -> Boolean = { true }, load: () -> List<ContactSection>) =
        ContactsViewModel(
            ApplicationProvider.getApplicationContext<Application>(),
            permitted,
            load,
            dispatcher,
        )

    @Test
    fun startsLoading() {
        assertThat(viewModel { sections }.state.value).isEqualTo(ContactsUiState.Loading)
    }

    @Test
    fun loadsContacts() {
        val vm = viewModel { sections }
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(ContactsUiState.Loaded(sections))
    }

    @Test
    fun noContactsIsEmpty() {
        val vm = viewModel { emptyList() }
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(ContactsUiState.Empty)
    }

    @Test
    fun missingPermissionAsksForIt() {
        var loads = 0
        val vm =
            viewModel(permitted = { false }) {
                loads++
                sections
            }
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(ContactsUiState.NeedsPermission)
        assertThat(loads).isEqualTo(0)
    }

    @Test
    fun revokedWhileLoadingAsksForIt() {
        val vm = viewModel { throw SecurityException("revoked") }
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(ContactsUiState.NeedsPermission)
    }

    @Test
    fun grantingThePermissionLoads() {
        var permitted = false
        val vm = viewModel(permitted = { permitted }) { sections }
        vm.refresh()
        permitted = true
        vm.refresh()
        assertThat(vm.state.value).isEqualTo(ContactsUiState.Loaded(sections))
    }
}
