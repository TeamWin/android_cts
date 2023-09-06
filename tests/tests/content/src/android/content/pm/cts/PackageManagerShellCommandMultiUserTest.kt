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

package android.content.pm.cts

import android.Manifest
import android.app.UiAutomation
import android.content.Context
import android.content.Context.RECEIVER_EXPORTED
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.PackageManager.MATCH_ARCHIVED_PACKAGES
import android.content.pm.PackageManager.MATCH_KNOWN_PACKAGES
import android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES
import android.content.pm.PackageManager.PackageInfoFlags
import android.content.pm.cts.PackageManagerShellCommandInstallTest.PackageBroadcastReceiver
import android.content.pm.cts.util.AbandonAllPackageSessionsRule
import android.os.Handler
import android.os.HandlerThread
import android.os.UserManager
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.android.bedstead.harrier.BedsteadJUnit4
import com.android.bedstead.harrier.DeviceState
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser
import com.android.bedstead.nene.users.UserReference
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import java.io.File
import java.util.regex.Pattern
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@EnsureHasSecondaryUser
@RunWith(BedsteadJUnit4::class)
@AppModeFull(reason = "Cannot query other apps if instant")
class PackageManagerShellCommandMultiUserTest {

    companion object {

        private const val TEST_APP_PACKAGE = PackageManagerShellCommandInstallTest.TEST_APP_PACKAGE
        private const val TEST_HW5 = PackageManagerShellCommandInstallTest.TEST_HW5

        @JvmField
        @ClassRule(order = 0)
        @Rule
        val deviceState = DeviceState()

        @JvmField
        @ClassRule(order = 1)
        var mAbandonSessionsRule = AbandonAllPackageSessionsRule()

        @JvmField
        @ClassRule(order = 2)
        var mBroadcastBarrierRule = object : TestRule {
            override fun apply(base: Statement, description: Description): Statement {
                return object : Statement() {
                    override fun evaluate() {
                        if (description.isSuite()) {
                            // Run "wait-for-broadcast-idle" to clear any
                            // pending broadcasts in the System so that they do not have any
                            // impact on the tests.
                            SystemUtil.runShellCommand("am wait-for-broadcast-idle")
                        }
                        base.evaluate()
                    }
                }
            }
        }

        private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        private val uiAutomation: UiAutomation =
            InstrumentationRegistry.getInstrumentation().uiAutomation

        private var backgroundThread = HandlerThread("PackageManagerShellCommandMultiUserTest")

        fun skipTheInstallType(installTypeString: String): Boolean {
            if (installTypeString == "install-incremental" &&
                !context.packageManager.hasSystemFeature(
                    PackageManager.FEATURE_INCREMENTAL_DELIVERY)) {
                return true
            }
            return false
        }
    }

    private lateinit var primaryUser: UserReference
    private lateinit var secondaryUser: UserReference

    @Before
    fun cacheUsers() {
        primaryUser = deviceState.primaryUser()
        secondaryUser = deviceState.secondaryUser()
    }

    private var mPackageVerifier: String? = null
    private var mStreamingVerificationTimeoutMs =
        PackageManagerShellCommandInstallTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS

    @Before
    fun setup() {
        assumeTrue(UserManager.supportsMultipleUsers())
        assumeFalse(getUserManager().hasUserRestriction(UserManager.DISALLOW_REMOVE_USER))
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandInstallTest.isAppInstalled(TEST_APP_PACKAGE))
        mPackageVerifier =
            SystemUtil.runShellCommand("settings get global verifier_verify_adb_installs")
        // Disable the package verifier for non-incremental installations to avoid the dialog
        // when installing an app.
        SystemUtil.runShellCommand("settings put global verifier_verify_adb_installs 0")
        mStreamingVerificationTimeoutMs = SystemUtil.runShellCommand(
            "settings get global streaming_verifier_timeout"
        )
            .toLongOrNull()
            ?: PackageManagerShellCommandInstallTest.DEFAULT_STREAMING_VERIFICATION_TIMEOUT_MS
    }

    @After
    fun reset() {
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(PackageManagerShellCommandInstallTest.isAppInstalled(TEST_APP_PACKAGE))
        assertEquals(null, PackageManagerShellCommandInstallTest.getSplits(TEST_APP_PACKAGE))

        // Reset the global settings to their original values.
        SystemUtil.runShellCommand(
            "settings put global verifier_verify_adb_installs $mPackageVerifier"
        )

        // Set the test override to invalid.
        setSystemProperty("debug.pm.uses_sdk_library_default_cert_digest", "invalid")
        setSystemProperty("debug.pm.prune_unused_shared_libraries_delay", "invalid")
        setSystemProperty("debug.pm.adb_verifier_override_packages", "invalid")
    }

    @Test
    fun testGetFirstInstallTime() {
        val startTimeMillisForPrimaryUser = System.currentTimeMillis()
        installPackageAsUser(TEST_HW5, primaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        val origFirstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // Validate the timestamp
        assertTrue(origFirstInstallTimeForPrimaryUser > 0)
        assertTrue(startTimeMillisForPrimaryUser < origFirstInstallTimeForPrimaryUser)
        assertTrue(System.currentTimeMillis() > origFirstInstallTimeForPrimaryUser)

        // Install again with replace and the firstInstallTime should remain the same
        installPackage(TEST_HW5)
        var firstInstallTimeForPrimaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)

        // Start another user and install this test itself for that user
        var startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(context.packageName, secondaryUser)
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        // Install test package with replace
        installPackageAsUser(TEST_HW5, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime should remain unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        var firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for the other user
        uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Install test package as an existing package
        startTimeMillisForSecondaryUser = System.currentTimeMillis()
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // firstInstallTime still remains unchanged for the current user
        assertEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // firstInstallTime for the other user should be different
        assertNotEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
        assertTrue(startTimeMillisForSecondaryUser < firstInstallTimeForSecondaryUser)
        assertTrue(System.currentTimeMillis() > firstInstallTimeForSecondaryUser)

        // Uninstall for all users
        uninstallPackageSilently(TEST_APP_PACKAGE)
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        // Reinstall for all users
        installPackage(TEST_HW5)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        firstInstallTimeForPrimaryUser = getFirstInstallTimeAsUser(TEST_APP_PACKAGE, primaryUser)
        // First install time is now different because the package was fully uninstalled
        assertNotEquals(origFirstInstallTimeForPrimaryUser, firstInstallTimeForPrimaryUser)
        firstInstallTimeForSecondaryUser =
            getFirstInstallTimeAsUser(TEST_APP_PACKAGE, secondaryUser)
        // Same firstInstallTime because package was installed for both users at the same time
        assertEquals(firstInstallTimeForPrimaryUser, firstInstallTimeForSecondaryUser)
    }

    @Test
    fun testPackageRemovedBroadcastsMultiUser() {
        if (!backgroundThread.isAlive) {
            backgroundThread.start()
        }
        val backgroundHandler = Handler(backgroundThread.looper)
        installExistingPackageAsUser(context.packageName, secondaryUser)
        installPackage(TEST_HW5)
        assertTrue(isAppInstalledForUser(context.packageName, primaryUser))
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        val removedBroadcastReceiverForPrimaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            primaryUser.id(),
            Intent.ACTION_PACKAGE_REMOVED
        )
        val removedBroadcastReceiverForSecondaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            secondaryUser.id(),
            Intent.ACTION_PACKAGE_REMOVED
        )
        val fullyRemovedBroadcastReceiverForPrimaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            primaryUser.id(),
            Intent.ACTION_PACKAGE_FULLY_REMOVED
        )
        val fullyRemovedBroadcastReceiverForSecondaryUser = PackageBroadcastReceiver(
            TEST_APP_PACKAGE,
            secondaryUser.id(),
            Intent.ACTION_PACKAGE_FULLY_REMOVED
        )
        val intentFilter = IntentFilter()
        intentFilter.addAction(Intent.ACTION_PACKAGE_REMOVED)
        intentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED)
        intentFilter.addDataScheme("package")
        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val contextPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                val contextSecondaryUser = context.createContextAsUser(
                    secondaryUser.userHandle(),
                    0
                )
                contextPrimaryUser.registerReceiver(
                    removedBroadcastReceiverForPrimaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextPrimaryUser.registerReceiver(
                    fullyRemovedBroadcastReceiverForPrimaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextSecondaryUser.registerReceiver(
                    removedBroadcastReceiverForSecondaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                contextSecondaryUser.registerReceiver(
                    fullyRemovedBroadcastReceiverForSecondaryUser,
                    intentFilter,
                    null,
                    backgroundHandler,
                    RECEIVER_EXPORTED
                )
                // Uninstall with "keep data" sends the REMOVED broadcast but not the FULLY_REMOVED
                // Only the targeted user will get the broadcast
                uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.reset()
                uninstallPackageWithKeepData(TEST_APP_PACKAGE, primaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForPrimaryUser.reset()

                installPackage(TEST_HW5)
                // Uninstall without "KEEP DATA" sends both REMOVED and FULLY_REMOVED broadcasts
                // to the targeted user
                uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastNotReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                removedBroadcastReceiverForSecondaryUser.reset()
                fullyRemovedBroadcastReceiverForSecondaryUser.reset()

                installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
                // Uninstall on both users sends the broadcasts to both users
                uninstallPackageSilently(TEST_APP_PACKAGE)
                removedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                fullyRemovedBroadcastReceiverForPrimaryUser.assertBroadcastReceived()
                removedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()
                fullyRemovedBroadcastReceiverForSecondaryUser.assertBroadcastReceived()

                // Clean up
                contextPrimaryUser.unregisterReceiver(removedBroadcastReceiverForPrimaryUser)
                contextPrimaryUser.unregisterReceiver(fullyRemovedBroadcastReceiverForPrimaryUser)
                contextSecondaryUser.unregisterReceiver(removedBroadcastReceiverForSecondaryUser)
                contextSecondaryUser.unregisterReceiver(
                    fullyRemovedBroadcastReceiverForSecondaryUser
                )
                backgroundThread.interrupt()
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    @Test
    fun testListPackageDefaultAllUsers() {
        installPackageAsUser(TEST_HW5, primaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertFalse(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        var out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out.split(":").last().split(",").size == 1)
        out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertEquals("", out)
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
            .replace("\n", "")
        var installedUsersCount = out.split(":").last().split(",").size
        assertTrue(out, installedUsersCount >= 1)
        installExistingPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser))
        assertTrue(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser))
        out = SystemUtil.runShellCommand("pm list packages -U $TEST_APP_PACKAGE")
            .replace("\n", "")
        assertTrue(out, out.split(":").last().split(",").size > installedUsersCount)
        out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${primaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out, out.split(":").last().split(",").size == 1)
        out = SystemUtil.runShellCommand(
            "pm list packages -U --user ${secondaryUser.id()} $TEST_APP_PACKAGE"
        ).replace("\n", "")
        assertTrue(out, out.split(":").last().split(",").size == 1)
    }

    @Test
    fun testCreateUserCurAsType() {
        val oldPropertyValue = getSystemProperty(UserManager.DEV_CREATE_OVERRIDE_PROPERTY)
        setSystemProperty(UserManager.DEV_CREATE_OVERRIDE_PROPERTY, "1")
        try {
            val pattern = Pattern.compile("Success: created user id (\\d+)\\R*")
            var commandResult = SystemUtil.runShellCommand(
                "pm create-user --profileOf cur " +
                    "--user-type android.os.usertype.profile.CLONE test"
            )
            var matcher = pattern.matcher(commandResult)
            assertTrue(commandResult, matcher.find())
            commandResult = SystemUtil.runShellCommand("pm remove-user " + matcher.group(1))
            assertEquals("Success: removed user\n", commandResult)
            commandResult = SystemUtil.runShellCommand(
                "pm create-user --profileOf current " +
                    "--user-type android.os.usertype.profile.CLONE test"
            )
            matcher = pattern.matcher(commandResult)
            assertTrue(commandResult, matcher.find())
            commandResult = SystemUtil.runShellCommand("pm remove-user " + matcher.group(1))
            assertEquals("Success: removed user\n", commandResult)
        } finally {
            setSystemProperty(
                UserManager.DEV_CREATE_OVERRIDE_PROPERTY,
                if (oldPropertyValue.isEmpty()) "invalid" else oldPropertyValue
            )
        }
    }

    @Test
    fun testUninstallWithKeepDataMultiUserMatchUninstalled() {
        testUninstallWithKeepDataMultiUser(PackageInfoFlags.of(MATCH_UNINSTALLED_PACKAGES.toLong()))
    }

    @Test
    fun testUninstallWithKeepDataMultiUserMatchKnown() {
        testUninstallWithKeepDataMultiUser(PackageInfoFlags.of(MATCH_KNOWN_PACKAGES.toLong()))
    }

    @Test
    fun testUninstallWithKeepDataMultiUserMatchArchived() {
        testUninstallWithKeepDataMultiUser(PackageInfoFlags.of(MATCH_ARCHIVED_PACKAGES.toLong()))
    }

    private fun testUninstallWithKeepDataMultiUser(matchFlag: PackageInfoFlags) {
        // Install this test itself for the secondary user
        installExistingPackageAsUser(context.packageName, secondaryUser)
        assertTrue(isAppInstalledForUser(context.packageName, secondaryUser))
        // Install the test package
        installPackageAsUser(TEST_HW5, primaryUser)
        installPackageAsUser(TEST_HW5, secondaryUser)
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, primaryUser)).isTrue()
        assertThat(isAppInstalledForUser(TEST_APP_PACKAGE, secondaryUser)).isTrue()
        // Delete app with DELETE_KEEP_DATA on second user
        uninstallPackageWithKeepData(TEST_APP_PACKAGE, secondaryUser)

        runWithShellPermissionIdentity(
            uiAutomation,
            {
                val pmPrimaryUser = context.createContextAsUser(primaryUser.userHandle(), 0)
                    .packageManager
                val pmSecondaryUser = context.createContextAsUser(secondaryUser.userHandle(), 0)
                    .packageManager
                // App is not queryable without the match flag
                assertThrows(PackageManager.NameNotFoundException::class.java) {
                    pmSecondaryUser.getPackageInfo(
                        TEST_APP_PACKAGE,
                        PackageInfoFlags.of(0L)
                    )
                }
                // Queryable with the match flag
                var packageInfoSecondUser = pmSecondaryUser.getPackageInfo(
                    TEST_APP_PACKAGE,
                    matchFlag
                )
                val oldDataDir = packageInfoSecondUser.applicationInfo!!.dataDir
                assertThat(oldDataDir).isNotNull()
                // Delete app on primary user without DELETE_KEEP_DATA flag and verify that the app is
                // still queryable with the match flag
                uninstallPackageAsUser(TEST_APP_PACKAGE, primaryUser)
                packageInfoSecondUser = pmSecondaryUser.getPackageInfo(
                    TEST_APP_PACKAGE,
                    matchFlag
                )
                assertThat(packageInfoSecondUser.applicationInfo!!.dataDir).isEqualTo(oldDataDir)
                // Expect not to throw when the package is queryable
                pmPrimaryUser.getPackageInfo(
                    TEST_APP_PACKAGE,
                    matchFlag
                )
                // Reinstall the app on the second user and verifies that the data dir stays the same,
                // and the app is still not queryable on the primary user
                installPackageAsUser(TEST_HW5, secondaryUser)
                packageInfoSecondUser = pmSecondaryUser.getPackageInfo(
                    TEST_APP_PACKAGE,
                    PackageInfoFlags.of(0L)
                )
                assertThat(packageInfoSecondUser.applicationInfo!!.dataDir).isEqualTo(oldDataDir)
                pmPrimaryUser.getPackageInfo(
                    TEST_APP_PACKAGE,
                    matchFlag
                )
                // Fully uninstall the app on the second user and verify it's no longer queryable
                uninstallPackageAsUser(TEST_APP_PACKAGE, secondaryUser)
                assertThrows(PackageManager.NameNotFoundException::class.java) {
                    pmSecondaryUser.getPackageInfo(
                        TEST_APP_PACKAGE,
                        matchFlag
                    )
                }
            },
            Manifest.permission.INTERACT_ACROSS_USERS,
            Manifest.permission.INTERACT_ACROSS_USERS_FULL
        )
    }

    private fun getUserManager(): UserManager {
        return context.getSystemService(UserManager::class.java)!!
    }

    private fun getFirstInstallTimeAsUser(packageName: String, user: UserReference) =
        context.createContextAsUser(user.userHandle(), 0)
            .packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .firstInstallTime

    private fun installPackage(baseName: String) {
        val file = File(PackageManagerShellCommandInstallTest.createApkPath(baseName))
        assertThat(SystemUtil.runShellCommand("pm install -t -g ${file.path}"))
            .isEqualTo("Success\n")
    }

    private fun installExistingPackageAsUser(packageName: String, user: UserReference) {
        val userId = user.id()
        assertThat(SystemUtil.runShellCommand("pm install-existing --user $userId $packageName"))
            .isEqualTo("Package $packageName installed for user: $userId\n")
    }

    private fun installPackageAsUser(
        baseName: String,
        user: UserReference
    ) {
        val file = File(PackageManagerShellCommandInstallTest.createApkPath(baseName))
        assertThat(
            SystemUtil.runShellCommand(
                "pm install -t -g --user ${user.id()} ${file.path}"
            )
        )
            .isEqualTo("Success\n")
    }

    private fun uninstallPackageAsUser(packageName: String, user: UserReference) =
        assertThat(SystemUtil.runShellCommand("pm uninstall --user ${user.id()} $packageName"))
            .isEqualTo("Success\n")

    private fun uninstallPackageWithKeepData(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm uninstall -k --user ${user.id()} $packageName")

    private fun uninstallPackageSilently(packageName: String) =
        SystemUtil.runShellCommand("pm uninstall $packageName")

    private fun isAppInstalledForUser(packageName: String, user: UserReference) =
        SystemUtil.runShellCommand("pm list packages --user ${user.id()} $packageName")
            .split("\\r?\\n".toRegex())
            .any { it == "package:$packageName" }

    private fun setSystemProperty(name: String, value: String) =
        assertThat(SystemUtil.runShellCommand("setprop $name $value"))
            .isEmpty()

    fun getSystemProperty(prop: String): String {
        return SystemUtil.runShellCommand("getprop $prop").replace("\n", "")
    }
}
