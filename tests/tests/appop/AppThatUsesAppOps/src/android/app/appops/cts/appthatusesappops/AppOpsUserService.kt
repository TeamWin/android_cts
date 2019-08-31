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

package android.app.appops.cts.appthatusesappops

import android.app.AppOpsManager
import android.app.AppOpsManager.OPSTR_COARSE_LOCATION
import android.app.AppOpsManager.OPSTR_FINE_LOCATION
import android.app.AppOpsManager.OPSTR_GET_ACCOUNTS
import android.app.AsyncNotedAppOp
import android.app.Service
import android.app.SyncNotedAppOp
import android.app.appops.cts.IAppOpsUserClient
import android.app.appops.cts.IAppOpsUserService
import android.app.appops.cts.eventually
import android.content.Intent
import android.os.IBinder
import android.os.Process.FIRST_APPLICATION_UID
import com.google.common.truth.Truth.assertThat
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.IllegalArgumentException

private external fun noteSyncOpFromNativeCode(binder: IBinder)

class AppOpsUserService : Service() {
    override fun onCreate() {
        super.onCreate()

        System.loadLibrary("AppThatUsesAppOps_jni")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return object : IAppOpsUserService.Stub() {
            private val appOpsManager = getSystemService(AppOpsManager::class.java)

            // Collected note-op calls inside of this process
            private val noted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
            private val selfNoted = mutableListOf<Pair<SyncNotedAppOp, Array<StackTraceElement>>>()
            private val asyncNoted = mutableListOf<AsyncNotedAppOp>()

            private fun setNotedAppOpsCollector() {
                appOpsManager.setNotedAppOpsCollector(
                        object : AppOpsManager.AppOpsCollector() {
                            override fun onNoted(op: SyncNotedAppOp) {
                                noted.add(op to Throwable().stackTrace)
                            }

                            override fun onSelfNoted(op: SyncNotedAppOp) {
                                selfNoted.add(op to Throwable().stackTrace)
                            }

                            override fun onAsyncNoted(asyncOp: AsyncNotedAppOp) {
                                asyncNoted.add(asyncOp)
                            }
                        })
            }

            init {
                setNotedAppOpsCollector()
            }

            /**
             * Cheapo variant of {@link ParcelableException}
             */
            inline fun forwardThrowableFrom(r: () -> Unit) {
                try {
                    r()
                } catch (t: Throwable) {
                    val sw = StringWriter()
                    t.printStackTrace(PrintWriter(sw))

                    throw IllegalArgumentException("\n" + sw.toString() + "called by")
                }
            }

            override fun disableCollectorAndCallSyncOpsWhichWillNotBeCollected(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    appOpsManager.setNotedAppOpsCollector(null)

                    client.noteSyncOp()

                    assertThat(asyncNoted).isEmpty()

                    setNotedAppOpsCollector()

                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                }
            }

            override fun disableCollectorAndCallASyncOpsWhichWillBeCollected(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    appOpsManager.setNotedAppOpsCollector(null)

                    client.noteAsyncOp()

                    setNotedAppOpsCollector()

                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOp()

                    assertThat(noted.map { it.first.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    assertThat(noted[0].second.map { it.methodName })
                        .contains("callApiThatNotesSyncOpAndCheckLog")
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpAndClearLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOp()

                    assertThat(noted.map { it.first.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    assertThat(noted[0].second.map { it.methodName })
                        .contains("callApiThatNotesSyncOpAndClearLog")
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()

                    noted.clear()
                }
            }

            override fun callApiThatCallsBackIntoServiceAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    // This calls back into the service via callApiThatNotesSyncOpAndClearLog
                    client.callBackIntoService()

                    // The noteSyncOp called in callApiThatNotesSyncOpAndClearLog should not have
                    // affected the callBackIntoService call
                    assertThat(noted.map { it.first.op }).containsExactly(OPSTR_FINE_LOCATION)
                    assertThat(noted[0].second.map { it.methodName })
                        .contains("callApiThatCallsBackIntoServiceAndCheckLog")
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpFromNativeCodeAndCheckLog(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    noteSyncOpFromNativeCode(client.asBinder())

                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpFromNativeCodeAndCheckMessage(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    noteSyncOpFromNativeCode(client.asBinder())

                    eventually {
                        assertThat(asyncNoted[0].notingPackageName).isEqualTo(
                            "android.app.appops.cts")
                        assertThat(asyncNoted[0].notingUid).isAtLeast(FIRST_APPLICATION_UID)
                        assertThat(asyncNoted[0].message).isNotEmpty()
                    }
                }
            }

            override fun callApiThatNotesSyncOpAndCheckStackTrace(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOp()
                    assertThat(noted[0].second.map { it.methodName }).contains(
                            "callApiThatNotesSyncOpAndCheckStackTrace")
                }
            }

            override fun callApiThatNotesNonPermissionSyncOpAndCheckLog(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    client.noteNonPermissionSyncOp()

                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                    assertThat(noted).isEmpty()
                }
            }

            override fun callApiThatNotesTwiceSyncOpAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOpTwice()

                    // Ops noted twice are only reported once
                    assertThat(noted.map { it.first.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    assertThat(noted[0].second.map { it.methodName })
                        .contains("callApiThatNotesTwiceSyncOpAndCheckLog")
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesTwoSyncOpAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteTwoSyncOp()

                    assertThat(noted.map { it.first.op }).containsExactly(
                            OPSTR_COARSE_LOCATION, OPSTR_GET_ACCOUNTS)
                    assertThat(noted[0].second.map { it.methodName })
                        .contains("callApiThatNotesTwoSyncOpAndCheckLog")
                    assertThat(noted[1].second.map { it.methodName })
                        .contains("callApiThatNotesTwoSyncOpAndCheckLog")
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpNativelyAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOpNative()

                    // All native notes will be reported as async notes
                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callApiThatNotesNonPermissionSyncOpNativelyAndCheckLog(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    client.noteNonPermissionSyncOpNative()

                    // All native notes will be reported as async notes
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callOnewayApiThatNotesSyncOpAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOpOneway()

                    // There is not return value from a one-way call, hence async note is the only
                    // option
                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callOnewayApiThatNotesSyncOpNativelyAndCheckLog(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    client.noteSyncOpOnewayNative()

                    // There is not return value from a one-way call, hence async note is the only
                    // option
                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpOtherUidAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteSyncOpOtherUid()

                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesSyncOpOtherUidNativelyAndCheckLog(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    client.noteSyncOpOtherUidNative()

                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                    assertThat(asyncNoted).isEmpty()
                }
            }

            override fun callApiThatNotesAsyncOpAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteAsyncOp()

                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }

            override fun callApiThatNotesAsyncOpAndCheckDefaultMessage(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteAsyncOp()

                    eventually {
                        assertThat(asyncNoted[0].notingPackageName).isEqualTo(
                                "android.app.appops.cts")
                        assertThat(asyncNoted[0].notingUid).isAtLeast(FIRST_APPLICATION_UID)
                        assertThat(asyncNoted[0].message).contains(
                            "AppOpsLoggingTest\$AppOpsUserClient\$noteAsyncOp")
                    }
                }
            }

            override fun callApiThatNotesAsyncOpAndCheckCustomMessage(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteAsyncOpWithCustomMessage()

                    eventually {
                        assertThat(asyncNoted[0].notingPackageName).isEqualTo(
                                "android.app.appops.cts")
                        assertThat(asyncNoted[0].notingUid).isAtLeast(FIRST_APPLICATION_UID)
                        assertThat(asyncNoted[0].message).isEqualTo("custom msg")
                    }
                }
            }

            override fun callApiThatNotesAsyncOpNativelyAndCheckCustomMessage(
                client: IAppOpsUserClient
            ) {
                forwardThrowableFrom {
                    client.noteAsyncOpNativeWithCustomMessage()

                    eventually {
                        assertThat(asyncNoted[0].notingPackageName).isNull()
                        assertThat(asyncNoted[0].notingUid).isAtLeast(FIRST_APPLICATION_UID)
                        assertThat(asyncNoted[0].message).isEqualTo("native custom msg")
                    }
                }
            }

            override fun callApiThatNotesAsyncOpNativelyAndCheckLog(client: IAppOpsUserClient) {
                forwardThrowableFrom {
                    client.noteAsyncOpNative()

                    eventually {
                        assertThat(asyncNoted.map { it.op }).containsExactly(OPSTR_COARSE_LOCATION)
                    }
                    assertThat(noted).isEmpty()
                    assertThat(selfNoted).isEmpty()
                }
            }
        }
    }
}