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
 * limitations under the License
 */

package org.prauga.phone.dialer.common

import android.content.Context
import android.content.pm.PackageManager

/** Utility class for package management. */
object PackageUtils {

    private fun isPackageInstalled(packageName: String, context: Context): Boolean {
        Assert.isNotNull(packageName)
        Assert.isNotNull(context)
        try {
            val info =
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0),
                )
            if (info.packageName != null) {
                LogUtil.d("PackageUtils.isPackageInstalled", "$packageName is found")
                return true
            }
        } catch (e: PackageManager.NameNotFoundException) {
            LogUtil.d("PackageUtils.isPackageInstalled", "$packageName is NOT found")
        }
        return false
    }

    /** Returns true if the pkg is installed and enabled/default */
    @JvmStatic
    fun isPackageEnabled(packageName: String, context: Context): Boolean {
        Assert.isNotNull(packageName)
        Assert.isNotNull(context)
        if (isPackageInstalled(packageName, context)) {
            if (
                context.packageManager.getApplicationEnabledSetting(packageName) !=
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                return true
            }
        }
        return false
    }
}
