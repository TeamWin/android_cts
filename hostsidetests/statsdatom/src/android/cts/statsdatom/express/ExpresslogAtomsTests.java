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

package android.cts.statsdatom.express;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.internal.os.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.StatsLog.EventMetricData;
import com.android.os.expresslog.ExpressEventReported;
import com.android.os.expresslog.ExpressUidEventReported;
import com.android.os.expresslog.ExpresslogExtensionAtoms;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;
import com.android.tradefed.util.RunUtil;

import com.google.protobuf.ExtensionRegistry;

import java.io.FileNotFoundException;
import java.util.List;

import javax.annotation.Nullable;

public class ExpresslogAtomsTests extends DeviceTestCase implements IBuildReceiver {

    private static final String EXPRESSLOG_TEST_APK = "CtsStatsdExpressLogHelper.apk";
    private static final String EXPRESSLOG_TEST_PKG = "com.android.server.cts.device.expresslog";

    private IBuildInfo mCtsBuild;

    /**
     * Install the expresslog test app to the device.
     */
    private static void installExpressLogTestApp(ITestDevice device, IBuildInfo ctsBuildInfo)
            throws FileNotFoundException, DeviceNotAvailableException {
        DeviceUtils.installTestApp(device, EXPRESSLOG_TEST_APK, EXPRESSLOG_TEST_PKG, ctsBuildInfo);
    }

    /**
     * Uninstall the expresslog test app to the device.
     */
    private static void uninstallExpressLogTestApp(ITestDevice device) throws Exception {
        DeviceUtils.uninstallTestApp(device, EXPRESSLOG_TEST_PKG);
    }

    private static void runDeviceTestsOnExpresslogApp(ITestDevice device,
            @Nullable String testMethodName)
            throws DeviceNotAvailableException {
        DeviceUtils.runDeviceTests(device, EXPRESSLOG_TEST_PKG, ".AtomTests",
                testMethodName);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        installExpressLogTestApp(getDevice(), mCtsBuild);
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        uninstallExpressLogTestApp(getDevice());
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testExpressLogCounterAtom() throws Exception {
        final int atomTag = ExpresslogExtensionAtoms.EXPRESS_EVENT_REPORTED_FIELD_NUMBER;
        final int metricIdFieldNumber = 1;
        final long testMetricId = -2003432401640472195L;

        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(EXPRESSLOG_TEST_PKG);
        FieldValueMatcher.Builder expressMetricIdMatcher = ConfigUtils.createFvm(
                        metricIdFieldNumber)
                .setEqInt(testMetricId);
        ConfigUtils.addEventMetric(config, atomTag, List.of(expressMetricIdMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);

        runDeviceTestsOnExpresslogApp(getDevice(), "testCounterMetric");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        ExpresslogExtensionAtoms.registerAllExtensions(registry);

        // Verify correctness of data - we expect two atoms emitted by the test
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);
        assertThat(data).hasSize(2);

        ExpressEventReported atom1 = data.get(0).getAtom().getExtension(
                ExpresslogExtensionAtoms.expressEventReported);
        assertThat(atom1.getValue()).isEqualTo(1);
        ExpressEventReported atom2 = data.get(1).getAtom().getExtension(
                ExpresslogExtensionAtoms.expressEventReported);
        assertThat(atom2.getValue()).isEqualTo(10);
    }

    public void testExpressLogCounterWithUidAtom() throws Exception {
        final int atomTag = ExpresslogExtensionAtoms.EXPRESS_UID_EVENT_REPORTED_FIELD_NUMBER;
        final int metricIdFieldNumber = 1;
        final long testMetricId = 4877884967786456322L;

        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(EXPRESSLOG_TEST_PKG);
        FieldValueMatcher.Builder expressMetricIdMatcher = ConfigUtils.createFvm(
                        metricIdFieldNumber)
                .setEqInt(testMetricId);
        ConfigUtils.addEventMetric(config, atomTag, List.of(expressMetricIdMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);

        runDeviceTestsOnExpresslogApp(getDevice(), "testCounterWithUidMetric");
        RunUtil.getDefault().sleep(AtomTestUtils.WAIT_TIME_SHORT);

        ExtensionRegistry registry = ExtensionRegistry.newInstance();
        ExpresslogExtensionAtoms.registerAllExtensions(registry);

        // Verify correctness of data - we expect two atoms emitted by the test
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice(), registry);
        assertThat(data).hasSize(2);

        ExpressUidEventReported atom1 = data.get(0).getAtom().getExtension(
                ExpresslogExtensionAtoms.expressUidEventReported);
        assertThat(atom1.getValue()).isEqualTo(1);
        assertThat(atom1.getUid()).isAtLeast(0);
        ExpressUidEventReported atom2 = data.get(1).getAtom().getExtension(
                ExpresslogExtensionAtoms.expressUidEventReported);
        assertThat(atom2.getValue()).isEqualTo(10);
        assertThat(atom2.getUid()).isAtLeast(0);
    }
}
