/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import android.content.Intent
import android.hardware.SensorPrivacyManager
import android.hardware.SensorPrivacyManager.Sensors.CAMERA
import android.hardware.SensorPrivacyManager.Sensors.MICROPHONE
import android.location.LocationManager
import android.os.Build
import org.junit.Test
import android.provider.Settings
import androidx.test.filters.SdkSuppress
import android.support.test.uiautomator.By
import org.junit.Assume
import org.junit.Before

/**
 * Banner card display tests on sensors being blocked
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
class SensorBlockedBannerTest : BaseUsePermissionTest() {
    companion object {
        const val LOCATION = -1
    }

    val sensorPrivacyManager = context.getSystemService(SensorPrivacyManager::class.java)!!
    val locationManager = context.getSystemService(LocationManager::class.java)!!

    private val permToLabel = mapOf(CAMERA to "privdash_label_camera",
            MICROPHONE to "privdash_label_microphone",
            LOCATION to "privdash_label_location")

    @Before
    fun install() {
        installPackage(APP_APK_PATH_31)
    }

    private fun navigateAndTest(sensor: Int) {
        val permLabel = permToLabel.getOrDefault(sensor, "Break")
        val intent = Intent(Settings.ACTION_PRIVACY_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        click(By.text(getPermissionControllerString("app_permission_manager")))
        click(By.text(getPermissionControllerString(permLabel)))
        waitFindObject(By.res("android:id/title"))
        pressBack()
        pressBack()
        pressBack()
    }

    private fun runSensorTest(sensor: Int) {
        val blocked = isSensorPrivacyEnabled(sensor)
        if (!blocked) {
            setSensor(sensor, true)
        }
        navigateAndTest(sensor)
        if (!blocked) {
            setSensor(sensor, false)
        }
    }
    @Test
    fun testCameraCardDisplayed() {
        Assume.assumeTrue(sensorPrivacyManager.supportsSensorToggle(CAMERA))
        runSensorTest(CAMERA)
    }

    @Test
    fun testMicCardDisplayed() {
        Assume.assumeTrue(sensorPrivacyManager.supportsSensorToggle(MICROPHONE))
        runSensorTest(MICROPHONE)
    }

    @Test
    fun testLocationCardDisplayed() {
        runSensorTest(LOCATION)
    }

    private fun setSensor(sensor: Int, enable: Boolean) {
        if (sensor == LOCATION) {
            runWithShellPermissionIdentity {
                locationManager.setLocationEnabledForUser(!enable,
                        android.os.Process.myUserHandle())
            }
        } else {
            runWithShellPermissionIdentity {
                sensorPrivacyManager.setSensorPrivacy(SensorPrivacyManager.Sources.OTHER,
                        sensor, enable)
            }
        }
    }

    private fun isSensorPrivacyEnabled(sensor: Int): Boolean {
        return if (sensor == LOCATION) {
            callWithShellPermissionIdentity {
                !locationManager.isLocationEnabled()
            }
        } else {
            callWithShellPermissionIdentity {
                sensorPrivacyManager.isSensorPrivacyEnabled(sensor)
            }
        }
    }
}
