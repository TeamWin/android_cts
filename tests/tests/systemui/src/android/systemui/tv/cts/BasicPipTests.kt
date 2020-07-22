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

package android.systemui.tv.cts

import android.content.Intent
import android.platform.test.annotations.Postsubmit
import android.server.wm.annotation.Group2
import android.systemui.tv.cts.Components.PIP_ACTIVITY
import android.systemui.tv.cts.Components.PIP_MENU_ACTIVITY
import android.systemui.tv.cts.Components.activityName
import android.systemui.tv.cts.PipActivity.ACTION_ENTER_PIP
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Tests most basic picture in picture (PiP) behavior.
 *
 * Build/Install/Run:
 * atest CtsSystemUiTestCases:BasicPipTests
 */
@Postsubmit
@Group2
@RunWith(AndroidJUnit4::class)
class BasicPipTests : PipTestBase() {

    @After
    fun tearDown() {
        stopPackage(PIP_ACTIVITY)
    }

    /** Open an app in pip mode and ensure its pip menu can be opened. */
    @Test
    fun pipMenu_open() {
        launchActivity(PIP_ACTIVITY, ACTION_ENTER_PIP)
        wmState.waitForValidState(PIP_ACTIVITY)
        // enter pip menu
        sendBroadcast(PipMenu.ACTION_MENU, setOf(Intent.FLAG_ACTIVITY_NEW_TASK))
        wmState.waitForValidState(PIP_MENU_ACTIVITY)

        wmState.assertActivityDisplayed(PIP_ACTIVITY)
        assertEquals(
            expected = PIP_MENU_ACTIVITY.activityName(),
            actual = wmState.focusedActivity,
            message = "The PiP Menu activity must be focused!"
        )
    }
}
