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
import androidx.test.InstrumentationRegistry
import androidx.test.InstrumentationRegistry.getInstrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.IllegalArgumentException
import java.util.function.Consumer
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that the updateMediaTapToTransferSenderDisplay method fails in all the expected ways.
 *
 * These tests are for [StatusBarManager.updateMediaTapToTransferSenderDisplay].
 */
@RunWith(AndroidJUnit4::class)
class UpdateMediaTapToTransferSenderDisplayTest {
    private lateinit var statusBarManager: StatusBarManager
    private lateinit var instrumentation: Instrumentation
    private lateinit var uiAutomation: UiAutomation
    private lateinit var context: Context

    @Before
    fun setUp() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        context = instrumentation.getTargetContext()
        statusBarManager = context.getSystemService(StatusBarManager::class.java)!!
        uiAutomation = getInstrumentation().getUiAutomation()
        uiAutomation.adoptShellPermissionIdentity(MEDIA_PERMISSION)
    }

    @After
    fun tearDown() {
        uiAutomation.dropShellPermissionIdentity()
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
    fun undoCallbackButNoExecutor_throwsException() {
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            /* executor= */ null,
            Runnable { }
        )
    }

    @Test
    fun succeedStateWithCallbackAndExecutor_noException() {
        // No assert, just want to check no crash
        statusBarManager.updateMediaTapToTransferSenderDisplay(
            StatusBarManager.MEDIA_TRANSFER_SENDER_STATE_TRANSFER_TO_RECEIVER_SUCCEEDED,
            ROUTE_INFO,
            context.getMainExecutor(),
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
}

private val MEDIA_PERMISSION: String = android.Manifest.permission.MEDIA_CONTENT_CONTROL
private val ROUTE_INFO = MediaRoute2Info.Builder("id", "Test Name")
    .addFeature("feature")
    .build()
