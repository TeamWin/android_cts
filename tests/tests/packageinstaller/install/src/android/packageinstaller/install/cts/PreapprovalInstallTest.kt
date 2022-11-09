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

import android.content.pm.PackageInstaller
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.SystemUtil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class PreapprovalInstallTest : PackageInstallerTestBase() {

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
     * Check that we can request a user pre-approval
     */
    @Test
    fun requestUserPreapproval_userAgree_statusSuccess() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
        assertEquals(true, result.preapproval)
    }

    /**
     * Request a user pre-approval, but then cancel it when it prompts.
     */
    @Test
    fun requestUserPreapproval_userCancel_statusFailureAborted() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        // request should have been aborted
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_FAILURE_ABORTED, result.status)
        assertEquals(true, result.preapproval)
    }

    /**
     * Check that we cannot request a user preapproval with an approved session again.
     */
    @Test
    fun requestUserPreapproval_alreadyApproved_throwException() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        val details = preparePreapprovalDetails()
        startRequestUserPreapproval(session, details)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()
        try {
            startRequestUserPreapproval(session, details)
            fail("Cannot request user preapproval on an approved/rejected session again")
        } catch (expected: IllegalStateException) {
        }
    }

    /**
     * Check that we can install an app without prompt after getting pre-approval from users.
     */
    @Test
    fun commitPreapprovalSession_success() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME)
        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
        assertInstalled()
    }

    /**
     * Check that we can install an app with app label in another locale, and this label is
     * in the split APK file.
     */
    @Test
    fun commitPreapprovalSession_usingAnotherLocaleInSplitApk_success() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetailsInPl())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME)
        writeSession(session, TEST_APK_NAME_PL)
        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
        assertInstalled()
    }

    /**
     * Check that we can update an app without prompt after getting pre-approval from users.
     */
    @Test
    fun commitPreapprovalSession_update_success() {
        installTestPackage()
        assertInstalled()

        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME)
        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
    }

    /**
     * Check that the pre-approval install session fails when the information from
     * PreapprovalDetails doesn't match the APK files.
     */
    @Test
    fun commitPreapprovalSession_notMatchApk_fail() {
        // Using incorrect app label in PreapprovalDetails.
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, prepareWrongPreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME)
        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_FAILURE, result.status)
        assertNotInstalled()
    }

    /**
     * Check that a parent of multi-package session cannot be requested user pre-approval.
     */
    @Test
    fun requestUserPreapproval_multiPackageSession_throwException() {
        val (sessionId, session) = createSession(0 /* flags */, true /* isMultiPackage */,
                null /* packageSource */)
        try {
            startRequestUserPreapproval(session, preparePreapprovalDetails())
            fail("A parent of multi-package session cannot be requested.")
        } catch (expected: IllegalStateException) {
        }
    }

    /**
     * Check that a child of multi-package session can be requested user pre-approval.
     */
    @Test
    fun requestUserPreapproval_childSession_statusSuccess() {
        val (sessionId, session) = createSession(0 /* flags */, true /* isMultiPackage */,
                null /* packageSource */)
        val (childSessionId, childSession) = createSession(
                0 /* flags */, false /* isMultiPackage */, null /* packageSource */)
        session.addChildSessionId(childSessionId)

        startRequestUserPreapproval(childSession, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
        assertEquals(true, result.preapproval)
    }

    /**
     * Check that we can install the multi-package session without prompt after getting
     * pre-approval from users.
     */
    @Test
    fun commitPreapprovalSession_multiPackage_successWithoutUserAction() {
        val (sessionId, session) = createSession(0 /* flags */, true /* isMultiPackage */,
                null /* packageSource */)
        val (childSessionId, childSession) = createSession(
                0 /* flags */, false /* isMultiPackage */, null /* packageSource */)

        startRequestUserPreapproval(childSession, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        // Add the pre-approved session into a multi-package session, and commit
        writeSession(childSession, TEST_APK_NAME)
        session.addChildSessionId(childSessionId)

        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
        assertInstalled()
    }

    @Test
    fun requestUserPreapproval_featureDisabled_statusFailureBlocked() {
        val config = getDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE)
        setDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE, "false")

        try {
            val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                    null /* packageSource */)
            startRequestUserPreapproval(session, preparePreapprovalDetails(),
                    false /* expectedPrompt */)

            val result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_FAILURE_BLOCKED, result.status)
        } finally {
            setDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE, config)
        }
    }

    @Test
    fun requestUserPreapproval_featureDisabled_couldUseCommitInstead() {
        val config = getDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE)
        setDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE, "false")

        try {
            val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                    null /* packageSource */)
            startRequestUserPreapproval(session, preparePreapprovalDetails(),
                    false /* expectedPrompt */)

            // request should be failed
            getInstallSessionResult()

            writeSession(session, TEST_APK_NAME)
            startInstallationViaPreapprovalSession(session)
            // Since requestUserPreapproval isn't allowed, the installers should be able to use
            // typical install flow instead.
            var result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, result.status)

            clickInstallerUIButton(INSTALL_BUTTON_ID)

            result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertInstalled()
        } finally {
            setDeviceProperty(PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE, config)
        }
    }

    private fun getDeviceProperty(name: String): String? {
        return SystemUtil.callWithShellPermissionIdentity {
            DeviceConfig.getProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name)
        }
    }

    private fun setDeviceProperty(name: String, value: String?) {
        SystemUtil.callWithShellPermissionIdentity {
            DeviceConfig.setProperty(DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE, name, value,
                    false /* makeDefault */)
        }
    }
}
