/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.net.toUri
import android.provider.CallLog.Calls
import android.telephony.TelephonyManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.prauga.phone.contacts.common.dialog.CallSubjectDialog
import com.prauga.pvot.designsystem.theme.PvotSpacing
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.calllog.calllogcache.CallLogCache
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackPresenter
import org.prauga.phone.dialer.app.voicemail.VoicemailPlaybackState
import org.prauga.phone.dialer.app.voicemail.VoicemailStatusCard
import org.prauga.phone.dialer.app.voicemail.VoicemailTermsScreen
import org.prauga.phone.dialer.voicemail.listui.error.VoicemailErrorMessage
import org.prauga.phone.dialer.calldetails.OldCallDetailsActivity
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.database.CallLogQueryHandler
import org.prauga.phone.dialer.dialercontact.DialerContact
import org.prauga.phone.dialer.dialercontact.SimDetails
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.util.DialerUtils
import org.prauga.phone.dialer.util.PermissionsUtil
import org.prauga.phone.dialer.util.UriUtils

/**
 * A call history list wired to the phone: it loads through [viewModel] while shown and turns the
 * user's actions into the same calls, messages and screens the call history has always opened.
 *
 * @param showEmptyAction Whether an empty list offers "Make a call"
 * @param onShowDialpad Opens the dialpad, for that action
 * @param onMissedCallRead Called after an opened missed call is marked as read
 */
@Composable
fun CallHistoryRoute(
    viewModel: CallHistoryViewModel,
    callType: Int,
    showEmptyAction: Boolean,
    onShowDialpad: () -> Unit,
    onMissedCallRead: () -> Unit,
    modifier: Modifier = Modifier,
    topPadding: Dp = 0.dp,
    bottomPadding: Dp? = null,
    voicemail: VoicemailParts? = null,
    onSelectionModeChanged: (enabled: Boolean, exit: () -> Unit) -> Unit = { _, _ -> },
    filter: CallLogFilter = CallLogFilter.ALL,
) {
    val context = LocalContext.current
    val loadedState by viewModel.state.collectAsStateWithLifecycle()
    // Contacts and Blocked narrow the loaded rows; All and Missed come from the query.
    val state = loadedState.let { loaded ->
        if (loaded is CallHistoryUiState.Loaded && filter.narrows) {
            loaded.copy(groups = loaded.groups.filter { filter.matches(it, loaded.blockedNumbers) })
        } else {
            loaded
        }
    }
    val currentOnShowDialpad by rememberUpdatedState(onShowDialpad)
    val currentOnMissedCallRead by rememberUpdatedState(onMissedCallRead)
    val currentOnSelectionModeChanged by rememberUpdatedState(onSelectionModeChanged)
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it.values.firstOrNull() == true) viewModel.refresh()
    }

    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        viewModel.resumed()
        onPauseOrDispose { viewModel.stop() }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val resources = LocalResources.current
    LaunchedEffect(viewModel) {
        viewModel.errors.collect { message -> snackbarHostState.showSnackbar(resources.getString(message)) }
    }

    val actions = remember(viewModel, context) {
        CallHistoryIntents(
            context = context,
            viewModel = viewModel,
            requestPermissions = permissionLauncher::launch,
            showDialpad = { currentOnShowDialpad() },
            missedCallRead = { currentOnMissedCallRead() },
            selectionModeChanged = { enabled, exit -> currentOnSelectionModeChanged(enabled, exit) },
            voicemail = voicemail,
        )
    }
    val statusMessage = voicemail?.statusMessage

    CallHistoryScreen(
        state = state,
        emptyMessage = when (callType) {
            Calls.MISSED_TYPE -> R.string.call_log_missed_empty
            Calls.VOICEMAIL_TYPE -> R.string.call_log_voicemail_empty
            else -> R.string.call_log_all_empty
        },
        showEmptyAction = showEmptyAction,
        actions = actions,
        modifier = modifier,
        topPadding = topPadding,
        bottomPadding = bottomPadding ?: (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + PvotSpacing.L),
        emptyIcon = if (voicemail != null) R.drawable.ic_call_voicemail else R.drawable.ic_history,
        header = statusMessage?.takeUnless { it.isModal }?.let { message -> { VoicemailStatusCard(message) } },
        fullScreen = statusMessage?.takeIf { it.isModal }?.let { message -> { VoicemailTermsScreen(message) } },
        voicemailPlayback = voicemail?.playbackState,
        selectable = true,
        snackbarHostState = snackbarHostState,
    )
}

/** The call history's actions, as intents, dialogs and view model calls. */
private class CallHistoryIntents(
    private val context: Context,
    private val viewModel: CallHistoryViewModel,
    private val requestPermissions: (Array<String>) -> Unit,
    private val showDialpad: () -> Unit,
    private val missedCallRead: () -> Unit,
    private val selectionModeChanged: (Boolean, () -> Unit) -> Unit,
    private val voicemail: VoicemailParts?,
) : CallHistoryActions {

    private val callLogCache = CallLogCache(context)

    override fun primaryAction(row: CallLogRowUi) {
        CallLogAsyncTaskUtil.markCallAsRead(context, row.group.callIds.toLongArray())
        val provider = when (row.primaryAction) {
            PrimaryAction.VIDEO_CALL -> IntentProvider.getReturnVideoCallIntentProvider(row.group.number, row.accountHandle)
            PrimaryAction.CALL_VOICEMAIL -> IntentProvider.getReturnVoicemailCallIntentProvider()
            PrimaryAction.CALL -> callProvider(row, row.group.number + row.group.postDialDigits)
            PrimaryAction.PLAY_VOICEMAIL, PrimaryAction.NONE -> return
        }
        start(provider)
    }

    override fun rowAction(row: CallLogRowUi, action: RowAction) {
        val number = row.group.number
        when (action) {
            RowAction.CALL -> start(callProvider(row, number))
            RowAction.VIDEO_CALL -> start(IntentProvider.getReturnVideoCallIntentProvider(number))
            RowAction.SEND_MESSAGE -> start(IntentProvider.getSendSmsIntentProvider(number))
            RowAction.CALL_WITH_NOTE -> context.findActivity()?.let { activity ->
                CallSubjectDialog.start(
                    activity,
                    row.info.photoId,
                    row.info.photoUri,
                    row.info.lookupUri,
                    row.title,
                    number,
                    if (row.info.name.isNullOrEmpty()) null else row.displayNumber,
                    row.numberType,
                    contactType(row),
                    row.accountHandle,
                )
            }
            RowAction.CREATE_CONTACT, RowAction.ADD_TO_CONTACT -> start(
                IntentProvider.getAddContactIntentProvider(
                    row.info.lookupUri, row.info.name, row.info.number, row.info.type,
                    action == RowAction.CREATE_CONTACT,
                ),
            )
            RowAction.DETAILS -> start(
                IntentProvider.getCallDetailIntentProvider(
                    row.group.entries,
                    dialerContact(row),
                    // Assisted dialing needs a contact, and is never offered for emergency numbers.
                    row.info.lookupKey != null && !row.isEmergency,
                ),
            )
            RowAction.BLOCK, RowAction.UNBLOCK, RowAction.DELETE -> Unit
        }
    }

    override fun opened(row: CallLogRowUi, playNow: Boolean) {
        val voicemailUri = row.group.voicemailUri
        if (voicemail != null && voicemailUri != null) {
            val uri = voicemailUri.toUri()
            voicemail.presenter.resetAll()
            voicemail.presenter.setPlaybackView(
                voicemail.playbackState, row.group.rowId, uri, playNow, voicemail.playbackState::showShare)
            CallLogAsyncTaskUtil.markVoicemailAsRead(context, uri)
            missedCallRead()
            return
        }
        if (row.group.callType != Calls.MISSED_TYPE) return
        CallLogAsyncTaskUtil.markCallAsRead(context, row.group.callIds.toLongArray())
        missedCallRead()
    }

    override fun closed(row: CallLogRowUi) {
        voicemail?.presenter?.resetAll()
    }

    override fun deleteSelected(groups: List<CallLogGroup>) = viewModel.deleteNow(groups)

    override fun selectionModeChanged(enabled: Boolean, exit: () -> Unit) = selectionModeChanged.invoke(enabled, exit)

    override fun deleteLater(group: CallLogGroup) = viewModel.deleteLater(group)
    override fun undoDelete(group: CallLogGroup) = viewModel.undoDelete(group)
    override fun block(row: CallLogRowUi) = viewModel.block(row.group.number, row.group.countryIso)
    override fun unblock(row: CallLogRowUi) = viewModel.unblock(row.group.number)

    override fun turnOnPermission() {
        val denied = PermissionsUtil.getPermissionsCurrentlyDenied(
            context, PermissionsUtil.allPhoneGroupPermissionsUsedInDialer)
        if (denied.isNotEmpty()) {
            LogUtil.i("CallHistoryIntents.turnOnPermission", "Requesting permissions: %s", denied.joinToString())
            requestPermissions(denied)
        }
    }

    override fun emptyAction() = showDialpad()

    private fun callProvider(row: CallLogRowUi, number: String): IntentProvider =
        if (row.info.lookupKey != null) {
            IntentProvider.getAssistedDialIntentProvider(number, context, context.getSystemService(TelephonyManager::class.java))
        } else {
            IntentProvider.getReturnCallIntentProvider(number)
        }

    private fun start(provider: IntentProvider) {
        val intent: Intent = provider.getClickIntent(context) ?: return
        if (OldCallDetailsActivity.isLaunchIntent(intent)) {
            context.startActivity(intent)
        } else {
            DialerUtils.startActivityWithErrorToast(context, intent)
        }
    }

    private fun contactType(row: CallLogRowUi): Int = LetterTileDrawable.getContactTypeFromPrimitives(
        callLogCache.isVoicemailNumber(row.accountHandle, row.group.number),
        /* isSpam= */ false,
        /* isBusiness= */ false,
        row.group.presentation,
        /* isConference= */ false,
    )

    private fun dialerContact(row: CallLogRowUi): DialerContact {
        val info = row.info
        val contact = DialerContact.newBuilder()
            .setPhotoId(info.photoId)
            .setNameOrNumber(row.title)
            .setContactType(contactType(row))
            .setNumber(row.group.number)
            .setNumberLabel(row.numberType)
        info.photoUri?.let { contact.setPhotoUri(it.toString()) }
        info.lookupUri?.let { contact.setContactUri(it.toString()) }
        if (row.group.postDialDigits.isNotEmpty()) contact.setPostDialDigits(row.group.postDialDigits)
        if (!info.name.isNullOrEmpty()) contact.setDisplayNumber(row.displayNumber)
        val accountLabel = callLogCache.getAccountLabel(row.accountHandle)
        if (!accountLabel.isNullOrEmpty()) {
            contact.setSimDetails(
                SimDetails.newBuilder().setNetwork(accountLabel).setColor(callLogCache.getAccountColor(row.accountHandle)),
            )
        }
        return contact.build()
    }
}

/** What the voicemail tab adds to a call history list. */
class VoicemailParts(
    val presenter: VoicemailPlaybackPresenter,
    val playbackState: VoicemailPlaybackState,
    val statusMessage: VoicemailErrorMessage?,
)

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Whether a list of [callType] calls offers "Make a call" when empty, on the main screen only. */
internal fun showsEmptyAction(callType: Int, isCallLogActivity: Boolean) =
    !isCallLogActivity && callType == CallLogQueryHandler.CALL_TYPE_ALL

/** Which calls a call history list shows. */
enum class CallLogFilter {
    /** Every call but voicemail. */
    ALL,

    /** Missed and rejected calls; the list's view model queries these. */
    MISSED,

    /** Calls with someone in the contacts. */
    CONTACTS,

    /** Calls from numbers that are blocked, or that were blocked. */
    BLOCKED;

    /** Whether this filter narrows the loaded rows, rather than the query choosing them. */
    val narrows: Boolean get() = this == CONTACTS || this == BLOCKED

    /** Whether [group] shows under this filter, given the [blockedNumbers]. */
    fun matches(group: CallLogGroup, blockedNumbers: Set<String>): Boolean = when (this) {
        ALL, MISSED -> true
        // A number that is not a contact can still cache an "encoded" lookup URI, a temporary contact.
        CONTACTS -> !group.cachedInfo.lookupKey.isNullOrEmpty() ||
            (group.cachedInfo.lookupUri != null && !UriUtils.isEncodedContactUri(group.cachedInfo.lookupUri))
        BLOCKED -> Calls.BLOCKED_TYPE in group.callTypes || group.number in blockedNumbers
    }
}
