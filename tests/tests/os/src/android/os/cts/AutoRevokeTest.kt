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

import android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_TOP_SLEEPING
import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_AUTO_REVOKE_PERMISSIONS
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import android.content.pm.PackageManager
import android.content.pm.PackageManager.PERMISSION_DENIED
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.content.res.Resources
import android.net.Uri
import android.platform.test.annotations.AppModeFull
import android.support.test.uiautomator.By
import android.support.test.uiautomator.BySelector
import android.support.test.uiautomator.UiDevice
import android.support.test.uiautomator.UiObject2
import android.support.test.uiautomator.UiScrollable
import android.support.test.uiautomator.UiSelector
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
import android.widget.Switch
import androidx.test.InstrumentationRegistry
import androidx.test.runner.AndroidJUnit4
import com.android.compatibility.common.util.MatcherUtils.hasTextThat
import com.android.compatibility.common.util.SystemUtil
import com.android.compatibility.common.util.SystemUtil.eventually
import com.android.compatibility.common.util.SystemUtil.getEventually
import com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow
import com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity
import com.android.compatibility.common.util.UI_ROOT
import com.android.compatibility.common.util.click
import com.android.compatibility.common.util.depthFirstSearch
import com.android.compatibility.common.util.lowestCommonAncestor
import com.android.compatibility.common.util.textAsString
import com.android.compatibility.common.util.uiDump
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.containsStringIgnoringCase
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.Matcher
import org.hamcrest.Matchers.greaterThan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Modifier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import java.util.regex.Pattern

private const val READ_CALENDAR = "android.permission.READ_CALENDAR"

/**
 * Test for auto revoke
 */
@RunWith(AndroidJUnit4::class)
class AutoRevokeTest {

    private val context: Context = InstrumentationRegistry.getTargetContext()
    private val instrumentation: Instrumentation = InstrumentationRegistry.getInstrumentation()
    private val uiDevice: UiDevice = UiDevice.getInstance(instrumentation)

    private val mPermissionControllerResources: Resources = context.createPackageContext(
            context.packageManager.permissionControllerPackageName, 0).resources

    private lateinit var supportedApkPath: String
    private lateinit var supportedAppPackageName: String
    private lateinit var preMinVersionApkPath: String
    private lateinit var preMinVersionAppPackageName: String

    companion object {
        const val LOG_TAG = "AutoRevokeTest"
    }

    @Before
    fun setup() {
        // Collapse notifications
        assertThat(
                runShellCommandOrThrow("cmd statusbar collapse"),
                equalTo(""))

        // Wake up the device
        runShellCommandOrThrow("input keyevent KEYCODE_WAKEUP")
        runShellCommandOrThrow("input keyevent 82")

        if (isAutomotiveDevice()) {
            supportedApkPath = APK_PATH_S_APP
            supportedAppPackageName = APK_PACKAGE_NAME_S_APP
            preMinVersionApkPath = APK_PATH_R_APP
            preMinVersionAppPackageName = APK_PACKAGE_NAME_R_APP
        } else {
            supportedApkPath = APK_PATH_R_APP
            supportedAppPackageName = APK_PACKAGE_NAME_R_APP
            preMinVersionApkPath = APK_PATH_Q_APP
            preMinVersionAppPackageName = APK_PACKAGE_NAME_Q_APP
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testUnusedApp_getsPermissionRevoked() {
        withUnusedThresholdMs(3L) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                assertPermission(PERMISSION_GRANTED)
                killDummyApp()
                Thread.sleep(5)

                // Run
                runAppHibernationJob(context, LOG_TAG)

                // Verify
                assertPermission(PERMISSION_DENIED)
                runShellCommandOrThrow("cmd statusbar expand-notifications")
                waitFindObject(By.textContains("unused app"))
                        .click()
                waitFindObject(By.text(supportedAppPackageName))
                waitFindObject(By.text("Calendar permission removed"))
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testUsedApp_doesntGetPermissionRevoked() {
        withUnusedThresholdMs(100_000L) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                assertPermission(PERMISSION_GRANTED)
                killDummyApp()
                Thread.sleep(5)

                // Run
                runAppHibernationJob(context, LOG_TAG)
                Thread.sleep(1000)

                // Verify
                assertPermission(PERMISSION_GRANTED)
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testPreMinAutoRevokeVersionUnusedApp_doesntGetPermissionRevoked() {
        withUnusedThresholdMs(3L) {
            withDummyApp(preMinVersionApkPath, preMinVersionAppPackageName) {
                withDummyApp {
                    startApp(preMinVersionAppPackageName)
                    clickPermissionAllow()
                    assertPermission(PERMISSION_GRANTED, preMinVersionAppPackageName)

                    killDummyApp(preMinVersionAppPackageName)

                    startApp()
                    clickPermissionAllow()
                    assertPermission(PERMISSION_GRANTED)

                    killDummyApp()
                    Thread.sleep(20)

                    // Run
                    runAppHibernationJob(context, LOG_TAG)
                    Thread.sleep(500)

                    // Verify
                    assertPermission(PERMISSION_DENIED)
                    assertPermission(PERMISSION_GRANTED, preMinVersionAppPackageName)
                }
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testAutoRevoke_userAllowlisting() {
        assumeFalse(context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE))
        withUnusedThresholdMs(4L) {
            withDummyApp {
                // Setup
                startApp()
                clickPermissionAllow()
                assertAllowlistState(false)

                // Verify
                waitFindObject(byTextIgnoreCase("Request allowlist")).click()
                waitFindObject(byTextIgnoreCase("Permissions")).click()
                val autoRevokeEnabledToggle = getAllowlistToggle()
                assertTrue(autoRevokeEnabledToggle.isChecked)

                // Grant allowlist
                autoRevokeEnabledToggle.click()
                eventually {
                    assertFalse(getAllowlistToggle().isChecked)
                }

                // Run
                goBack()
                goBack()
                goBack()
                runAppHibernationJob(context, LOG_TAG)
                Thread.sleep(500L)

                // Verify
                startApp()
                assertAllowlistState(true)
                assertPermission(PERMISSION_GRANTED)
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testInstallGrants_notRevokedImmediately() {
        withUnusedThresholdMs(TimeUnit.DAYS.toMillis(30)) {
            withDummyApp {
                // Setup
                goToPermissions()
                click("Calendar")
                // Wear OS uses a switch and does not display a dialog
                if (!hasFeatureWatch()) {
                    click("Allow")
                }
                goBack()
                goBack()
                goBack()

                // Run
                runAppHibernationJob(context, LOG_TAG)
                Thread.sleep(500)

                // Verify
                assertPermission(PERMISSION_GRANTED)
            }
        }
    }

    @AppModeFull(reason = "Uses separate apps for testing")
    @Test
    fun testAutoRevoke_allowlistingApis() {
        withDummyApp {
            val pm = context.packageManager
            runWithShellPermissionIdentity {
                assertFalse(pm.isAutoRevokeWhitelisted(supportedAppPackageName))
            }

            runWithShellPermissionIdentity {
                assertTrue(pm.setAutoRevokeWhitelisted(supportedAppPackageName, true))
            }
            eventually {
                runWithShellPermissionIdentity {
                    assertTrue(pm.isAutoRevokeWhitelisted(supportedAppPackageName))
                }
            }

            runWithShellPermissionIdentity {
                assertTrue(pm.setAutoRevokeWhitelisted(supportedAppPackageName, false))
            }
            eventually {
                runWithShellPermissionIdentity {
                    assertFalse(pm.isAutoRevokeWhitelisted(supportedAppPackageName))
                }
            }
        }
    }

    private fun isAutomotiveDevice(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
    }

    private fun installApp() {
        installApk(supportedApkPath)
    }

    private fun uninstallApp() {
        uninstallApp(supportedAppPackageName)
    }

    private fun startApp() {
        startApp(supportedAppPackageName)
    }

    private fun goHome() {
        runShellCommandOrThrow("input keyevent KEYCODE_HOME")
    }

    private fun goBack() {
        runShellCommandOrThrow("input keyevent KEYCODE_BACK")
    }

    private fun killDummyApp(pkg: String = supportedAppPackageName) {
        assertThat(
                runShellCommandOrThrow("am force-stop " + pkg),
                equalTo(""))
        awaitAppState(pkg, greaterThan(IMPORTANCE_TOP_SLEEPING))
    }

    private fun clickPermissionAllow() {
        if (isAutomotiveDevice()) {
            waitFindObject(By.text(Pattern.compile(
                    Pattern.quote(mPermissionControllerResources.getString(
                            mPermissionControllerResources.getIdentifier(
                                    "grant_dialog_button_allow", "string",
                                    "com.android.permissioncontroller"))),
                    Pattern.CASE_INSENSITIVE or Pattern.UNICODE_CASE))).click()
        } else {
            waitFindObject(By.res("com.android.permissioncontroller:id/permission_allow_button"))
                    .click()
        }
    }

    private inline fun withDummyApp(
        apk: String = supportedApkPath,
        packageName: String = supportedAppPackageName,
        action: () -> Unit
    ) {
        withApp(apk, packageName, action)
    }

    private fun assertPermission(state: Int, packageName: String = supportedAppPackageName) {
        assertPermission(packageName, READ_CALENDAR, state)
    }

    private fun goToPermissions(packageName: String = supportedAppPackageName) {
        context.startActivity(Intent(ACTION_AUTO_REVOKE_PERMISSIONS)
                .setData(Uri.fromParts("package", packageName, null))
                .addFlags(FLAG_ACTIVITY_NEW_TASK))

        waitForIdle()

        if (hasFeatureWatch()) {
            // WearOS need to scroll down to find the Permission item to click
            UiScrollable(UiSelector().scrollable(true))
                    .getChildByText(UiSelector(), "Permissions")
        }

        click("Permissions")
    }

    private fun click(label: String) {
        waitFindNode(hasTextThat(containsStringIgnoringCase(label))).click()
        waitForIdle()
    }

    private fun hasFeatureWatch(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
    }

    private fun assertAllowlistState(state: Boolean) {
        assertThat(
            waitFindObject(By.textStartsWith("Auto-revoke allowlisted: ")).text,
            containsString(state.toString()))
    }

    private fun getAllowlistToggle(): AccessibilityNodeInfo {
        waitForIdle()
        return waitFindSwitch("Remove permissions if app isn’t used")
    }

    private fun waitForIdle() {
        instrumentation.uiAutomation.waitForIdle(1000, 10000)
        Thread.sleep(500)
        instrumentation.uiAutomation.waitForIdle(1000, 10000)
    }

    private inline fun <T> eventually(crossinline action: () -> T): T {
        val res = AtomicReference<T>()
        SystemUtil.eventually {
            res.set(action())
        }
        return res.get()
    }

    private fun waitFindObject(selector: BySelector): UiObject2 {
        return waitFindObject(instrumentation.uiAutomation, selector)
    }
}

private fun permissionStateToString(state: Int): String {
    return constToString<PackageManager>("PERMISSION_", state)
}

fun waitFindSwitch(label: String): AccessibilityNodeInfo {
    return getEventually {
        val ui = UI_ROOT
        val node = ui.lowestCommonAncestor(
                { node -> node.textAsString == label },
                { node -> node.className == Switch::class.java.name })
        if (node == null) {
            ui.depthFirstSearch { it.isScrollable }?.performAction(ACTION_SCROLL_FORWARD)
        }
        return@getEventually node.assertNotNull {
            "Switch not found: $label in\n${uiDump(ui)}"
        }.depthFirstSearch { node -> node.className == Switch::class.java.name }!!
    }
}

/**
 * For some reason waitFindObject sometimes fails to find UI that is present in the view hierarchy
 */
fun waitFindNode(
    matcher: Matcher<AccessibilityNodeInfo>,
    failMsg: String? = null,
    timeoutMs: Long = 10_000
): AccessibilityNodeInfo {
    return getEventually({
        val ui = UI_ROOT
        ui.depthFirstSearch { node ->
            matcher.matches(node)
        }.assertNotNull {
            buildString {
                if (failMsg != null) {
                    appendLine(failMsg)
                }
                appendLine("No view found matching $matcher:\n\n${uiDump(ui)}")
            }
        }
    }, timeoutMs)
}

fun byTextIgnoreCase(txt: String): BySelector {
    return By.text(Pattern.compile(txt, Pattern.CASE_INSENSITIVE))
}

fun waitForIdle() {
    InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(1000, 10000)
}

fun uninstallApp(packageName: String) {
    assertThat(runShellCommandOrThrow("pm uninstall $packageName"), containsString("Success"))
}

fun installApk(apk: String) {
    assertThat(runShellCommandOrThrow("pm install -r $apk"), containsString("Success"))
}

fun assertPermission(packageName: String, permissionName: String, state: Int) {
    assertThat(permissionName, containsString("permission."))
    eventually {
        runWithShellPermissionIdentity {
            assertEquals(
                    permissionStateToString(state),
                    permissionStateToString(
                            InstrumentationRegistry.getTargetContext()
                                    .packageManager
                                    .checkPermission(permissionName, packageName)))
        }
    }
}

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
