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

package android.cts.statsdatom.alarm;

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

public class WakeupAlarmStatsTests extends DeviceTestCase implements IBuildReceiver {
    private static final String FEATURE_AUTOMOTIVE = "android.hardware.type.automotive";

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

    public void testWakeupAlarm() throws Exception {
        // For automotive, all wakeup alarm becomes normal alarm. So this
        // test does not work.
        if (DeviceUtils.hasFeature(getDevice(), FEATURE_AUTOMOTIVE)) return;
        final int atomTag = AtomsProto.Atom.WAKEUP_ALARM_OCCURRED_FIELD_NUMBER;

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWakeupAlarm");

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data.size()).isAtLeast(1);
        for (int i = 0; i < data.size(); i++) {
            AtomsProto.WakeupAlarmOccurred wao = data.get(i).getAtom().getWakeupAlarmOccurred();
            assertThat(wao.getTag()).isEqualTo("*walarm*:android.cts.statsdatom.testWakeupAlarm");
            assertThat(wao.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        }
    }
}
