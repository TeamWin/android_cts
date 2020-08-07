/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.systemui.cts.tv

import android.app.NotificationManager
import android.content.Intent
import android.platform.test.annotations.Postsubmit
import android.server.wm.annotation.Group2
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.PipActivity
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import android.systemui.tv.cts.PipActivity.EXTRA_MEDIA_SESSION_ACTIVE
import android.systemui.tv.cts.PipActivity.EXTRA_MEDIA_SESSION_TITLE
import android.systemui.tv.cts.ShellCommands.CMD_TEMPLATE_NOTIFICATION_ALLOW_LISTENER
import android.systemui.tv.cts.ShellCommands.CMD_TEMPLATE_NOTIFICATION_DISALLOW_LISTENER
import android.systemui.tv.cts.TVNotificationExtender.EXTRA_CONTENT_INTENT
import android.systemui.tv.cts.TVNotificationExtender.EXTRA_DELETE_INTENT
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests notification-related (PiP) behavior.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:PipNotificationTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class PipNotificationTests : PipTestBase() {
    private val notificationManager: NotificationManager =
        context.getSystemService(NotificationManager::class.java)
            ?: error("Could not find a NotificationManager!")

    init {
        val intent = Intent(context, PipNotificationListenerService::class.java)
        context.startService(intent)
    }

    @Before
    override fun setUp() {
        super.setUp()
        toggleListenerAccess(allow = true)
        notificationListener.clearNotifications()
    }

    @After
    fun tearDown() {
        stopPackage(PIP_ACTIVITY)
        toggleListenerAccess(allow = false)
    }

    /** Ensure a notification is posted when an app is in pip mode. */
    @Test
    fun pipNotification_isPosted() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)

        assertNotNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))
    }

    /** Ensure the pip notification has a functional pending intent to show the pip menu. */
    @Test
    fun pipNotification_detailsButton() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)

        val notification =
            assertNotNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))

        val contentIntent = notification.pendingTvIntent(EXTRA_CONTENT_INTENT)

        contentIntent.send()
        assertPipMenuOpen()
    }

    /** Ensure the pip notification has a functional pending intent to dismiss the app. */
    @Test
    fun pipNotification_dismissButton() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        waitForEnterPip(PIP_ACTIVITY)

        val notification =
            assertNotNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))

        val deleteIntent = notification.pendingTvIntent(EXTRA_DELETE_INTENT)

        deleteIntent.send()
        wmState.waitFor("The PiP app must be closed!") {
            !it.containsActivity(PIP_ACTIVITY)
        }

        // Also make sure the pip notification was removed
        assertNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))
    }

    /** Ensure the pip notifications reflect the title of the active media session. */
    @Test
    fun mediaSession_setsNotificationTitle() {
        val mediaTitle = "\"Where has my time gone?\" - Google Interns' Choir"
        launchPipWithMediaTitle(mediaTitle)

        // ensure the launcher notification has the correct title
        assertNotNull(notificationListener.findActivePipNotification(mediaTitle))
        // also ensure that there is no default notification
        assertNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))
    }

    /** Ensure the pip notification can display long media titles with many characters. */
    @Test
    fun pipNotification_canDisplayAllChars() {
        val az = ('a'..'z').joinToString("")
        val AZ = ('A'..'Z').joinToString("")
        val num = ('0'..'9').joinToString("")
        val extra = """öäüÖÄÜß^°âêîôû!"²§³$¼%½6¬/{([)]=}?\´`¸@€+*~#'<>|µ;,·.:…-_–¯\_(ツ)_/¯"""
        val emoji = "\uD83D\uDE00\uD83E\uDD87\uD83D\uDC00"
        val spaces = " \t"

        val title = "$emoji$spaces$az$AZ$num$extra"
        launchPipWithMediaTitle(title)
        assertNotNull(notificationListener.findActivePipNotification(title))
    }

    /** Ensure the notification displays app name after media session is stopped. */
    @Test
    fun mediaSession_revertsNotificationTitle() {
        val title = "Hello there"
        launchPipWithMediaTitle(title)
        // ensure the media title is used
        assertNotNull(notificationListener.findActivePipNotification(title))

        // stop the media session
        sendBroadcast(
            action = PipActivity.ACTION_SET_MEDIA_TITLE,
            boolExtras = mapOf(EXTRA_MEDIA_SESSION_ACTIVE to false)
        )
        // assert the notification reverted to the app name
        assertNotNull(notificationListener.findActivePipNotification(PIP_ACTIVITY.packageName))
    }

    /** Ensure a change to the media session's title is propagated to the pip notification. */
    @Test
    fun mediaSession_changesNotificationTitle() {
        val firstMediaTitle = "First Media Title"
        launchPipWithMediaTitle(firstMediaTitle)

        assertNotNull(notificationListener.findActivePipNotification(firstMediaTitle))

        // now change the title
        val secondMediaTitle = "Second Media Title"
        sendBroadcast(
            action = PipActivity.ACTION_SET_MEDIA_TITLE,
            stringExtras = mapOf(EXTRA_MEDIA_SESSION_TITLE to secondMediaTitle.urlEncoded())
        )
        assertNull(notificationListener.findActivePipNotification(firstMediaTitle))
        assertNotNull(notificationListener.findActivePipNotification(secondMediaTitle))
    }

    /** Enable/disable the [PipNotificationListenerService] listening to notifications. */
    private fun toggleListenerAccess(allow: Boolean) {
        val listenerName = PipNotificationListenerService.componentName
        val cmd = if (allow) {
            CMD_TEMPLATE_NOTIFICATION_ALLOW_LISTENER
        } else {
            CMD_TEMPLATE_NOTIFICATION_DISALLOW_LISTENER
        }
        executeShellCommand(cmd.format(listenerName.flattenToShortString()))

        // Ensure we were successful
        assertEquals(allow, notificationManager.isNotificationListenerAccessGranted(listenerName))
    }

    private val notificationListener: PipNotificationListenerService
        get() = PipNotificationListenerService.instance
            ?: error("PipNotificationListenerService not connected!")

    /** Launches an app into pip mode and sets its media session title. */
    private fun launchPipWithMediaTitle(title: String) {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP,
            boolExtras = mapOf(EXTRA_MEDIA_SESSION_ACTIVE to true),
            stringExtras = mapOf(EXTRA_MEDIA_SESSION_TITLE to title.urlEncoded())
        )
        waitForEnterPip(PIP_ACTIVITY)
    }
}
