/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
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
            val info = context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
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
            if (context.packageManager.getApplicationEnabledSetting(packageName) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            ) {
                return true
            }
        }
        return false
    }
}
