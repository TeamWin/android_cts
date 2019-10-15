/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.app.AppOpsManager
import android.app.AppOpsManager.AppOpsCollector
import android.app.AppOpsManager.OPSTR_ACCESS_ACCESSIBILITY
import android.app.AppOpsManager.OPSTR_COARSE_LOCATION
import android.app.AppOpsManager.OPSTR_FINE_LOCATION
import android.app.AppOpsManager.OPSTR_GET_ACCOUNTS
import android.app.AppOpsManager.strOpToOp
import android.app.AsyncNotedAppOp
import android.app.PendingIntent
import android.app.SyncNotedAppOp
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.platform.test.annotations.AppModeFull
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Assert.fail
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit.MILLISECONDS
import java.util.concurrent.TimeoutException

private const val TEST_SERVICE_PKG = "android.app.appops.cts.appthatusesappops"
private const val TIMEOUT_MILLIS = 10000L

private external fun nativeNoteOp(op: Int, uid: Int, packageName: String)
private external fun nativeNoteOpWithMessage(
    op: Int,
    uid: Int,
    packageName: String,
    message: String
)

@AppModeFull(reason = "Test relies on other app to connect to. Instant apps can't see other apps")
class AppOpsLoggingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)

    private val myUid = android.os.Process.myUid()
    private val myPackage = context.packageName

    private lateinit var testService: IAppOpsUserService
    private lateinit var serviceConnection: ServiceConnection

    // Collected note-op calls inside of this process
    private val noted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
    private val selfNoted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
    private val asyncNoted = mutableListOf<AsyncNotedAppOp>()

    @Before
    fun loadNativeCode() {
        System.loadLibrary("CtsAppOpsTestCases_jni")
    }

    @Before
    fun setNotedAppOpsCollectorAndClearCollectedNoteOps() {
        setNotedAppOpsCollector()
        clearCollectedNotedOps()
    }

    @Before
    fun connectToService() {
        val serviceIntent = Intent()
        serviceIntent.component = ComponentName(TEST_SERVICE_PKG,
                TEST_SERVICE_PKG + ".AppOpsUserService")

        val newService = CompletableFuture<IAppOpsUserService>()
        serviceConnection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                newService.complete(IAppOpsUserService.Stub.asInterface(service))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                fail("test service disconnected")
            }
        }

        context.bindService(serviceIntent, serviceConnection, BIND_AUTO_CREATE)
        testService = newService.get(TIMEOUT_MILLIS, MILLISECONDS)
    }

    private fun clearCollectedNotedOps() {
        noted.clear()
        selfNoted.clear()
        asyncNoted.clear()
    }

    private fun setNotedAppOpsCollector() {
        appOpsManager.setNotedAppOpsCollector(
                object : AppOpsCollector() {
                    override fun onNoted(op: SyncNotedAppOp) {
                        noted.add(op to Throwable().stackTrace)
                    }

                    override fun onSelfNoted(op: SyncNotedAppOp) {
                        selfNoted.add(op to Throwable().stackTrace)
                    }

                    override fun onAsyncNoted(asyncOp: AsyncNotedAppOp) {
                        asyncNoted.add(asyncOp)
                    }

                    override fun getAsyncNotedExecutor(): Executor {
                        // Execute callbacks immediately
                        return Executor { it.run() }
                    }
                })
    }

    private inline fun rethrowThrowableFrom(r: () -> Unit) {
        try {
            r()
        } catch (e: Throwable) {
            throw e.cause ?: e
        }
    }

    @Test
    fun selfNoteAndCheckLog() {
        appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, myUid, myPackage)

        assertThat(noted).isEmpty()
        assertThat(asyncNoted).isEmpty()

        assertThat(selfNoted.map { it.first.op }).containsExactly(OPSTR_COARSE_LOCATION)
    }

    @Test
    fun nativeSelfNoteAndCheckLog() {
        nativeNoteOp(strOpToOp(OPSTR_COARSE_LOCATION), myUid, myPackage)

        assertThat(noted).isEmpty()
        assertThat(selfNoted).isEmpty()

        // All native notes will be reported as async notes
        eventually {
            assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
        }
    }

    @Test
    fun selfNotesAreDeliveredAsAsyncOpsWhenCollectorIsRegistered() {
        appOpsManager.setNotedAppOpsCollector(null)

        appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, myUid, myPackage)

        assertThat(noted).isEmpty()
        assertThat(selfNoted).isEmpty()
        assertThat(asyncNoted).isEmpty()

        setNotedAppOpsCollector()

        assertThat(noted).isEmpty()
        assertThat(selfNoted).isEmpty()
        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
    }

    @Test
    fun disableCollectedAndNoteSyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.disableCollectorAndCallSyncOpsWhichWillNotBeCollected(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun disableCollectedAndNoteASyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.disableCollectorAndCallASyncOpsWhichWillBeCollected(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun callsBackIntoServiceAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatCallsBackIntoServiceAndCheckLog(
                AppOpsUserClient(context, testService))
        }
    }

    @Test
    fun noteSyncOpFromNativeCodeAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpFromNativeCodeAndCheckLog(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpFromNativeCodeAndCheckMessage() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpFromNativeCodeAndCheckMessage(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpAndCheckStackTrace() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpAndCheckStackTrace(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteNonPermissionSyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesNonPermissionSyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpTwiceAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesTwiceSyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteTwoSyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesTwoSyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpNativeAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpNativelyAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteNonPermissionSyncOpNativeAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesNonPermissionSyncOpNativelyAndCheckLog(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpOneway() {
        rethrowThrowableFrom {
            testService.callOnewayApiThatNotesSyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpOnewayNative() {
        rethrowThrowableFrom {
            testService.callOnewayApiThatNotesSyncOpNativelyAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpOtherUidAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpOtherUidAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteSyncOpOtherUidNativeAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesSyncOpOtherUidNativelyAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteAsyncOpAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesAsyncOpAndCheckLog(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteAsyncOpAndCheckDefaultMessage() {
        rethrowThrowableFrom {
            testService.callApiThatNotesAsyncOpAndCheckDefaultMessage(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteAsyncOpAndCheckCustomMessage() {
        rethrowThrowableFrom {
            testService.callApiThatNotesAsyncOpAndCheckCustomMessage(AppOpsUserClient(context))
        }
    }

    @Test
    fun noteAsyncOpNativelyAndCheckCustomMessage() {
        rethrowThrowableFrom {
            testService.callApiThatNotesAsyncOpNativelyAndCheckCustomMessage(
                    AppOpsUserClient(context))
        }
    }

    @Test
    fun noteAsyncOpNativeAndCheckLog() {
        rethrowThrowableFrom {
            testService.callApiThatNotesAsyncOpNativelyAndCheckLog(AppOpsUserClient(context))
        }
    }

    /**
     * Realistic end-to-end test for getting last location
     */
    @Test
    fun getLastKnownLocation() {
        val location = context.getSystemService(LocationManager::class.java)
            .getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

        Assume.assumeTrue("Could not get last known location", location != null)

        assertThat(noted.map { it.first.op }).containsAnyOf(OPSTR_COARSE_LOCATION,
            OPSTR_FINE_LOCATION)
        assertThat(noted[0].second.map { it.methodName }).contains("getLastKnownLocation")
    }

    /**
     * Realistic end-to-end test for getting an async location
     */
    @Test
    fun getAsyncLocation() {
        val gotLocationChangeCallback = CompletableFuture<Unit>()

        val locationListener = object : LocationListener {
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String?) {}
            override fun onProviderDisabled(provider: String?) {}

            override fun onLocationChanged(location: Location?) {
                gotLocationChangeCallback.complete(Unit)
            }
        }

        context.getSystemService(LocationManager::class.java).requestSingleUpdate(
            LocationManager.NETWORK_PROVIDER, locationListener, Looper.getMainLooper())

        try {
            gotLocationChangeCallback.get(TIMEOUT_MILLIS, MILLISECONDS)
        } catch (e: TimeoutException) {
            Assume.assumeTrue("Could not get location", false)
        }

        eventually {
            assertThat(asyncNoted.map { it.op }).containsAnyOf(OPSTR_COARSE_LOCATION,
                OPSTR_FINE_LOCATION)
            assertThat(asyncNoted[0].message).contains(locationListener::class.java.name)
            assertThat(asyncNoted[0].message).contains(
                Integer.toHexString(System.identityHashCode(locationListener)))
        }
    }

    /**
     * Realistic end-to-end test for getting called back for a proximity alert
     */
    @Test
    fun triggerProximityAlert() {
        val PROXIMITY_ALERT_ACTION = "proxAlert"

        val gotProximityAlert = CompletableFuture<Unit>()

        val locationManager = context.getSystemService(LocationManager::class.java)!!

        val proximityAlertReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                gotProximityAlert.complete(Unit)
            }
        }

        context.registerReceiver(proximityAlertReceiver, IntentFilter(PROXIMITY_ALERT_ACTION))
        try {
            val proximityAlertReceiverPendingIntent = PendingIntent.getBroadcast(context, 0,
                Intent(PROXIMITY_ALERT_ACTION).setPackage(myPackage)
                    .setFlags(Intent.FLAG_RECEIVER_FOREGROUND),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_ONE_SHOT)

            locationManager.addProximityAlert(0.0, 0.0, Float.MAX_VALUE, TIMEOUT_MILLIS,
                proximityAlertReceiverPendingIntent)
            try {
                try {
                    gotProximityAlert.get(TIMEOUT_MILLIS, MILLISECONDS)
                } catch (e: TimeoutException) {
                    Assume.assumeTrue("Could not get proximity alert", false)
                }

                eventually {
                    assertThat(asyncNoted.map { it.op }).contains(OPSTR_FINE_LOCATION)

                    assertThat(asyncNoted[0].message).contains(
                        proximityAlertReceiverPendingIntent::class.java.name)
                    assertThat(asyncNoted[0].message).contains(
                        Integer.toHexString(
                            System.identityHashCode(proximityAlertReceiverPendingIntent)))
                }
            } finally {
                locationManager.removeProximityAlert(proximityAlertReceiverPendingIntent)
            }
        } finally {
            context.unregisterReceiver(proximityAlertReceiver)
        }
    }

    @After
    fun removeNotedAppOpsCollector() {
        appOpsManager.setNotedAppOpsCollector(null)
    }

    @After
    fun disconnectFromService() {
        context.unbindService(serviceConnection)
    }

    /**
     * Calls various noteOp-like methods in binder calls called by
     * {@link android.app.appops.cts.appthatusesappops.AppOpsUserService}
     */
    private class AppOpsUserClient(
        context: Context,
        val testService: IAppOpsUserService? = null
    ) : IAppOpsUserClient.Stub() {
        private val handler = Handler(Looper.getMainLooper())
        private val appOpsManager = context.getSystemService(AppOpsManager::class.java)

        private val myUid = android.os.Process.myUid()
        private val myPackage = context.packageName

        override fun noteSyncOp() {
            runWithShellPermissionIdentity {
                appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, getCallingUid(),
                        TEST_SERVICE_PKG)
            }
        }

        override fun callBackIntoService() {
            runWithShellPermissionIdentity {
                appOpsManager.noteOpNoThrow(OPSTR_FINE_LOCATION, getCallingUid(),
                    TEST_SERVICE_PKG)
            }

            testService?.callApiThatNotesSyncOpAndClearLog(this)
        }

        override fun noteNonPermissionSyncOp() {
            runWithShellPermissionIdentity {
                appOpsManager.noteOpNoThrow(OPSTR_ACCESS_ACCESSIBILITY, getCallingUid(),
                        TEST_SERVICE_PKG)
            }
        }

        override fun noteSyncOpTwice() {
            noteSyncOp()
            noteSyncOp()
        }

        override fun noteTwoSyncOp() {
            runWithShellPermissionIdentity {
                appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, getCallingUid(),
                        TEST_SERVICE_PKG)

                appOpsManager.noteOpNoThrow(OPSTR_GET_ACCOUNTS, getCallingUid(), TEST_SERVICE_PKG)
            }
        }

        override fun noteSyncOpNative() {
            runWithShellPermissionIdentity {
                nativeNoteOp(strOpToOp(OPSTR_COARSE_LOCATION), getCallingUid(), TEST_SERVICE_PKG)
            }
        }

        override fun noteNonPermissionSyncOpNative() {
            runWithShellPermissionIdentity {
                nativeNoteOp(strOpToOp(OPSTR_ACCESS_ACCESSIBILITY), getCallingUid(),
                        TEST_SERVICE_PKG)
            }
        }

        override fun noteSyncOpOneway() {
            runWithShellPermissionIdentity {
                appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, getCallingUid(),
                        TEST_SERVICE_PKG)
            }
        }

        override fun noteSyncOpOnewayNative() {
            runWithShellPermissionIdentity {
                nativeNoteOp(strOpToOp(OPSTR_COARSE_LOCATION), getCallingUid(), TEST_SERVICE_PKG)
            }
        }

        override fun noteSyncOpOtherUid() {
            appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, myUid, myPackage)
        }

        override fun noteSyncOpOtherUidNative() {
            nativeNoteOp(strOpToOp(OPSTR_COARSE_LOCATION), myUid, myPackage)
        }

        override fun noteAsyncOp() {
            val callingUid = getCallingUid()

            handler.post {
                runWithShellPermissionIdentity {
                    appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, callingUid, TEST_SERVICE_PKG)
                }
            }
        }

        override fun noteAsyncOpWithCustomMessage() {
            val callingUid = getCallingUid()

            handler.post {
                runWithShellPermissionIdentity {
                    appOpsManager.noteOpNoThrow(OPSTR_COARSE_LOCATION, callingUid, TEST_SERVICE_PKG,
                            "custom msg")
                }
            }
        }

        override fun noteAsyncOpNative() {
            val callingUid = getCallingUid()

            handler.post {
                runWithShellPermissionIdentity {
                    nativeNoteOp(strOpToOp(OPSTR_COARSE_LOCATION), callingUid, TEST_SERVICE_PKG)
                }
            }
        }

        override fun noteAsyncOpNativeWithCustomMessage() {
            val callingUid = getCallingUid()

            handler.post {
                runWithShellPermissionIdentity {
                    nativeNoteOpWithMessage(strOpToOp(OPSTR_COARSE_LOCATION), callingUid,
                            TEST_SERVICE_PKG, "native custom msg")
                }
            }
        }
    }
}
