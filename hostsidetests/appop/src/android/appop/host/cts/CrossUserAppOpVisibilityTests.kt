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

package android.appop.host.cts

import android.platform.test.annotations.AppModeFull
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that appop checks across user boundaries behave correctly
 */
@AppModeFull
@RunWith(DeviceJUnit4ClassRunner::class)
class CrossUserAppOpVisibilityTests : AppOpMultiUserTestBase() {
    private fun runDeviceTest(testMethod: String) =
            runDeviceTest("CrossUserAppOpVisibilityTests", testMethod)

    @Test
    fun cannotCheckAppOpInDifferentUser() {
        runDeviceTest("cannotCheckAppOpInDifferentUser")
    }

    @Test
    fun canCheckAppOpInDifferentUser() {
        device.executeShellCommand(
                "pm grant --user $user $TEST_PACKAGE android.permission.INTERACT_ACROSS_USERS")
        runDeviceTest("canCheckAppOpInDifferentUser")
    }

    @Test
    fun cannotCheckAppOpInDifferentProfile() {
        runDeviceTest("cannotCheckAppOpInDifferentProfile")
    }

    @Test
    fun canCheckAppOpInDifferentProfile() {
        device.executeShellCommand(
                "appops set --user $user --uid $TEST_PACKAGE INTERACT_ACROSS_PROFILES allow")
        runDeviceTest("canCheckAppOpInDifferentProfile")
    }

    @Test
    fun canCheckAppOpInDifferentProfileWithPrivilegedPermission() {
        runDeviceTest("canCheckAppOpInDifferentProfileAsShell")
    }
}