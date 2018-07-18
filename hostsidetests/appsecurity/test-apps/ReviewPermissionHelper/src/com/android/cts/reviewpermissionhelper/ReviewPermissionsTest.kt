/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.cts.reviewpermissionhelper

import android.content.ComponentName
import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until

import org.junit.Test
import org.junit.runner.RunWith

private const val UI_TIMEOUT = 5000L

@RunWith(AndroidJUnit4::class)
class ReviewPermissionsTest {
    @Test
    fun approveReviewPermissions() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val startAutoClosingActivity = Intent()
        startAutoClosingActivity.component = ComponentName("com.android.cts.usepermission",
                "com.android.cts.usepermission.AutoClosingActivity")
        startAutoClosingActivity.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        instrumentation.targetContext.startActivity(startAutoClosingActivity)

        UiDevice.getInstance(instrumentation).wait(Until.findObject(
                By.res("com.android.packageinstaller:id/continue_button")), UI_TIMEOUT).click()
    }
}
