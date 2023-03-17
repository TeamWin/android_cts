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

package android.cts.statsdatom.voiceinteraction;

import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.STATSD_LOG_DEBOUNCE_MS;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_APK;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_CLASS;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.TEST_PKG;
import static android.cts.statsdatom.voiceinteraction.HotwordMetricsTestUtils.getTestAppUid;

import static com.google.common.truth.Truth.assertThat;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.hotword.Enums.HotwordDetectorType;

import com.android.compatibility.common.util.NonApiTest;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.List;

/**
 * Tests for HotwordDetectorCreateRequested logging.
 */
@NonApiTest(exemptionReasons = {}, justification = "METRIC")
public class HotwordDetectorCreateRequestedStatsTests extends DeviceTestCase implements
        IBuildReceiver {
    private static final String TEST_METHOD_DSP_SUCCESS_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_validHotwordDetectionComponentName_triggerSuccess";
    private static final String TEST_METHOD_DSP_FAILURE_FOR_METRIC_COLLECT =
            "testVoiceInteractionService_withoutManageHotwordDetectionPermission_triggerFailure";
    private static final String TEST_METHOD_SOFTWARE_SUCCESS_FOR_METRIC_COLLECT =
            "testHotwordDetectionService_onDetectFromMic_success";

    protected IBuildInfo mCtsBuild;

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
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        // Upload config to collect HotwordDetectorCreateRequested event.
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.HOTWORD_DETECTOR_CREATE_REQUESTED_FIELD_NUMBER);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallStatsdTestApp(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        super.tearDown();
    }

    public void testLogHotwordDetectorCreateRequestedspCreateSuccess() throws Exception {
        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_SUCCESS_FOR_METRIC_COLLECT);
        // Wait CTS test finish
        Thread.sleep(STATSD_LOG_DEBOUNCE_MS);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();
        // After the voice CTS test executes completely. the test will switch to original VIS. If
        // the original VIS creates detector, the test may receive more than 1 metric. Only the 1st
        // is the creation from the CTS testing.
        assertThat(data.size()).isAtLeast(1);
        assertHotwordDetectorCreateRequested(data.get(0),
                HotwordDetectorType.TRUSTED_DETECTOR_DSP, /* expectedCreatedDone= */ true);
    }

    public void testLogHotwordDetectorCreateRequestedDspCreateFail() throws Exception {
        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_DSP_FAILURE_FOR_METRIC_COLLECT);
        // Wait CTS test finish
        Thread.sleep(STATSD_LOG_DEBOUNCE_MS);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();
        // After testing finish, the test will switch to original VIS. If the original VIS creates
        // detector, we may receive more than 1. The 1st is the creation from the CTS testing.
        assertThat(data.size()).isAtLeast(1);
        assertHotwordDetectorCreateRequested(data.get(0),
                HotwordDetectorType.TRUSTED_DETECTOR_DSP, /* expectedCreatedDone= */ false);
    }

    public void testLogHotwordDetectorCreateRequestedSoftwareCreateSuccess() throws Exception {
        // Run test in CTS package
        DeviceUtils.runDeviceTests(getDevice(), TEST_PKG, TEST_CLASS,
                TEST_METHOD_SOFTWARE_SUCCESS_FOR_METRIC_COLLECT);
        // Wait CTS test finish
        Thread.sleep(STATSD_LOG_DEBOUNCE_MS);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isNotNull();
        // After testing finish, the test will switch to original VIS. If the original VIS creates
        // detector, we may receive more than 1. The 1st is the creation from the CTS testing.
        assertThat(data.size()).isAtLeast(1);
        assertHotwordDetectorCreateRequested(data.get(0),
                HotwordDetectorType.TRUSTED_DETECTOR_SOFTWARE, /* expectedCreatedDone= */ true);
    }

    private void assertHotwordDetectorCreateRequested(StatsLog.EventMetricData metric,
            HotwordDetectorType expectedType, boolean expectedCreatedDone) throws Exception {
        HotwordDetectorType detectorType =
                metric.getAtom().getHotwordDetectorCreateRequested().getDetectorType();
        boolean isCreatedDone =
                metric.getAtom().getHotwordDetectorCreateRequested().getIsCreatedDone();
        int uid = metric.getAtom().getHotwordDetectorCreateRequested().getUid();

        assertThat(detectorType).isEqualTo(expectedType);
        assertThat(isCreatedDone).isEqualTo(expectedCreatedDone);
        assertThat(uid).isEqualTo(getTestAppUid(getDevice()));
    }
}
