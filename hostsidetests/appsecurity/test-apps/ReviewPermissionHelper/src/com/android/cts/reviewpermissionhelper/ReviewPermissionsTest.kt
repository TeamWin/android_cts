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

import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val UI_TIMEOUT = 5000L

@RunWith(AndroidJUnit4::class)
class ReviewPermissionsTest {
    @get:Rule
    val activityStarter = ActivityTestRule(ActivityStarter::class.java)

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiDevice = UiDevice.getInstance(instrumentation)

    fun startActivityInReviewedAp() {
        val startAutoClosingActivity = Intent()
        startAutoClosingActivity.component = ComponentName("com.android.cts.usepermission",
                "com.android.cts.usepermission.AutoClosingActivity")
        activityStarter.activity.startActivityForResult(startAutoClosingActivity, 42)
    }

    fun clickContinue() {
        uiDevice.wait(Until.findObject(
                By.res("com.android.permissioncontroller:id/continue_button")), UI_TIMEOUT).click()
    }

    @Test
    fun approveReviewPermissions() {
        startActivityInReviewedAp()
        clickContinue()
        assertEquals(RESULT_OK, installDialogResults.poll(UI_TIMEOUT, TimeUnit.MILLISECONDS))
    }

    @Test
    fun cancelReviewPermissions() {
        startActivityInReviewedAp()

        uiDevice.wait(Until.findObject(
                By.res("com.android.permissioncontroller:id/cancel_button")), UI_TIMEOUT).click()
        assertEquals(RESULT_CANCELED, installDialogResults.poll(UI_TIMEOUT, TimeUnit.MILLISECONDS))
    }

    @Test
    fun assertNoReviewPermissionsNeeded() {
        startActivityInReviewedAp()
        assertEquals(RESULT_OK, installDialogResults.poll(UI_TIMEOUT, TimeUnit.MILLISECONDS))
    }

    @Test
    fun denyGrantDenyStoragePermissions() {
        startActivityInReviewedAp()

        // Deny
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()
        // Confirm deny
        uiDevice.wait(Until.findObject(By.res("android:id/button1")), UI_TIMEOUT).click()

        // Grant
        uiDevice.waitForIdle()
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()

        // Deny
        uiDevice.waitForIdle()
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()

        uiDevice.waitForIdle()
        clickContinue()
    }

    @Test
    fun denyGrantStoragePermissions() {
        startActivityInReviewedAp()

        // Deny
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()
        // Confirm deny
        uiDevice.wait(Until.findObject(By.res("android:id/button1")), UI_TIMEOUT).click()

        // Grant
        uiDevice.waitForIdle()
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()

        uiDevice.waitForIdle()
        clickContinue()
    }

    @Test
    fun denyStoragePermissions() {
        startActivityInReviewedAp()

        // Deny
        uiDevice.wait(Until.findObject(By.text("Storage")), UI_TIMEOUT).click()
        // Confirm deny
        uiDevice.wait(Until.findObject(By.res("android:id/button1")), UI_TIMEOUT).click()

        uiDevice.waitForIdle()
        clickContinue()
    }
}
