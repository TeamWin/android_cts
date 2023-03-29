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

import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_POWER_RESTRICTIONS;
import static android.app.admin.DevicePolicyManager.EXEMPT_FROM_SUSPENSION;
import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;

import static com.android.bedstead.metricsrecorder.truth.MetricQueryBuilderSubject.assertThat;
import static com.android.bedstead.nene.appops.AppOpsMode.ALLOWED;
import static com.android.bedstead.nene.appops.AppOpsMode.DEFAULT;
import static com.android.bedstead.nene.appops.CommonAppOps.OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION;
import static com.android.bedstead.nene.permissions.CommonPermissions.MANAGE_DEVICE_POLICY_APP_EXEMPTIONS;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.admin.DevicePolicyManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.pm.PackageManager.NameNotFoundException;
import android.stats.devicepolicy.EventId;
import android.util.ArrayMap;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.IntTestParameter;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.metricsrecorder.EnterpriseMetricsRecorder;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RunWith(BedsteadJUnit4.class)
@RequireFeature(FEATURE_DEVICE_ADMIN)
public class ApplicationExemptionsTest {

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();
    private static final Context sContext = TestApis.context().instrumentedContext();
    private static final DevicePolicyManager sLocalDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final TestApp sTestApp = sDeviceState.testApps().any();

    private static final String INVALID_PACKAGE_NAME = "com.google.android.notapackage";
    private static final Set<Integer> INVALID_EXEMPTIONS = new HashSet<>(List.of(-1));
    private static final Map<Integer, String> APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS =
            new ArrayMap<>();
    static {
        APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.put(
                EXEMPT_FROM_SUSPENSION, OPSTR_SYSTEM_EXEMPT_FROM_SUSPENSION);
    }

    @IntTestParameter({EXEMPT_FROM_SUSPENSION })
    @Retention(RetentionPolicy.RUNTIME)
    private @interface ApplicationExemptionConstants {}

    @Test
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_noPermission_throwsSecurityException() {
        Set<Integer> exemptionSet = new HashSet<>(EXEMPT_FROM_SUSPENSION);
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(SecurityException.class, () ->
                    sLocalDevicePolicyManager.setApplicationExemptions(
                            sTestApp.packageName(),
                            exemptionSet));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_validExemptionSet_exemptionAppOpsGranted(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(), exemptionSet);

            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption)))
                    .isEqualTo(ALLOWED);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_emptyExemptionSet_unsetsAllExemptions(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(exemption);
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    new HashSet<>());

            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption)))
                    .isEqualTo(DEFAULT);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_invalidExemptionInSet_throwsIllegalArgumentException() {
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(IllegalArgumentException.class, () ->
                    sLocalDevicePolicyManager.setApplicationExemptions(
                            sTestApp.packageName(),
                            INVALID_EXEMPTIONS));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_invalidPackage_throwsNameNotFoundException() {
        Set<Integer> exemptionSet = new HashSet<>(EXEMPT_FROM_SUSPENSION);
        assertThrows(NameNotFoundException.class, () ->
                sLocalDevicePolicyManager.setApplicationExemptions(
                        INVALID_PACKAGE_NAME, exemptionSet));
    }

    @Test
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void getApplicationExemptions_noPermission_throwsSecurityException() {
        try (TestAppInstance testApp = sTestApp.install()) {
            assertThrows(SecurityException.class, () ->
                    sLocalDevicePolicyManager.getApplicationExemptions(sTestApp.packageName()));
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void getApplicationExemptions_validPackage_returnsExemptionsSet(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            Set<Integer> applicationExemptions =
                    sLocalDevicePolicyManager.getApplicationExemptions(sTestApp.packageName());
            assertThat(applicationExemptions).isEqualTo(exemptionSet);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void getApplicationExemptions_invalidPackage_throwsNameNotFoundException() {
        assertThrows(NameNotFoundException.class, () ->
                sLocalDevicePolicyManager.getApplicationExemptions(INVALID_PACKAGE_NAME));
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_reinstallApplication_exemptionAppOpsReset()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_SUSPENSION));
        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);
        }

        try (TestAppInstance testApp = sTestApp.install()) {
            assertThat(sTestApp.pkg().appOps()
                    .get(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(EXEMPT_FROM_SUSPENSION)))
                    .isEqualTo(DEFAULT);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_powerRestrictionExemption_exemptedAppStandbyBucket()
            throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(EXEMPT_FROM_POWER_RESTRICTIONS));

        try (TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(),
                    exemptionSet);

            assertThat(sTestApp.pkg().getAppStandbyBucket()).isEqualTo(
                    UsageStatsManager.STANDBY_BUCKET_EXEMPTED);
        }
    }

    @Test
    @EnsureHasPermission(MANAGE_DEVICE_POLICY_APP_EXEMPTIONS)
    @Postsubmit(reason = "new test")
    public void setApplicationExemptions_validExemptionSet_logsEvent(
            @ApplicationExemptionConstants int exemption) throws NameNotFoundException {
        Set<Integer> exemptionSet = new HashSet<>(List.of(exemption));
        try (EnterpriseMetricsRecorder metrics = EnterpriseMetricsRecorder.create();
            TestAppInstance testApp = sTestApp.install()) {
            sLocalDevicePolicyManager.setApplicationExemptions(
                    sTestApp.packageName(), exemptionSet);

            assertThat(metrics.query()
                .whereType()
                .isEqualTo(EventId.SET_APPLICATION_EXEMPTIONS_VALUE)
                .whereStrings().size().isEqualTo(exemptionSet.size() + 1)
                .whereStrings().contains(sTestApp.packageName())
                .whereStrings()
                .contains(APPLICATION_EXEMPTION_CONSTANTS_TO_APP_OPS.get(exemption))
                .whereAdminPackageName()
                .isEqualTo(TestApis.context().instrumentedContext().getPackageName()))
                .wasLogged();
        }
    }
}
