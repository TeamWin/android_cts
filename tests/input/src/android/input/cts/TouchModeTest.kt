/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.input.cts

import android.app.Activity
import android.app.Instrumentation
import android.os.SystemClock
import android.support.test.uiautomator.UiDevice
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.WindowUtil
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val EVENT_PROPAGATION_TIMEOUT_MILLIS: Long = 5000

@RunWith(AndroidJUnit4::class)
class TouchModeTest {
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)

    @get:Rule
    val activityRule = ActivityScenarioRule<Activity>(Activity::class.java)
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        activityRule.scenario.onActivity {
            activity = it
        }
        WindowUtil.waitForFocus(activity)
        instrumentation.setInTouchMode(false)
    }

    fun isInTouchMode(): Boolean {
        return activity.window.decorView.isInTouchMode
    }

    @Test
    fun testFocusedWindowOwnerCanChangeTouchMode() {
        instrumentation.setInTouchMode(true)
        PollingCheck.waitFor { isInTouchMode() }
        assertTrue(isInTouchMode())
    }

    @Test
    @Ignore("b/218883063")
    fun testNonFocusedWindowOwnerCannotChangeTouchMode() {
        uiDevice.pressHome()
        PollingCheck.waitFor { !activity.hasWindowFocus() }
        instrumentation.setInTouchMode(true)
        // It takes 400-500 milliseconds for DecorView to receive the touch mode changed event on
        // 2021 hardware, so we set the timeout to 10x that. It's still possible that a test would
        // fail, but we don't have a better way to check that an event does not occur.
        SystemClock.sleep(EVENT_PROPAGATION_TIMEOUT_MILLIS)
        assertFalse(isInTouchMode())
    }
}
