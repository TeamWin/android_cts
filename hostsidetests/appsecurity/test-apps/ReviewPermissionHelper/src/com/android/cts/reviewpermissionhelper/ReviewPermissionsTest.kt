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

import android.Manifest.permission.READ_CALENDAR
import android.app.Activity.RESULT_CANCELED
import android.app.Activity.RESULT_OK
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import androidx.test.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import com.android.compatibility.common.util.FutureResultActivity
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

private const val UI_TIMEOUT_UNEXPECTED = 500L
private const val UI_TIMEOUT = 5000L
private const val USE_PERMISSION_PKG = "com.android.cts.usepermission"

@RunWith(AndroidJUnit4::class)
class ReviewPermissionsTest {
    @get:Rule
    val activityStarter = ActivityTestRule(FutureResultActivity::class.java)

    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val uiDevice = UiDevice.getInstance(instrumentation)

    fun startActivityInReviewedAp(): CompletableFuture<Int> {
        val startAutoClosingActivity = Intent()
        startAutoClosingActivity.component = ComponentName(USE_PERMISSION_PKG,
                USE_PERMISSION_PKG + ".AutoClosingActivity")
        return activityStarter.activity.startActivityForResult(startAutoClosingActivity)
    }

    private inline fun startActivityInReviewedAp(expectedResult: Int, runAfterStart: () -> Unit) {
        val activityResult = startActivityInReviewedAp()
        runAfterStart()
        assertEquals(expectedResult, activityResult.get(UI_TIMEOUT, TimeUnit.MILLISECONDS))
    }

    fun clickContinue() {
        click(By.res("com.android.permissioncontroller:id/continue_button"))
    }

    @Test
    fun approveReviewPermissions() {
        startActivityInReviewedAp(expectedResult = RESULT_OK) {
            clickContinue()
        }
    }

    @Test
    fun cancelReviewPermissions() {
        startActivityInReviewedAp(expectedResult = RESULT_CANCELED) {
            click(By.res("com.android.permissioncontroller:id/cancel_button"))
        }
    }

    @Test
    fun assertNoReviewPermissionsNeeded() {
        startActivityInReviewedAp(expectedResult = RESULT_OK) {}
    }

    @Test
    fun denyGrantDenyCalendarPermissions() {
        startActivityInReviewedAp()

        // Deny
        click(By.text("Calendar"))
        // Confirm deny
        click(By.res("android:id/button1"))

        // Grant
        uiDevice.waitForIdle()
        click(By.text("Calendar"))

        // Deny
        uiDevice.waitForIdle()
        click(By.text("Calendar"))

        uiDevice.waitForIdle()
        clickContinue()
    }

    @Test
    fun denyGrantCalendarPermissions() {
        startActivityInReviewedAp()

        // Deny
        click(By.text("Calendar"))
        // Confirm deny
        click(By.res("android:id/button1"))

        // Grant
        uiDevice.waitForIdle()
        click(By.text("Calendar"))

        uiDevice.waitForIdle()
        clickContinue()
    }

    @Test
    fun denyCalendarPermissions() {
        startActivityInReviewedAp()

        // Deny
        click(By.text("Calendar"))
        // Confirm deny
        click(By.res("android:id/button1"))

        uiDevice.waitForIdle()
        clickContinue()
    }

    private fun click(selector: BySelector) {
        waitFindObject(selector, UI_TIMEOUT).click()
    }

    @Test
    fun reviewPermissionWhenServiceIsBound() {
        val permissionCheckerServiceIntent = Intent()
        permissionCheckerServiceIntent.component = ComponentName(USE_PERMISSION_PKG,
                "$USE_PERMISSION_PKG.PermissionCheckerService")

        val results = LinkedBlockingQueue<Int>()
        permissionCheckerServiceIntent.putExtra("$USE_PERMISSION_PKG.RESULT",
                object : ResultReceiver(Handler(Looper.getMainLooper())) {
                    override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                        results.offer(resultCode)
                    }
                })
        permissionCheckerServiceIntent.putExtra("$USE_PERMISSION_PKG.PERMISSION", READ_CALENDAR)

        activityStarter.activity.startService(permissionCheckerServiceIntent)

        // Service is not started before permission are reviewed
        assertNull(results.poll(UI_TIMEOUT_UNEXPECTED, TimeUnit.MILLISECONDS))

        clickContinue()

        // Service should be started after permission review
        assertEquals(PERMISSION_GRANTED, results.poll(UI_TIMEOUT, TimeUnit.MILLISECONDS))
    }
}
