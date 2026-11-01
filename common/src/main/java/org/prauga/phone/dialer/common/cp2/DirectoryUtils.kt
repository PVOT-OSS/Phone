/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common.cp2

import android.provider.ContactsContract.Directory

/** Utilities for [Directory]. */
object DirectoryUtils {

    /** Returns true if the given ID belongs to an invisible directory. */
    @JvmStatic
    fun isInvisibleDirectoryId(directoryId: Long): Boolean =
        directoryId == Directory.LOCAL_INVISIBLE || directoryId == Directory.ENTERPRISE_LOCAL_INVISIBLE

    /** Returns true if the given ID belongs to a local enterprise directory. */
    @JvmStatic
    fun isLocalEnterpriseDirectoryId(directoryId: Long): Boolean =
        Directory.isEnterpriseDirectoryId(directoryId) && !Directory.isRemoteDirectoryId(directoryId)
}
