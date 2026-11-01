/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

/** What the Recents and Voicemail tabs need from the main screen. */
interface CallLogHost {
    /** Opens the dialpad. */
    fun showDialpad()
}

/** Told about changes the main screen reflects outside the tab. */
interface CallLogListener {
    /** Recounts unread missed calls and voicemails, for the nav bar badges. */
    fun updateTabUnreadCounts()

    /**
     * Voicemails are being selected, or not any more; the main screen hides its bars meanwhile and
     * may end the selection with [exitSelection], for example when the dialpad opens.
     */
    fun onSelectionModeChanged(enabled: Boolean, exitSelection: Runnable)
}
