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

package android.permission.host.cts.app

import android.Manifest.permission.INTERNET
import android.os.Process.myUid
import android.os.UserHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.google.common.truth.Truth.assertThat
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.reflect.KClass

/**
 * Device-tests for {@link android.permission.host.cts.CrossUserPermissionVisibilityTests}
 */
@RunWith(AndroidJUnit4::class)
class CrossUserPermissionVisibilityTests : PermissionMultiUserTestBase() {
    @Test
    fun canCheckPermissionInDifferentUser() {
        context.checkPermission(INTERNET, -1,
                UserHandle.getUid(createdUser, UserHandle.getAppId(myUid())))
    }

    @Test(expected = SecurityException::class)
    fun cannotCheckPermissionInDifferentUser() {
        context.checkPermission(INTERNET, -1,
                UserHandle.getUid(createdUser, UserHandle.getAppId(myUid())))
    }

    @Test
    fun canCheckPermissionInDifferentProfile() {
        context.checkPermission(INTERNET, -1,
                UserHandle.getUid(createdProfile, UserHandle.getAppId(myUid())))
    }

    @Test(expected = SecurityException::class)
    fun cannotCheckPermissionInDifferentProfile() {
        context.checkPermission(INTERNET, -1,
                UserHandle.getUid(createdProfile, UserHandle.getAppId(myUid())))
    }

    @Test
    fun permissionCacheGetsInvalidatedWhenCrossProfileAppOpIsDenied() {
        runShellCommand("appops set ${myUid()} INTERACT_ACROSS_PROFILES allow")
        context.checkPermission(INTERNET, -1,
                UserHandle.getUid(createdProfile, UserHandle.getAppId(myUid())))

        runShellCommand("appops set ${myUid()} INTERACT_ACROSS_PROFILES default")
        assertThrows(SecurityException::class) {
            context.checkPermission(INTERNET, -1,
                    UserHandle.getUid(createdProfile, UserHandle.getAppId(myUid())))
        }
    }

    private fun assertThrows(exceptionClass: KClass<out Exception>, r: () -> Unit) {
        try {
            r()
        } catch (exception: Exception) {
            assertThat(exception).isInstanceOf(exceptionClass.java)
            return
        }

        fail("Expected $exceptionClass to be thrown.")
    }
}