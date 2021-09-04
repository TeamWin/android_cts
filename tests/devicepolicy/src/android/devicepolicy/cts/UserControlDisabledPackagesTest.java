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

package android.devicepolicy.cts;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.remotedpc.RemoteDpc.DPC_COMPONENT_NAME;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.assertThrows;

import android.Manifest.permission;
import android.app.ActivityManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.stats.devicepolicy.EventId;
import android.util.Log;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PositivePolicyTest;
import com.android.bedstead.harrier.policies.UserControlDisabledPackages;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstanceReference;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.queryable.queries.StringQuery;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@RunWith(BedsteadJUnit4.class)
public class UserControlDisabledPackagesTest {
    private static final String TAG = "UserControlDisabledPackagesTest";

    private static final TestApis sTestApis = new TestApis();
    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApp sTestApp =
            sTestAppProvider.query().whereActivities().isNotEmpty().get();

    private static final ActivityManager sActivityManager =
            sTestApis.context().instrumentedContext().getSystemService(ActivityManager.class);
    private static final PackageManager sPackageManager =
            sTestApis.context().instrumentedContext().getPackageManager();

    private static final String PACKAGE_NAME = "com.android.foo.bar.baz";

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    @CanSetPolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "New test")
    public void setUserControlDisabledPackages_verifyMetricIsLogged() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        DPC_COMPONENT_NAME);

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    DPC_COMPONENT_NAME,
                    Arrays.asList(PACKAGE_NAME));

            assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_USER_CONTROL_DISABLED_PACKAGES_VALUE)
                    .whereAdminPackageName().isEqualTo(
                            sDeviceState.dpc().componentName().getPackageName())
                    .whereStrings().contains(
                            StringQuery.string().isEqualTo(PACKAGE_NAME))).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    DPC_COMPONENT_NAME,
                    originalDisabledPackages);
        }
    }

    @Test
    @PositivePolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_toOneProtectedPackage() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(DPC_COMPONENT_NAME,
                Arrays.asList(PACKAGE_NAME));
        try {
            assertThat(sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                    DPC_COMPONENT_NAME))
                    .containsExactly(PACKAGE_NAME);
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    DPC_COMPONENT_NAME,
                    originalDisabledPackages);
        }
    }

    @Test
    @PositivePolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_toEmptyProtectedPackages() {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        DPC_COMPONENT_NAME);

        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(DPC_COMPONENT_NAME,
                Collections.emptyList());
        try {
            assertThat(
                    sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                            DPC_COMPONENT_NAME)).isEmpty();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    DPC_COMPONENT_NAME,
                    originalDisabledPackages);
        }
    }

    @Test
    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void setUserControlDisabledPackages_notAllowedToSetProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                        DPC_COMPONENT_NAME,
                        Collections.emptyList()));
    }

    @Test
    @PositivePolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void
    getUserControlDisabledPackages_noProtectedPackagesSet_returnsEmptyProtectedPackages() {
        // This is testing the default state of the device so the disabled packages returned should
        // be empty.
        assertThat(sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                DPC_COMPONENT_NAME))
                .isEmpty();
    }

    @Test
    @CannotSetPolicyTest(policy = UserControlDisabledPackages.class)
    public void
    getUserControlDisabledPackages_notAllowedToRetrieveProtectedPackages_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        DPC_COMPONENT_NAME));
    }

    @Test
    @EnsureHasPermission(value = permission.FORCE_STOP_PACKAGES)
    @PositivePolicyTest(policy = UserControlDisabledPackages.class)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    public void setUserControlDisabledPackages_launchActivity_verifyPackageNotStopped()
            throws Exception {
        List<String> originalDisabledPackages =
                sDeviceState.dpc().devicePolicyManager().getUserControlDisabledPackages(
                        DPC_COMPONENT_NAME);
        UserReference currentRunningUserOnTest = sTestApis.users().instrumented();
        int currentRunningUserId = currentRunningUserOnTest.id();
        String testAppPackageName = sTestApp.packageName();

        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(DPC_COMPONENT_NAME,
                Arrays.asList(testAppPackageName));
        try (TestAppInstanceReference instance = sTestApp.install(currentRunningUserOnTest)) {
            instance.activities().any().start();

            sActivityManager.forceStopPackageAsUser(testAppPackageName, currentRunningUserId);

            try {
                assertPackageStopped(
                        testAppPackageName, currentRunningUserId, /* stopped= */ false);
            } finally {
                stopPackage(testAppPackageName, currentRunningUserId);
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(
                    DPC_COMPONENT_NAME,
                    originalDisabledPackages);
        }
    }

    private void stopPackage(String packageName, int userId) throws Exception {
        sDeviceState.dpc().devicePolicyManager().setUserControlDisabledPackages(DPC_COMPONENT_NAME,
                Collections.emptyList());
        sActivityManager.forceStopPackageAsUser(packageName, userId);
        assertPackageStopped(packageName, userId, /* stopped= */ true);
    }

    private void assertPackageStopped(String packageName, int userId, boolean stopped)
            throws Exception {
        assertWithMessage("Package %s stopped for user %s", packageName, userId)
                .that(isPackageStopped(packageName, userId)).isEqualTo(stopped);
    }

    private boolean isPackageStopped(String packageName, int userId) throws Exception {
        PackageInfo packageInfo = sPackageManager.getPackageInfoAsUser(
                packageName, PackageManager.GET_META_DATA, userId);
        boolean stopped = (packageInfo.applicationInfo.flags & ApplicationInfo.FLAG_STOPPED)
                == ApplicationInfo.FLAG_STOPPED;
        Log.i(TAG, "Application flags for " + packageName + " on user " + userId + " = "
                + Integer.toHexString(packageInfo.applicationInfo.flags) + ". Stopped: " + stopped);
        return stopped;
    }
}
