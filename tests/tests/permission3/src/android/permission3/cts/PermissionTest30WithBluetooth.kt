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

import android.content.Intent
import androidx.core.os.BuildCompat
import androidx.test.InstrumentationRegistry
import junit.framework.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

/**
 * Runtime Bluetooth-permission behavior of apps targeting API 30
 */
class PermissionTest30WithBluetooth : BaseUsePermissionTest() {

    val AUTHORITY = "android.permission3.cts.usepermission.AccessBluetoothOnCommand"

    private enum class BluetoothScanResult {
        UNKNOWN, EXCEPTION, EMPTY, FILTERED, FULL
    }

    @Before
    fun installApp() {
        installPackage(APP_APK_PATH_30_WITH_BLUETOOTH)
    }

    @Test
    fun testGivenBluetoothIsDeniedWhenScanIsAttemptedThenThenGetEmptyScanResult() {
        assumeTrue(BuildCompat.isAtLeastS())
        revokeAppPermissions(android.Manifest.permission.BLUETOOTH_SCAN)
        assertEquals(BluetoothScanResult.EMPTY, scanForBluetoothDevices())
    }

    private fun scanForBluetoothDevices(): BluetoothScanResult {
        val resolver = InstrumentationRegistry.getTargetContext().getContentResolver()
        val result = resolver.call(AUTHORITY, "", null, null)
        return BluetoothScanResult.values()[result!!.getInt(Intent.EXTRA_INDEX)]
    }
}
