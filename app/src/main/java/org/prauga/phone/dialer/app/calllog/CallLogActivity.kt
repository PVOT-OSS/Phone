/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.app.calllog

import android.Manifest.permission.READ_CALL_LOG
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.pvotGlassSurface
import com.prauga.pvot.designsystem.effects.LocalPvotGlassBackdrop
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassMaterial
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarDefaults
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotSpacing
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.callstats.CallStatsActions
import org.prauga.phone.dialer.callstats.CallStatsDetailActivity
import org.prauga.phone.dialer.callstats.CallStatsDetails
import org.prauga.phone.dialer.callstats.CallStatsEntry
import org.prauga.phone.dialer.callstats.CallStatsListScreen
import org.prauga.phone.dialer.callstats.CallStatsListViewModel
import org.prauga.phone.dialer.clipboard.ClipboardUtils
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil
import org.prauga.phone.dialer.util.DialerUtils

/** The call statistics screen, opened from the main screen's menu. */
class CallLogActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePvotEdgeToEdge()
        setPvotContent { PhoneScreenTheme { CallStatsContent(onBack = ::finish) } }
    }
}

@Composable
private fun CallStatsContent(onBack: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val stats: CallStatsListViewModel =
        viewModel(
            factory = viewModelFactory { initializer { CallStatsListViewModel(application) } }
        )
    PhoneScaffold(topBar = { CallStatsTopBar(onBack = onBack) }) { padding ->
        // The list scrolls under the glass bar, so it frosts the rows as they pass.
        CallStatsRoute(stats, Modifier.fillMaxSize(), padding.calculateTopPadding())
    }
}

/** The statistics' top bar: back and the title, on glass the list scrolls under. */
@Composable
internal fun CallStatsTopBar(onBack: () -> Unit) {
    PvotTopBar(
        title = stringResource(R.string.call_log_stats_title),
        onBack = onBack,
        compact = true,
        backdrop = null,
        colors = PvotTopBarDefaults.glassColors(),
        modifier =
            Modifier.fillMaxWidth()
                .pvotGlassSurface(
                    backdrop = LocalPvotGlassBackdrop.current,
                    cornerRadius = 0.dp,
                    style = PvotGlassDefaults.materialStyle(PvotGlassMaterial.Thick),
                    rim = false,
                    role = null,
                ),
    )
}

/**
 * Picks the statistics' time range: Material's date range picker, the one Material component kept,
 * in a Pvot dialog. Days come back as the picker's UTC midnights.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CallStatsRangeDialog(
    visible: Boolean,
    initialStartUtcDay: Long?,
    initialEndUtcDay: Long?,
    onDismiss: () -> Unit,
    onPick: (startUtcDay: Long, endUtcDay: Long) -> Unit,
) {
    val pickerState =
        rememberDateRangePickerState(
            initialSelectedStartDateMillis = initialStartUtcDay,
            initialSelectedEndDateMillis = initialEndUtcDay,
        )
    val dateFormatter = remember { DatePickerDefaults.dateFormatter() }
    val locale = LocalConfiguration.current.locales[0]
    PvotDialog(
        visible = visible,
        onDismissRequest = onDismiss,
        title = null,
        content = {
            DateRangePicker(
                state = pickerState,
                modifier =
                    Modifier.bleedToDialogEdges()
                        .height(RangePickerHeight)
                        .testTag(TestTags.CALL_STATS_RANGE_PICKER),
                // The dialog is the surface; a second fill would draw a box inside it.
                colors = DatePickerDefaults.colors(containerColor = Color.Transparent),
                // The picker's own title and heading are sized and indented for a full screen and
                // wrap in a dialog. The heading alone says what to pick, at the dialog's indent, so
                // a whole month and both actions fit.
                title = null,
                headline = {
                    val start =
                        pickerState.selectedStartDateMillis?.let {
                            dateFormatter.formatDate(it, locale)
                        }
                    val end =
                        pickerState.selectedEndDateMillis?.let {
                            dateFormatter.formatDate(it, locale)
                        }
                    Text(
                        text =
                            stringResource(
                                R.string.call_stats_range_heading,
                                start ?: stringResource(R.string.call_stats_range_start),
                                end ?: stringResource(R.string.call_stats_range_end),
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(RangePickerHeadlinePadding),
                    )
                },
            )
        },
    ) {
        PvotDialogAction(
            label = stringResource(android.R.string.ok),
            style = PvotDialogActionStyle.Primary,
            onClick = {
                val start = pickerState.selectedStartDateMillis
                if (start != null) onPick(start, pickerState.selectedEndDateMillis ?: start)
                else onDismiss()
            },
        )
        PvotDialogAction(label = stringResource(android.R.string.cancel), onClick = onDismiss)
    }
}

/** Tall enough for a month of the picker; the dialog keeps its actions under it. */
private val RangePickerHeight = 384.dp

/** The dialog's indent for the picked range. */
private val RangePickerHeadlinePadding =
    PaddingValues(start = 24.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)

/** The padding inside a Pvot dialog's card, which the picker spans so a week fits across. */
private val DialogContentPadding = PvotSpacing.L * 1.5f

/** Lays the content out across the dialog's own padding, out to the card's edges. */
private fun Modifier.bleedToDialogEdges(): Modifier = layout { measurable, constraints ->
    val bleed = DialogContentPadding.roundToPx()
    val width = constraints.maxWidth + bleed * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-bleed, 0) }
}

@Composable
private fun CallStatsRoute(viewModel: CallStatsListViewModel, modifier: Modifier, topPadding: Dp) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val version by viewModel.contactInfoVersion.collectAsStateWithLifecycle()
    var pickingRange by remember { mutableStateOf(false) }
    val displayOrder = remember {
        ContactsComponent.get(context).contactDisplayPreferences().displayOrder
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) viewModel.refresh()
        }

    LifecycleResumeEffect(viewModel) {
        viewModel.start()
        viewModel.refresh()
        onPauseOrDispose { viewModel.stop() }
    }

    val actions =
        remember(viewModel) {
            object : CallStatsActions {
                override fun open(entry: CallStatsEntry, total: CallStatsDetails) {
                    context.startActivity(
                        Intent(context, CallStatsDetailActivity::class.java)
                            .putExtra(CallStatsDetailActivity.EXTRA_DETAILS, entry.details)
                            .putExtra(CallStatsDetailActivity.EXTRA_TOTAL, total)
                            .putExtra(
                                CallStatsDetailActivity.EXTRA_FROM,
                                viewModel.filter.value.from,
                            )
                            .putExtra(CallStatsDetailActivity.EXTRA_TO, viewModel.filter.value.to)
                    )
                }

                override fun copyNumber(number: String) =
                    ClipboardUtils.copyText(context, null, number, true)

                override fun editBeforeCall(number: String) =
                    DialerUtils.startActivityWithErrorToast(
                        context,
                        Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(number)),
                    )

                override fun pickRange() {
                    pickingRange = true
                }

                override fun clearRange() = viewModel.setRange(-1, -1)

                override fun sortByDuration(byDuration: Boolean) =
                    viewModel.setSortByDuration(byDuration)

                override fun turnOnPermission() = permissionLauncher.launch(READ_CALL_LOG)
            }
        }

    CallStatsListScreen(
        state = state,
        filter = filter,
        prepare = { entry ->
            version.let {
                val details = entry.details
                if (
                    PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation) &&
                        !details.isVoicemailNumber
                ) {
                    viewModel.contactInfoCache
                        .getValue(
                            details.number + details.postDialDigits,
                            details.countryIso,
                            entry.cachedInfo,
                            false,
                        )
                        ?.let(details::updateFromInfo)
                }
                details.updateDisplayProperties(context, displayOrder)
                details
            }
        },
        actions = actions,
        modifier = modifier,
        topPadding = topPadding,
    )

    if (pickingRange) {
        CallStatsRangeDialog(
            visible = true,
            initialStartUtcDay = filter.from.takeIf { it != -1L }?.let(::localToUtcDay),
            initialEndUtcDay = filter.to.takeIf { it != -1L }?.let(::localToUtcDay),
            onDismiss = { pickingRange = false },
            onPick = { start, end ->
                viewModel.setRange(startOfLocalDay(start), startOfLocalDay(end) + DAY_MILLIS - 1)
                pickingRange = false
            },
        )
    }
}

private const val DAY_MILLIS = 24 * 60 * 60 * 1000L

/** The picker's UTC midnight for a day, as the start of that day in the phone's time zone. */
private fun startOfLocalDay(utcDayMillis: Long): Long =
    Instant.ofEpochMilli(utcDayMillis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/** A local time, as the picker's UTC midnight of its day. */
private fun localToUtcDay(localMillis: Long): Long =
    Instant.ofEpochMilli(localMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
