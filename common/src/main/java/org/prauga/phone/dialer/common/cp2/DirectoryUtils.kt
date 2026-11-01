/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package org.prauga.phone.dialer.common.cp2

import android.provider.ContactsContract.Directory

/** Utilities for [Directory]. */
object DirectoryUtils {

    /** Returns true if the given ID belongs to an invisible directory. */
    @JvmStatic
    fun isInvisibleDirectoryId(directoryId: Long): Boolean =
        directoryId == Directory.LOCAL_INVISIBLE ||
            directoryId == Directory.ENTERPRISE_LOCAL_INVISIBLE

    /** Returns true if the given ID belongs to a local enterprise directory. */
    @JvmStatic
    fun isLocalEnterpriseDirectoryId(directoryId: Long): Boolean =
        Directory.isEnterpriseDirectoryId(directoryId) &&
            !Directory.isRemoteDirectoryId(directoryId)
}
