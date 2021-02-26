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
import android.content.pm.PackageManager.FEATURE_COMPANION_DEVICE_SETUP
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.MacAddress
import android.os.UserHandle
import android.platform.test.annotations.AppModeFull
import android.test.InstrumentationTestCase
import android.widget.TextView
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.MatcherUtils.hasIdThat
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils.waitFindObject
import com.android.compatibility.common.util.children
import com.android.compatibility.common.util.click
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.Matchers.hasSize
import org.junit.Assert.assertThat
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

const val COMPANION_APPROVE_WIFI_CONNECTIONS =
        "android.permission.COMPANION_APPROVE_WIFI_CONNECTIONS"
const val DUMMY_MAC_ADDRESS = "00:00:00:00:00:10"
const val MANAGE_COMPANION_DEVICES = "android.permission.MANAGE_COMPANION_DEVICES"
const val SHELL_PACKAGE_NAME = "com.android.shell"
val InstrumentationTestCase.context get() = InstrumentationRegistry.getTargetContext()

/**
 * Test for [CompanionDeviceManager]
 */
@RunWith(AndroidJUnit4::class)
class CompanionDeviceManagerTest : InstrumentationTestCase() {

    val cdm: CompanionDeviceManager by lazy {
        context.getSystemService(CompanionDeviceManager::class.java)
    }

    private fun isShellAssociated(macAddress: String, packageName: String): Boolean {
        val userId = context.userId
        return runShellCommand("cmd companiondevice list $userId")
                .lines()
                .any {
                    packageName in it && macAddress in it
                }
    }

    private fun isCdmAssociated(
        macAddress: String,
        packageName: String,
        vararg permissions: String
    ): Boolean {
        return runWithShellPermissionIdentity(ThrowingSupplier {
            cdm.isDeviceAssociatedForWifiConnection(packageName,
                    MacAddress.fromString(macAddress), context.user)
        }, *permissions)
    }

    @Before
    fun assumeHasFeature() {
        assumeTrue(context.packageManager.hasSystemFeature(FEATURE_COMPANION_DEVICE_SETUP))
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociated() {
        val userId = context.userId
        val packageName = context.packageName

        assertFalse(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))

        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            assertTrue(isCdmAssociated(DUMMY_MAC_ADDRESS, packageName, MANAGE_COMPANION_DEVICES))
            assertTrue(isShellAssociated(DUMMY_MAC_ADDRESS, packageName))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testIsDeviceAssociatedWithCompanionApproveWifiConnectionsPermission() {
        assertTrue(isCdmAssociated(
            DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME, MANAGE_COMPANION_DEVICES,
            COMPANION_APPROVE_WIFI_CONNECTIONS))
        assertFalse(isShellAssociated(DUMMY_MAC_ADDRESS, SHELL_PACKAGE_NAME))
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testDump() {
        val userId = context.userId
        val packageName = context.packageName

        try {
            runShellCommand(
                    "cmd companiondevice associate $userId $packageName $DUMMY_MAC_ADDRESS")
            val output = runShellCommand("dumpsys companiondevice")
            assertThat(output, containsString(packageName))
            assertThat(output, containsString(DUMMY_MAC_ADDRESS))
        } finally {
            runShellCommand(
                    "cmd companiondevice disassociate $userId $packageName $DUMMY_MAC_ADDRESS")
        }
    }

    @AppModeFull(reason = "Companion API for non-instant apps only")
    @Test
    fun testProfiles() {
        val packageName = "android.os.cts.companiontestapp"
        installApk("/data/local/tmp/cts/os/CtsCompanionTestApp.apk")
        startApp(packageName)

        click("Watch")
        click("Associate")
        val device = waitFindNode(hasIdThat(containsString("device_list")))
                .children
                .find { it.className == TextView::class.java.name }
        assumeTrue("Test requires a discoverable bluetooth device nearby", device != null)
        device!!.click()

        eventually {
            assertThat(getAssociatedDevices(packageName), hasSize(1))
        }
        val deviceAddress = getAssociatedDevices(packageName)[0]

        runShellCommandOrThrow("cmd companiondevice simulate_connect $deviceAddress")
        assertPermission(packageName, "android.permission.CALL_PHONE", PERMISSION_GRANTED)

        runShellCommandOrThrow("cmd companiondevice simulate_disconnect $deviceAddress")
        assertPermission(packageName, "android.permission.CALL_PHONE", PERMISSION_GRANTED)
    }

    private fun getAssociatedDevices(
        pkg: String,
        user: UserHandle = android.os.Process.myUserHandle()
    ): List<String> {
        return runShellCommandOrThrow("cmd companiondevice list ${user.identifier}")
                .lines()
                .filter { it.startsWith(pkg) }
                .map { it.substringAfterLast(" ") }
    }
}

private fun click(label: String) {
    waitFindObject(byTextIgnoreCase(label)).click()
    waitForIdle()
}