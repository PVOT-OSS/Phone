/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.common

import android.content.Context
import android.content.SharedPreferences
import android.telecom.PhoneAccountHandle

/**
 * Class that helps us store dialer preferences that are phone account dependent. This is necessary
 * for cases such as settings that are phone account dependent e.g endless vm. The logic is to
 * essentially store the shared preference by appending the phone account id to the key.
 */
open class PerAccountSharedPreferences {
    private val sharedPrefsKeyPrefix: String
    private val preferences: SharedPreferences
    private val phoneAccountHandle: PhoneAccountHandle

    constructor(context: Context, handle: PhoneAccountHandle, prefs: SharedPreferences) {
        preferences = prefs
        phoneAccountHandle = handle
        sharedPrefsKeyPrefix = "phone_account_dependent_"
    }

    /**
     * Not to be used, currently only used by {@VisualVoicemailPreferences} for legacy reasons.
     */
    protected constructor(
        context: Context,
        handle: PhoneAccountHandle,
        prefs: SharedPreferences,
        prefix: String,
    ) {
        Assert.checkArgument(prefix == "visual_voicemail_")
        preferences = prefs
        phoneAccountHandle = handle
        sharedPrefsKeyPrefix = prefix
    }

    inner class Editor internal constructor() {

        private val editor: SharedPreferences.Editor = preferences.edit()

        fun apply() {
            editor.apply()
        }

        fun putBoolean(key: String, value: Boolean): Editor {
            editor.putBoolean(getKey(key), value)
            return this
        }

        fun putFloat(key: String, value: Float): Editor {
            editor.putFloat(getKey(key), value)
            return this
        }

        fun putInt(key: String, value: Int): Editor {
            editor.putInt(getKey(key), value)
            return this
        }

        fun putLong(key: String, value: Long): Editor {
            editor.putLong(getKey(key), value)
            return this
        }

        fun putString(key: String, value: String?): Editor {
            editor.putString(getKey(key), value)
            return this
        }

        fun putStringSet(key: String, value: Set<String>?): Editor {
            editor.putStringSet(getKey(key), value)
            return this
        }
    }

    fun edit(): Editor = Editor()

    fun getBoolean(key: String, defValue: Boolean): Boolean = getValue(key, defValue)

    fun getFloat(key: String, defValue: Float): Float = getValue(key, defValue)

    fun getInt(key: String, defValue: Int): Int = getValue(key, defValue)

    fun getLong(key: String, defValue: Long): Long = getValue(key, defValue)

    fun getString(key: String, defValue: String?): String? = getValue(key, defValue)

    fun getString(key: String): String? = getValue(key, null)

    fun getStringSet(key: String, defValue: Set<String>?): Set<String>? = getValue(key, defValue)

    fun contains(key: String): Boolean = preferences.contains(getKey(key))

    private fun <T> getValue(key: String, defValue: T): T {
        if (!contains(key)) {
            return defValue
        }
        val `object` = preferences.all[getKey(key)] ?: return defValue
        @Suppress("UNCHECKED_CAST")
        return `object` as T
    }

    private fun getKey(key: String): String = sharedPrefsKeyPrefix + key + "_" + phoneAccountHandle.id
}
