/*
 * Copyright (C) 2022 The Android Open Source Project
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

import android.Manifest
import android.content.pm.PackageInstaller
import android.platform.test.annotations.AppModeFull
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class UpdateOwnershipEnforcementTest : PackageInstallerTestBase() {

    companion object {
        const val TEST_NOT_ALLOW_UPDATE_OWNERSHIP_APK_NAME =
                "CtsEmptyTestApp_NotAllowUpdateOwnership.apk"

        const val TEST_INSTALLER_APK_NAME = "CtsEmptyInstallerApp.apk"
        const val TEST_INSTALLER_APK_PACKAGE_NAME = "android.packageinstaller.emptyinstaller.cts"
    }

    private var isUpdateOwnershipEnforcementAvailable: String? = null
    private val notAllowUpdateOwnershipApkFile = File(
            context.filesDir,
            TEST_NOT_ALLOW_UPDATE_OWNERSHIP_APK_NAME
    )

    /**
     * Make sure the feature flag of update ownership enforcement is available.
     */
    @Before
    fun setUpdateOwnershipEnforcementAvailable() {
        isUpdateOwnershipEnforcementAvailable =
                getDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE)
        setDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE, "true")
    }

    /**
     * Install the test installer package.
     */
    @Before
    fun installTestInstaller() {
        installPackage(TEST_INSTALLER_APK_NAME)
    }

    /**
     * Restore the status of update ownership enforcement.
     */
    @After
    fun recoverUpdateOwnershipEnforcement() {
        setDeviceProperty(
                PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE,
                isUpdateOwnershipEnforcementAvailable
        )
    }

    /**
     * Uninstall the test installer package.
     */
    @After
    fun uninstallTestInstaller() {
        uninstallPackage(TEST_INSTALLER_APK_PACKAGE_NAME)
    }

    /**
     * Clean up all sessions created by this test.
     */
    @After
    fun cleanUpSessions() {
        pi.mySessions.forEach {
            try {
                pi.abandonSession(it.sessionId)
            } catch (ignored: Exception) {
            }
        }
    }

    /**
     * Checks that we can get default value from isRequestUpdateOwnership.
     */
    @Test
    fun isRequestUpdateOwnership_notSet_returnFalse() {
        val (sessionId, session) = createSession(
                0 /* installFlags */,
                false /* isMultiPackage */,
                null /* packageSource */
        )
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(false, sessionInfo!!.isRequestUpdateOwnership)
        assertEquals(false, session.isRequestUpdateOwnership)
    }

    /**
     * Checks that we can get correct value from isRequestUpdateOwnership.
     */
    @Test
    fun isRequestUpdateOwnership_set_returnTrue() {
        val (sessionId, session) = createSession(
                INSTALL_REQUEST_UPDATE_OWNERSHIP,
                false /* isMultiPackage */,
                null /* packageSource */
        )
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(true, sessionInfo!!.isRequestUpdateOwnership)
        assertEquals(true, session.isRequestUpdateOwnership)
    }

    /**
     * Checks that we can enforce the update ownership when the first install.
     */
    @Test
    fun setRequestUpdateOwnership_whenInitialInstall_hasUpdateOwner() {
        // First install the test app with enforcing the update ownership.
        startInstallationViaSession(INSTALL_REQUEST_UPDATE_OWNERSHIP)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)

        val sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        // This installer should be the update owner
        assertEquals(context.opPackageName, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we cannot enforce the update ownership when the update.
     */
    @Test
    fun setRequestUpdateOwnership_whenUpdate_hasNoUpdateOwner() {
        // First install the test app without enforcing the update ownership.
        installTestPackage()
        assertInstalled()

        // Try to update the app with using SessionParams.setRequestUpdateOwnership.
        startInstallationViaSession(INSTALL_REQUEST_UPDATE_OWNERSHIP)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)

        val sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        // Since we don't allow enabling the update ownership when the update, the update
        // owner should be null.
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that update owner is removed after it is uninstalled.
     */
    @Test
    fun uninstallUpdateOwner_hasNoUpdateOwner() {
        // Install the test apk and assign above test installer as the update owner
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        uninstallPackage(TEST_INSTALLER_APK_PACKAGE_NAME)

        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that shell command can enable the update ownership enforcement.
     */
    @Test
    fun installViaShellCommand_enableUpdateOwnership() {
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        val sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that shell command can request to update the update owner.
     */
    @Test
    fun installViaShellCommand_requestUpdateOwner() {
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        // Update without '--request-update-owner' and assign self as the installer.
        installTestPackage("-i " + context.opPackageName)
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        // Update with '--request-update-owner' and assign self as the installer.
        installTestPackage("--update-ownership -i " + context.opPackageName)
        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(context.opPackageName, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that an update owner can update the package without user action.
     */
    @Test
    fun updateOwnershipEnforcement_updateByOwner_hasNoUserAction() {
        // Install the test app and enable update ownership enforcement with self package
        installTestPackage("--update-ownership -i " + context.opPackageName)

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            startInstallationViaSessionNoPrompt()
            // No need to click installer UI here.

            val result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertInstalled()
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    /**
     * Checks that an installer needs user action to update a package when
     * it's not the update owner even if it has granted INSTALL_PACKAGES permission.
     */
    @Test
    fun updateOwnershipEnforcement_updateByNonOwner_hasUserAction() {
        // Install the test app and enable update ownership enforcement with another package
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            startInstallationViaSession()
            // Expecting a prompt to proceed.
            clickInstallerUIButton(INSTALL_BUTTON_ID)

            val result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertInstalled()
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    /**
     * Checks that a privileged installer can update the package without user action even if
     * it's not the update owner when the feature flag is turn off.
     */
    @Test
    fun featureDisabled_updateByNonOwner_hasNoUserAction() {
        // Install the test app and enable update ownership enforcement with another package
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        setDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE, "false")

        try {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)
            startInstallationViaSessionNoPrompt()
            // No need to click installer UI here.

            val result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertInstalled()
        } finally {
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity()
        }
    }

    /**
     * Checks that the update owner will change if the installer requests.
     */
    @Test
    fun setRequestUpdateOwnership_requestWhenUpdate_ownerChanged() {
        // Install the test app and enable update ownership enforcement with another package
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        startInstallationViaSession(INSTALL_REQUEST_UPDATE_OWNERSHIP)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)

        val sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(context.opPackageName, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that the update owner will retain if the installer doesn't request.
     */
    @Test
    fun setRequestUpdateOwnership_notRequestWhenUpdate_ownerRetained() {
        // Install the test app and enable update ownership enforcement with another package
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")

        startInstallationViaSession()
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)

        val sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that the pending user action reason is REASON_UNSPECIFIED when not requesting the
     * update ownership.
     */
    @Test
    fun getPendingUserActionReason_notRequestUpdateOwnership_reasonUnspecified() {
        installTestPackage()
        assertInstalled()

        val (sessionId, session) = createSession(
                0 /* installFlags */,
                false /* isMultiPackage */,
                null /* packageSource*/
        )
        writeAndCommitSession(TEST_APK_NAME, session)

        // Since SessionInfo will be null once install is complete, we need to get it when prompting
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(
                PackageInstaller.REASON_CONFIRM_PACKAGE_CHANGE,
                sessionInfo!!.getPendingUserActionReason()
        )

        clickInstallerUIButton(INSTALL_BUTTON_ID)
        // request should have succeeded
        getInstallSessionResult()
    }

    /**
     * Checks that the pending user action reason is REASON_OWNERSHIP_CHANGED when update owner
     * is changed.
     */
    @Test
    fun getPendingUserActionReason_requestUpdateOwner_reasonOwnershipChanged() {
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        assertInstalled()

        val (sessionId, session) = createSession(
                INSTALL_REQUEST_UPDATE_OWNERSHIP,
                false /* isMultiPackage */,
                null /* packageSource*/
        )
        writeAndCommitSession(TEST_APK_NAME, session)

        // Since SessionInfo will be null once install is complete, we need to get it when prompting
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(
                PackageInstaller.REASON_OWNERSHIP_CHANGED,
                sessionInfo!!.getPendingUserActionReason()
        )

        clickInstallerUIButton(INSTALL_BUTTON_ID)
        // request should have succeeded
        getInstallSessionResult()
    }

    /**
     * Checks that the pending user action reason is REASON_REMIND_OWNERSHIP when update owner
     * isn't changed.
     */
    @Test
    fun getPendingUserActionReason_notRequestUpdateOwner_reasonRemindOwnership() {
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        assertInstalled()

        val (sessionId, session) = createSession(
                0 /* installFlags */,
                false /* isMultiPackage */,
                null /* packageSource*/
        )
        writeAndCommitSession(TEST_APK_NAME, session)

        // Since SessionInfo will be null once install is complete, we need to get it when prompting
        val sessionInfo = pi.getSessionInfo(sessionId)
        assertNotNull(sessionInfo)
        assertEquals(
                PackageInstaller.REASON_REMIND_OWNERSHIP,
                sessionInfo!!.getPendingUserActionReason()
        )

        clickInstallerUIButton(INSTALL_BUTTON_ID)
        // request should have succeeded
        getInstallSessionResult()
    }

    /**
     * Checks that the app can opt out the update ownership via manifest attr.
     */
    @Test
    fun allowUpdateOwnership_shouldRemoveUpdateOwner() {
        copyTestNotAllowUpdateOwnershipApk()
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(TEST_INSTALLER_APK_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        startInstallationViaSession(
                0 /* installFlags */,
                TEST_NOT_ALLOW_UPDATE_OWNERSHIP_APK_NAME
        )
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)

        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    private fun copyTestNotAllowUpdateOwnershipApk() {
        File(
                TEST_APK_LOCATION,
                TEST_NOT_ALLOW_UPDATE_OWNERSHIP_APK_NAME
        ).copyTo(target = notAllowUpdateOwnershipApkFile, overwrite = true)
    }

    /**
     * Checks that the update owner can relinquish the update ownership.
     */
    @Test
    fun relinquishUpdateOwnership_fromUpdateOwner_shouldRemoveUpdateOwner() {
        installTestPackage("--update-ownership -i " + context.opPackageName)
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(context.opPackageName, sourceInfo.updateOwnerPackageName)

        pm.relinquishUpdateOwnership(TEST_APK_PACKAGE_NAME)

        sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(null, sourceInfo.updateOwnerPackageName)
    }

    /**
     * Checks that we cannot relinquish the update ownership from non-update owner.
     */
    @Test
    fun relinquishUpdateOwnership_notFromUpdateOwner_throwSecurityException() {
        installTestPackage("--update-ownership")
        var sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
        assertEquals(SHELL_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)

        try {
            pm.relinquishUpdateOwnership(TEST_APK_PACKAGE_NAME)
            fail("relinquishUpdateOwnership from non-update owner should throw SecurityException.")
        } catch (e: SecurityException) {
            // Expected behavior
            sourceInfo = pm.getInstallSourceInfo(TEST_APK_PACKAGE_NAME)
            assertEquals(SHELL_PACKAGE_NAME, sourceInfo.updateOwnerPackageName)
        }
    }
}
