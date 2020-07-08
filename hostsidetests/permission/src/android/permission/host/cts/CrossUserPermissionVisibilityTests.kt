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

package android.permission.host.cts

import android.platform.test.annotations.AppModeFull
import android.platform.test.annotations.SecurityTest
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Test that permission checks across user boundaries behave correctly
 */
@AppModeFull
@RunWith(DeviceJUnit4ClassRunner::class)
class CrossUserPermissionVisibilityTests : PermissionMultiUserTestBase() {
    private fun runDeviceTest(testMethod: String) =
            runDeviceTest("CrossUserPermissionVisibilityTests", testMethod)

    @Test
    @SecurityTest
    fun cannotCheckPermissionInDifferentUser() {
        runDeviceTest("cannotCheckPermissionInDifferentUser")
    }

    @Test
    fun canCheckPermissionInDifferentUser() {
        device.executeShellCommand(
                "pm grant --user $user $TEST_PACKAGE android.permission.INTERACT_ACROSS_USERS")
        runDeviceTest("canCheckPermissionInDifferentUser")
    }

    @Test
    fun cannotCheckPermissionInDifferentProfile() {
        runDeviceTest("cannotCheckPermissionInDifferentProfile")
    }

    @Test
    fun canCheckPermissionInDifferentProfile() {
        device.executeShellCommand(
                "appops set --user $user --uid $TEST_PACKAGE INTERACT_ACROSS_PROFILES allow")
        runDeviceTest("canCheckPermissionInDifferentProfile")
    }
}