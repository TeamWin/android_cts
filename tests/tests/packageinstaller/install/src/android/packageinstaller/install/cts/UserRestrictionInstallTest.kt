/*
 * Copyright (C) 2023 The Android Open Source Project
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

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageInstaller.SessionParams
import android.os.Bundle
import android.os.UserManager.DISALLOW_DEBUGGING_FEATURES
import android.os.UserManager.DISALLOW_INSTALL_APPS
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject
import android.support.test.uiautomator.UiSelector
import androidx.core.content.FileProvider
import androidx.test.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasAppOp
import com.android.bedstead.harrier.annotations.EnsureHasPermission
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile
import com.android.bedstead.nene.TestApis
import com.android.bedstead.nene.appops.CommonAppOps.OPSTR_REQUEST_INSTALL_PACKAGES
import com.android.bedstead.nene.exceptions.AdbException
import com.android.bedstead.nene.permissions.CommonPermissions.INTERACT_ACROSS_USERS_FULL
import com.android.bedstead.nene.users.UserReference
import com.android.bedstead.nene.utils.ShellCommand
import com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME
import com.android.compatibility.common.util.ApiTest
import com.android.compatibility.common.util.BlockingBroadcastReceiver
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.assertFailsWith
import org.junit.After
import org.junit.Assert.fail
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(BedsteadJUnit4::class)
class UserRestrictionInstallTest : PackageInstallerTestBase() {
    private val APP_INSTALL_ACTION =
            "android.packageinstaller.install.cts.UserRestrictionInstallTest.action"
    private val INSTALL_BUTTON_ID = "button1"
    private val context = InstrumentationRegistry.getTargetContext()
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    companion object {
        @JvmField
        @ClassRule
        @Rule
        val sDeviceState = DeviceState()
    }

    @Before
    fun uninstallTestApp() {
        val cmd = ShellCommand.builder("pm uninstall")
        cmd.addOperand(TEST_APK_PACKAGE_NAME)
        try {
            cmd.execute()
        } catch (_: AdbException) {
            fail("Could not uninstall $TEST_APK_PACKAGE_NAME")
        }
    }

    @After
    fun clearUserRestrictions() {
        val restrictions = arrayOf(DISALLOW_DEBUGGING_FEATURES, DISALLOW_INSTALL_APPS)
        for (restriction in restrictions) {
            sDeviceState.dpc().devicePolicyManager().clearUserRestriction(
                    DPC_COMPONENT_NAME, restriction)
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun disallowDebuggingFeatures_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        setUserRestriction(DISALLOW_DEBUGGING_FEATURES)
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isTrue()
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isFalse()

        val primaryUser = sDeviceState.primaryUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_EXTERNAL_LOCATION/$TEST_APK_NAME")

        assertWithMessage("Test app should be installed in initial user")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(primaryUser))
                .isTrue()

        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
                .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun disallowDebuggingFeatures_adbInstallOnWorkProfile_fails() {
        setUserRestriction(DISALLOW_DEBUGGING_FEATURES)
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isTrue()
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isFalse()

        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_EXTERNAL_LOCATION/$TEST_APK_NAME",
                user = workProfile)

        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(TestApis.packages().find(TEST_APK_PACKAGE_NAME).installedOnUser(workProfile))
                .isFalse()
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowDebuggingFeatures_sessionInstallOnWorkProfile_getInstallRequest() {
        setUserRestriction(DISALLOW_DEBUGGING_FEATURES)
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isTrue()
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isFalse()

        val workProfile = sDeviceState.workProfile()
        val (_, session) = createSessionForUser(workProfile)
        try {
            writeSessionAsUser(workProfile, session)
            val result: Intent? = commitSessionAsUser(workProfile, session)
            assertThat(result).isNotNull()
            assertThat(result!!.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID))
                    .isEqualTo(STATUS_PENDING_USER_ACTION)
        } finally {
            session.abandon()
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES"])
    @EnsureHasAppOp(OPSTR_REQUEST_INSTALL_PACKAGES)
    @RequireRunOnWorkProfile
    fun disallowDebuggingFeatures_intentInstallOnWorkProfile_installationSucceeds() {
        setUserRestriction(DISALLOW_DEBUGGING_FEATURES)
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isTrue()
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isFalse()

        val context = TestApis.context().instrumentedContext()
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        clickInstallerUIButton(INSTALL_BUTTON_ID)

        // Install should have succeeded
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS)).isEqualTo(Activity.RESULT_OK)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun disallowInstallApps_adbInstallOnAllUsers_installedOnUnrestrictedUser() {
        setUserRestriction(DISALLOW_INSTALL_APPS)
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isTrue()
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isFalse()

        val primaryUser = sDeviceState.primaryUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_EXTERNAL_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(primaryUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
                .that(targetPackage.size).isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_INSTALL_APPS set")
                .that(targetPackage.size).isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun disallowInstallApps_adbInstallOnWorkProfile_fails() {
        setUserRestriction(DISALLOW_INSTALL_APPS)
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isTrue()
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isFalse()

        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_EXTERNAL_LOCATION/$TEST_APK_NAME",
                user = workProfile)

        val targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app shouldn't be installed in a work profile with " +
                "$DISALLOW_DEBUGGING_FEATURES set")
                .that(targetPackage.size).isEqualTo(0)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    @EnsureHasPermission(INTERACT_ACROSS_USERS_FULL)
    fun disallowInstallApps_sessionInstallOnWorkProfile_throwsException() {
        setUserRestriction(DISALLOW_INSTALL_APPS)
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isTrue()
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isFalse()

        val workProfile = sDeviceState.workProfile()
        assertFailsWith(SecurityException::class) {
            createSessionForUser(workProfile)
        }
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @EnsureHasAppOp(OPSTR_REQUEST_INSTALL_PACKAGES)
    @RequireRunOnWorkProfile
    fun disallowInstallApps_intentInstallOnWorkProfile_installationFails() {
        setUserRestriction(DISALLOW_INSTALL_APPS)
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isTrue()
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isFalse()

        val context = TestApis.context().instrumentedContext()
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        val appInstallIntent = getAppInstallationIntent(apkFile)

        val installation = startInstallationViaIntent(appInstallIntent)
        val okBtn: UiObject = uiDevice.findObject(UiSelector().text("OK"))
        okBtn.click()

        // Install should have failed
        assertThat(installation.get(TIMEOUT, TimeUnit.MILLISECONDS))
                .isEqualTo(Activity.RESULT_CANCELED)
    }

    @Test
    @ApiTest(apis = ["android.os.UserManager#DISALLOW_DEBUGGING_FEATURES",
        "android.os.UserManager#DISALLOW_INSTALL_APPS"])
    @EnsureHasWorkProfile(dpcIsPrimary = true)
    fun unrestrictedWorkProfile_adbInstallOnAllUsers_installedOnAllUsers() {
        assertThat(isUserRestricted(DISALLOW_DEBUGGING_FEATURES)).isFalse()
        assertThat(isUserRestricted(DISALLOW_INSTALL_APPS)).isFalse()

        val primaryUser = sDeviceState.primaryUser()
        val workProfile = sDeviceState.workProfile()

        installPackageViaAdb(apkPath = "$TEST_APK_EXTERNAL_LOCATION/$TEST_APK_NAME")

        var targetPackage = TestApis.packages().installedForUser(primaryUser).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in initial user")
                .that(targetPackage.size).isNotEqualTo(0)

        targetPackage = TestApis.packages().installedForUser(workProfile).filter {
            it.packageName().equals(TEST_APK_PACKAGE_NAME)
        }
        assertWithMessage("Test app should be installed in work profile")
                .that(targetPackage.size).isNotEqualTo(0)
    }

    private fun installPackageViaAdb(apkPath: String, user: UserReference? = null): String? {
        val cmd = ShellCommand.builderForUser(user, "pm install")
        cmd.addOperand(apkPath)
        return try {
            cmd.execute()
        } catch (e: AdbException) {
            null
        }
    }

    @Throws(SecurityException::class)
    private fun createSessionForUser(user: UserReference = sDeviceState.primaryUser()):
            Pair<Int, Session> {
        val context = TestApis.context().androidContextAsUser(user)
        val pm = context.packageManager
        val pi = pm.packageInstaller

        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)
        params.setRequireUserAction(SessionParams.USER_ACTION_REQUIRED)

        val sessionId = pi.createSession(params)
        val session = pi.openSession(sessionId)

        return Pair(sessionId, session)
    }

    private fun writeSessionAsUser(
        user: UserReference = sDeviceState.primaryUser(),
        session: Session
    ) {
        val context = TestApis.context().androidContextAsUser(user)
        val apkFile = File(context.filesDir, TEST_APK_NAME)
        // Write data to session
        apkFile.inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }
    }

    private fun commitSessionAsUser(
        user: UserReference = sDeviceState.primaryUser(),
        session: Session
    ): Intent? {
        val context = TestApis.context().androidContextAsUser(user)
        val receiver: BlockingBroadcastReceiver =
                sDeviceState.registerBroadcastReceiverForUser(user, APP_INSTALL_ACTION)
        receiver.register()

        val intent = Intent(APP_INSTALL_ACTION).setPackage(context.packageName)
        val pendingIntent = PendingIntent.getBroadcast(
                context, 0 /* requestCode */, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)

        session.commit(pendingIntent.intentSender)

        // The system should have asked us to launch the installer
        return receiver.awaitForBroadcast()
    }

    private fun getAppInstallationIntent(apkFile: File): Intent {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE)
        intent.data = FileProvider.getUriForFile(context, CONTENT_AUTHORITY, apkFile)
        intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        intent.putExtra(Intent.EXTRA_RETURN_RESULT, true)
        return intent
    }

    private fun setUserRestriction(restriction: String) {
        sDeviceState.dpc().devicePolicyManager().addUserRestriction(DPC_COMPONENT_NAME, restriction)
    }

    private fun isUserRestricted(restriction: String): Boolean {
        val restrictions: Bundle = sDeviceState.dpc().devicePolicyManager()
                .getUserRestrictions(DPC_COMPONENT_NAME)
        return restrictions.getBoolean(restriction, false)
    }
}
