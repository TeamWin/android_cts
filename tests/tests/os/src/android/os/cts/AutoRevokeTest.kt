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

package android.os.cts

import android.Manifest.permission.READ_CALENDAR
import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.graphics.Rect
import android.net.Uri
import android.platform.test.annotations.AppModeFull
import android.provider.DeviceConfig
import android.provider.Settings.*
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiObject2
import android.test.InstrumentationTestCase
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Switch
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.*
import com.android.compatibility.common.util.ThrowingSupplier
import com.android.compatibility.common.util.UiAutomatorUtils
import com.android.compatibility.common.util.UiDumpUtils
import org.hamcrest.CoreMatchers.containsString
import org.junit.Assert.assertThat
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

private const val APK_PATH = "/data/local/tmp/cts/os/CtsAutoRevokeDummyApp.apk"
private const val APK_PACKAGE_NAME = "android.os.cts.autorevokedummyapp"

/**
 * Test for auto revoke
 */
class AutoRevokeTest : InstrumentationTestCase() {

    companion object {
        const val LOG_TAG = "AutoRevokeTest"
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    fun testUnusedApp_getsPermissionRevoked() {
        wakeUpScreen()
        withUnusedThresholdMs(3L) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                eventually {
                    assertPermission(PERMISSION_GRANTED)
                }
                goHome()
                Thread.sleep(5)

                // Run
                runAutoRevoke()

                // Verify
                eventually {
                    assertPermission(PERMISSION_DENIED)
                }
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    fun testUsedApp_doesntGetPermissionRevoked() {
        wakeUpScreen()
        withUnusedThresholdMs(100_000L) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                eventually {
                    assertPermission(PERMISSION_GRANTED)
                }
                goHome()
                Thread.sleep(5)

                // Run
                runAutoRevoke()
                Thread.sleep(500)

                // Verify
                assertPermission(PERMISSION_GRANTED)
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    fun testAutoRevoke_userWhitelisting() {
        wakeUpScreen()
        withUnusedThresholdMs(TimeUnit.DAYS.toMillis(30)) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                assertWhitelistState(false)

                // Verify
                waitFindObject(byTextIgnoreCase("Request whitelist")).click()
                waitFindObject(byTextIgnoreCase("Permissions")).click()
                val autoRevokeEnabledToggle = getWhitelistToggle()
                assertTrue(autoRevokeEnabledToggle.isChecked)

                // Grant whitelist
                autoRevokeEnabledToggle.click()
                eventually {
                    assertFalse(getWhitelistToggle().isChecked)
                }

                // Verify
                goBack()
                goBack()
                goBack()
                startApp()
                assertWhitelistState(true)
            }
        }
    }

    // TODO grantRuntimePermission fails to grant permission
    @AppModeFull(reason = "Uses separate apps for testing")
    fun _testInstallGrants_notRevokedImmediately() {
        wakeUpScreen()
        withUnusedThresholdMs(TimeUnit.DAYS.toMillis(30)) {
            withDummyApp {
                // Setup
                runWithShellPermissionIdentity {
                    instrumentation.uiAutomation
                            .grantRuntimePermission(APK_PACKAGE_NAME, READ_CALENDAR)
                }
                eventually {
                    assertPermission(PERMISSION_GRANTED)
                }

                // Run
                runAutoRevoke()
                Thread.sleep(500)

                // Verify
                assertPermission(PERMISSION_GRANTED)
            }
        }
    }

    private fun wakeUpScreen() {
        runShellCommand("input keyevent KEYCODE_WAKEUP")
        runShellCommand("input keyevent 82")
    }

    private fun runAutoRevoke() {
        runShellCommand("cmd jobscheduler run -u 0 " +
                "-f ${context.packageManager.permissionControllerPackageName} 2")
    }

    private inline fun <T> withDeviceConfig(
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

    private inline fun <T> withUnusedThresholdMs(threshold: Long, action: () -> T): T {
        return withDeviceConfig(
                "permissions", "auto_revoke_unused_threshold_millis", threshold.toString(), action)
    }

    private fun installApp() {
        assertThat(runShellCommand("pm install -r $APK_PATH"), containsString("Success"))
    }

    private fun uninstallApp() {
        assertThat(runShellCommand("pm uninstall $APK_PACKAGE_NAME"), containsString("Success"))
    }

    private fun startApp() {
        runShellCommand("am start -n $APK_PACKAGE_NAME/$APK_PACKAGE_NAME.MainActivity")
    }

    private fun goHome() {
        runShellCommand("input keyevent KEYCODE_HOME")
    }

    private fun goBack() {
        runShellCommand("input keyevent KEYCODE_BACK")
    }

    private fun clickPermissionAllow() {
        waitFindObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
                .click()
    }

    private inline fun withDummyApp(action: () -> Unit) {
        installApp()
        try {
            action()
        } finally {
            uninstallApp()
        }
    }

    private fun assertPermission(state: Int) {
        // For some reason this incorrectly always returns PERMISSION_DENIED
//        runWithShellPermissionIdentity {
//            assertEquals(
//                permissionStateToString(state),
//                permissionStateToString(context.packageManager.checkPermission(READ_CALENDAR, APK_PACKAGE_NAME)))
//        }

        try {
            context.startActivity(Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", APK_PACKAGE_NAME, null))
                    .addFlags(FLAG_ACTIVITY_NEW_TASK))

            waitFindObject(byTextIgnoreCase("Permissions")).click()

            waitForIdle()
            val ui = instrumentation.uiAutomation.rootInActiveWindow
            val permStateSection = ui.lowestCommonAncestor(
                    { textAsString.equals("Allowed", ignoreCase = true) },
                    { textAsString.equals("Denied", ignoreCase = true) }
            ).assertNotNull {
                "Cannot find permissions state section in\n${dumpUi(ui)}"
            }
            val sectionHeaderIndex = permStateSection.children.indexOfFirst {
                it?.depthFirstSearch {
                    textAsString.equals(
                            if (state == PERMISSION_GRANTED) "Allowed" else "Denied",
                            ignoreCase = true)
                } != null
            }
            permStateSection.getChild(sectionHeaderIndex + 1).depthFirstSearch {
                textAsString.equals("Calendar", ignoreCase = true)
            }.assertNotNull {
                "Permission must be ${permissionStateToString(state)}\n${dumpUi(ui)}"
            }
        } finally {
            goBack()
            goBack()
        }
    }

    private fun assertWhitelistState(state: Boolean) {
        assertThat(
            waitFindObject(By.textStartsWith("Auto-revoke whitelisted: ")).text,
            containsString(state.toString()))
    }

    private fun getWhitelistToggle(): AccessibilityNodeInfo {
        waitForIdle()
        return eventually {
            val ui = instrumentation.uiAutomation.rootInActiveWindow
            return@eventually ui.lowestCommonAncestor(
                { textAsString == "Remove permissions if app isn’t used" },
                { className == Switch::class.java.name }
            ).assertNotNull {
                "No auto-revoke whitelist toggle found in\n${dumpUi(ui)}"
            }.depthFirstSearch { className == Switch::class.java.name }!!
        }
    }

    private fun waitForIdle() {
        instrumentation.uiAutomation.waitForIdle(2000, 5000)
        Thread.sleep(500)
        instrumentation.uiAutomation.waitForIdle(2000, 5000)
    }

    private inline fun <T> eventually(crossinline action: () -> T): T {
        val res = AtomicReference<T>()
        SystemUtil.eventually {
            res.set(action())
        }
        return res.get()
    }

    private fun waitFindObject(selector: BySelector): UiObject2 {
        try {
            return UiAutomatorUtils.waitFindObject(selector)
        } catch (e: RuntimeException) {
            val ui = instrumentation.uiAutomation.rootInActiveWindow

            val title = ui.depthFirstSearch { viewIdResourceName?.contains("alertTitle") == true }
            val okButton = ui.depthFirstSearch {
                (text as CharSequence?)?.toString()?.equals("OK", ignoreCase = true) ?: false
            }

            if (title?.text?.toString() == "Android System" && okButton != null) {
                // Auto dismiss occasional system dialogs to prevent interfering with the test
                android.util.Log.w(LOG_TAG, "Ignoring exception", e)
                okButton.click()
                return UiAutomatorUtils.waitFindObject(selector)
            } else {
                throw e
            }
        }
    }

    private fun byTextIgnoreCase(txt: String): BySelector {
        return By.text(Pattern.compile(txt, Pattern.CASE_INSENSITIVE))
    }

    private fun permissionStateToString(state: Int): String {
        return constToString<PackageManager>("PERMISSION_", state)
    }
}

val AccessibilityNodeInfo.bounds: Rect get() = Rect().also { getBoundsInScreen(it) }

fun AccessibilityNodeInfo.click() {
    runShellCommand("input tap ${bounds.centerX()} ${bounds.centerY()}")
}

fun AccessibilityNodeInfo.depthFirstSearch(
    condition: AccessibilityNodeInfo.() -> Boolean
): AccessibilityNodeInfo? {
    for (child in children) {
        child?.depthFirstSearch(condition)?.let { return it }
    }
    if (this.condition()) return this
    return null
}

fun AccessibilityNodeInfo.lowestCommonAncestor(
    condition1: AccessibilityNodeInfo.() -> Boolean,
    condition2: AccessibilityNodeInfo.() -> Boolean
): AccessibilityNodeInfo? {
    return depthFirstSearch {
        depthFirstSearch(condition1) != null &&
            depthFirstSearch(condition2) != null
    }
}

val AccessibilityNodeInfo.children: List<AccessibilityNodeInfo?> get() =
    List(childCount) { i -> getChild(i) }

val AccessibilityNodeInfo.textAsString: String? get() = (text as CharSequence?).toString()

inline fun <reified T> constToString(prefix: String, value: Int): String {
    return T::class.java.declaredFields.filter {
        Modifier.isStatic(it.modifiers) && it.name.startsWith(prefix)
    }.map {
        it.isAccessible = true
        it.name to it.get(null)
    }.find { (k, v) ->
        v == value
    }.assertNotNull {
        "None of ${T::class.java.simpleName}.$prefix* == $value"
    }.first
}

inline fun <T> T?.assertNotNull(errorMsg: () -> String): T {
    return if (this == null) throw AssertionError(errorMsg()) else this
}

fun dumpUi(ui: AccessibilityNodeInfo?) = buildString { UiDumpUtils.dumpNodes(ui, this) }