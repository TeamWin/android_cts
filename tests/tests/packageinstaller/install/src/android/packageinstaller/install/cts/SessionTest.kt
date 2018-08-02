/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.packageinstaller.install.cts

import android.app.Activity.RESULT_CANCELED
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.Intent
import android.content.pm.ApplicationInfo.CATEGORY_MAPS
import android.content.pm.ApplicationInfo.CATEGORY_UNDEFINED
import android.content.pm.PackageInstaller.STATUS_FAILURE_ABORTED
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.STATUS_SUCCESS
import android.content.pm.PackageManager
import android.support.test.InstrumentationRegistry
import android.support.test.runner.AndroidJUnit4
import android.support.test.uiautomator.By
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.Until
import com.android.compatibility.common.util.AppOpsUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

private const val INSTALL_BUTTON_ID = "ok_button"
private const val CANCEL_BUTTON_ID = "cancel_button"

@RunWith(AndroidJUnit4::class)
class SessionTest : PackageInstallerTestBase() {
    private val context = InstrumentationRegistry.getTargetContext()
    private val pm = context.packageManager
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Before
    fun allowToInstallPackages() {
        AppOpsUtils.setOpMode(context.packageName, APP_OP_STR, MODE_ALLOWED)
    }

    /**
     * Wait for result of install dialog and return it
     */
    private fun getInstallDialogResult(timeout: Long = TIMEOUT): Int? {
        return installDialogResults.poll(timeout, TimeUnit.MILLISECONDS)
    }

    private fun assertInstalled() {
        // Throws exception if package is not installed.
        pm.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
    }

    private fun assertNotInstalled() {
        try {
            pm.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    /**
     * Click a button in the UI of the installer app
     *
     * @param resId The resource ID of the button to click
     */
    private fun clickInstallerUIButton(resId: String) {
        uiDevice.wait(Until.findObject(By.res(PACKAGE_INSTALLER_PACKAGE_NAME, resId)), TIMEOUT)
                .click()
    }

    /**
     * Assert that there are no more callbacks from the install session or install dialog
     */
    private fun assertNoMoreInstallResults() {
        assertNull(getInstallSessionResult(0))
        assertEquals(0, installDialogResults.size)
    }

    /**
     * Check that we can install an app via a package-installer session
     */
    @Test
    fun confirmInstallation() {
        startInstallationViaSession()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertEquals(STATUS_SUCCESS, getInstallSessionResult())
        assertInstalled()

        // Even when the install succeeds the install confirm dialog returns 'canceled'
        assertEquals(RESULT_CANCELED, getInstallDialogResult())

        assertNoMoreInstallResults()

        assertTrue(AppOpsUtils.allowedOperationLogged(context.packageName, APP_OP_STR))
    }

    /**
     * Check that we can set an app category for an app we installed
     */
    @Test
    fun setAppCategory() {
        startInstallationViaSession()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Wait for installation to finish
        getInstallSessionResult()

        assertEquals(CATEGORY_UNDEFINED, pm.getApplicationInfo(TEST_APK_PACKAGE_NAME, 0).category)

        // This app installed the app, hence we can set the category
        pm.setApplicationCategoryHint(TEST_APK_PACKAGE_NAME, CATEGORY_MAPS)

        assertEquals(CATEGORY_MAPS, pm.getApplicationInfo(TEST_APK_PACKAGE_NAME, 0).category)
    }

    /**
     * Install an app via a package-installer session, but then cancel it when the package installer
     * pops open.
     */
    @Test
    fun cancelInstallation() {
        startInstallationViaSession()
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // Install should have been aborted
        assertEquals(STATUS_FAILURE_ABORTED, getInstallSessionResult())
        assertEquals(RESULT_CANCELED, getInstallDialogResult())
        assertNotInstalled()

        assertNoMoreInstallResults()
    }

    /**
     * Commit the same session twice.
     */
    @Test
    fun commitTwice() {
        // Create session and commit it. Then wait until install confirm dialog is launched
        val session = startInstallationViaSession()

        // Commit session the second time
        val pendingIntent = PendingIntent.getBroadcast(context, 0, Intent(INSTALL_ACTION_CB),
                FLAG_UPDATE_CURRENT)
        session.commit(pendingIntent.intentSender)

        // The system should have asked us to launch the install confirm dialog a second time
        assertEquals(STATUS_PENDING_USER_ACTION, getInstallSessionResult())

        // Confirm one dialog
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertEquals(STATUS_SUCCESS, getInstallSessionResult())
        assertInstalled()

        // The session should not have called back again
        assertNull(getInstallSessionResult(TIMEOUT_EXPECTED))

        // Both dialogs finish
        assertEquals(RESULT_CANCELED, getInstallDialogResult())
        assertEquals(RESULT_CANCELED, getInstallDialogResult())

        assertNoMoreInstallResults()
    }
}
