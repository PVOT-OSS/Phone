/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.contacts.common.dialog

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.provider.ContactsContract.QuickContact
import android.telecom.TelecomManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.core.os.BundleCompat
import androidx.preference.PreferenceManager
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotDialogDefaults
import com.prauga.pvot.designsystem.components.PvotSheetItem
import com.prauga.pvot.designsystem.components.textfield.PvotTextField
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.nio.charset.Charset
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.precall.PreCall
import org.prauga.phone.dialer.ui.ContactAvatar
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags

/** Lets the user type a note (call subject) to send with a call, then places the call. */
class CallSubjectDialog : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        val args = intent.extras ?: run {
            LogUtil.e("CallSubjectDialog.onCreate", "arguments cannot be null")
            finish()
            return
        }
        val number = args.getString(ARG_NUMBER).orEmpty()
        val account = BundleCompat.getParcelable(args, ARG_PHONE_ACCOUNT_HANDLE, PhoneAccountHandle::class.java)
        val limits = SubjectLimits.of(account?.let { getSystemService(TelecomManager::class.java).getPhoneAccount(it) })
        val prefs = PreferenceManager.getDefaultSharedPreferences(this)
        val nameOrNumber = args.getString(ARG_NAME_OR_NUMBER).orEmpty()
        val displayNumber = args.getString(ARG_DISPLAY_NUMBER)
        val label = args.getString(ARG_NUMBER_LABEL)
        val contactUri = BundleCompat.getParcelable(args, ARG_CONTACT_URI, Uri::class.java)

        val photoUri = BundleCompat.getParcelable(args, ARG_PHOTO_URI, Uri::class.java)

        setPvotContent {
            // Not filled: the window is see-through, and the dialog draws its own scrim over the app below.
            PhoneScreenTheme(fillBackground = false) {
                CallSubjectContent(
                    name = nameOrNumber,
                    number = when {
                        displayNumber.isNullOrEmpty() -> null
                        label.isNullOrEmpty() -> displayNumber
                        else -> getString(R.string.old_call_subject_type_and_number, label, displayNumber)
                    },
                    limits = limits,
                    history = remember { loadSubjectHistory(prefs) },
                    onSend = { subject ->
                        PreCall.start(this, CallIntentBuilder(number, CallInitiationType.Type.CALL_SUBJECT_DIALOG)
                            .setPhoneAccountHandle(account)
                            .setCallSubject(subject))
                        saveSubjectHistory(prefs, loadSubjectHistory(prefs) + subject)
                        finish()
                    },
                    onDismiss = ::finish,
                    photo = if (contactUri == null) null else { modifier ->
                        // Tapping the photo opens the contact's quick card, as the badge it replaces did.
                        val view = LocalView.current
                        ContactAvatar(
                            name = nameOrNumber,
                            photoUri = photoUri?.toString(),
                            colorKey = contactUri,
                            size = PvotDialogDefaults.IconCircleSize,
                            modifier = modifier
                                .clip(CircleShape)
                                .clickable(onClickLabel = getString(R.string.description_quick_contact_for, nameOrNumber)) {
                                    QuickContact.showQuickContact(this, view, contactUri, null, Phone.CONTENT_ITEM_TYPE)
                                },
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val PREF_KEY_SUBJECT_HISTORY_COUNT = "subject_history_count"
        const val PREF_KEY_SUBJECT_HISTORY_ITEM = "subject_history_item"
        const val ARG_PHOTO_ID = "PHOTO_ID"
        const val ARG_PHOTO_URI = "PHOTO_URI"
        const val ARG_CONTACT_URI = "CONTACT_URI"
        const val ARG_NAME_OR_NUMBER = "NAME_OR_NUMBER"
        const val ARG_NUMBER = "NUMBER"
        const val ARG_DISPLAY_NUMBER = "DISPLAY_NUMBER"
        const val ARG_NUMBER_LABEL = "NUMBER_LABEL"
        const val ARG_PHONE_ACCOUNT_HANDLE = "PHONE_ACCOUNT_HANDLE"
        const val ARG_CONTACT_TYPE = "CONTACT_TYPE"
        private const val HISTORY_SIZE = 5

        /** Asks for a note before calling [number], which has no contact details. */
        @JvmStatic
        fun start(activity: Activity, number: String) = start(
            activity, -1, null, null, number, number, null, null, LetterTileDrawable.TYPE_DEFAULT, null)

        /** Asks for a note before calling a contact's [number]. */
        @JvmStatic
        fun start(
            activity: Activity,
            photoId: Long,
            photoUri: Uri?,
            contactUri: Uri?,
            nameOrNumber: String?,
            number: String?,
            displayNumber: String?,
            numberLabel: String?,
            contactType: Int,
            phoneAccountHandle: PhoneAccountHandle?,
        ) = start(activity, Bundle().apply {
            putLong(ARG_PHOTO_ID, photoId)
            putParcelable(ARG_PHOTO_URI, photoUri)
            putParcelable(ARG_CONTACT_URI, contactUri)
            putString(ARG_NAME_OR_NUMBER, nameOrNumber)
            putString(ARG_NUMBER, number)
            putString(ARG_DISPLAY_NUMBER, displayNumber)
            putString(ARG_NUMBER_LABEL, numberLabel)
            putInt(ARG_CONTACT_TYPE, contactType)
            putParcelable(ARG_PHONE_ACCOUNT_HANDLE, phoneAccountHandle)
        })

        @JvmStatic
        fun start(activity: Activity, arguments: Bundle) {
            activity.startActivity(Intent(activity, CallSubjectDialog::class.java).putExtras(arguments))
        }

        /** The notes sent with recent calls, oldest first. */
        @JvmStatic
        fun loadSubjectHistory(prefs: SharedPreferences): List<String> =
            (0 until prefs.getInt(PREF_KEY_SUBJECT_HISTORY_COUNT, 0))
                .mapNotNull { prefs.getString(PREF_KEY_SUBJECT_HISTORY_ITEM + it, null)?.ifEmpty { null } }

        /** Keeps the last [HISTORY_SIZE] non-empty notes of [history]. */
        internal fun saveSubjectHistory(prefs: SharedPreferences, history: List<String>) {
            val kept = history.filter { it.isNotEmpty() }.takeLast(HISTORY_SIZE)
            prefs.edit {
                kept.forEachIndexed { index, subject -> putString(PREF_KEY_SUBJECT_HISTORY_ITEM + index, subject) }
                putInt(PREF_KEY_SUBJECT_HISTORY_COUNT, kept.size)
            }
        }
    }
}

/**
 * How long a note may be: the calling account may set a maximum and a character encoding to count
 * bytes in; otherwise 16 characters, counted one by one.
 */
internal data class SubjectLimits(val maxLength: Int, val encoding: Charset?) {

    /** The length of [subject] as the network counts it. */
    fun lengthOf(subject: String): Int = encoding?.let { subject.toByteArray(it).size } ?: subject.length

    companion object {
        const val DEFAULT_MAX_LENGTH = 16

        fun of(account: PhoneAccount?): SubjectLimits {
            val extras = account?.extras ?: return SubjectLimits(DEFAULT_MAX_LENGTH, null)
            val encoding = extras.getString(PhoneAccount.EXTRA_CALL_SUBJECT_CHARACTER_ENCODING)?.ifEmpty { null }?.let {
                runCatching { Charset.forName(it) }
                    .onFailure { _ -> LogUtil.e("CallSubjectDialog.SubjectLimits", "invalid charset: $it") }
                    .getOrNull()
            }
            return SubjectLimits(extras.getInt(PhoneAccount.EXTRA_CALL_SUBJECT_MAX_LENGTH, DEFAULT_MAX_LENGTH), encoding)
        }
    }
}

/** The note dialog: who is being called, the note with its length, recent notes, and send. */
@Composable
internal fun CallSubjectContent(
    name: String,
    number: String?,
    limits: SubjectLimits,
    history: List<String>,
    onSend: (String) -> Unit,
    onDismiss: () -> Unit,
    photo: (@Composable (Modifier) -> Unit)?,
) {
    var subject by rememberSaveable { mutableStateOf("") }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val length = limits.lengthOf(subject)
    PvotDialog(
        visible = true,
        onDismissRequest = onDismiss,
        title = name,
        message = number,
        // The photo fills the dialog's icon circle.
        icon = photo?.let { { it(Modifier) } },
        content = {
            Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
                // Shown above the field: text field labels are one line, and this hint is longer.
                Text(stringResource(R.string.call_subject_hint), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                PvotTextField(
                    value = subject,
                    // Characters past the limit are refused, as the old dialog's length filter did.
                    onValueChange = { if (it.length <= limits.maxLength) subject = it },
                    singleLine = true,
                    supportingText = stringResource(R.string.call_subject_limit, length.toString(), limits.maxLength.toString()),
                    isError = length >= limits.maxLength,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag(TestTags.CALL_SUBJECT_FIELD),
                    trailingIcon = if (history.isEmpty()) null else {
                        {
                            IconButton(onClick = { showHistory = !showHistory }, modifier = Modifier.testTag(TestTags.CALL_SUBJECT_HISTORY)) {
                                Icon(painterResource(R.drawable.ic_history), stringResource(R.string.call_subject_recent))
                            }
                        }
                    },
                )
                if (showHistory) {
                    for (recent in history.asReversed()) {
                        PvotSheetItem(title = recent, onClick = {
                            subject = recent.take(limits.maxLength)
                            showHistory = false
                        })
                    }
                }
            }
        },
    ) {
        PvotDialogAction(
            label = stringResource(R.string.send_and_call_button),
            style = PvotDialogActionStyle.Primary,
            onClick = { onSend(subject) },
            modifier = Modifier.testTag(TestTags.CALL_SUBJECT_SEND),
        )
        PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = onDismiss)
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}
