/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.os.cts

import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.Instrumentation
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DeviceConfig
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.LogcatInspector
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommand
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import org.hamcrest.CoreMatchers
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert.assertThat
import java.io.InputStream

fun runAppHibernationJob(context: Context, tag: String) {
    val logcat = Logcat()

    // Sometimes first run observes stale package data
    // so run twice to prevent that
    repeat(2) {
        val mark = logcat.mark(tag)
        eventually {
            runShellCommandOrThrow("cmd jobscheduler run -u " +
                "${Process.myUserHandle().identifier} -f " +
                "${context.packageManager.permissionControllerPackageName} 2")
        }
        logcat.assertLogcatContainsInOrder("*:*", 30_000,
            mark,
            "onStartJob",
            "Done auto-revoke for user")
    }
}

inline fun withApp(
    apk: String,
    packageName: String,
    action: () -> Unit
) {
    installApk(apk)
    try {
        // Try to reduce flakiness caused by new package update not propagating in time
        Thread.sleep(1000)
        action()
    } finally {
        uninstallApp(packageName)
    }
}

inline fun <T> withDeviceConfig(
    namespace: String,
    name: String,
    value: String,
    action: () -> T
): T {
    val oldValue = runWithShellPermissionIdentity(ThrowingSupplier {
        DeviceConfig.getProperty(namespace, name)
    })
    try {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, value, false /* makeDefault */)
        }
        return action()
    } finally {
        runWithShellPermissionIdentity {
            DeviceConfig.setProperty(namespace, name, oldValue, false /* makeDefault */)
        }
    }
}

inline fun <T> withUnusedThresholdMs(threshold: Long, action: () -> T): T {
    return withDeviceConfig(
        DeviceConfig.NAMESPACE_PERMISSIONS, "auto_revoke_unused_threshold_millis2",
        threshold.toString(), action)
}

fun awaitAppState(pkg: String, stateMatcher: Matcher<Int>) {
    val context: Context = InstrumentationRegistry.getTargetContext()
    eventually {
        runWithShellPermissionIdentity {
            val packageImportance = context
                .getSystemService(ActivityManager::class.java)!!
                .getPackageImportance(pkg)
            assertThat(packageImportance, stateMatcher)
        }
    }
}

fun startApp(packageName: String) {
    assertThat(
        runShellCommand("monkey -p $packageName -c android.intent.category.LAUNCHER 1"),
        CoreMatchers.containsString("Events injected: 1"))
    awaitAppState(packageName, Matchers.lessThanOrEqualTo(IMPORTANCE_TOP_SLEEPING))
    waitForIdle()
}

class Logcat() : LogcatInspector() {
    override fun executeShellCommand(command: String?): InputStream {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        return ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command))
    }
}
