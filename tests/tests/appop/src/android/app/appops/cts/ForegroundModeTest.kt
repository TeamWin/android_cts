/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.app.appops.cts

import android.Manifest.permission.ACCESS_BACKGROUND_LOCATION
import android.app.AppOpsManager
import android.app.AppOpsManager.MODE_ALLOWED
import android.app.AppOpsManager.MODE_FOREGROUND
import android.app.AppOpsManager.MODE_IGNORED
import android.app.AppOpsManager.OPSTR_FINE_LOCATION
import android.app.AppOpsManager.WATCH_FOREGROUND_CHANGES
import android.content.ComponentName
import android.content.Context.BIND_AUTO_CREATE
import android.content.Context.BIND_NOT_FOREGROUND
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.ServiceConnection
import android.os.IBinder
import android.support.test.uiautomator.UiDevice
import androidx.test.platform.app.InstrumentationRegistry
import com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.MILLISECONDS

private const val TEST_SERVICE_PKG = "android.app.appops.cts.appthatcanbeforcedintoforegroundstates"
private const val TIMEOUT_MILLIS = 45000L

class ForegroundModeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val appopsManager = context.getSystemService(AppOpsManager::class.java)!!
    private val testPkgUid = context.packageManager.getPackageUid(TEST_SERVICE_PKG, 0)

    private lateinit var foregroundControlService: IAppOpsForegroundControlService
    private lateinit var serviceConnection: ServiceConnection

    private val testPkgAppOpMode: Int
        get() {
            return callWithShellPermissionIdentity {
                appopsManager.noteOp(OPSTR_FINE_LOCATION, testPkgUid, TEST_SERVICE_PKG,
                        null, null)
            }
        }

    private fun wakeUpScreen() {
        val uiDevice = UiDevice.getInstance(instrumentation)
        uiDevice.wakeUp()
        uiDevice.executeShellCommand("wm dismiss-keyguard")
    }

    @Before
    fun setTestPkgPermissionState() {
        instrumentation.uiAutomation.revokeRuntimePermission(
                TEST_SERVICE_PKG, ACCESS_BACKGROUND_LOCATION)
        runWithShellPermissionIdentity {
            appopsManager.setUidMode(OPSTR_FINE_LOCATION, testPkgUid, MODE_FOREGROUND)
        }
    }

    @Before
    fun connectToService() {
        val serviceIntent = Intent().setComponent(ComponentName(TEST_SERVICE_PKG,
                "$TEST_SERVICE_PKG.AppOpsForegroundControlService"))

        val newService = CompletableFuture<IAppOpsForegroundControlService>()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                newService.complete(IAppOpsForegroundControlService.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Assert.fail("foreground control service disconnected")
            }
        }

        context.bindService(serviceIntent, serviceConnection,
                BIND_AUTO_CREATE or BIND_NOT_FOREGROUND)
        foregroundControlService = newService.get(TIMEOUT_MILLIS, MILLISECONDS)
    }

    private fun makeTop() {
        wakeUpScreen()

        context.startActivity(Intent().setComponent(
                ComponentName(TEST_SERVICE_PKG,
                        "$TEST_SERVICE_PKG.AppOpsForegroundControlActivity"))
                .setFlags(FLAG_ACTIVITY_NEW_TASK))
        foregroundControlService.waitUntilForeground()

        // Sometimes it can take some time for the lock screen to disappear. Use eval'ed appop mode
        // as a proxy
        eventually(timeout = TIMEOUT_MILLIS) {
            assertThat(testPkgAppOpMode).isEqualTo(MODE_ALLOWED)
        }
    }

    private fun makeBackground() {
        foregroundControlService.finishActivity()
    }

    @Test
    fun modeIsIgnoredWhenAppIsBackground() {
        eventually(timeout = TIMEOUT_MILLIS) {
            assertThat(testPkgAppOpMode).isEqualTo(MODE_IGNORED)
        }
    }

    @Test
    fun modeIsAllowedWhenForeground() {
        makeTop()
        assertThat(testPkgAppOpMode).isEqualTo(MODE_ALLOWED)
    }

    @Test
    fun modeBecomesIgnoredAfterEnteringBackground() {
        makeTop()
        assertThat(testPkgAppOpMode).isEqualTo(MODE_ALLOWED)

        makeBackground()
        eventually(timeout = TIMEOUT_MILLIS) {
            assertThat(testPkgAppOpMode).isEqualTo(MODE_IGNORED)
        }
    }

    @Test
    fun modeChangeCallbackWhenEnteringForeground() {
        eventually(timeout = TIMEOUT_MILLIS) {
            assertThat(testPkgAppOpMode).isEqualTo(MODE_IGNORED)
        }

        val gotCallback = CompletableFuture<Unit>()
        appopsManager.startWatchingMode(OPSTR_FINE_LOCATION, TEST_SERVICE_PKG,
                WATCH_FOREGROUND_CHANGES) { op, packageName ->
            if (op == OPSTR_FINE_LOCATION && packageName == TEST_SERVICE_PKG) {
                gotCallback.complete(Unit)
            }
        }

        makeTop()
        gotCallback.get(TIMEOUT_MILLIS, MILLISECONDS)
    }

    @Test
    fun modeChangeCallbackWhenEnteringBackground() {
        makeTop()

        val gotCallback = CompletableFuture<Unit>()
        appopsManager.startWatchingMode(OPSTR_FINE_LOCATION, TEST_SERVICE_PKG,
                WATCH_FOREGROUND_CHANGES) { op, packageName ->
            if (op == OPSTR_FINE_LOCATION && packageName == TEST_SERVICE_PKG) {
                gotCallback.complete(Unit)
            }
        }

        makeBackground()
        gotCallback.get(TIMEOUT_MILLIS, MILLISECONDS)
    }

    @After
    fun disconnectFromService() {
        try {
            foregroundControlService.cleanup()
            context.unbindService(serviceConnection)
        } catch (ignored: Throwable) {
        }
    }
}