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

package com.android.cts.packagemanager.stats.host;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PackageInstallationSessionReportedStatsTests extends PackageManagerStatsTestsBase{
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.emptyapp";

    @Override
    protected void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        super.tearDown();
    }

    public void testPackageInstallationSessionReportedForApkSuccess() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLATION_SESSION_REPORTED_FIELD_NUMBER);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.installTestApp(getDevice(), TEST_INSTALL_APK, TEST_INSTALL_PACKAGE, mCtsBuild);
        assertThat(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE,
                String.valueOf(getDevice().getCurrentUser()))).isTrue();
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        List<AtomsProto.PackageInstallationSessionReported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallationSessionReported()) {
                reports.add(data.getAtom().getPackageInstallationSessionReported());
            }
        }
        assertThat(reports.size()).isEqualTo(1);
        AtomsProto.PackageInstallationSessionReported report = reports.get(0);
        final int expectedUid = getAppUid(TEST_INSTALL_PACKAGE);
        final int expectedUser = getDevice().getCurrentUser();
        final long expectedApksSizeBytes = getTestFileSize(TEST_INSTALL_APK);
        // TODO(b/249294752): check installflags, installer, userTypes and versionCode in report
        checkReportResult(report, expectedUid, Collections.singletonList(expectedUser),
                Collections.emptyList(), 1 /* success */,
                expectedApksSizeBytes, 0 /* dataLoaderType */, false,
                false, false, false, false, false, false);
    }

    private void checkReportResult(AtomsProto.PackageInstallationSessionReported report,
            int expectedUid, List<Integer> expectedUserIds,
            List<Integer> expectedOriginalUserIds, int expectedPublicReturnCode,
            long expectedApksSizeBytes, int expectedDataLoaderType,
            boolean expectedIsInstant, boolean expectedIsReplace, boolean expectedIsSystem,
            boolean expectedIsInherit, boolean expectedInstallingExistingAsUser,
            boolean expectedIsMoveInstall, boolean expectedIsStaged) {
        assertThat(report.getUid()).isEqualTo(expectedUid);
        assertThat(report.getUserIdsList()).isEqualTo(expectedUserIds);
        assertThat(report.getOriginalUserIdsList()).isEqualTo(expectedOriginalUserIds);
        assertThat(report.getPublicReturnCode()).isEqualTo(expectedPublicReturnCode);
        assertThat(report.getApksSizeBytes()).isEqualTo(expectedApksSizeBytes);
        final long totalDuration = report.getTotalDurationMillis();
        assertThat(totalDuration).isGreaterThan(0);
        assertThat(report.getInstallStepsCount()).isEqualTo(4);
        long sumStepDurations = 0;
        for (long duration : report.getStepDurationMillisList()) {
            assertThat(duration).isAtLeast(0);
            sumStepDurations += duration;
        }
        assertThat(sumStepDurations).isGreaterThan(0);
        assertThat(sumStepDurations).isLessThan(totalDuration);
        assertThat(report.getOriginalInstallerPackageUid()).isEqualTo(-1);
        assertThat(report.getDataLoaderType()).isEqualTo(expectedDataLoaderType);
        assertThat(report.getIsInstant()).isEqualTo(expectedIsInstant);
        assertThat(report.getIsReplace()).isEqualTo(expectedIsReplace);
        assertThat(report.getIsSystem()).isEqualTo(expectedIsSystem);
        assertThat(report.getIsInherit()).isEqualTo(expectedIsInherit);
        assertThat(report.getIsInstallingExistingAsUser()).isEqualTo(
                expectedInstallingExistingAsUser);
        assertThat(report.getIsMoveInstall()).isEqualTo(expectedIsMoveInstall);
        assertThat(report.getIsStaged()).isEqualTo(expectedIsStaged);
    }
}
