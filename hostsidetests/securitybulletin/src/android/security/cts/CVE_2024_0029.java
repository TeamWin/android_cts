/*
 * Copyright (C) 2024 The Android Open Source Project
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

import static com.android.sts.common.CommandUtil.runAndCheck;
import static com.android.sts.common.DumpsysUtils.hasActivityResumed;

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeNoException;

import android.platform.test.annotations.AsbSecurityTest;

import com.android.sts.common.UserUtils;
import com.android.sts.common.tradefed.testtype.NonRootSecurityTestCase;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.UserInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;
import com.android.tradefed.util.RunUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class CVE_2024_0029 extends NonRootSecurityTestCase {

    @AsbSecurityTest(cveBugId = 305664128)
    @Test
    public void testPocCVE_2024_0029() {
        try {
            // Create a managed user 'cve_2024_0029_managedUser'.
            ITestDevice device = getDevice();
            final int primaryUserId = device.getCurrentUser();
            final String managedUserName = "cve_2024_0029_managedUser";
            try (AutoCloseable withManagedUser =
                    new UserUtils.SecondaryUser(device)
                            .managed(primaryUserId)
                            .name(managedUserName)
                            .withUser()) {
                // Fetch the userId of 'cve_2024_0029_managedUser'
                int managedUserId = -1;
                Map<Integer, UserInfo> mapOfUserInfos = device.getUserInfos();
                for (UserInfo userInfo : mapOfUserInfos.values()) {
                    if (userInfo.userName().equals(managedUserName)) {
                        managedUserId = userInfo.userId();
                        break;
                    }
                }
                assume().withMessage("UserId not found for the managed user")
                        .that(managedUserId)
                        .isNotEqualTo(-1);

                // Install test-app for both users
                installPackage("CVE-2024-0029.apk", "-t");

                // Set the 'PocAdminReceiver' as profile-owner using device policy
                // manager and disable the 'screen capture' policy for
                // 'cve_2024_0029_managedUser'.
                // Later, set the 'PocAdminReceiver' as profile-owner using
                // device policy manager and disable the 'screen capture' policy for the
                // primary user.
                final String testPkg = "android.security.cts.CVE_2024_0029";
                try (AutoCloseable withPocAdminReceiverComponentAsProfileOwnerForManagedUser =
                                withPocAdminReceiverComponentAsProfileOwner(
                                        device, testPkg, managedUserId);
                        AutoCloseable withPocAdminReceiverComponentAsProfileOwnerForPrimaryUser =
                                withPocAdminReceiverComponentAsProfileOwner(
                                        device, testPkg, primaryUserId)) {
                    // Run DeviceTest
                    runDeviceTests(
                            new DeviceTestRunOptions(testPkg)
                                    .setDevice(device)
                                    .setTestClassName(testPkg + ".DeviceTest")
                                    .setTestMethodName("testPocCVE_2024_0029")
                                    .setUserId(managedUserId));
                }
            }
        } catch (Exception e) {
            assumeNoException(e);
        }
    }

    private AutoCloseable withPocAdminReceiverComponentAsProfileOwner(
            ITestDevice device, String pkgName, int userId) throws Exception {

        // Set the 'PocAdminReceiver' as profile-owner using device policy manager
        final String componentName = String.format("%s/%s.PocAdminReceiver", pkgName, pkgName);
        runAndCheck(
                device, String.format("dpm set-profile-owner --user %d %s", userId, componentName));

        // Start 'HelperActivity' to disable the 'screen capture' policy
        // Failing to set the policy, results in ASSUMPTION_FAILURE
        runAndCheck(
                device,
                String.format(
                        "am start-activity --user %d -cmp %s/.HelperActivity", userId, pkgName));
        final long timeout = 5_000L;
        final long startTime = System.currentTimeMillis();
        do {
            if (hasActivityResumed(device, pkgName + ".HelperActivity")) {
                break;
            }
            RunUtil.getDefault().sleep(500L);
        } while (System.currentTimeMillis() - startTime <= timeout);
        assume().withMessage("HelperActivity did not start successfully")
                .that(hasActivityResumed(device, pkgName + ".HelperActivity"))
                .isTrue();

        // Return 'AutoCloseable' to remove the 'PocAdminReceiver' as profile-owner
        return () -> device.removeAdmin(componentName, userId);
    }
}
