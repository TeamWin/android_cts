/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.cts.appbinding;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.regex.Pattern;

public class AppBindingHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final boolean SKIP_UNINSTALL = false;

    private static final String APK_1 = "CtsAppBindingService1.apk";
    private static final String APK_2 = "CtsAppBindingService2.apk";
    private static final String APK_3 = "CtsAppBindingService3.apk";
    private static final String APK_4 = "CtsAppBindingService4.apk";
    private static final String APK_5 = "CtsAppBindingService5.apk";
    private static final String APK_B = "CtsAppBindingServiceB.apk";

    private static final String PACKAGE_A = "com.android.cts.appbinding.app";
    private static final String PACKAGE_B = "com.android.cts.appbinding.app.b";

    private static final String APP_BINDING_SETTING = "app_binding_constants";

    private static final String SERVICE_1 = "com.android.cts.appbinding.app.MyService";
    private static final String SERVICE_2 = "com.android.cts.appbinding.app.MyService2";

    private IBuildInfo mCtsBuild;

    private static final int USER_SYSTEM = 0;

    private static final int DEFAULT_TIMEOUT_SEC = 10;

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private void installAppAsUser(String appFileName, boolean grantPermissions, int userId)
            throws Exception {
        CLog.d("Installing app " + appFileName + " for user " + userId);
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        String result = getDevice().installPackageForUser(
                buildHelper.getTestFile(appFileName), true, grantPermissions, userId, "-t");
        assertNull("Failed to install " + appFileName + " for user " + userId + ": " + result,
                result);
    }

    private void runCommand(String command, String expectedOutputPattern) throws Exception {
        CLog.d("Executing command: " + command);
        final String output = getDevice().executeShellCommand(command);
        CLog.d("Output: " + output);

        final Pattern pat = Pattern.compile(
                expectedOutputPattern, Pattern.MULTILINE | Pattern.COMMENTS | Pattern.DOTALL);
        if (!pat.matcher(output.trim()).find()) {
            fail("Output from \"" + command + "\" didn't match \"" + expectedOutputPattern + "\"");
        }
    }

    private void updateConstants(String settings) throws Exception {
        runCommand("settings put global " + APP_BINDING_SETTING + " '" + settings + "'", "");
    }

    private void setSmsApp(String pkg, int userId) throws Exception {
        runCommand("cmd phone sms set-default-app --user " + userId + " " + pkg,
                "^SMS \\s app \\s set \\s to \\s " + Pattern.quote(pkg) + "$");
    }

    private void uninstallTestApps() throws Exception {
        if (SKIP_UNINSTALL) {
            return;
        }
        getDevice().uninstallPackage(PACKAGE_A);
        getDevice().uninstallPackage(PACKAGE_B);
    }

    private void runWithRetries(int timeoutSeconds, ThrowingRunnable r) throws Throwable {
        final long timeout = System.currentTimeMillis() + timeoutSeconds * 1000;
        Throwable lastThrowable = null;

        int sleep = 200;
        while (System.currentTimeMillis() < timeout) {
            try {
                r.run();
                return;
            } catch (Throwable th) {
                lastThrowable = th;
            }
            Thread.sleep(sleep);
            sleep = Math.min(1000, sleep * 2);
        }
        throw lastThrowable;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // Reset to the default setting.
        updateConstants(",");

        uninstallTestApps();
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallTestApps();

        super.tearDown();
    }

    private void installAndCheckBound(String apk, String packageName,
            String serviceClass, int userId) throws Throwable {
        // Install
        installAppAsUser(apk, true, userId);

        // Set as the default app
        setSmsApp(packageName, userId);

        runWithRetries(DEFAULT_TIMEOUT_SEC, () -> {
            runCommand("dumpsys activity service " + packageName + "/" + serviceClass,
                    Pattern.quote("[" + packageName + "]") + " .* "
                    + Pattern.quote("[" + serviceClass + "]"));
        });

        // This should contain:
        // "conn,0,[Default SMS app],PACKAGE,CLASS,bound,connected"
        runCommand("dumpsys app_binding -s",
                "^" + Pattern.quote("conn,[Default SMS app]," + userId + "," + packageName + ","
                        + serviceClass + ",bound,connected") + "$");
    }

    private void installAndCheckNotBound(String apk, String packageName, int userId,
            String expectedErrorPattern) throws Throwable {
        // Install
        installAppAsUser(apk, true, userId);

        // Set as the default app
        setSmsApp(packageName, userId);

        // This should contain:
        // "finder,0,[Default SMS app],PACKAGE,null,ERROR-MESSAGE"
        runWithRetries(DEFAULT_TIMEOUT_SEC, () -> {
            runCommand("dumpsys app_binding -s",
                    "^" + Pattern.quote("finder,[Default SMS app]," + userId + ","
                            + packageName + ",null,") + ".*"
                            + Pattern.quote(expectedErrorPattern) + ".*$");
        });
    }

    /**
     * Install APK 1 and make it the default SMS app and make sure the service gets bound.
     */
    public void testSimpleBind1() throws Throwable {
        installAndCheckBound(APK_1, PACKAGE_A, SERVICE_1, USER_SYSTEM);
    }

    /**
     * Install APK 2 and make it the default SMS app and make sure the service gets bound.
     */
    public void testSimpleBind2() throws Throwable {
        installAndCheckBound(APK_2, PACKAGE_A, SERVICE_2, USER_SYSTEM);
    }

    /**
     * Install APK B and make it the default SMS app and make sure the service gets bound.
     */
    public void testSimpleBindB() throws Throwable {
        installAndCheckBound(APK_B, PACKAGE_B, SERVICE_1, USER_SYSTEM);
    }

    /**
     * APK 3 doesn't have a valid service to be bound.
     */
    public void testSimpleNotBound3() throws Throwable {
        installAndCheckNotBound(APK_3, PACKAGE_A, USER_SYSTEM,
                "must be protected with android.permission.BIND_SMS_APP_SERVICE");
    }

    /**
     * APK 3 doesn't have a valid service to be bound.
     */
    public void testSimpleNotBound4() throws Throwable {
        installAndCheckNotBound(APK_4, PACKAGE_A, USER_SYSTEM, "More than one");
    }

    /**
     * APK 3 doesn't have a valid service to be bound.
     */
    public void testSimpleNotBound5() throws Throwable {
        installAndCheckNotBound(APK_5, PACKAGE_A, USER_SYSTEM,
                "Service with android.telephony.action.SMS_APP_SERVICE not found");
    }

    /**
     * Make sure when the SMS app gets updated, the service still gets bound correctly.
     */
    public void testUpgrade() throws Throwable {
        // Replace existing package without uninstalling.
        installAndCheckBound(APK_1, PACKAGE_A, SERVICE_1, USER_SYSTEM);
        installAndCheckBound(APK_2, PACKAGE_A, SERVICE_2, USER_SYSTEM);
        installAndCheckNotBound(APK_3, PACKAGE_A, USER_SYSTEM,
                "must be protected with android.permission.BIND_SMS_APP_SERVICE");
        installAndCheckBound(APK_1, PACKAGE_A, SERVICE_1, USER_SYSTEM);
        installAndCheckNotBound(APK_4, PACKAGE_A, USER_SYSTEM, "More than one");
    }

    /**
     * Make sure when the SMS app changes, the service still gets bound correctly.
     */
    public void testSwitchDefaultApp() throws Throwable {
        installAndCheckBound(APK_1, PACKAGE_A, SERVICE_1, USER_SYSTEM);
        installAndCheckBound(APK_B, PACKAGE_B, SERVICE_1, USER_SYSTEM);
        installAndCheckBound(APK_2, PACKAGE_A, SERVICE_2, USER_SYSTEM);
    }
}
