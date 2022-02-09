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

package android.app.cts

import android.app.Instrumentation
import android.app.StatusBarManager
import android.app.UiAutomation
import android.content.Context
import android.media.MediaRoute2Info
import android.net.Uri
import android.server.wm.WindowManagerStateHelper
import androidx.test.InstrumentationRegistry
import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.AdoptShellPermissionsRule
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that the updateMediaTapToTransferReceiverDisplay method fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.updateMediaTapToTransferReceiverDisplay].
 */
@RunWith(AndroidJUnit4::class)
class UpdateMediaTapToTransferReceiverDisplayTest {
    @Rule
    fun permissionsRule() = AdoptShellPermissionsRule(
        getInstrumentation().getUiAutomation(), MEDIA_PERMISSION
    )

    private lateinit var statusBarManager: StatusBarManager
    private lateinit var instrumentation: Instrumentation
    private lateinit var uiAutomation: UiAutomation
    private lateinit var context: Context
    private lateinit var windowManagerStateHelper: WindowManagerStateHelper

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.getTargetContext()
        statusBarManager = context.getSystemService(StatusBarManager::class.java)!!
        uiAutomation = getInstrumentation().getUiAutomation()
        windowManagerStateHelper = WindowManagerStateHelper()
    }

    @After
    fun tearDown() {
        // Explicitly run with the permission granted since it may have been dropped in the test.
        runWithShellPermissionIdentity {
            // Clear any existing chip
            statusBarManager.updateMediaTapToTransferReceiverDisplay(
                StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_FAR_FROM_SENDER,
                ROUTE_INFO
            )
        }
    }

    @Test(expected = SecurityException::class)
    fun noPermission_throwsSecurityException() {
        uiAutomation.dropShellPermissionIdentity()
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO
        )
    }

    @Test
    fun closeToSender_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferReceiverDisplay(
            StatusBarManager.MEDIA_TRANSFER_RECEIVER_STATE_CLOSE_TO_SENDER,
            ROUTE_INFO
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }
    // TODO(b/216318437): Write tests for the FAR_FROM_SENDER state and verify that the window
    //   isn't displayed. Writing a test that called
    //   `updateMediaTapToTransferReceiverDisplay(FAR_FROM_SENDER, ...)` then
    //   `windowManagerStateHelper.assertWindowNotDisplayed` resulted in test flakiness.
}

private const val MEDIA_CHIP_WINDOW_TITLE = "Media Transfer Chip View"
private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
private val ROUTE_INFO = MediaRoute2Info.Builder("id", "Test Name")
    .addFeature("feature")
    .setIconUri(Uri.parse("content://ctstest"))
    .build()
