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

package android.cts.statsdatom.appstart;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

public class AppStartStatsTests extends DeviceTestCase implements IBuildReceiver {
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.installStatsdTestApp(getDevice(), mCtsBuild);
        DeviceUtils.turnScreenOn(getDevice());
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

    public void testAppStartOccurred() throws Exception {
        final int atomTag = AtomsProto.Atom.APP_START_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.sleep_top", 3_500);

        // Sorted list of events in order in which they occurred.
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AtomsProto.AppStartOccurred atom = data.get(0).getAtom().getAppStartOccurred();
        assertThat(atom.getPkgName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getActivityName())
                .isEqualTo("com.android.server.cts.device.statsdatom.StatsdCtsForegroundActivity");
        assertThat(atom.getIsInstantApp()).isFalse();
        assertThat(atom.getActivityStartMillis()).isGreaterThan(0L);
        assertThat(atom.getTransitionDelayMillis()).isGreaterThan(0);
    }
}
