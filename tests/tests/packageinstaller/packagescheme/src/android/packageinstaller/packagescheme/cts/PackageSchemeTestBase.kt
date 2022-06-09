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

package android.packageinstaller.packagescheme.cts

import android.Manifest
import android.app.Activity
import android.app.Instrumentation
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Bundle
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject
import android.support.test.uiautomator.UiSelector
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import org.junit.After
import org.junit.Before

open class PackageSchemeTestBase {
    val TARGET_APP_PKG_NAME: String = "android.packageinstaller.emptytestapp.cts"
    val TARGET_APP_APK: String = "CtsEmptyTestApp.apk"
    val RECEIVER_ACTION: String = "android.packageinstaller.emptytestapp.cts.action"
    val REQUEST_CODE = 1

    var mScenario: ActivityScenario<TestActivity>? = null
    val mInstrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    val mUiDevice = UiDevice.getInstance(mInstrumentation)
    val mContext: Context = mInstrumentation.context
    val mInstaller: PackageInstaller = mContext.packageManager.packageInstaller

    class TestActivity : Activity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)

            val appInstallIntent: Intent? = intent.getExtra(Intent.EXTRA_INTENT) as Intent?
            val requestCode: Int = intent.getIntExtra("requestCode", Integer.MIN_VALUE)
            startActivityForResult(appInstallIntent, requestCode)
        }

        override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            super.onActivityResult(requestCode, resultCode, data)
            var intent = Intent()
            if (data != null) {
                intent = Intent(data)
            }
            intent.putExtra("requestCodeVerify", requestCode)
            setResult(resultCode, intent)
            finish()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        private val results = ArrayBlockingQueue<Intent>(1)

        override fun onReceive(context: Context, intent: Intent) {
            // Added as a safety net. Have observed instances where the Queue isn't empty which
            // causes the test suite to crash.
            if (results.size != 0) {
                clear()
            }
            results.add(intent)
        }

        fun makeIntentSender(sessionId: Int) = PendingIntent.getBroadcast(
            mContext, sessionId,
            Intent(RECEIVER_ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT
                or PendingIntent.FLAG_MUTABLE_UNAUDITED
        ).intentSender

        fun getResult(unit: TimeUnit, timeout: Long) = results.poll(timeout, unit)

        fun clear() = results.clear()
    }

    @Before
    fun setup() {
        receiver.clear()
        mContext.registerReceiver(receiver, IntentFilter(RECEIVER_ACTION))
        // The device screen needs to be turned on to perform UI interaction
        mUiDevice.wakeUp()
    }

    @Before
    @After
    fun uninstall() {
        mInstrumentation.uiAutomation.executeShellCommand("pm uninstall $TARGET_APP_PKG_NAME")
    }

    @After
    fun tearDown() {
        mContext.unregisterReceiver(receiver)
        mInstrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    fun runTest(packageName: String, packageHasVisibility: Boolean, needTargetApp: Boolean) {
        if (packageHasVisibility) {
            mInstrumentation.uiAutomation.executeShellCommand(
                "appops set $packageName android:query_all_packages allow"
            )
            mInstrumentation.uiAutomation.executeShellCommand(
                "appops set $packageName android:request_install_packages allow"
            )
        }

        if (needTargetApp) {
            installTargetApp(resourceSupplier(TARGET_APP_APK))
        }

        val intent = Intent(ApplicationProvider.getApplicationContext(), TestActivity::class.java)
        intent.putExtra(Intent.EXTRA_INTENT, getAppInstallIntent())
        intent.putExtra("requestCode", REQUEST_CODE)

        mScenario = ActivityScenario.launch(intent)
        mScenario!!.onActivity {
            var dialog: UiObject
            var button: UiObject
            if (packageHasVisibility && needTargetApp) {
                dialog = mUiDevice.findObject(
                    UiSelector().text(
                        "Do you want to update this app?"
                    )
                )
                button = mUiDevice.findObject(UiSelector().text("Cancel"))
            } else {
                dialog = mUiDevice.findObject(
                    UiSelector().text(
                        "There was a problem parsing the package."
                    )
                )
                button = mUiDevice.findObject(UiSelector().text("OK"))
            }
            if (dialog.exists() && button.exists() && button.isEnabled) {
                button.click()
            }
        }

        if (packageHasVisibility && needTargetApp) {
            assertThat(mScenario!!.result.resultCode)
                .isNotEqualTo(Activity.RESULT_FIRST_USER)
        } else {
            assertThat(mScenario!!.result.resultCode)
                .isEqualTo(Activity.RESULT_FIRST_USER)
        }

        assertThat(
            mScenario!!.result.resultData
                .getIntExtra("requestCodeVerify", Int.MIN_VALUE)
        )
            .isEqualTo(REQUEST_CODE)
        mScenario!!.close()
    }

    private fun installTargetApp(apkStreamSupplier: Supplier<InputStream>) {
        setupPermissions()
        val params = PackageInstaller.SessionParams(
            PackageInstaller.SessionParams.MODE_FULL_INSTALL
        )
        params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        val sessionId: Int = mInstaller.createSession(params)
        val session: PackageInstaller.Session = mInstaller.openSession(sessionId)

        session.openWrite("apk", 0, -1).use { os ->
            apkStreamSupplier.get().copyTo(os)
        }

        session.commit(receiver.makeIntentSender(sessionId))
        session.close()

        val result = receiver.getResult(TimeUnit.SECONDS, 30)
        val installStatus = result.getIntExtra(PackageInstaller.EXTRA_STATUS, Int.MIN_VALUE)
        if (installStatus != PackageInstaller.STATUS_SUCCESS) {
            val id = result.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, Int.MIN_VALUE)
            mContext.packageManager.packageInstaller.abandonSession(id)
        }
        assertThat(installStatus).isEqualTo(PackageInstaller.STATUS_SUCCESS)
    }

    private fun resourceSupplier(resourceName: String): Supplier<InputStream> {
        return Supplier<InputStream> {
            val resourceAsStream = javaClass.classLoader.getResourceAsStream(resourceName)
                ?: throw RuntimeException("Resource $resourceName could not be found.")
            resourceAsStream
        }
    }

    private fun setupPermissions() {
        mInstrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.INSTALL_PACKAGES
        )
    }

    private fun getAppInstallIntent(): Intent {
        return Intent(Intent.ACTION_INSTALL_PACKAGE)
            .setData(Uri.parse("package:$TARGET_APP_PKG_NAME"))
    }
}
