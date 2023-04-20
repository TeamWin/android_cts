/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeNoException;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.AsbSecurityTest;
import android.provider.Settings;
import android.support.test.uiautomator.UiDevice;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.sts.common.SystemUtil;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.regex.Pattern;

@RunWith(AndroidJUnit4.class)
public class TestManagePermissionsActivity extends StsExtraBusinessLogicTestCase {

    // This CTS covers 4 bugs (b/253043495, b/253043502, b/253043218, b/253043490). These 4 bugs
    // were not marked duplicates initially, but during the bug fix, it has been realigned to have a
    // common fix for all of them. CTS is written for the common fix provided and for the sake of
    // testing the bugs independently, different Test methods are used.

    @AsbSecurityTest(cveBugId = 253043218)
    @Test
    public void testPocCVE_2023_21132() {
        testManagePermissionsActivity("b/253043218");
    }

    @AsbSecurityTest(cveBugId = 253043502)
    @Test
    public void testPocCVE_2023_21133() {
        testManagePermissionsActivity("b/253043502");
    }

    @AsbSecurityTest(cveBugId = 253043495)
    @Test
    public void testPocCVE_2023_21134() {
        testManagePermissionsActivity("b/253043495");
    }

    @AsbSecurityTest(cveBugId = 253043490)
    @Test
    public void testPocCVE_2023_21140() {
        testManagePermissionsActivity("b/253043490");
    }

    public void testManagePermissionsActivity(String bugId) {
        UiAutomation uiAutomation = null;
        UiDevice uiDevice = null;
        try {
            Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
            uiDevice = UiDevice.getInstance(instrumentation);
            uiAutomation = instrumentation.getUiAutomation();
            Context context = instrumentation.getContext();

            Intent vulnerableActivityIntent =
                    new Intent(Intent.ACTION_MANAGE_UNUSED_APPS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            String vulnerableActivityName =
                    vulnerableActivityIntent
                            .resolveActivity(context.getPackageManager())
                            .getClassName();

            // Disable global settings DEVICE_PROVISIONED and secure settings USER_SETUP_COMPLETE.
            // This is required as fix adds a check based on the state of these setting
            try (AutoCloseable deviceProvisionedAutoCloseable =
                            SystemUtil.withSetting(
                                    instrumentation,
                                    context.getString(
                                            R.string.testManagePermissionsActivity_global),
                                    Settings.Global.DEVICE_PROVISIONED,
                                    context.getString(
                                            R.string.testManagePermissionsActivity_settingValue));
                    AutoCloseable userSetupCompleteAutoCloseable =
                            SystemUtil.withSetting(
                                    instrumentation,
                                    context.getString(
                                            R.string.testManagePermissionsActivity_secure),
                                    Settings.Secure.USER_SETUP_COMPLETE,
                                    context.getString(
                                            R.string.testManagePermissionsActivity_settingValue))) {
                // Launching the vulnerable activity requires GRANT_RUNTIME_PERMISSIONS
                uiAutomation.adoptShellPermissionIdentity(
                        android.Manifest.permission.GRANT_RUNTIME_PERMISSIONS);
                context.startActivity(
                        new Intent(Intent.ACTION_MANAGE_UNUSED_APPS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

                // Check if the vulnerable activity is launched when device is not provisioned or
                // user setup is not complete
                boolean isVulnerable = false;

                Pattern resumedTruePattern =
                        Pattern.compile(
                                context.getString(
                                        R.string.testManagePermissionsActivity_mResumedTruePattern),
                                Pattern.CASE_INSENSITIVE);

                // Wait for dumpsys result to update
                int iteration = 0;
                do {
                    String activityDump =
                            uiDevice.executeShellCommand(
                                    String.format(
                                            context.getString(
                                                    R.string.testManagePermissionsActivity_dumpsys),
                                            vulnerableActivityName));
                    if (resumedTruePattern.matcher(activityDump).find()) {
                        isVulnerable = true;
                        break;
                    }
                    iteration++;
                    Thread.sleep(100);
                } while (iteration < 5);

                assertFalse(
                        context.getString(R.string.testManagePermissionsActivity_failMsg, bugId),
                        isVulnerable);
            }
        } catch (Exception e) {
            assumeNoException(e);
        } finally {
            try {
                uiDevice.pressHome();
                uiAutomation.dropShellPermissionIdentity();
            } catch (Exception e) {
                // Ignoring exceptions as the test has completed.
            }
        }
    }
}
