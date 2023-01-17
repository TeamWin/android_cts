/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.server.wm.WindowManagerStateHelper
import android.view.KeyEvent
import android.view.ViewConfiguration
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.PollingCheck
import com.android.compatibility.common.util.ShellUtils
import com.google.common.truth.Truth
import org.junit.After
import org.junit.Before
import org.junit.Rule

open class SystemShortcutTestBase {
    val instrumentation = InstrumentationRegistry.getInstrumentation()

    @get:Rule
    val activityRule = ActivityScenarioRule(CaptureEventActivity::class.java)
    lateinit var activity: CaptureEventActivity
    lateinit var keyboard: UinputKeyboard
    var wmState = WindowManagerStateHelper()

    @Before
    @Throws(Exception::class)
    fun setUp() {
        activityRule.getScenario().onActivity {
            activity = it
        }
        PollingCheck.waitFor { activity.hasWindowFocus() }
        keyboard = UinputKeyboard(instrumentation)
    }

    @After
    fun tearDown() {
        // dismiss all system dialogs after test.
        ShellUtils.runShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        keyboard.close()
    }

    protected fun tapKey(keyCode: Int) {
        keyboard.injectKey(keyCode, true)
        keyboard.injectKey(keyCode, false)
    }

    protected fun sendKeyCombination(keyCodes: IntArray, pressTime: Long) {
        for (keyCode in keyCodes) {
            keyboard.injectKey(keyCode, true)
        }

        // Give the latency time to make sure policy handler could handle combination key properly.
        val latency = 50
        try {
            Thread.sleep(pressTime + latency)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }

        for (keyCode in keyCodes) {
            keyboard.injectKey(keyCode, false)
        }
    }

    protected fun doubleTapKey(keyCode: Int) {
        tapKey(keyCode)
        try {
            Thread.sleep(ViewConfiguration.getKeyRepeatDelay().toLong())
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        tapKey(keyCode)
    }

    // Wait until expected window surface shown.
    protected fun waitForReady(windowName: String): Boolean {
        return wmState.waitForWithAmState(
                { state -> state.isWindowSurfaceShown(windowName) },
                "$windowName's surface is not appeared")
    }

    protected fun getKeyEvent(): KeyEvent {
        val event = activity.getInputEvent()
        Truth.assertThat(event).isNotNull()
        Truth.assertThat(event).isInstanceOf(KeyEvent::class.java)
        return event as KeyEvent
    }
}
