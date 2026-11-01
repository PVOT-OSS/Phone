/*
 * Copyright (C) 2019-2023 The LineageOS Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.prauga.phone.dialer.helplines

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.telephony.SubscriptionManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import com.prauga.pvot.coreui.PvotEmptyContent
import com.prauga.pvot.coreui.PvotLoadingContent
import com.prauga.pvot.coreui.UiState
import com.prauga.pvot.designsystem.components.PvotBottomSheet
import com.prauga.pvot.designsystem.components.PvotCard
import com.prauga.pvot.designsystem.components.PvotDialog
import com.prauga.pvot.designsystem.components.PvotDialogAction
import com.prauga.pvot.designsystem.components.PvotDialogActionStyle
import com.prauga.pvot.designsystem.components.PvotScreen
import com.prauga.pvot.designsystem.components.PvotScreenDefaults
import com.prauga.pvot.designsystem.components.list.PvotListRow
import com.prauga.pvot.designsystem.navigation.PvotTopBar
import com.prauga.pvot.designsystem.navigation.PvotTopBarAction
import com.prauga.pvot.designsystem.navigation.rememberPvotTopBarCompact
import com.prauga.pvot.designsystem.platform.enablePvotEdgeToEdge
import com.prauga.pvot.designsystem.platform.setPvotContent
import com.prauga.pvot.designsystem.theme.PvotShapes
import com.prauga.pvot.designsystem.theme.PvotSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.prauga.phone.dialer.R
import org.prauga.phone.dialer.app.calllog.IntentProvider
import org.prauga.phone.dialer.helplines.utils.HelplineUtils
import org.prauga.phone.dialer.ui.PhoneScaffold
import org.prauga.phone.dialer.ui.PhoneScreenTheme
import org.prauga.phone.dialer.ui.TestTags

/**
 * Helplines for the countries of the active SIMs. Calls to them are kept out of the call history,
 * and the screen closes as soon as the user leaves it so it leaves no trace in recents.
 */
class HelplineActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enablePvotEdgeToEdge()
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(SHARED_PREFERENCES_KEY, Context.MODE_PRIVATE)
        val firstLaunch = !prefs.getBoolean(KEY_FIRST_LAUNCH, false)
        if (firstLaunch) prefs.edit { putBoolean(KEY_FIRST_LAUNCH, true) }

        setPvotContent {
            PhoneScreenTheme {
                val state by
                    produceState<UiState<List<HelplineItem>>>(UiState.Loading) {
                        value =
                            UiState.Success(
                                withContext(Dispatchers.Default) {
                                    HelplineLoader.load(
                                        resources,
                                        getSystemService(SubscriptionManager::class.java),
                                    )
                                }
                            )
                    }
                HelplinesScreen(
                    state = state,
                    showHelpAtStart = firstLaunch,
                    onBack = ::finish,
                    onCall = ::call,
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        finish()
    }

    private fun call(number: String) {
        // Start the call and close this screen, so it leaves no trace.
        startActivity(IntentProvider.getReturnCallIntentProvider(number).getClickIntent(this))
        finish()
    }

    companion object {
        const val SHARED_PREFERENCES_KEY = "org.prauga.phone.dialer.prefs"
        private const val KEY_FIRST_LAUNCH = "pref_first_launch"
    }
}

/** The helplines list with its top bar, help dialogs and details sheet. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun HelplinesScreen(
    state: UiState<List<HelplineItem>>,
    showHelpAtStart: Boolean,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
) {
    var help by rememberSaveable { mutableStateOf(showHelpAtStart) }
    var moreInfo by rememberSaveable { mutableStateOf(false) }
    var details by rememberSaveable { mutableStateOf<String?>(null) }
    var website by rememberSaveable { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()
    PhoneScaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = {
            PvotTopBar(
                title = stringResource(R.string.helplines_name),
                onBack = onBack,
                compact = rememberPvotTopBarCompact(listState),
            ) {
                PvotTopBarAction(
                    icon = painterResource(R.drawable.ic_info_circle),
                    contentDescription = stringResource(R.string.helplines_help),
                    onClick = { help = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (state) {
                is UiState.Loading -> PvotLoadingContent()
                is UiState.Error -> PvotEmptyContent(stringResource(R.string.helplines_empty))
                is UiState.Success ->
                    if (state.data.isEmpty()) {
                        PvotEmptyContent(
                            message = stringResource(R.string.helplines_empty),
                            modifier = Modifier.padding(PvotSpacing.L),
                        )
                    } else {
                        HelplineList(
                            state.data,
                            listState,
                            padding.calculateTopPadding(),
                            onOpen = { details = it.name },
                            onCall = onCall,
                        )
                    }
            }
        }
    }

    val shown = (state as? UiState.Success)?.data?.firstOrNull { it.name == details }
    PvotBottomSheet(
        visible = shown != null,
        onDismissRequest = { details = null },
        title = shown?.displayName(),
    ) {
        if (shown != null) {
            HelplineDetails(
                shown,
                onOpenWebsite = {
                    details = null
                    website = it
                },
            )
        }
    }
    PvotDialog(
        visible = website != null,
        onDismissRequest = { website = null },
        title = stringResource(R.string.helpline_website),
        content = { website?.let { PrivateWebView(it) } },
    ) {
        PvotDialogAction(label = stringResource(android.R.string.ok), onClick = { website = null })
    }
    PvotDialog(
        visible = help,
        onDismissRequest = { help = false },
        title = stringResource(R.string.helplines_help_title),
        message = stringResource(R.string.helplines_help_message),
    ) {
        PvotDialogAction(
            label = stringResource(android.R.string.ok),
            style = PvotDialogActionStyle.Primary,
            onClick = { help = false },
        )
        PvotDialogAction(
            label = stringResource(R.string.helpline_button_more),
            onClick = {
                help = false
                moreInfo = true
            },
        )
    }
    PvotDialog(
        visible = moreInfo,
        onDismissRequest = { moreInfo = false },
        title = stringResource(R.string.helplines_name),
        message =
            stringResource(
                R.string.helplines_help_more_message,
                stringResource(R.string.helplines_contact_url),
            ),
    ) {
        PvotDialogAction(
            label = stringResource(android.R.string.ok),
            onClick = { moreInfo = false },
        )
    }
}

@Composable
private fun HelplineList(
    helplines: List<HelplineItem>,
    listState: LazyListState,
    topPadding: Dp,
    onOpen: (HelplineItem) -> Unit,
    onCall: (String) -> Unit,
) {
    val resources = LocalContext.current.resources
    PvotScreen(
        modifier = Modifier.fillMaxSize().testTag(TestTags.HELPLINE_LIST),
        state = listState,
        contentPadding =
            PaddingValues(
                start = PvotScreenDefaults.HorizontalPadding,
                end = PvotScreenDefaults.HorizontalPadding,
                top = topPadding + PvotSpacing.S,
                bottom =
                    PvotScreenDefaults.BottomSpacing +
                        WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
            ),
    ) {
        // A short list, one solid card, grouped like the settings rows.
        item(key = "helplines") {
            PvotCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(vertical = PvotSpacing.XS)) {
                    for (helpline in helplines) {
                        val number = helpline.item.number
                        PvotListRow(
                            title = helpline.displayName(),
                            supportingText =
                                listOfNotNull(
                                        HelplineUtils.getCategories(resources, helpline).takeIf {
                                            it.isNotEmpty()
                                        },
                                        HelplineUtils.getLanguages(resources, helpline).takeIf {
                                            it.isNotEmpty()
                                        },
                                    )
                                    .joinToString("\n")
                                    .ifEmpty { null },
                            onClick = { onOpen(helpline) },
                            trailing =
                                if (number.isNullOrEmpty()) null
                                else {
                                    {
                                        IconButton(
                                            onClick = { onCall(number) },
                                            modifier =
                                                Modifier.testTag(TestTags.helplineCall(number)),
                                        ) {
                                            Icon(
                                                painterResource(R.drawable.ic_phone),
                                                contentDescription = stringResource(R.string.call),
                                            )
                                        }
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

/** Name, organization, categories, number and website, each shown only when known. */
@Composable
private fun HelplineDetails(helpline: HelplineItem, onOpenWebsite: (String) -> Unit) {
    val resources = LocalContext.current.resources
    val item = helpline.item
    Column(
        modifier =
            Modifier.fillMaxWidth().padding(horizontal = PvotSpacing.L, vertical = PvotSpacing.S),
        verticalArrangement = Arrangement.spacedBy(PvotSpacing.M),
    ) {
        DetailRow(R.string.helpline_org, item.organization)
        DetailRow(R.string.helpline_categories, HelplineUtils.getCategories(resources, helpline))
        DetailRow(R.string.helpline_number, item.number)
        DetailRow(
            R.string.helpline_website,
            item.website,
            onClick = item.website?.let { { onOpenWebsite(it) } },
        )
    }
}

@Composable
private fun DetailRow(label: Int, value: String?, onClick: (() -> Unit)? = null) {
    if (value.isNullOrEmpty()) return
    val link = onClick != null
    PvotListRow(
        title = { Text(value, textDecoration = if (link) TextDecoration.Underline else null) },
        supportingText = { Text(stringResource(label)) },
        onClick = onClick,
    )
}

/**
 * A website opened without cookies or cache, with Do Not Track, and forgotten when closed, on a
 * solid panel so the page reads as its own surface.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PrivateWebView(url: String) {
    AndroidView(
        modifier =
            Modifier.fillMaxWidth()
                .height(WebsitePanelHeight)
                .clip(PvotShapes.Card)
                .background(MaterialTheme.colorScheme.surface),
        factory = { context ->
            CookieManager.getInstance().setAcceptCookie(false)
            WebView(context).apply {
                webViewClient =
                    object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest,
                        ) = false
                    }
                loadUrl(
                    url,
                    mapOf("Pragma" to "no-cache", "Cache-Control" to "no-cache", "DNT" to "1"),
                )
            }
        },
        onRelease = { it.clearHistory() },
    )
}

/** Tall enough to read a page in, short enough that the dialog's title and button still fit. */
private val WebsitePanelHeight = 360.dp

@Composable
private fun HelplineItem.displayName(): String =
    name.ifEmpty { stringResource(R.string.unknown_helpline_name) }
