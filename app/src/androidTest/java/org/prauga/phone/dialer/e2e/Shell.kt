/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.e2e

import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/** Runs shell commands as the shell user, the way adb would. */
object Shell {

    fun run(command: String): String {
        val fd =
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return ParcelFileDescriptor.AutoCloseInputStream(fd)
            .bufferedReader()
            .use { it.readText() }
            .trim()
    }
}
