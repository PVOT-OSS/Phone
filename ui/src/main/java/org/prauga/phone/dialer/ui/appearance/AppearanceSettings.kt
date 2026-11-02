/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui.appearance

import android.content.Context
import android.content.SharedPreferences
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import org.prauga.phone.ui.R

/**
 * The user's appearance preferences: whether the floating layer is glass at all and how much, and
 * whether the colors come from the wallpaper.
 *
 * Stored in the default preferences beside the other settings. [preferredIntensity] is what the
 * root hands to PvotLib's `ProvidePvotGlassIntensity`, which still holds it to the device and drops
 * it to solid under battery saver.
 */
object AppearanceSettings {

    /** Everything the glass needs to know, read once per change. */
    data class Appearance(
        /** The amount of glass the user chose, 0 to 1. */
        val intensity: Float,
        /** Whether the floating layer is glass at all; off means solid surfaces. */
        val glass: Boolean,
        /** Whether the system's animations are off, which also makes the glass solid. */
        val animationsOff: Boolean,
        /** Whether the colors are the wallpaper's Material You colors rather than Phone's own. */
        val dynamicColor: Boolean = false,
    ) {
        /** What to ask PvotLib for: solid when glass is off or animations are, else [intensity]. */
        val preferredIntensity: Float
            get() = if (!glass || animationsOff) PvotGlassDefaults.SolidIntensity else intensity
    }

    /**
     * The device-protected copy of the default preferences, like the sound settings': the call
     * screen reads the appearance before the first unlock after a restart, when the usual
     * (credential-encrypted) preferences can't be opened.
     */
    fun prefs(context: Context): SharedPreferences {
        val storage = context.createDeviceProtectedStorageContext()
        val prefs = PreferenceManager.getDefaultSharedPreferences(storage)
        moveFromCredentialStorage(context, prefs)
        return prefs
    }

    /**
     * Brings over the choices made while they still lived in the credential-encrypted preferences.
     */
    private fun moveFromCredentialStorage(context: Context, target: SharedPreferences) {
        if (target.getBoolean(KEY_MOVED, false)) return
        val unlocked = context.getSystemService(UserManager::class.java)?.isUserUnlocked ?: true
        if (!unlocked) return
        val old = PreferenceManager.getDefaultSharedPreferences(context).all
        target.edit {
            for (id in MOVED_KEYS) {
                val key = context.getString(id)
                when (val value = old[key]) {
                    is Boolean -> if (!target.contains(key)) putBoolean(key, value)
                    is Float -> if (!target.contains(key)) putFloat(key, value)
                }
            }
            putBoolean(KEY_MOVED, true)
        }
    }

    private val MOVED_KEYS =
        listOf(
            R.string.appearance_glass_key,
            R.string.appearance_glass_intensity_key,
            R.string.appearance_dynamic_color_key,
        )

    private const val KEY_MOVED = "appearance_moved_to_device_storage"

    fun intensity(context: Context): Float =
        prefs(context)
            .getFloat(
                context.getString(R.string.appearance_glass_intensity_key),
                PvotGlassDefaults.defaultIntensity(),
            )
            .coerceIn(0f, 1f)

    fun isGlass(context: Context): Boolean =
        prefs(context).getBoolean(context.getString(R.string.appearance_glass_key), true)

    fun isDynamicColor(context: Context): Boolean =
        prefs(context).getBoolean(context.getString(R.string.appearance_dynamic_color_key), false)

    fun setIntensity(context: Context, value: Float) {
        prefs(context).edit {
            putFloat(
                context.getString(R.string.appearance_glass_intensity_key),
                value.coerceIn(0f, 1f),
            )
        }
    }

    fun setGlass(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(context.getString(R.string.appearance_glass_key), enabled)
        }
    }

    fun setDynamicColor(context: Context, enabled: Boolean) {
        prefs(context).edit {
            putBoolean(context.getString(R.string.appearance_dynamic_color_key), enabled)
        }
    }

    /** Whether the user turned animations off in the system settings. */
    fun animationsOff(context: Context): Boolean =
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f

    fun current(context: Context): Appearance =
        Appearance(
            intensity = intensity(context),
            glass = isGlass(context),
            animationsOff = animationsOff(context),
            dynamicColor = isDynamicColor(context),
        )

    /** The current [Appearance], kept up to date with the preferences and the animation setting. */
    @Composable
    fun rememberAppearance(): State<Appearance> {
        val context = LocalContext.current.applicationContext
        val state = remember(context) { mutableStateOf(current(context)) }
        DisposableEffect(context) {
            val prefs = prefs(context)
            val listener =
                SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
                    state.value = current(context)
                }
            prefs.registerOnSharedPreferenceChangeListener(listener)
            val observer =
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        state.value = current(context)
                    }
                }
            context.contentResolver.registerContentObserver(
                Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
                false,
                observer,
            )
            state.value = current(context)
            onDispose {
                prefs.unregisterOnSharedPreferenceChangeListener(listener)
                context.contentResolver.unregisterContentObserver(observer)
            }
        }
        return state
    }
}
