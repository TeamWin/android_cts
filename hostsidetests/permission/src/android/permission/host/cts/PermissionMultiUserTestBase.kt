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

import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test
import org.junit.After
import org.junit.Assume
import org.junit.Before

open class PermissionMultiUserTestBase : BaseHostJUnit4Test() {
    private val RUNNER = "androidx.test.runner.AndroidJUnitRunner"
    private val DEFAULT_TIMEOUT = 10 * 60 * 1000L

    protected val TEST_PACKAGE = "android.permission.host.cts.app"

    protected var createdUser = 0
    protected var createdProfile = 0
    protected var user = 0

    @Before
    fun setUp() {
        Assume.assumeTrue("Device does not support multiple users",
                device.maxNumberOfUsersSupported > 1)
        user = device.primaryUserId
        device.executeShellCommand(
                "pm revoke --user $user $TEST_PACKAGE android.permission.INTERACT_ACROSS_USERS")
        device.executeShellCommand("appops reset --user $user $TEST_PACKAGE")

        val createProfileOutput = device.executeShellCommand(
                "pm create-user --managed --profileOf $user profile_test_user")
        createdProfile = createProfileOutput.substring(createProfileOutput.lastIndexOf(" "))
                .trim().toInt()
        device.executeShellCommand("pm install-existing --user $createdProfile $TEST_PACKAGE")

        createdUser = device.createUser("full_test_user")
        device.executeShellCommand("pm install-existing --user $createdUser $TEST_PACKAGE")
    }

    @After
    fun tearDown() {
        if (createdUser != 0) {
            device.removeUser(createdUser)
        }

        if (createdProfile != 0) {
            device.removeUser(createdProfile)
        }
    }

    protected fun runDeviceTest(testClassName: String, testMethodName: String) {
        runDeviceTests(device, RUNNER, TEST_PACKAGE, "$TEST_PACKAGE.$testClassName",
                testMethodName, user, DEFAULT_TIMEOUT, DEFAULT_TIMEOUT, 0L, true, false,
                mapOf("createdUser" to createdUser.toString(),
                        "createdProfile" to createdProfile.toString()))
    }
}