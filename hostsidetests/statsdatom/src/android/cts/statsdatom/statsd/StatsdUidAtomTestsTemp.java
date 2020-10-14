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

package android.cts.statsdatom.statsd;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.AtomsProto.Atom;
import com.android.os.StatsLog.EventMetricData;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public final class StatsdUidAtomTestsTemp extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

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
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    /**
     * Tests that statsd correctly maps isolated uids to host uids by verifying that atoms logged
     * from an isolated process are seen as coming from their host process.
     */
    public void testIsolatedToHostUidMapping() throws Exception {
        StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(config, Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER,
                /*uidInAttributionChain=*/false, DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.uploadConfig(getDevice(), config);
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Create an isolated service from which an AppBreadcrumbReported atom is logged.
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                "testIsolatedProcessService");

        // Verify correctness of data.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(1);
        AppBreadcrumbReported atom = data.get(0).getAtom().getAppBreadcrumbReported();
        assertThat(atom.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(atom.getLabel()).isEqualTo(0);
        assertThat(atom.getState()).isEqualTo(AppBreadcrumbReported.State.START);
    }

    public void testCpuTimePerUid() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;

        StatsdConfig.Builder config =
                ConfigUtils.createConfigBuilder(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addGaugeMetricForUidAtom(config, Atom.CPU_TIME_PER_UID_FIELD_NUMBER,
                /*uidInAttributionChain=*/false, DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.uploadConfig(getDevice(), config);

        // Do some trivial work on the app
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Trigger atom pull
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Verify correctness of data
        List<Atom> atoms = ReportUtils.getGaugeMetricAtoms(getDevice());
        boolean found = false;
        int appUid = DeviceUtils.getStatsdTestAppUid(getDevice());
        for (Atom atom : atoms) {
            assertThat(atom.getCpuTimePerUid().getUid()).isEqualTo(appUid);
            assertThat(atom.getCpuTimePerUid().getUserTimeMicros()).isGreaterThan(0L);
            assertThat(atom.getCpuTimePerUid().getSysTimeMicros()).isGreaterThan(0L);
            found = true;
        }
        assertWithMessage("Found no CpuTimePerUid atoms from uid " + appUid).that(found).isTrue();
    }
}
