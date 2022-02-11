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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val EVENT_PROPAGATION_TIMEOUT_MILLIS: Long = 100

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
    fun testNonFocusedWindowOwnerCannotChangeTouchMode() {
        uiDevice.pressHome()
        PollingCheck.waitFor { !activity.hasWindowFocus() }
        instrumentation.setInTouchMode(true)
        SystemClock.sleep(EVENT_PROPAGATION_TIMEOUT_MILLIS)
        assertFalse(isInTouchMode())
    }
}
