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
package android.permission3.cts

import android.Manifest
import android.app.AppOpsManager
import android.content.Intent
import android.hardware.camera2.CameraManager
import android.provider.DeviceConfig
import android.support.test.uiautomator.UiSelector
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

private const val APP_PKG = "android.permission3.cts.appthataccessescameraandmic"
private const val APP_LABEL = "CtsCameraMicAccess"
private const val APK =
    "/data/local/tmp/cts/permissions/CtsAppThatAccessesMicAndCameraPermission.apk"
private const val USE_CAMERA = "use camera"
private const val USE_MICROPHONE = "use_microphone"
private const val INTENT_ACTION = "test.action.USE_CAMERA_OR_MIC"
private const val PRIVACY_CHIP_ID = "com.android.systemui:id/privacy_chip"
private const val INDICATORS_FLAG = "camera_mic_icons_enabled"

class CameraMicIndicatorsPermissionTest : BasePermissionTest() {

    private var wasEnabled = false
    private val micLabel = packageManager.getPermissionGroupInfo(
        Manifest.permission_group.MICROPHONE, 0).loadLabel(packageManager).toString()
    private val cameraLabel = packageManager.getPermissionGroupInfo(
        Manifest.permission_group.CAMERA, 0).loadLabel(packageManager).toString()

    @Before
    override fun setUp() {
        super.setUp()
        wasEnabled = setIndicatorsEnabledStateIfNeeded(true)
    }

    private fun setIndicatorsEnabledStateIfNeeded(shouldBeEnabled: Boolean): Boolean {
        var currentlyEnabled = false
        runWithShellPermissionIdentity {
            currentlyEnabled = DeviceConfig.getBoolean(DeviceConfig.NAMESPACE_PRIVACY,
                INDICATORS_FLAG, false)
            if (currentlyEnabled != shouldBeEnabled) {
                DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PRIVACY, INDICATORS_FLAG,
                    shouldBeEnabled.toString(), false)
            }
        }
        return currentlyEnabled
    }

    @After
    override fun tearDown() {
        super.tearDown()
        if (!wasEnabled) {
            setIndicatorsEnabledStateIfNeeded(false)
        }
    }

    private fun openApp(useMic: Boolean, useCamera: Boolean) {
        context.startActivity(Intent(INTENT_ACTION).apply {
            putExtra(USE_CAMERA, useCamera)
            putExtra(USE_MICROPHONE, useMic)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    @Test
    fun testCameraIndicator() {
        val manager = context.getSystemService(CameraManager::class.java)!!
        assumeTrue(manager.cameraIdList.isNotEmpty())
        testCameraAndMicIndicator(useMic = false, useCamera = true)
    }

    @Test
    fun testMicIndicator() {
        testCameraAndMicIndicator(useMic = true, useCamera = false)
    }

    private fun testCameraAndMicIndicator(useMic: Boolean, useCamera: Boolean) {
        openApp(useMic, useCamera)
        Thread.sleep(1500)
        val appOpsManager = context.getSystemService(AppOpsManager::class.java)!!
        uiDevice.openNotification()
        // Ensure the privacy chip is present
        eventually {
            val privacyChip = uiDevice.findObject(UiSelector().resourceId(PRIVACY_CHIP_ID))
            assertTrue("view with id $PRIVACY_CHIP_ID not found", privacyChip.exists())
            privacyChip.click()
        }
        eventually {
            if (useMic) {
                val appView = uiDevice.findObject(UiSelector().textContains(micLabel))
                assertTrue("View with text $APP_LABEL not found", appView.exists())
            }
            if (useCamera) {
                val appView = uiDevice.findObject(UiSelector().textContains(cameraLabel))
                assertTrue("View with text $APP_LABEL not found", appView.exists())
            }
            val appView = uiDevice.findObject(UiSelector().textContains(APP_LABEL))
            assertTrue("View with text $APP_LABEL not found", appView.exists())
        }
        pressBack()
        runShellCommand("am force-stop $APP_PKG")
    }
}