/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.rtt

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.core.net.toUri
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.list.PvotAvatarDefaults
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import org.prauga.phone.dialer.common.Assert
import org.prauga.phone.dialer.common.LogUtil
import org.prauga.phone.dialer.glidephotomanager.PhotoInfo
import org.prauga.phone.dialer.protos.ProtoParsers
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.QuickContactAvatar
import org.prauga.phone.dialer.ui.TestTags

/** Shows the saved text of an RTT call, opened from call details. */
class RttTranscriptActivity : ComponentActivity() {

    private var title by mutableStateOf("")
    private var photoInfo by mutableStateOf(PhotoInfo.getDefaultInstance())
    private var messages by mutableStateOf<List<RttTranscriptMessage>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enablePvotEdgeToEdge()
        setPvotContent {
            PhoneScreenTheme {
                RttTranscriptScreen(title, photoInfo, messages, onBack = ::finish)
            }
        }
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        Assert.checkArgument(intent.hasExtra(EXTRA_TRANSCRIPT_ID))
        Assert.checkArgument(intent.hasExtra(EXTRA_PRIMARY_TEXT))
        Assert.checkArgument(intent.hasExtra(EXTRA_PHOTO_INFO))
        title = intent.getStringExtra(EXTRA_PRIMARY_TEXT).orEmpty()
        photoInfo = ProtoParsers.getTrusted(intent, EXTRA_PHOTO_INFO, PhotoInfo.getDefaultInstance())
        val id = intent.getStringExtra(EXTRA_TRANSCRIPT_ID)
        lifecycleScope.launch {
            val transcript = RttTranscriptUtil.loadRttTranscript(this@RttTranscriptActivity, id).await()
            if (transcript == null) {
                LogUtil.w("RttTranscriptActivity.handleIntent", "null RttTranscript")
            } else {
                messages = transcript.messagesList
            }
        }
    }

    companion object {
        const val EXTRA_TRANSCRIPT_ID = "extra_transcript_id"
        const val EXTRA_PRIMARY_TEXT = "extra_primary_text"
        const val EXTRA_PHOTO_INFO = "extra_photo_info"

        @JvmStatic
        fun getIntent(context: Context, transcriptId: String, primaryText: String, photoInfo: PhotoInfo): Intent =
            Intent(context, RttTranscriptActivity::class.java)
                .putExtra(EXTRA_TRANSCRIPT_ID, transcriptId)
                .putExtra(EXTRA_PRIMARY_TEXT, primaryText)
                .also { ProtoParsers.put(it, EXTRA_PHOTO_INFO, Assert.isNotNull(photoInfo)) }
    }
}

/**
 * The first message whose timestamp shows the full date: the end of the first run of messages
 * from one side. Later timestamps show the time only.
 */
internal fun firstPositionToShowTimestamp(messages: List<RttTranscriptMessage>): Int {
    var i = 0
    while (i + 1 < messages.size && messages[i].isRemote == messages[i + 1].isRemote) i++
    return i
}

/** An RTT conversation: the other side's bubbles on the start side with their photo, ours on the end. */
@Composable
fun RttTranscriptScreen(title: String, photoInfo: PhotoInfo, messages: List<RttTranscriptMessage>, onBack: () -> Unit) {
    val context = LocalContext.current
    val fullDateAt = firstPositionToShowTimestamp(messages)
    val listState = rememberLazyListState()
    PhoneScaffold(
        topBar = { PvotTopBar(title = title, onBack = onBack, compact = rememberPvotTopBarCompact(listState)) },
    ) { padding ->
        PvotScreen(
            modifier = Modifier.fillMaxSize().testTag(TestTags.RTT_TRANSCRIPT),
            state = listState,
            contentPadding = PaddingValues(start = PvotSpacing.L, end = PvotSpacing.L, top = padding.calculateTopPadding()),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start,
        ) {
            itemsIndexed(messages) { i, message ->
                val sameGroup = i > 0 && messages[i - 1].isRemote == message.isRemote
                val moreInGroup = i + 1 < messages.size && messages[i + 1].isRemote == message.isRemote
                TranscriptMessage(
                    message = message,
                    photoInfo = photoInfo,
                    sameGroup = sameGroup,
                    timestamp = if (moreInGroup) null else timestampText(context, message.timestamp, i == fullDateAt),
                )
            }
            item { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = PvotSpacing.L)) }
        }
    }
}

private fun timestampText(context: Context, timestamp: Long, fullDate: Boolean): String =
    DateUtils.formatDateTime(
        context,
        timestamp,
        if (fullDate) DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME else DateUtils.FORMAT_SHOW_TIME,
    )

@Composable
private fun TranscriptMessage(message: RttTranscriptMessage, photoInfo: PhotoInfo, sameGroup: Boolean, timestamp: String?) {
    val remote = message.isRemote
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = if (sameGroup) PvotSpacing.XS / 2 else PvotSpacing.M),
        horizontalAlignment = if (remote) Alignment.Start else Alignment.End,
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(PvotSpacing.S)) {
            if (remote) {
                if (sameGroup) {
                    Spacer(Modifier.size(AvatarSize))
                } else {
                    QuickContactAvatar(
                        name = photoInfo.name,
                        contactUri = null,
                        photoId = photoInfo.photoId,
                        photoUri = photoInfo.photoUri.takeIf { it.isNotEmpty() }?.toUri(),
                        size = AvatarSize,
                        colorKey = photoInfo.lookupUri.ifEmpty { photoInfo.formattedNumber },
                    )
                }
            }
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyLarge,
                color = if (remote) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .widthIn(max = BubbleMaxWidth)
                    .background(
                        if (remote) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.primaryContainer,
                        PvotShapes.Card,
                    )
                    .padding(horizontal = PvotSpacing.M, vertical = PvotSpacing.S),
            )
        }
        if (timestamp != null) {
            Text(
                text = timestamp,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    top = PvotSpacing.XS,
                    start = if (remote) AvatarSize + PvotSpacing.S else 0.dp,
                ),
            )
        }
    }
}

private val AvatarSize = PvotAvatarDefaults.Small * 0.75f

/** The widest a bubble gets, so long messages wrap well short of the other side. */
private val BubbleMaxWidth = 280.dp
