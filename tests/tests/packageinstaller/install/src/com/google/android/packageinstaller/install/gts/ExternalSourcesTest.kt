/*
 * Copyright (C) 2017 Google Inc.
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
package com.google.android.packageinstaller.install.gts

import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_ERRORED
import android.content.Intent
import android.os.Build
import android.support.test.InstrumentationRegistry
import android.support.test.filters.MediumTest
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until
import androidx.core.content.FileProvider
import com.android.compatibility.common.util.ApiLevelUtil
import com.android.compatibility.common.util.AppOpsUtils
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

private const val CONTENT_AUTHORITY = "com.google.android.packageinstaller.install.gts.fileprovider"
private const val INSTALL_CONFIRM_TEXT_ID = "install_confirm_question"
private const val ALERT_DIALOG_TITLE_ID = "android:id/alertTitle"

@RunWith(AndroidJUnit4::class)
@MediumTest
class ExternalSourcesTest : PackageInstallerTestBase() {
    private val isAtLeastP = ApiLevelUtil.isAtLeast(Build.VERSION_CODES.P)

    private val context = InstrumentationRegistry.getTargetContext()
    private val pm = context.packageManager
    private val packageName = context.packageName
    private val apkFile = File(context.filesDir, TEST_APK_NAME)
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun onlyRunOnO() {
        assumeTrue(ApiLevelUtil.isAtLeast(Build.VERSION_CODES.O))
    }

    @Before
    fun copyTestApk() {
        File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME).copyTo(target = apkFile, overwrite = true)
    }

    private fun setAppOpsMode(mode: Int) {
        AppOpsUtils.setOpMode(packageName, APP_OP_STR, mode)
    }

    private fun startInstallationViaIntent() {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun assertUiObject(errorMessage: String, selector: BySelector) {
        assertNotNull(errorMessage, uiDevice.wait(Until.findObject(selector), TIMEOUT))
    }

    private fun assertInstallAllowed(errorMessage: String) {
        assertUiObject(errorMessage, By.res(PACKAGE_INSTALLER_PACKAGE_NAME,
                INSTALL_CONFIRM_TEXT_ID))
        uiDevice.pressBack()
    }

    private fun assertInstallBlocked(errorMessage: String) {
        assertUiObject(errorMessage, By.res(ALERT_DIALOG_TITLE_ID))
        uiDevice.pressBack()
    }

    private fun blockedSourceTest(startInstallation: () -> Unit) {
        setAppOpsMode(MODE_ERRORED)
        assertFalse("Package $packageName allowed to install packages after setting app op to " +
                "errored", pm.canRequestPackageInstalls())

        startInstallation()
        assertInstallBlocked("Install blocking dialog not shown when app op set to errored")

        if (isAtLeastP) {
            assertTrue("Operation not logged", AppOpsUtils.rejectedOperationLogged(packageName,
                    APP_OP_STR))
        }
    }

    @Test
    fun blockedSourceTestViaIntent() {
        blockedSourceTest { startInstallationViaIntent() }
    }

    @Test
    fun blockedSourceTestViaSession() {
        blockedSourceTest { startInstallationViaSession() }
    }

    private fun allowedSourceTest(startInstallation: () -> Unit) {
        setAppOpsMode(MODE_ALLOWED)
        assertTrue("Package $packageName blocked from installing packages after setting app op " +
                "to allowed", pm.canRequestPackageInstalls())

        startInstallation()
        assertInstallAllowed("Install confirmation not shown when app op set to allowed")

        if (isAtLeastP) {
            assertTrue("Operation not logged", AppOpsUtils.allowedOperationLogged(packageName,
                    APP_OP_STR))
        }
    }

    @Test
    fun allowedSourceTestViaIntent() {
        allowedSourceTest { startInstallationViaIntent() }
    }

    @Test
    fun allowedSourceTestViaSession() {
        allowedSourceTest { startInstallationViaSession() }
    }

    private fun defaultSourceTest(startInstallation: () -> Unit) {
        assertFalse("Package $packageName with default app ops state allowed to install packages",
                pm.canRequestPackageInstalls())

        startInstallation()
        assertInstallBlocked("Install blocking dialog not shown when app op set to default")

        if (isAtLeastP) {
            assertTrue("Operation not logged", AppOpsUtils.rejectedOperationLogged(packageName,
                    APP_OP_STR))
        }
    }

    @Test
    fun defaultSourceTestViaIntent() {
        defaultSourceTest { startInstallationViaIntent() }
    }

    @Test
    fun defaultSourceTestViaSession() {
        defaultSourceTest { startInstallationViaSession() }
    }
}
