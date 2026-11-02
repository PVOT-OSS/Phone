/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.prauga.pvot.designsystem.effects.ProvidePvotGlassIntensity
import com.prauga.pvot.designsystem.theme.PvotAppTheme
import com.prauga.pvot.designsystem.theme.PvotDarkColorScheme
import com.prauga.pvot.designsystem.theme.PvotLightColorScheme
import org.prauga.phone.dialer.ui.appearance.AppearanceSettings

/**
 * The Pvot theme and background a full Compose screen sits on, with test tags exposed as ids.
 *
 * It also provides the glass intensity from the user's Appearance setting, held to the device by
 * PvotLib (solid under battery saver), so every glass surface in the app reads one setting. A
 * screen that fills its window also paints the window in the theme's background, so the window
 * shown before the first frame and behind transitions matches the colors the user chose.
 *
 * @param fillBackground Whether to paint the background and fill the space; off for content that
 *   sizes itself or slides over another screen
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PhoneScreenTheme(
    fillBackground: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val appearance by AppearanceSettings.rememberAppearance()
    PhoneTheme {
        if (fillBackground) WindowBackground(MaterialTheme.colorScheme.background)
        ProvidePvotGlassIntensity(appearance.preferredIntensity) {
            Surface(
                modifier =
                    modifier
                        .then(if (fillBackground) Modifier.fillMaxSize() else Modifier)
                        .semantics { testTagsAsResourceId = true },
                color =
                    if (fillBackground) MaterialTheme.colorScheme.background else Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onBackground,
                content = content,
            )
        }
    }
}

/**
 * The Pvot theme in the colors the user chose in Appearance: Phone's own, the palette with its
 * secondary blue as the accent (selection, filled buttons, toggles, links), or the wallpaper's
 * Material You colors.
 */
@Composable
fun PhoneTheme(content: @Composable () -> Unit) {
    val appearance by AppearanceSettings.rememberAppearance()
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme =
        remember(context, appearance.dynamicColor, dark) {
            PhoneColors.scheme(context, appearance.dynamicColor, dark)
        }
    PvotAppTheme(
        darkTheme = dark,
        dynamicColor = appearance.dynamicColor,
        colorScheme = scheme,
        content = content,
    )
}

/**
 * Phone's color schemes outside Compose, for what views still draw (window backgrounds, the call
 * screen's background), so they match the Compose screens in either choice of colors.
 */
object PhoneColors {

    /**
     * The scheme for [dark]: the wallpaper's when [dynamicColor] is on, else the Pvot palette with
     * Phone's accent.
     */
    fun scheme(context: Context, dynamicColor: Boolean, dark: Boolean): ColorScheme =
        when {
            dynamicColor && dark -> dynamicDarkColorScheme(context)
            dynamicColor -> dynamicLightColorScheme(context)
            dark -> PvotDarkColorScheme.withSecondaryAccent()
            else -> PvotLightColorScheme.withSecondaryAccent()
        }

    /** The current scheme, as the user chose it and for the context's light or dark mode. */
    @JvmStatic
    fun scheme(context: Context): ColorScheme {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return scheme(
            context,
            AppearanceSettings.isDynamicColor(context),
            night == Configuration.UI_MODE_NIGHT_YES,
        )
    }

    /** The current scheme's background, as a color int. */
    @JvmStatic fun background(context: Context): Int = scheme(context).background.toArgb()
}

/**
 * Paints the activity's window in [color] when this composition fills that window. Skips dialogs
 * and see-through windows, which show what is behind them.
 */
@Composable
private fun WindowBackground(color: Color) {
    val view = LocalView.current
    SideEffect {
        if (view.isInEditMode) return@SideEffect
        val activity = view.context.findActivity() ?: return@SideEffect
        if (activity.window.decorView != view.rootView || activity.isTranslucent())
            return@SideEffect
        activity.window.setBackgroundDrawable(ColorDrawable(color.toArgb()))
    }
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

private fun Activity.isTranslucent(): Boolean {
    val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowIsTranslucent))
    return try {
        attrs.getBoolean(0, false)
    } finally {
        attrs.recycle()
    }
}

/** This scheme with its secondary roles copied into the primary ones. */
internal fun ColorScheme.withSecondaryAccent(): ColorScheme =
    copy(
        primary = secondary,
        onPrimary = onSecondary,
        primaryContainer = secondaryContainer,
        onPrimaryContainer = onSecondaryContainer,
        surfaceTint = secondary,
    )
