/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.callstats

import android.content.Intent
import android.os.Bundle
import android.provider.CallLog.Calls
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.core.content.IntentCompat
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotGlassCard
import com.prauga.pvot.designsystem.components.list.PvotAvatarDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassDefaults
import com.prauga.pvot.designsystem.effects.PvotGlassMaterial
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.button.PvotButton
import com.prauga.pvot.designsystem.components.button.PvotButtonStyle
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.AccountSelectionActivity
import org.prauga.phone.dialer.callintent.CallInitiationType
import org.prauga.phone.dialer.callintent.CallIntentBuilder
import org.prauga.phone.dialer.clipboard.ClipboardUtils
import org.prauga.phone.dialer.contacts.ContactsComponent
import org.prauga.phone.dialer.lettertile.LetterTileDrawable
import org.prauga.phone.dialer.location.GeoUtil
import org.prauga.phone.dialer.phonenumbercache.ContactInfoHelper
import org.prauga.phone.dialer.phonenumberutil.PhoneNumberHelper
import org.prauga.phone.dialer.ui.PhoneBackground
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.QuickContactAvatar
import org.prauga.phone.dialer.ui.TestTags
import org.prauga.phone.dialer.util.CallUtil

/** Call statistics for one number, against all calls in the same time range. */
class CallStatsDetailActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        val details = IntentCompat.getParcelableExtra(intent, EXTRA_DETAILS, CallStatsDetails::class.java)
        val total = IntentCompat.getParcelableExtra(intent, EXTRA_TOTAL, CallStatsDetails::class.java)
        if (details == null || total == null) {
            finish()
            return
        }
        val from = intent.getLongExtra(EXTRA_FROM, -1)
        val to = intent.getLongExtra(EXTRA_TO, -1)
        val dateRange = if (from == -1L) null else DateUtils.formatDateRange(this, from, to, 0)
        val contactInfoHelper = ContactInfoHelper(this, GeoUtil.getCurrentCountryIso(this))
        val displayOrder = ContactsComponent.get(this).contactDisplayPreferences().displayOrder

        setPvotContent {
            PhoneScreenTheme {
                // Bumped once the contact lookup has filled in name and photo.
                var revision by remember { mutableIntStateOf(0) }
                LaunchedEffect(Unit) {
                    val info = withContext(Dispatchers.IO) { contactInfoHelper.lookupNumber(details.number, details.countryIso) }
                    if (info != null) {
                        details.updateFromInfo(info)
                        revision++
                    }
                }
                val header = remember(revision) {
                    details.updateDisplayProperties(this, displayOrder)
                    CallStatsHeader.from(this, details, contactInfoHelper.isBusiness(details.sourceType))
                }
                CallStatsDetailScreen(
                    header = header,
                    summary = CallStatsSummary(details, total),
                    dateRange = dateRange,
                    onBack = ::finish,
                    onCall = { startActivity(CallIntentBuilder(details.number, CallInitiationType.Type.CALL_LOG).build()) },
                    callWithIntent = AccountSelectionActivity.createIntent(this, details.number, CallInitiationType.Type.CALL_LOG),
                    onEditNumber = { startActivity(Intent(Intent.ACTION_DIAL, CallUtil.getCallUri(details.number))) },
                    onCopy = { ClipboardUtils.copyText(this, null, details.number, true) },
                    avatar = { size ->
                        QuickContactAvatar(
                            name = header.nameForPhoto,
                            contactUri = details.contactUri,
                            photoId = details.photoId,
                            photoUri = details.photoUri,
                            size = size,
                        )
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_DETAILS = "details"
        const val EXTRA_TOTAL = "total"
        const val EXTRA_FROM = "from"
        const val EXTRA_TO = "to"
    }
}

/** Who the statistics are for, and which actions apply to the number. */
internal data class CallStatsHeader(
    val title: String,
    val subtitle: String?,
    val nameForPhoto: String,
    val contactType: Int,
    val canCall: Boolean,
    val canEditBeforeCall: Boolean,
) {
    companion object {
        fun from(context: android.content.Context, details: CallStatsDetails, isBusiness: Boolean): CallStatsHeader {
            val canCall = PhoneNumberHelper.canPlaceCallsTo(details.number, details.numberPresentation)
            val hasName = !details.displayName.isNullOrEmpty()
            val locationOrType: CharSequence? =
                if (hasName) Phone.getTypeLabel(context.resources, details.numberType, details.numberLabel) else details.geocode
            return CallStatsHeader(
                title = if (hasName) details.displayName else details.displayNumber,
                subtitle = if (hasName) "$locationOrType ${details.displayNumber}" else locationOrType?.toString()?.ifEmpty { null },
                nameForPhoto = details.name?.ifEmpty { null } ?: details.displayNumber,
                contactType = when {
                    details.isVoicemailNumber -> LetterTileDrawable.TYPE_VOICEMAIL
                    isBusiness -> LetterTileDrawable.TYPE_BUSINESS
                    else -> LetterTileDrawable.TYPE_DEFAULT
                },
                canCall = canCall,
                canEditBeforeCall = canCall && !PhoneNumberHelper.isSipNumber(details.number) && !details.isVoicemailNumber,
            )
        }
    }
}

/** The statistics screen: who, what you can do, then durations, counts and averages. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun CallStatsDetailScreen(
    header: CallStatsHeader,
    summary: CallStatsSummary,
    dateRange: String?,
    onBack: () -> Unit,
    onCall: () -> Unit,
    callWithIntent: Intent?,
    onEditNumber: () -> Unit,
    onCopy: () -> Unit,
    avatar: @Composable (Dp) -> Unit,
) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    PhoneScaffold(
        background = PhoneBackground.Live,
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            PvotTopBar(
                title = stringResource(R.string.call_stats_detail_title),
                onBack = onBack,
                compact = rememberPvotTopBarCompact(listState),
            )
        },
    ) { padding ->
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.CALL_STATS_DETAIL),
            state = listState,
            contentPadding = PaddingValues(
                start = PvotScreenDefaults.HorizontalPadding,
                end = PvotScreenDefaults.HorizontalPadding,
                top = padding.calculateTopPadding() + PvotSpacing.S,
                bottom = PvotScreenDefaults.BottomSpacing + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
        ) {
            item(key = "header") {
                PvotGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = PvotGlassDefaults.materialStyle(PvotGlassMaterial.Regular),
                    contentPadding = PaddingValues(PvotSpacing.L),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS),
                    ) {
                        avatar(PvotAvatarDefaults.Hero)
                        Text(header.title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = PvotSpacing.S))
                        header.subtitle?.let {
                            Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center)
                        }
                        dateRange?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (header.canCall || header.canEditBeforeCall) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth().padding(top = PvotSpacing.L),
                            horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(PvotSpacing.S),
                        ) {
                            if (header.canCall) {
                                PvotButton(text = stringResource(R.string.call), onClick = onCall,
                                    icon = painterResource(R.drawable.ic_phone), modifier = Modifier.testTag(TestTags.CALL_STATS_CALL))
                                if (callWithIntent != null) {
                                    PvotButton(text = stringResource(R.string.pre_call_select_phone_account), style = PvotButtonStyle.Tonal,
                                        onClick = { context.startActivity(callWithIntent) })
                                }
                            }
                            if (header.canEditBeforeCall) {
                                PvotButton(text = stringResource(R.string.call_details_edit_number), style = PvotButtonStyle.Tonal, onClick = onEditNumber)
                            }
                            if (header.canCall) {
                                PvotButton(text = stringResource(R.string.call_details_copy_number), style = PvotButtonStyle.Text, onClick = onCopy)
                            }
                        }
                    }
                }
            }
            if (summary.totalDuration != 0L) {
                item(key = "durations") {
                    StatsSection(
                        title = stringResource(R.string.call_stats_title_durations),
                        forNumber = durationText(summary.totalDuration),
                        ofTotal = durationText(summary.allCallsDuration),
                        numberShares = summary.durationShares(summary.totalDuration),
                        totalShares = summary.durationShares(summary.allCallsDuration),
                        lines = summary.durations.map { it to durationText(it.amount) },
                    )
                }
            }
            item(key = "counts") {
                StatsSection(
                    title = stringResource(R.string.call_stats_title_count),
                    forNumber = countText(summary.totalCount.toLong()),
                    ofTotal = countText(summary.allCallsCount.toLong()),
                    numberShares = summary.countShares(summary.totalCount.toLong()),
                    totalShares = summary.countShares(summary.allCallsCount.toLong()),
                    lines = summary.counts.map { it to countText(it.amount) },
                )
            }
            if (summary.averages.isNotEmpty()) {
                item(key = "averages") {
                    StatsSection(
                        title = stringResource(R.string.call_stats_title_average_duration),
                        lines = summary.averages.map { it to durationText(it.amount) },
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsSection(
    title: String,
    lines: List<Pair<StatLine, String>>,
    forNumber: String? = null,
    ofTotal: String? = null,
    numberShares: List<StatShare> = emptyList(),
    totalShares: List<StatShare> = emptyList(),
) {
    PvotCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(PvotSpacing.L), verticalArrangement = Arrangement.spacedBy(PvotSpacing.M)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (forNumber != null && ofTotal != null) {
                ShareBar(stringResource(R.string.call_stats_title_for_number), forNumber, numberShares)
                ShareBar(stringResource(R.string.call_stats_title_of_total), ofTotal, totalShares)
            }
            for ((line, amount) in lines) {
                Row(horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
                    Spacer(Modifier.size(PvotSpacing.M).clip(CircleShape).background(colorFor(line.type)).align(Alignment.CenterVertically))
                    Text(stringResource(labelFor(line.type), amount), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    line.percent?.let {
                        Text(stringResource(R.string.call_stats_percent, it), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** A labelled bar split by call type, each part as wide as its share. */
@Composable
private fun ShareBar(label: String, amount: String, shares: List<StatShare>) {
    Column(verticalArrangement = Arrangement.spacedBy(PvotSpacing.XS)) {
        Row {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(amount, style = MaterialTheme.typography.bodySmall)
        }
        Row(
            Modifier.fillMaxWidth().height(PvotSpacing.S).clip(PvotShapes.Pill)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            for (share in shares.filter { it.fraction > 0f }) {
                Spacer(Modifier.weight(share.fraction).fillMaxSize().background(colorFor(share.type)))
            }
            val rest = 1f - shares.sumOf { it.fraction.toDouble() }.toFloat()
            if (rest > 0.0001f) Spacer(Modifier.weight(rest))
        }
    }
}

@Composable
private fun colorFor(type: Int): Color = when (type) {
    Calls.INCOMING_TYPE -> MaterialTheme.colorScheme.primary
    Calls.OUTGOING_TYPE -> MaterialTheme.colorScheme.tertiary
    Calls.MISSED_TYPE -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.outline
}

private fun labelFor(type: Int): Int = when (type) {
    Calls.INCOMING_TYPE -> R.string.call_stats_incoming
    Calls.OUTGOING_TYPE -> R.string.call_stats_outgoing
    Calls.MISSED_TYPE -> R.string.call_stats_missed
    else -> R.string.call_stats_blocked
}

@Composable
private fun durationText(seconds: Long): String =
    CallStatsFormat.duration(LocalContext.current, seconds, true)

@Composable
private fun countText(count: Long): String =
    CallStatsFormat.callCount(LocalContext.current, count)
