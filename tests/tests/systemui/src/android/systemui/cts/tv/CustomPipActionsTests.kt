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

import android.app.ActivityTaskManager
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.Intent
import android.graphics.drawable.Icon
import android.platform.test.annotations.Postsubmit
import android.server.wm.UiDeviceUtils
import android.server.wm.annotation.Group2
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.PipActivity
import android.systemui.tv.cts.PipActivity.ACTION_CLEAR_CUSTOM_ACTIONS
import android.systemui.tv.cts.PipActivity.ACTION_MEDIA_PLAY
import android.systemui.tv.cts.PipActivity.ACTION_NO_OP
import android.systemui.tv.cts.PipActivity.EXTRA_ENTER_PIP
import android.systemui.tv.cts.PipMenu
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_CLOSE_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_CUSTOM_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_FULLSCREEN_BUTTON
import android.systemui.tv.cts.ResourceNames.ID_PIP_MENU_PLAY_PAUSE_BUTTON
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests custom picture in picture (PiP) actions.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:CustomPipActionsTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class CustomPipActionsTests : PipTestBase() {

    // reuse the icon from another test for our purposes
    private val icon: Icon =
        Icon.createWithResource(resources, android.systemui.cts.R.drawable.ic_save)

    private val maxPipActions: Int =
        ActivityTaskManager.getService().getMaxNumPictureInPictureActions(context.activityToken)

    @Before
    override fun setUp() {
        super.setUp()
        UiDeviceUtils.pressHomeButton()
    }

    @After
    fun tearDown() {
        stopPackage(PIP_ACTIVITY)
    }

    /** Ensure the pip menu contains a custom action button if set by the user. */
    @Test
    fun pipMenu_contains_CustomActionButton() {
        val action = "Custom Action"
        launchPipMenuWithActions(createClearActionsList(listOf(action)))
        assertActionButtonPresent(action)
    }

    /** Ensure it is possible to set [maxPipActions] many custom actions. */
    @Test
    fun pipMenu_canHave_maxNumberOfActions() {
        val titles = List(maxPipActions) { num: Int ->
            "Action $num"
        }
        val actions = createClearActionsList(titles)

        launchPipMenuWithActions(actions)

        titles.forEach { title ->
            assertActionButtonPresent(title)
        }

        // also ensure the default buttons are still there
        assertFullscreenAndCloseButtons()
    }

    /** Ensure the pip menu cannot display more than [maxPipActions] custom actions. */
    @Test
    fun pipMenu_cannotHave_moreThan_maxNumberOfActions() {
        val titles = List(maxPipActions + 1) { num ->
            "Action $num"
        }
        val actions = createClearActionsList(titles)

        launchPipMenuWithActions(actions)

        // ensure the first maxPipActions are there
        titles.take(maxPipActions).forEach { title ->
            assertActionButtonPresent(title)
        }

        // also make sure the last button with is absent
        assertActionButtonGone(titles.last())
    }

    /** Ensure it's possible for a custom action to clear all custom actions (including itself). */
    @Test
    fun pipMenu_customAction_canClear_allActions() {
        val titles = List(maxPipActions) { num: Int ->
            "Action $num"
        }
        val actions = createClearActionsList(titles)
        launchPipMenuWithActions(createClearActionsList(titles))

        // click on a random button
        val clearButton = assertNotNull(findActionButton(titles.random()))
        clearButton.click()

        // and ensure there are no custom actions
        titles.forEach { title ->
            assertActionButtonGone(title)
        }

        // ensure the default buttons are still there
        assertFullscreenAndCloseButtons()
    }

    /** Ensure that custom controls override media controls on app startup. */
    @Test
    fun pipMenu_customActions_overrideMediaSessionControls() {
        // Start a pip app with an active playing media session and custom controls
        val intent = makeStartPipIntent().apply {
            setCustomActions(createClearActionsList(listOf("Custom Action")))
            startMediaSession()
        }
        startAndOpenPipMenu(intent)

        assertMediaControlsGone()
    }

    /** Ensure that media controls disappear when custom controls are set. */
    @Test
    fun pipMenu_customActions_removeMediaSessionControls() {
        // first start a pip activity with an active media session
        val mediaPip = makeStartPipIntent().startMediaSession()
        startAndOpenPipMenu(mediaPip)
        assertMediaControlsPresent()

        // now set the custom actions
        val customActionsPip =
            makeUpdatePipIntent().setCustomActions(createClearActionsList(listOf("Custom Action")))
        context.sendBroadcast(customActionsPip)
        // make sure the media controls are gone
        assertMediaControlsGone()

        // remove the custom controls again
        val removeCustomActions = makeUpdatePipIntent().setCustomActions(arrayListOf())
        context.sendBroadcast(removeCustomActions)
        // make sure the media button is present again
        assertMediaControlsPresent()
    }

    /** Find the pip media controls or throw. */
    private fun assertMediaControlsPresent() {
        assertTrue("Media buttons must be shown!") {
            uiDevice.wait(Until.hasObject(By.res(ID_PIP_MENU_PLAY_PAUSE_BUTTON)), defaultTimeout)
        }
    }

    /** Throw if pip media controls are present. */
    private fun assertMediaControlsGone() {
        assertTrue("No media buttons must be shown!") {
            uiDevice.wait(Until.gone(By.res(ID_PIP_MENU_PLAY_PAUSE_BUTTON)), defaultTimeout)
        }
    }

    /** Fail if the default fullscreen and close buttons cannot be found. */
    private fun assertFullscreenAndCloseButtons() {
        locateByResourceName(ID_PIP_MENU_FULLSCREEN_BUTTON)
        locateByResourceName(ID_PIP_MENU_CLOSE_BUTTON)
    }

    /** Ensure the action with [title] exists or throw. */
    private fun assertActionButtonPresent(title: String) {
        assertNotNull(
            findActionButton(title),
            "Could not find custom action button for $title"
        )
    }

    /** Ensure action with [title] no longer exists. */
    private fun assertActionButtonGone(title: String) = assertNull(
        findActionButton(title),
        "Button $title must be gone!"
    )

    private fun findActionButton(title: String): UiObject2? = uiDevice.wait(Until.findObject(
        By.res(ID_PIP_MENU_CUSTOM_BUTTON).desc(title)), defaultTimeout)

    /**
     * Create a remote action that will clear all remote actions.
     * This action's title and description will be the given [title].
     */
    private fun createRemoteClearAction(title: String): RemoteAction =
        RemoteAction(icon, title, title, makePendingClearBroadcast())

    private fun makePendingClearBroadcast(): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_CLEAR_CUSTOM_ACTIONS),
            PendingIntent.FLAG_CANCEL_CURRENT
        )

    /** Launches a pip app with given custom actions and enters the pip menu. */
    private fun launchPipMenuWithActions(actions: ArrayList<RemoteAction>) {
        val intent = makeStartPipIntent().setCustomActions(actions)
        startAndOpenPipMenu(intent)
    }

    /** Modify a pip app intent to start a media session. */
    private fun Intent.startMediaSession(): Intent = apply {
        action = ACTION_MEDIA_PLAY
        putExtra(EXTRA_ENTER_PIP, true)
        putExtra(PipActivity.EXTRA_MEDIA_SESSION_ACTIVE, true)
        putExtra(PipActivity.EXTRA_MEDIA_SESSION_TITLE, "MediaTitle")
    }

    /** Modify a pip app intent to set custom actions. */
    private fun Intent.setCustomActions(actions: ArrayList<RemoteAction>): Intent = apply {
        putParcelableArrayListExtra(PipActivity.EXTRA_SET_CUSTOM_ACTIONS, actions)
    }

    /** Create an intent to start an app in pip mode. */
    private fun makeStartPipIntent(): Intent = Intent().apply {
        component = PIP_ACTIVITY
        putExtra(EXTRA_ENTER_PIP, true)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** Creates an intent to update an already running pip app. */
    private fun makeUpdatePipIntent() = makeStartPipIntent().apply {
        component = null
        action = ACTION_NO_OP
    }

    /** Start the app with the given intent and open its pip menu. */
    private fun startAndOpenPipMenu(startPipActivityIntent: Intent) {
        context.startActivity(startPipActivityIntent)
        waitForEnterPip(PIP_ACTIVITY)

        sendBroadcast(PipMenu.ACTION_MENU)
        assertPipMenuOpen()
    }

    /** Create a list of remote actions for clearing all custom actions with the given titles. */
    private fun createClearActionsList(titles: List<String>): ArrayList<RemoteAction> =
        titles.mapTo(arrayListOf()) { title -> createRemoteClearAction(title) }
}
