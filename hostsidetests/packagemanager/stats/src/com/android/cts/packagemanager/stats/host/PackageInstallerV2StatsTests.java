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

package com.android.cts.packagemanager.stats.host;
import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PackageInstallerV2StatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_INCREMENTAL_DELIVERY = "android.software.incremental_delivery";
    private static final String TEST_INSTALL_APK = "CtsStatsdAtomEmptyApp.apk";
    private static final String TEST_INSTALL_APK_BASE = "CtsStatsdAtomEmptySplitApp.apk";
    private static final String TEST_INSTALL_APK_SPLIT = "CtsStatsdAtomEmptySplitApp_pl.apk";
    private static final String TEST_INSTALL_PACKAGE =
            "com.android.cts.packagemanager.stats.emptyapp";
    private static final String TEST_REMOTE_DIR = "/data/local/tmp/statsdatom";
    private static final String SIGNATURE_FILE_SUFFIX = ".idsig";

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        getDevice().deleteFile(TEST_REMOTE_DIR);
        getDevice().uninstallPackage(TEST_INSTALL_PACKAGE);
        super.tearDown();
    }

    public void testPackageInstallerV2MetricsReported() throws Throwable {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_INCREMENTAL_DELIVERY)) return;
        final AtomsProto.PackageInstallerV2Reported report = installPackageUsingV2AndGetReport(
                new String[]{TEST_INSTALL_APK});
        assertTrue(report.getIsIncremental());
        // tests are ran using SHELL_UID and installation will be treated as adb install
        assertEquals("", report.getPackageName());
        assertEquals(1, report.getReturnCode());
        assertTrue(report.getDurationMillis() > 0);
        assertEquals(getTestFileSize(TEST_INSTALL_APK), report.getApksSizeBytes());
        assertTrue(report.getUid() != 0);
        assertEquals(getAppUid(TEST_INSTALL_PACKAGE), report.getUid());
    }

    public void testPackageInstallerV2MetricsReportedForSplits() throws Throwable {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_INCREMENTAL_DELIVERY)) return;
        final AtomsProto.PackageInstallerV2Reported report = installPackageUsingV2AndGetReport(
                new String[]{TEST_INSTALL_APK_BASE, TEST_INSTALL_APK_SPLIT});
        assertTrue(report.getIsIncremental());
        // tests are ran using SHELL_UID and installation will be treated as adb install
        assertEquals("", report.getPackageName());
        assertEquals(1, report.getReturnCode());
        assertTrue(report.getDurationMillis() > 0);
        assertEquals(
                getTestFileSize(TEST_INSTALL_APK_BASE) + getTestFileSize(TEST_INSTALL_APK_SPLIT),
                report.getApksSizeBytes());
        assertTrue(report.getUid() != 0);
        assertEquals(getAppUid(TEST_INSTALL_PACKAGE), report.getUid());
    }

    private long getTestFileSize(String fileName) throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File file = buildHelper.getTestFile(fileName);
        return file.length();
    }

    private AtomsProto.PackageInstallerV2Reported installPackageUsingV2AndGetReport(
            String[] apkNames) throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.PACKAGE_INSTALLER_V2_REPORTED_FIELD_NUMBER);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        installPackageUsingIncremental(apkNames, TEST_REMOTE_DIR);
        assertTrue(getDevice().isPackageInstalled(TEST_INSTALL_PACKAGE));
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);

        List<AtomsProto.PackageInstallerV2Reported> reports = new ArrayList<>();
        for (StatsLog.EventMetricData data : ReportUtils.getEventMetricDataList(getDevice())) {
            if (data.getAtom().hasPackageInstallerV2Reported()) {
                reports.add(data.getAtom().getPackageInstallerV2Reported());
            }
        }
        assertEquals(1, reports.size());
        return reports.get(0);
    }

    private void installPackageUsingIncremental(String[] apkNames, String remoteDirPath)
            throws Exception {
        getDevice().executeShellCommand("mkdir " + remoteDirPath);
        String[] remoteApkPaths = new String[apkNames.length];
        for (int i = 0; i < remoteApkPaths.length; i++) {
            remoteApkPaths[i] = pushApkToRemote(apkNames[i], remoteDirPath);
        }
        String installResult = getDevice().executeShellCommand(
                "pm install-incremental -t -g " + String.join(" ", remoteApkPaths));
        assertEquals("Success\n", installResult);
    }

    private String pushApkToRemote(String apkName, String remoteDirPath)
            throws Exception {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        final File apk = buildHelper.getTestFile(apkName);
        final File signature = buildHelper.getTestFile(apkName + SIGNATURE_FILE_SUFFIX);
        assertNotNull(apk);
        assertNotNull(signature);
        final String remoteApkPath = remoteDirPath + "/" + apk.getName();
        final String remoteSignaturePath = remoteApkPath + SIGNATURE_FILE_SUFFIX;
        assertTrue(getDevice().pushFile(apk, remoteApkPath));
        assertTrue(getDevice().pushFile(signature, remoteSignaturePath));
        return remoteApkPath;
    }

    protected int getAppUid(String pkgName) throws Exception {
        final int currentUser = getDevice().getCurrentUser();
        final String uidLine = getDevice().executeShellCommand(
                "cmd package list packages -U --user " + currentUser + " " + pkgName);
        final Pattern pattern = Pattern.compile("package:" + pkgName + " uid:(\\d+)");
        final Matcher matcher = pattern.matcher(uidLine);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        } else {
            return -1;
        }
    }

}
