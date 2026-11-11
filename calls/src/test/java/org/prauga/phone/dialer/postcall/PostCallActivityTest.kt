/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.phone.dialer.postcall

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Sending a post-call message over SMS hands it to the messaging app; the app sends nothing. */
@RunWith(RobolectricTestRunner::class)
class PostCallActivityTest {

    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun smsMessageOpensTheMessagingAppWithTheText() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The activity is declared in the app's manifest, not this module's.
        shadowOf(app.packageManager)
            .addActivityIfNotPresent(ComponentName(app, PostCallActivity::class.java))
        ActivityScenario.launch<PostCallActivity>(
                PostCallActivity.newIntent(app, "+15550100", /* isRcsPostCall= */ false)
            )
            .use { scenario ->
                compose.onNodeWithText("This is urgent. Call me back.").performClick()
                compose.onNodeWithText("Send message").performClick()
                compose.waitForIdle()

                scenario.onActivity { activity ->
                    val started = shadowOf(activity).nextStartedActivity
                    assertThat(started.action).isEqualTo(Intent.ACTION_SENDTO)
                    assertThat(started.data.toString()).isEqualTo("sms:+15550100")
                    assertThat(started.getStringExtra("sms_body"))
                        .isEqualTo("This is urgent. Call me back.")
                    assertThat(activity.isFinishing).isTrue()
                }
            }
    }
}
