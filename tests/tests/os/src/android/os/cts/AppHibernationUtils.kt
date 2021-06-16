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
import android.app.UiAutomation
import android.content.Context
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DeviceConfig
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiObject2
import androidx.test.InstrumentationRegistry
import com.android.compatibility.common.util.LogcatInspector
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils
import com.android.compatibility.common.util.click
import com.android.compatibility.common.util.depthFirstSearch
import com.android.compatibility.common.util.textAsString
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.junit.Assert.assertThat
import java.io.InputStream

const val APK_PATH_S_APP = "/data/local/tmp/cts/os/CtsAutoRevokeSApp.apk"
const val APK_PACKAGE_NAME_S_APP = "android.os.cts.autorevokesapp"
const val APK_PATH_R_APP = "/data/local/tmp/cts/os/CtsAutoRevokeRApp.apk"
const val APK_PACKAGE_NAME_R_APP = "android.os.cts.autorevokerapp"
const val APK_PATH_Q_APP = "/data/local/tmp/cts/os/CtsAutoRevokeQApp.apk"
const val APK_PACKAGE_NAME_Q_APP = "android.os.cts.autorevokeqapp"

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

inline fun withAppNoUninstallAssertion(
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
        uninstallAppWithoutAssertion(packageName)
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
    val context = InstrumentationRegistry.getTargetContext()
    val intent = context.packageManager.getLaunchIntentForPackage(packageName)
    context.startActivity(intent)
    awaitAppState(packageName, Matchers.lessThanOrEqualTo(IMPORTANCE_TOP_SLEEPING))
    waitForIdle()
}

fun goHome() {
    runShellCommandOrThrow("input keyevent KEYCODE_HOME")
}

fun waitFindObject(uiAutomation: UiAutomation, selector: BySelector): UiObject2 {
    try {
        return UiAutomatorUtils.waitFindObject(selector)
    } catch (e: RuntimeException) {
        val ui = uiAutomation.rootInActiveWindow

        val title = ui.depthFirstSearch { node ->
            node.viewIdResourceName?.contains("alertTitle") == true
        }
        val okButton = ui.depthFirstSearch { node ->
            node.textAsString?.equals("OK", ignoreCase = true) ?: false
        }

        if (title?.text?.toString() == "Android System" && okButton != null) {
            // Auto dismiss occasional system dialogs to prevent interfering with the test
            android.util.Log.w(AutoRevokeTest.LOG_TAG, "Ignoring exception", e)
            okButton.click()
            return UiAutomatorUtils.waitFindObject(selector)
        } else {
            throw e
        }
    }
}

class Logcat() : LogcatInspector() {
    override fun executeShellCommand(command: String?): InputStream {
        val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
        return ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command))
    }
}
