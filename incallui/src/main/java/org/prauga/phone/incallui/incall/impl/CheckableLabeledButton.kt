/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.incallui.incall.impl

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One slot of the in-call button grid, as Compose state. The [ButtonController]s drive it with the
 * same calls they made on the old button view; the grid composable draws it.
 *
 * A tap goes to the checked-change listener when there is one (asking for the opposite state; the
 * controller then sets it), and to the click listener otherwise, as the old view did.
 */
class CheckableLabeledButton(private val context: Context) {

    /** Called when the user taps a checkable button; [isChecked] is the state asked for. */
    fun interface OnCheckedChangeListener {
        fun onCheckedChanged(checkableLabeledButton: CheckableLabeledButton, isChecked: Boolean)
    }

    @get:DrawableRes
    var iconRes by mutableIntStateOf(0)
        private set

    var label by mutableStateOf<CharSequence>("")
        private set

    var description by mutableStateOf<CharSequence?>(null)
        private set

    var checkedState by mutableStateOf(false)
        private set

    var enabledState by mutableStateOf(true)
        private set

    var visible by mutableStateOf(true)
        private set

    var showMoreIndicator by mutableStateOf(false)
        private set

    /** Whether TalkBack skips the button, as while the dialpad covers the grid. */
    var hiddenFromAccessibility by mutableStateOf(false)

    private var clickListener: View.OnClickListener? = null
    private var checkedChangeListener: OnCheckedChangeListener? = null

    fun getContext(): Context = context

    fun getIconDrawable(): Drawable? = AppCompatResources.getDrawable(context, iconRes)

    fun setIconDrawable(@DrawableRes drawableRes: Int) {
        iconRes = drawableRes
    }

    fun setLabelText(@StringRes stringRes: Int) = setLabelText(context.getText(stringRes))

    fun setLabelText(label: CharSequence) {
        this.label = label
    }

    fun setContentDescription(description: CharSequence?) {
        this.description = description
    }

    fun isChecked(): Boolean = checkedState

    fun setChecked(checked: Boolean) {
        checkedState = checked
    }

    fun isEnabled(): Boolean = enabledState

    fun setEnabled(enabled: Boolean) {
        enabledState = enabled
    }

    /** [View.VISIBLE] shows the button; anything else leaves its slot empty. */
    fun setVisibility(visibility: Int) {
        visible = visibility == View.VISIBLE
    }

    fun setShouldShowMoreIndicator(shouldShow: Boolean) {
        showMoreIndicator = shouldShow
    }

    fun setOnClickListener(listener: View.OnClickListener?) {
        clickListener = listener
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        checkedChangeListener = listener
    }

    /** The user tapped the button. */
    fun performClick() {
        val checkedListener = checkedChangeListener
        if (checkedListener != null) {
            checkedListener.onCheckedChanged(this, !checkedState)
        } else {
            clickListener?.onClick(null)
        }
    }
}
