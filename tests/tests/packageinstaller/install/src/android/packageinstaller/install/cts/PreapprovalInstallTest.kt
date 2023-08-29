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
import android.app.PendingIntent
import android.app.compat.CompatChanges
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.icu.util.ULocale
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig
import android.support.test.uiautomator.By
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.DeviceConfigStateChangerRule
import com.android.compatibility.common.util.FutureResultActivity
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.regex.Pattern
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@AppModeFull(reason = "Instant apps cannot create installer sessions")
@RunWith(AndroidJUnit4::class)
class PreapprovalInstallTest : PackageInstallerTestBase() {

    companion object {
        const val TEST_APK_NAME_PL = "CtsEmptyTestApp_pl.apk"
        const val TEST_APK_NAME_V2 = "CtsEmptyTestAppV2.apk"
        const val TEST_APP_LABEL = "Empty Test App"
        const val TEST_APP_LABEL_PL = "Empty Test App Polish"
        const val TEST_APP_LABEL_V2 = "Empty Test App V2"
        const val TEST_FAKE_APP_LABEL = "Fake Test App"
        const val TEST_INSTALLER_APK_NAME = "CtsEmptyInstallerApp.apk"
        const val TEST_INSTALLER_APK_PACKAGE_NAME = "android.packageinstaller.emptyinstaller.cts"
        const val PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE = "is_preapproval_available"
        const val CHANGE_ID_PRE_APPROVAL_WITH_UPDATE_OWNERSHIP_FIX = 293644536L
    }

    private val apkFile_pl = File(context.filesDir, TEST_APK_NAME_PL)
    private val apkFile_v2 = File(context.filesDir, TEST_APK_NAME_V2)

    @get:Rule
    val deviceConfigPreApprovalRequestAvailable = DeviceConfigStateChangerRule(
        context, DeviceConfig.NAMESPACE_PACKAGE_MANAGER_SERVICE,
            PROPERTY_IS_PRE_APPROVAL_REQUEST_AVAILABLE, true.toString())

    @Before
    fun copyOtherTestApks() {
        File(TEST_APK_LOCATION, TEST_APK_NAME_PL).copyTo(target = apkFile_pl, overwrite = true)
        File(TEST_APK_LOCATION, TEST_APK_NAME_V2).copyTo(target = apkFile_v2, overwrite = true)
    }

    @Before
    fun checkPreconditions() {
        val res = context.getResources()
        val isPreapprovalAvailable = res.getBoolean(
            res.getIdentifier("config_isPreApprovalRequestAvailable", "bool", "android"))
        Assume.assumeTrue("Pre-approval is not available", isPreapprovalAvailable)
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
     * Check that we can request a user pre-approval with updating ownership case, the action of
     * the EXTRA_INTENT is PackageInstaller.ACTION_CONFIRM_PRE_APPROVAL.
     */
    @Test
    fun requestUserPreapprovalWithUpdateOwnership_userAgree_statusSuccess() {
        // If the build includes the fix, the feature is disabled. Otherwise, it is enabled.
        assumeFalse(
            "The rom doesn't include the fix for b/293644536",
            CompatChanges.isChangeEnabled(CHANGE_ID_PRE_APPROVAL_WITH_UPDATE_OWNERSHIP_FIX)
        )

        // Get the value of updateOwnership property to restore it finally
        var isUpdateOwnershipEnforcementAvailable: String? =
            getDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE)
        setDeviceProperty(PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE, "true")

        // Install the test installer to request update ownership
        installPackage(TEST_INSTALLER_APK_NAME)
        // Install the test app and enable update ownership enforcement with the test installer
        installTestPackage("--update-ownership -i $TEST_INSTALLER_APK_PACKAGE_NAME")
        assertInstalled()

        var isStatusPendingUserActionCalled = false
        var installResult = LinkedBlockingQueue<SessionResult>()

        // Create the receiver to handle the install result.
        // 1. If the Success status comes before STATUS_PENDING_USER_ACTION, we can assert it first
        // without waiting the timeout.
        // 2. Assert the action of the EXTRA_INTENT is PackageInstaller.ACTION_CONFIRM_PRE_APPROVAL
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE_INVALID
                )

                val preapproval = intent.getBooleanExtra(
                    PackageInstaller.EXTRA_PRE_APPROVAL,
                    false /* defaultValue */
                )
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                Log.d(TAG, "status: $status, msg: $msg preapproval: $preapproval")

                if (status == PackageInstaller.STATUS_SUCCESS) {
                    // Make sure the PendingUserAction is called before success
                    assertEquals(true, isStatusPendingUserActionCalled)
                } else if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    isStatusPendingUserActionCalled = true
                    val activityIntent =
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    assertEquals(
                        PackageInstaller.ACTION_CONFIRM_PRE_APPROVAL,
                        activityIntent!!.action
                    )
                    assertEquals(activityIntent.extras!!.keySet().size, 1)
                    activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or
                            Intent.FLAG_ACTIVITY_NEW_TASK)
                    installDialogStarter.activity.startActivityForResult(activityIntent)
                }

                installResult.offer(SessionResult(status, preapproval, msg))
            }
        }

        var uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation()

        try {
            // Adopt the INSTALL_PACKAGES permission
            uiAutomation.adoptShellPermissionIdentity(Manifest.permission.INSTALL_PACKAGES)

            val session = createSession(
                0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */
            ).second

            // Register the receiver for the install result
            val action = "PreapprovalInstallTest.install_cb"
            context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_EXPORTED)

            // Request user preapproval
            FutureResultActivity.doAndAwaitStart {
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 0 /* requestCode */,
                    Intent(action).setPackage(context.packageName)
                        .addFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
                session.requestUserPreapproval(
                    preparePreapprovalDetails(),
                    pendingIntent.intentSender
                )
            }

            // The system should have asked us to launch the installer with pre-approval true
            var result = getInstallSessionResult(installResult)
            assertEquals(PackageInstaller.STATUS_PENDING_USER_ACTION, result.status)
            assertEquals(true, result.preapproval)

            // Click the "Update anyway" button on the update ownership dialog
            clickInstallerUIButton(
                By.text(
                    Pattern.compile(
                        "UPDATE ANYWAY",
                        Pattern.CASE_INSENSITIVE
                    )
                )
            )

            // request should have succeeded
            result = getInstallSessionResult(installResult)
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertEquals(true, result.preapproval)
        } finally {
            context.unregisterReceiver(receiver)
            uninstallPackage(TEST_INSTALLER_APK_PACKAGE_NAME)
            uiAutomation.dropShellPermissionIdentity()
            setDeviceProperty(
                PROPERTY_IS_UPDATE_OWNERSHIP_ENFORCEMENT_AVAILABLE,
                isUpdateOwnershipEnforcementAvailable
            )
        }
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
     * Check that we cannot commit a session that has been declined by the user when
     * requesting user preapproval.
     */
    @Test
    fun requestUserPreapproval_userCancel_cannotCommitAgain() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(CANCEL_BUTTON_ID)

        getInstallSessionResult()
        try {
            commitSession(session)
            fail("Cannot commit an rejected session again")
        } catch (expected: SecurityException) {
        }
    }

    /**
     * Request a user pre-approval, but don't resolve it.
     * Check that we can install via commit this session later.
     */
    @Test
    fun requestUserPreapproval_doNothingAndCommitLater_installSuccessfully() {
        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)

        val latch = CountDownLatch(1)
        val dummyReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                // This is to make sure we receive the pre-approval intent before
                // committing the session
                val preapproval = intent.getBooleanExtra(PackageInstaller.EXTRA_PRE_APPROVAL, false)
                if (preapproval) {
                    latch.countDown()
                }
            }
        }

        try {
            val action = "PreapprovalInstallTest.install_cb"
            context.registerReceiver(dummyReceiver, IntentFilter(action),
                    Context.RECEIVER_EXPORTED)

            val pendingIntent = PendingIntent.getBroadcast(context, 0 /* requestCode */,
                    Intent(action).setPackage(context.packageName),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.requestUserPreapproval(preparePreapprovalDetails(), pendingIntent.intentSender)

            latch.await(2000, TimeUnit.MILLISECONDS)
            writeAndCommitSession(TEST_APK_NAME, session)
            clickInstallerUIButton(INSTALL_BUTTON_ID)

            val result = getInstallSessionResult()
            assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
            assertInstalled()
        } finally {
            context.unregisterReceiver(dummyReceiver)
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
        startRequestUserPreapproval(session, preparePreapprovalDetailsV2())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME_V2)
        startInstallationViaPreapprovalSession(session)
        // No need to click installer UI here.
        val result = getInstallSessionResult()
        assertEquals(PackageInstaller.STATUS_SUCCESS, result.status)
    }

    /**
     * Check that we can update an app with current app label even if the given label is
     * different from the label from the APK file.
     */
    @Test
    fun commitPreapprovalSession_updateUsingCurrentLabel_success() {
        installTestPackage()
        assertInstalled()

        val (sessionId, session) = createSession(0 /* flags */, false /* isMultiPackage */,
                null /* packageSource */)
        startRequestUserPreapproval(session, preparePreapprovalDetails())
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // request should have succeeded
        getInstallSessionResult()

        writeSession(session, TEST_APK_NAME_V2)
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

    /**
     * Check that we receive the expected status when requesting user pre-approval isn't available.
     */
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

    /**
     * Request a user pre-approval, but this feature is currently not available.
     * Check that we can still install via commit this session.
     */
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

    private fun preparePreapprovalDetails(): PackageInstaller.PreapprovalDetails {
        return preparePreapprovalDetails(TEST_APP_LABEL, ULocale.US, TEST_APK_PACKAGE_NAME)
    }

    private fun preparePreapprovalDetailsV2(): PackageInstaller.PreapprovalDetails {
        return preparePreapprovalDetails(TEST_APP_LABEL_V2, ULocale.US, TEST_APK_PACKAGE_NAME)
    }

    private fun preparePreapprovalDetailsInPl(): PackageInstaller.PreapprovalDetails {
        return preparePreapprovalDetails(TEST_APP_LABEL_PL, ULocale("pl"), TEST_APK_PACKAGE_NAME)
    }

    private fun prepareWrongPreapprovalDetails(): PackageInstaller.PreapprovalDetails {
        return preparePreapprovalDetails(TEST_FAKE_APP_LABEL, ULocale.US, TEST_APK_PACKAGE_NAME)
    }

    private fun preparePreapprovalDetails(
            appLabel: String,
            locale: ULocale,
            appPackageName: String
    ): PackageInstaller.PreapprovalDetails {
        return PackageInstaller.PreapprovalDetails.Builder()
                .setLabel(appLabel)
                .setLocale(locale)
                .setPackageName(appPackageName)
                .build()
    }
}
