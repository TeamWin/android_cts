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
 * Test that the updateMediaTapToTransferSenderDisplay method fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.updateMediaTapToTransferSenderDisplay].
 */
@RunWith(AndroidJUnit4::class)
class UpdateMediaTapToTransferSenderDisplayTest {
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
            statusBarManager.updateMediaTapToTransferSenderDisplay(
                StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_FAR_FROM_RECEIVER,
                ROUTE_INFO,
                null,
                null
            )
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun undoCallbackForNotSucceedState_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )
    }

    @Test
    fun noUndoCallbackWithNotSucceedState_noException() {
        // No assert, just want to check no crash
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToReceiverSucceeded_undoCallbackButNoExecutor_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            Runnable { }
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun transferToThisDeviceSucceeded_undoCallbackButNoExecutor_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            Runnable { }
        )
    }

    @Test(expected = SecurityException::class)
    fun noPermission_throwsSecurityException() {
        uiAutomation.dropShellPermissionIdentity()
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )
    }

    @Test
    fun almostCloseToStartCast_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_START_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun almostCloseToEndCast_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_ALMOST_CLOSE_TO_END_CAST,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToReceiverTriggered_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToThisDeviceTriggered_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_TRIGGERED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToReceiverSucceeded_nullCallback_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToReceiverSucceeded_withCallbackAndExecutor_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToThisDeviceSucceeded_nullCallback_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToThisDeviceSucceeded_withCallbackAndExecutor_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_SUCCEEDED,
            ROUTE_INFO,
            context.getMainExecutor(),
            Runnable { }
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToReceiverFailed_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_FAILED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    @Test
    fun transferToThisDeviceFailed_displaysChipWindow() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_THIS_DEVICE_FAILED,
            ROUTE_INFO,
            /* executor= */ null,
            /* callback= */ null
        )

        windowManagerStateHelper.assertWindowDisplayed(MEDIA_CHIP_WINDOW_TITLE)
    }

    // TODO(b/216318437): Write tests for the FAR_FROM_RECEIVER state and verify that the window
    //   isn't displayed. Writing a test that called
    //   `updateMediaTapToTransferSenderDisplay(FAR_FROM_RECEIVER, ...)` then
    //   `windowManagerStateHelper.assertWindowNotDisplayed` resulted in test flakiness.
}

private const val MEDIA_CHIP_WINDOW_TITLE = "Media Transfer Chip View"
private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
private val ROUTE_INFO = MediaRoute2Info.Builder("id", "Test Name")
    .addFeature("feature")
    .build()
