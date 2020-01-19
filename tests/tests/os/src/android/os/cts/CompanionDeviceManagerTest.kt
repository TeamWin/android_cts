/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.os.cts

import android.companion.CompanionDeviceManager
import android.net.MacAddress
import android.platform.test.annotations.AppModeFull
import android.test.InstrumentationTestCase
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity

const val DUMMY_MAC_ADDRESS = "00:00:00:00:00:10"
val InstrumentationTestCase.context get() = instrumentation.context

/**
 * Test for [CompanionDeviceManager]
 */
class CompanionDeviceManagerTest : InstrumentationTestCase() {

    val cdm by lazy { context.getSystemService(CompanionDeviceManager::class.java) }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    fun testIsDeviceAssociated() {
        val userId = context.userId
        val user = android.os.Process.myUserHandle()
        val packageName = context.packageName
        val isAssociated = {
            runWithShellPermissionIdentity<Boolean> {
                cdm.isDeviceAssociatedForWifiConnection(packageName,
                        MacAddress.fromString(DUMMY_MAC_ADDRESS), user)
            }
        }
        val shellIsAssociated = {
            runShellCommand("cmd companiondevice list $userId")
                    .lines()
                    .any {
                        packageName in it &&
                                DUMMY_MAC_ADDRESS in it
                    }
        }

        assertFalse(isAssociated())
        assertFalse(shellIsAssociated())

        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            assertTrue(isAssociated())
            assertTrue(shellIsAssociated())
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }
}