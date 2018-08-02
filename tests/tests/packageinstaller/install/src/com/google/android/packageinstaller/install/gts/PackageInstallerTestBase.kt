/*
 * Copyright (C) 2018 Google Inc.
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

import android.app.PendingIntent
import android.app.PendingIntent.FLAG_UPDATE_CURRENT
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.EXTRA_INTENT
import android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.EXTRA_STATUS
import android.content.pm.PackageInstaller.STATUS_FAILURE_INVALID
import android.content.pm.PackageInstaller.STATUS_PENDING_USER_ACTION
import android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
import android.content.pm.PackageManager
import android.support.test.InstrumentationRegistry
import android.support.test.rule.ActivityTestRule
import android.support.test.uiautomator.UiDevice
import com.android.compatibility.common.util.AppOpsUtils
import com.android.xts.common.util.GmsUtil
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import java.io.File
import java.lang.IllegalArgumentException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

const val TEST_APK_NAME = "GtsEmptyTestApp.apk"
const val TEST_APK_PACKAGE_NAME = "com.google.android.packageinstaller.emptytestapp.gts"
const val TEST_APK_EXTERNAL_LOCATION = "/data/local/tmp/gts/packageinstaller"
const val INSTALL_ACTION_CB = "PackageInstallerTestBase.install_cb"

const val PACKAGE_INSTALLER_PACKAGE_NAME = "com.android.packageinstaller"

const val TIMEOUT = 60000L
const val TIMEOUT_EXPECTED = 2000L
const val APP_OP_STR = "REQUEST_INSTALL_PACKAGES"

open class PackageInstallerTestBase {
    @get:Rule
    val installDialogStarter = ActivityTestRule(InstallConfirmDialogStarter::class.java)

    private val context = InstrumentationRegistry.getTargetContext()
    private val pm = context.packageManager
    private val uiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /** If a status was received the value of the status, otherwise null */
    private var installSessionResult = LinkedBlockingQueue<Int>()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val status = intent.getIntExtra(EXTRA_STATUS, STATUS_FAILURE_INVALID)

            if (status == STATUS_PENDING_USER_ACTION) {
                val activityIntent = intent.getParcelableExtra<Intent>(EXTRA_INTENT)
                activityIntent!!.addFlags(FLAG_ACTIVITY_CLEAR_TASK or FLAG_ACTIVITY_NEW_TASK)
                installDialogStarter.activity.startActivityForResult(activityIntent, 0)
            }

            installSessionResult.offer(status)
        }
    }

    @Before
    fun requireGoogleBuiltPackageInstallerApp() {
        // hasPlayStore && !isCnGmsBuild mean GMS build with Google built package installer app
        Assume.assumeTrue(GmsUtil.hasPlayStore())
        Assume.assumeFalse(GmsUtil.isCnGmsBuild())
    }

    @Before
    fun wakeUpScreen() {
        if (!uiDevice.isScreenOn) {
            uiDevice.wakeUp()
        }
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun assertTestPackageNotInstalled() {
        try {
            context.packageManager.getPackageInfo(TEST_APK_PACKAGE_NAME, 0)
            Assert.fail("Package should not be installed")
        } catch (expected: PackageManager.NameNotFoundException) {
        }
    }

    @Before
    fun registerInstallResultReceiver() {
        context.registerReceiver(receiver, IntentFilter(INSTALL_ACTION_CB))
    }

    /**
     * Wait for session's install result and return it
     */
    protected fun getInstallSessionResult(timeout: Long = TIMEOUT): Int? {
        return installSessionResult.poll(timeout, TimeUnit.MILLISECONDS)
    }

    /**
     * Start an installation via a session
     */
    protected fun startInstallationViaSession(): PackageInstaller.Session {
        val pi = pm.packageInstaller

        // Create session
        val sessionId = pi.createSession(PackageInstaller.SessionParams(MODE_FULL_INSTALL))
        val session = pi.openSession(sessionId)!!

        // Write data to session
        File(TEST_APK_EXTERNAL_LOCATION, TEST_APK_NAME).inputStream().use { fileOnDisk ->
            session.openWrite(TEST_APK_NAME, 0, -1).use { sessionFile ->
                fileOnDisk.copyTo(sessionFile)
            }
        }

        // Commit session
        val pendingIntent = PendingIntent.getBroadcast(context, 0, Intent(INSTALL_ACTION_CB),
                FLAG_UPDATE_CURRENT)
        session.commit(pendingIntent.intentSender)

        // The system should have asked us to launch the installer
        Assert.assertEquals(STATUS_PENDING_USER_ACTION, getInstallSessionResult())

        return session
    }

    @After
    fun unregisterInstallResultReceiver() {
        try {
            context.unregisterReceiver(receiver)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    @After
    fun uninstallTestPackage() {
        uiDevice.executeShellCommand("pm uninstall $TEST_APK_PACKAGE_NAME")
    }

    @After
    fun resetAppOps() {
        AppOpsUtils.reset(context.packageName)
    }
}
