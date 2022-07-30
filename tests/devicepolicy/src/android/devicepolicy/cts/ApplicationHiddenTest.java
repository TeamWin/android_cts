/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.content.Intent.ACTION_PACKAGE_ADDED;
import static android.content.Intent.ACTION_PACKAGE_REMOVED;
import static android.content.pm.PackageManager.MATCH_UNINSTALLED_PACKAGES;

import static com.android.bedstead.nene.permissions.CommonPermissions.QUERY_ALL_PACKAGES;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.pm.PackageManager;
import android.stats.devicepolicy.EventId;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureTestAppInstalled;
import com.android.bedstead.harrier.annotations.enterprise.CanSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.CannotSetPolicyTest;
import com.android.bedstead.harrier.annotations.enterprise.PolicyAppliesTest;
import com.android.bedstead.harrier.annotations.enterprise.RequireHasPolicyExemptApps;
import com.android.bedstead.harrier.policies.ApplicationHidden;
import com.android.bedstead.harrier.policies.ApplicationHiddenSystemOnly;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(BedsteadJUnit4.class)
public class ApplicationHiddenTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Package SYSTEM_PACKAGE =
            TestApis.packages().find("com.android.keychain");
    private static final Package NON_EXISTING_PACKAGE =
            TestApis.packages().find("non.existing.package");
    private static final PackageManager sLocalPackageManager =
            TestApis.context().instrumentedContext().getPackageManager();

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void isApplicationHidden_systemApp_isHidden_returnsTrue() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());

        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void isApplicationHidden_systemApp_isNotHidden_returnsFalse() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());

        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    false);

            assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHiddenSystemOnly.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_throwsException() {
        assertThrows(SecurityException.class, () -> sDeviceState.dpc().devicePolicyManager()
                .isApplicationHidden(sDeviceState.dpc().componentName(),
                        sDeviceState.testApp().packageName()));
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_isHidden_returnsTrue() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName());

        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    true);

            assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.testApp().packageName())).isTrue();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void isApplicationHidden_notSystemApp_isNotHidden_returnsFalse() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName());

        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    false);

            assertThat(sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                    sDeviceState.dpc().componentName(),
                    sDeviceState.testApp().packageName())).isFalse();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    originalValue);
        }
    }

    @PolicyAppliesTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_systemApp_true_hidesApplication() throws Exception {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());

        try {

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_PACKAGE_ADDED,
                                 (intent) -> intent.getData() != null
                                         && intent.getData().getSchemeSpecificPart().equals(
                                         SYSTEM_PACKAGE.packageName()))) {

                boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                        true);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                assertThat(sLocalPackageManager.getPackageInfo(
                        SYSTEM_PACKAGE.packageName(),
                        PackageManager.PackageInfoFlags.of(0))).isNull();
                assertThat(sLocalPackageManager.getPackageInfo(
                        SYSTEM_PACKAGE.packageName(),
                        PackageManager.PackageInfoFlags.of(
                                MATCH_UNINSTALLED_PACKAGES))).isNotNull();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_true_hidesApplication() throws Exception {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName());

        try {
            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_PACKAGE_ADDED,
                                 (intent) -> intent.getData() != null
                                         && intent.getData().getSchemeSpecificPart().equals(
                                         sDeviceState.testApp().packageName()))) {

                boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                        true);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                assertThat(sLocalPackageManager.getPackageInfo(
                        sDeviceState.testApp().packageName(),
                        PackageManager.PackageInfoFlags.of(0))).isNull();
                assertThat(sLocalPackageManager.getPackageInfo(
                        sDeviceState.testApp().packageName(),
                        PackageManager.PackageInfoFlags.of(
                                MATCH_UNINSTALLED_PACKAGES))).isNotNull();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    originalValue);
        }
    }

    @PolicyAppliesTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_systemApp_false_unHidesApplication() throws Exception {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());
        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_PACKAGE_REMOVED,
                                 (intent) -> intent.getData() != null
                                         && intent.getData().getSchemeSpecificPart().equals(
                                         SYSTEM_PACKAGE.packageName()))) {

                boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                        false);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                assertThat(sLocalPackageManager.getPackageInfo(
                        SYSTEM_PACKAGE.packageName(),
                        PackageManager.PackageInfoFlags.of(0))).isNotNull();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @PolicyAppliesTest(policy = ApplicationHidden.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_false_unHidesApplication() throws Exception {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName());
        try {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                    true);

            try (BlockingBroadcastReceiver broadcastReceiver =
                         sDeviceState.registerBroadcastReceiver(ACTION_PACKAGE_REMOVED,
                                 (intent) -> intent.getData() != null
                                         && intent.getData().getSchemeSpecificPart().equals(
                                         sDeviceState.testApp().packageName()))) {

                boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), sDeviceState.testApp().packageName(),
                        false);

                assertThat(result).isTrue();
                broadcastReceiver.awaitForBroadcastOrFail();
            }

            try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                assertThat(sLocalPackageManager.getPackageInfo(
                        sDeviceState.testApp().packageName(),
                        PackageManager.PackageInfoFlags.of(0))).isNotNull();
            }
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = ApplicationHidden.class)
    @RequireHasPolicyExemptApps
    public void setApplicationHidden_nonSystemApp_policyExempt_doesNotHideApplication() throws Exception {
        Set<String> policyExemptApps = TestApis.devicePolicy().getPolicyExemptApps();

        for (String packageName : policyExemptApps) {
            try {
                boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), packageName,
                        true);

                assertThat(result).isFalse();
                try (PermissionContext p = TestApis.permissions().withPermission(QUERY_ALL_PACKAGES)) {
                    assertThat(sLocalPackageManager.getPackageInfo(
                            packageName,
                            PackageManager.PackageInfoFlags.of(0))).isNotNull();
                }
            } finally {
                sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                        false);
            }
        }
    }

    @CannotSetPolicyTest(policy = ApplicationHidden.class)
    public void setApplicationHidden_systemApp_notAllowed_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(),
                        SYSTEM_PACKAGE.packageName(), true));
    }

    @CanSetPolicyTest(policy = ApplicationHiddenSystemOnly.class)
    @EnsureTestAppInstalled
    public void setApplicationHidden_nonSystemApp_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                        sDeviceState.dpc().componentName(),
                        sDeviceState.testApp().packageName(), true));
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_true_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    true);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                    .whereType().isEqualTo(EventId.SET_APPLICATION_HIDDEN_VALUE)
                    .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                    .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                    .whereStrings().contains(SYSTEM_PACKAGE.packageName())
                    .whereStrings().contains("hidden")
                    .whereStrings().contains(sDeviceState.dpc().isParentInstance() ? "calledFromParent" : "notCalledFromParent")
            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class, ApplicationHiddenSystemOnly.class})
    public void setApplicationHidden_false_logsEvent() {
        boolean originalValue = sDeviceState.dpc().devicePolicyManager().isApplicationHidden(
                sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName());

        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create()) {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    false);

            MetricQueryBuilderSubject.assertThat(metrics.query()
                            .whereType().isEqualTo(EventId.SET_APPLICATION_HIDDEN_VALUE)
                            .whereAdminPackageName().isEqualTo(sDeviceState.dpc().packageName())
                            .whereBoolean().isEqualTo(sDeviceState.dpc().isDelegate())
                            .whereStrings().contains(SYSTEM_PACKAGE.packageName())
                            .whereStrings().contains("not_hidden")
                            .whereStrings().contains(sDeviceState.dpc().isParentInstance() ? "calledFromParent" : "notCalledFromParent")
            ).wasLogged();
        } finally {
            sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                    sDeviceState.dpc().componentName(), SYSTEM_PACKAGE.packageName(),
                    originalValue);
        }
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    public void setApplicationHidden_notInstalledPackage_returnsFalse() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                sDeviceState.dpc().componentName(), NON_EXISTING_PACKAGE.packageName(),
                true);

        assertThat(result).isFalse();
    }

    @CanSetPolicyTest(policy = {ApplicationHidden.class})
    public void setApplicationHidden_deviceAdmin_returnsFalse() {
        boolean result = sDeviceState.dpc().devicePolicyManager().setApplicationHidden(
                sDeviceState.dpc().componentName(), sDeviceState.dpcOnly().packageName(),
                true);

        assertThat(result).isFalse();
    }
}
