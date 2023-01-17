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

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.provider.MediaStore
import android.provider.Settings
import android.server.wm.settings.SettingsSession
import android.view.KeyEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.android.compatibility.common.util.SystemUtil
import java.io.Closeable
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests for power key shortcut.
 */
@MediumTest
@RunWith(AndroidJUnit4::class)
class PowerKeyShortcutTest : SystemShortcutTestBase() {
    /**
     * Tests for double tap POWER shortcut.
     */
    private val isCameraDoubleTapPowerEnabled: Boolean
        get() {
            val pm = activity.packageManager
            if (!pm.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                return false
            }
            val resources = activity.resources
            return try {
                resources.getBoolean(resources.getIdentifier(
                        "config_cameraDoubleTapPowerGestureEnabled", "bool", "android"))
            } catch (e: Resources.NotFoundException) {
                // Assume there's no camera enabled.
                false
            }
        }

    @Test
    fun testDoubleTapPower() {
        Assume.assumeTrue(isCameraDoubleTapPowerEnabled)
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        val component = intent.resolveActivity(activity.packageManager)

        CameraGestureSession().use {
            doubleTapKey(KeyEvent.KEYCODE_POWER)
            waitForReady(component.flattenToString())
        }
    }

    class CameraGestureSession : Closeable {
        val disableCameraGesture = SettingsSession(
                Settings.Secure.getUriFor("camera_double_tap_power_gesture_disabled"),
                Settings.Secure::getInt, Settings.Secure::putInt)
        init {
            SystemUtil.runWithShellPermissionIdentity {
                disableCameraGesture.set(0)
            }
        }

        override fun close() {
            SystemUtil.runWithShellPermissionIdentity {
                disableCameraGesture.close()
            }
        }
    }
}
