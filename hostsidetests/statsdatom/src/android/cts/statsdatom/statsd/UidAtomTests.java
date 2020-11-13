/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.AppOpEnum;
import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;
import android.os.WakeLockLevelEnum;
import android.server.ErrorSource;

import com.android.internal.os.StatsdConfigProto.FieldValueMatcher;
import com.android.internal.os.StatsdConfigProto.StatsdConfig;
import com.android.os.AtomsProto;
import com.android.os.AtomsProto.ANROccurred;
import com.android.os.AtomsProto.AppBreadcrumbReported;
import com.android.os.AtomsProto.AppCrashOccurred;
import com.android.os.AtomsProto.AppUsageEventOccurred;
import com.android.os.AtomsProto.Atom;
import com.android.os.AtomsProto.AttributionNode;
import com.android.os.AtomsProto.AudioStateChanged;
import com.android.os.AtomsProto.BlobCommitted;
import com.android.os.AtomsProto.BlobLeased;
import com.android.os.AtomsProto.BlobOpened;
import com.android.os.AtomsProto.CameraStateChanged;
import com.android.os.AtomsProto.DeviceCalculatedPowerBlameUid;
import com.android.os.AtomsProto.FlashlightStateChanged;
import com.android.os.AtomsProto.ForegroundServiceAppOpSessionEnded;
import com.android.os.AtomsProto.ForegroundServiceStateChanged;
import com.android.os.AtomsProto.GpsScanStateChanged;
import com.android.os.AtomsProto.LmkKillOccurred;
import com.android.os.AtomsProto.MediaCodecStateChanged;
import com.android.os.AtomsProto.NotificationReported;
import com.android.os.AtomsProto.OverlayStateChanged;
import com.android.os.AtomsProto.SettingSnapshot;
import com.android.os.AtomsProto.SyncStateChanged;
import com.android.os.AtomsProto.TestAtomReported;
import com.android.os.AtomsProto.UiEventReported;
import com.android.os.AtomsProto.VibratorStateChanged;
import com.android.os.AtomsProto.WakelockStateChanged;
import com.android.os.StatsLog.EventMetricData;
import com.android.server.notification.SmallHash;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Statsd atom tests that are done via app, for atoms that report a uid.
 */
public class UidAtomTests extends DeviceTestCase implements IBuildReceiver {

    private static final String TAG = "Statsd.UidAtomTests";

    private static final String TEST_PACKAGE_NAME = "com.android.server.cts.device.statsd";

    private static final String ACTION_SHOW_APPLICATION_OVERLAY = "action.show_application_overlay";

    private static final String FEATURE_AUDIO_OUTPUT = "android.hardware.audio.output";
    private static final String FEATURE_CAMERA = "android.hardware.camera";
    private static final String FEATURE_CAMERA_FLASH = "android.hardware.camera.flash";
    private static final String FEATURE_CAMERA_FRONT = "android.hardware.camera.front";
    private static final String FEATURE_LEANBACK_ONLY = "android.software.leanback_only";
    private static final String FEATURE_LOCATION_GPS = "android.hardware.location.gps";
    private static final String FEATURE_PICTURE_IN_PICTURE = "android.software.picture_in_picture";

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
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.APP_BREADCRUMB_REPORTED_FIELD_NUMBER, /*uidInAttributionChain=*/false);

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

    public void testLmkKillOccurred() throws Exception {
        if (!"true".equals(DeviceUtils.getProperty(getDevice(), "ro.lmk.log_stats"))) {
            return;
        }

        final int atomTag = Atom.LMK_KILL_OCCURRED_FIELD_NUMBER;
        final String actionLmk = "action.lmk";
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.executeBackgroundService(getDevice(), actionLmk);
        Thread.sleep(15_000);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        int appUid = DeviceUtils.getStatsdTestAppUid(getDevice());

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasLmkKillOccurred()).isTrue();
        LmkKillOccurred atom = data.get(0).getAtom().getLmkKillOccurred();
        assertThat(atom.getUid()).isEqualTo(appUid);
        assertThat(atom.getProcessName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        assertThat(atom.getOomAdjScore()).isAtLeast(500);
    }

    public void testAppCrashOccurred() throws Exception {
        final int atomTag = Atom.APP_CRASH_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.crash");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        AppCrashOccurred atom = data.get(0).getAtom().getAppCrashOccurred();
        assertThat(atom.getEventType()).isEqualTo("crash");
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(AppCrashOccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(AppCrashOccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        // TODO(b/172866626): add tests for incremental packages that crashed during loading
        assertFalse(atom.getIsPackageLoading());
    }

    public void testAudioState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_AUDIO_OUTPUT)) return;

        final int atomTag = Atom.AUDIO_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testAudioState";

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(AudioStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag,  /*uidInAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Because the timestamp is truncated, we skip checking time differences between state
        // changes.
        AtomTestUtils.assertStatesOccurred(stateSet, data, 0,
                atom -> atom.getAudioStateChanged().getState().getNumber());

        // Check that timestamp is truncated
        for (EventMetricData metric : data) {
            long elapsedTimestampNs = metric.getElapsedTimestampNanos();
            AtomTestUtils.assertTimestampIsTruncated(elapsedTimestampNs);
        }
    }

    public void testCameraState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_CAMERA) && !DeviceUtils.hasFeature(
                getDevice(), FEATURE_CAMERA_FRONT)) {
            return;
        }

        final int atomTag = Atom.CAMERA_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> cameraOn = new HashSet<>(Arrays.asList(CameraStateChanged.State.ON_VALUE));
        Set<Integer> cameraOff = new HashSet<>(Arrays.asList(CameraStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(cameraOn, cameraOff);
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useAttributionChain=*/ true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testCameraState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, AtomTestUtils.WAIT_TIME_LONG,
                atom -> atom.getCameraStateChanged().getState().getNumber());
    }

    public void testDeviceCalculatedPowerUse() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK_ONLY)) return;

        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.DEVICE_CALCULATED_POWER_USE_FIELD_NUMBER);
        DeviceUtils.unplugDevice(getDevice());

        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        Atom atom = ReportUtils.getGaugeMetricAtoms(getDevice()).get(0);
        assertThat(atom.getDeviceCalculatedPowerUse().getComputedPowerNanoAmpSecs())
                .isGreaterThan(0L);
        DeviceUtils.resetBatteryStatus(getDevice());
    }


    public void testDeviceCalculatedPowerBlameUid() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LEANBACK_ONLY)) return;
        if (!DeviceUtils.hasBattery(getDevice())) {
            return;
        }
        String kernelVersion = getDevice().executeShellCommand("uname -r");
        if (kernelVersion.contains("3.18")) {
            LogUtil.CLog.d("Skipping calculated power blame uid test.");
            return;
        }
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.DEVICE_CALCULATED_POWER_BLAME_UID_FIELD_NUMBER);
        DeviceUtils.unplugDevice(getDevice());

        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSimpleCpu");
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<Atom> atomList = ReportUtils.getGaugeMetricAtoms(getDevice());
        boolean uidFound = false;
        int uid = DeviceUtils.getStatsdTestAppUid(getDevice());
        long uidPower = 0;
        for (Atom atom : atomList) {
            DeviceCalculatedPowerBlameUid item = atom.getDeviceCalculatedPowerBlameUid();
            if (item.getUid() == uid) {
                assertWithMessage(String.format("Found multiple power values for uid %d", uid))
                        .that(uidFound).isFalse();
                uidFound = true;
                uidPower = item.getPowerNanoAmpSecs();
            }
        }
        assertWithMessage(String.format("No power value for uid %d", uid)).that(uidFound).isTrue();
        assertWithMessage(String.format("Non-positive power value for uid %d", uid))
                .that(uidPower).isGreaterThan(0L);
        DeviceUtils.resetBatteryStatus(getDevice());
    }

    public void testFlashlightState() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_CAMERA_FLASH)) return;

        final int atomTag = Atom.FLASHLIGHT_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testFlashlight";

        Set<Integer> flashlightOn = new HashSet<>(
                Arrays.asList(FlashlightStateChanged.State.ON_VALUE));
        Set<Integer> flashlightOff = new HashSet<>(
                Arrays.asList(FlashlightStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(flashlightOn, flashlightOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getFlashlightStateChanged().getState().getNumber());
    }

    public void testForegroundServiceState() throws Exception {
        final int atomTag = Atom.FOREGROUND_SERVICE_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testForegroundService";

        Set<Integer> enterForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.ENTER_VALUE));
        Set<Integer> exitForeground = new HashSet<>(
                Arrays.asList(ForegroundServiceStateChanged.State.EXIT_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(enterForeground, exitForeground);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getForegroundServiceStateChanged().getState().getNumber());
    }


    public void testForegroundServiceAccessAppOp() throws Exception {
        final int atomTag = Atom.FOREGROUND_SERVICE_APP_OP_SESSION_ENDED_FIELD_NUMBER;
        final String name = "testForegroundServiceAccessAppOp";

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertWithMessage("Wrong atom size").that(data.size()).isEqualTo(3);
        for (int i = 0; i < data.size(); i++) {
            ForegroundServiceAppOpSessionEnded atom
                    = data.get(i).getAtom().getForegroundServiceAppOpSessionEnded();
            final int opName = atom.getAppOpName().getNumber();
            final int acceptances = atom.getCountOpsAccepted();
            final int rejections = atom.getCountOpsRejected();
            final int count = acceptances + rejections;
            int expectedCount = 0;
            switch (opName) {
                case AppOpEnum.APP_OP_CAMERA_VALUE:
                    expectedCount = 3;
                    break;
                case AppOpEnum.APP_OP_FINE_LOCATION_VALUE:
                    expectedCount = 1;
                    break;
                case AppOpEnum.APP_OP_RECORD_AUDIO_VALUE:
                    expectedCount = 2;
                    break;
                case AppOpEnum.APP_OP_COARSE_LOCATION_VALUE:
                    // fall-through
                default:
                    fail("Unexpected opName " + opName);
            }
            assertWithMessage("Wrong count for " + opName).that(count).isEqualTo(expectedCount);
        }
    }

    public void testGpsScan() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_LOCATION_GPS)) return;
        // Whitelist this app against background location request throttling
        String origWhitelist = getDevice().executeShellCommand(
                "settings get global location_background_throttle_package_whitelist").trim();
        getDevice().executeShellCommand(String.format(
                "settings put global location_background_throttle_package_whitelist %s",
                DeviceUtils.STATSD_ATOM_TEST_PKG));

        try {
            final int atom = Atom.GPS_SCAN_STATE_CHANGED_FIELD_NUMBER;
            final int key = GpsScanStateChanged.STATE_FIELD_NUMBER;
            final int stateOn = GpsScanStateChanged.State.ON_VALUE;
            final int stateOff = GpsScanStateChanged.State.OFF_VALUE;
            final int minTimeDiffMillis = 500;
            final int maxTimeDiffMillis = 60_000;

            ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(),
                    DeviceUtils.STATSD_ATOM_TEST_PKG,
                    atom, /*useUidAttributionChain=*/true);

            DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests",
                    "testGpsScan");

            List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
            assertThat(data).hasSize(2);
            GpsScanStateChanged a0 = data.get(0).getAtom().getGpsScanStateChanged();
            GpsScanStateChanged a1 = data.get(1).getAtom().getGpsScanStateChanged();
            AtomTestUtils.assertTimeDiffBetween(data.get(0), data.get(1), minTimeDiffMillis,
                    maxTimeDiffMillis);
            assertThat(a0.getState().getNumber()).isEqualTo(stateOn);
            assertThat(a1.getState().getNumber()).isEqualTo(stateOff);
        } finally {
            if ("null".equals(origWhitelist) || "".equals(origWhitelist)) {
                getDevice().executeShellCommand(
                        "settings delete global location_background_throttle_package_whitelist");
            } else {
                getDevice().executeShellCommand(String.format(
                        "settings put global location_background_throttle_package_whitelist %s",
                        origWhitelist));
            }
        }
    }

    public void testMediaCodecActivity() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;
        final int atomTag = Atom.MEDIA_CODEC_STATE_CHANGED_FIELD_NUMBER;

        // 5 seconds. Starting video tends to be much slower than most other
        // tests on slow devices. This is unfortunate, because it leaves a
        // really big slop in assertStatesOccurred.  It would be better if
        // assertStatesOccurred had a tighter range on large timeouts.
        final int waitTime = 5000;

        // From {@link VideoPlayerActivity#DELAY_MILLIS}
        final int videoDuration = 2000;

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(MediaCodecStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "VideoPlayerActivity", "action", "action.play_video",
                waitTime);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, videoDuration,
                atom -> atom.getMediaCodecStateChanged().getState().getNumber());
    }

    public void testOverlayState() throws Exception {
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH)) return;
        final int atomTag = Atom.OVERLAY_STATE_CHANGED_FIELD_NUMBER;

        Set<Integer> entered = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.ENTERED_VALUE));
        Set<Integer> exited = new HashSet<>(
                Arrays.asList(OverlayStateChanged.State.EXITED_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(entered, exited);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", "action.show_application_overlay",
                5_000);

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        // The overlay box should appear about 2sec after the app start
        AtomTestUtils.assertStatesOccurred(stateSet, data, 0,
                atom -> atom.getOverlayStateChanged().getState().getNumber());
    }

    public void testPictureInPictureState() throws Exception {
        String supported = getDevice().executeShellCommand("am supports-multiwindow");
        if (DeviceUtils.hasFeature(getDevice(), DeviceUtils.FEATURE_WATCH) ||
                !DeviceUtils.hasFeature(getDevice(), FEATURE_PICTURE_IN_PICTURE) ||
                !supported.contains("true")) {
            LogUtil.CLog.d("Skipping picture in picture atom test.");
            return;
        }

        StatsdConfig.Builder config = ConfigUtils.createConfigBuilder(
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        FieldValueMatcher.Builder uidFvm = ConfigUtils.createUidFvm(/*uidInAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);

        // PictureInPictureStateChanged atom is used prior to rvc-qpr
        ConfigUtils.addEventMetric(config, Atom.PICTURE_IN_PICTURE_STATE_CHANGED_FIELD_NUMBER,
                Collections.singletonList(uidFvm));
        // Picture-in-picture logs' been migrated to UiEvent since rvc-qpr
        FieldValueMatcher.Builder pkgMatcher = ConfigUtils.createFvm(
                UiEventReported.PACKAGE_NAME_FIELD_NUMBER)
                .setEqString(DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetric(config, Atom.UI_EVENT_REPORTED_FIELD_NUMBER,
                Arrays.asList(pkgMatcher));
        ConfigUtils.uploadConfig(getDevice(), config);

        LogUtil.CLog.d("Playing video in Picture-in-Picture mode");
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "VideoPlayerActivity", "action", "action.play_video_picture_in_picture_mode");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Filter out the PictureInPictureStateChanged and UiEventReported atom
        List<EventMetricData> pictureInPictureStateChangedData = data.stream()
                .filter(e -> e.getAtom().hasPictureInPictureStateChanged())
                .collect(Collectors.toList());
        List<EventMetricData> uiEventReportedData = data.stream()
                .filter(e -> e.getAtom().hasUiEventReported())
                .collect(Collectors.toList());

        assertThat(pictureInPictureStateChangedData).isEmpty();
        assertThat(uiEventReportedData).isNotEmpty();

        // See PipUiEventEnum for definitions
        final int enterPipEventId = 603;
        // Assert that log for entering PiP happens exactly once, we do not use
        // assertStateOccurred here since PiP may log something else when activity finishes.
        List<EventMetricData> entered = uiEventReportedData.stream()
                .filter(e -> e.getAtom().getUiEventReported().getEventId() == enterPipEventId)
                .collect(Collectors.toList());
        assertThat(entered).hasSize(1);
    }

    //Note: this test does not have uid, but must run on the device
    public void testScreenBrightness() throws Exception {
        int initialBrightness = getScreenBrightness();
        boolean isInitialManual = isScreenBrightnessModeManual();
        setScreenBrightnessMode(true);
        setScreenBrightness(200);
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        final int atomTag = Atom.SCREEN_BRIGHTNESS_CHANGED_FIELD_NUMBER;

        Set<Integer> screenMin = new HashSet<>(Arrays.asList(47));
        Set<Integer> screen100 = new HashSet<>(Arrays.asList(100));
        Set<Integer> screen200 = new HashSet<>(Arrays.asList(198));
        // Set<Integer> screenMax = new HashSet<>(Arrays.asList(255));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(screenMin, screen100, screen200);

        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testScreenBrightness");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Restore initial screen brightness
        setScreenBrightness(initialBrightness);
        setScreenBrightnessMode(isInitialManual);

        AtomTestUtils.popUntilFind(data, screenMin,
                atom -> atom.getScreenBrightnessChanged().getLevel());
        AtomTestUtils.popUntilFindFromEnd(data, screen200,
                atom -> atom.getScreenBrightnessChanged().getLevel());
        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getScreenBrightnessChanged().getLevel());
    }

    public void testSyncState() throws Exception {
        final int atomTag = Atom.SYNC_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> syncOn = new HashSet<>(Arrays.asList(SyncStateChanged.State.ON_VALUE));
        Set<Integer> syncOff = new HashSet<>(Arrays.asList(SyncStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(syncOn, syncOff, syncOn, syncOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.allowImmediateSyncs(getDevice());
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testSyncState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data,
                /* wait = */ 0 /* don't verify time differences between state changes */,
                atom -> atom.getSyncStateChanged().getState().getNumber());
    }

    public void testVibratorState() throws Exception {
        if (!DeviceUtils.checkDeviceFor(getDevice(), "checkVibratorSupported")) return;

        final int atomTag = Atom.VIBRATOR_STATE_CHANGED_FIELD_NUMBER;
        final String name = "testVibratorState";

        Set<Integer> onState = new HashSet<>(
                Arrays.asList(VibratorStateChanged.State.ON_VALUE));
        Set<Integer> offState = new HashSet<>(
                Arrays.asList(VibratorStateChanged.State.OFF_VALUE));

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(onState, offState);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", name);

        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        AtomTestUtils.assertStatesOccurred(stateSet, data, 300,
                atom -> atom.getVibratorStateChanged().getState().getNumber());
    }

    public void testWakelockState() throws Exception {
        final int atomTag = Atom.WAKELOCK_STATE_CHANGED_FIELD_NUMBER;
        Set<Integer> wakelockOn = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.ACQUIRE_VALUE,
                WakelockStateChanged.State.CHANGE_ACQUIRE_VALUE));
        Set<Integer> wakelockOff = new HashSet<>(Arrays.asList(
                WakelockStateChanged.State.RELEASE_VALUE,
                WakelockStateChanged.State.CHANGE_RELEASE_VALUE));

        final String EXPECTED_TAG = "StatsdPartialWakelock";
        final WakeLockLevelEnum EXPECTED_LEVEL = WakeLockLevelEnum.PARTIAL_WAKE_LOCK;

        // Add state sets to the list in order.
        List<Set<Integer>> stateSet = Arrays.asList(wakelockOn, wakelockOff);

        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWakelockState");

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        // Assert that the events happened in the expected order.
        AtomTestUtils.assertStatesOccurred(stateSet, data, AtomTestUtils.WAIT_TIME_SHORT,
                atom -> atom.getWakelockStateChanged().getState().getNumber());

        for (EventMetricData event : data) {
            String tag = event.getAtom().getWakelockStateChanged().getTag();
            WakeLockLevelEnum type = event.getAtom().getWakelockStateChanged().getType();
            assertThat(tag).isEqualTo(EXPECTED_TAG);
            assertThat(type).isEqualTo(EXPECTED_LEVEL);
        }
    }

    public void testANROccurred() throws Exception {
        final int atomTag = Atom.ANR_OCCURRED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/false);

        try (AutoCloseable a = DeviceUtils.withActivity(getDevice(),
                DeviceUtils.STATSD_ATOM_TEST_PKG, "ANRActivity", null, null)) {
            Thread.sleep(AtomTestUtils.WAIT_TIME_LONG * 2);
            getDevice().executeShellCommand(
                    "am broadcast -a action_anr -p " + DeviceUtils.STATSD_ATOM_TEST_PKG);
            Thread.sleep(20_000);
        }

        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data).hasSize(1);
        assertThat(data.get(0).getAtom().hasAnrOccurred()).isTrue();
        ANROccurred atom = data.get(0).getAtom().getAnrOccurred();
        assertThat(atom.getIsInstantApp().getNumber())
                .isEqualTo(ANROccurred.InstantApp.FALSE_VALUE);
        assertThat(atom.getForegroundState().getNumber())
                .isEqualTo(ANROccurred.ForegroundState.FOREGROUND_VALUE);
        assertThat(atom.getErrorSource()).isEqualTo(ErrorSource.DATA_APP);
        assertThat(atom.getPackageName()).isEqualTo(DeviceUtils.STATSD_ATOM_TEST_PKG);
        // TODO(b/172866626): add tests for incremental packages that ANR'd during loading
        assertFalse(atom.getIsPackageLoading());
    }

    public void testWriteRawTestAtom() throws Exception {
        final int atomTag = Atom.TEST_ATOM_REPORTED_FIELD_NUMBER;
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                atomTag, /*useUidAttributionChain=*/true);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testWriteRawTestAtom");

        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        // Sorted list of events in order in which they occurred.
        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(4);

        TestAtomReported atom = data.get(0).getAtom().getTestAtomReported();
        List<AttributionNode> attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(1234);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEqualTo("tag2");

        assertThat(atom.getIntField()).isEqualTo(42);
        assertThat(atom.getLongField()).isEqualTo(Long.MAX_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(3.14f);
        assertThat(atom.getStringField()).isEqualTo("This is a basic test!");
        assertThat(atom.getBooleanField()).isFalse();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.ON_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();


        atom = data.get(1).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(2);
        assertThat(attrChain.get(0).getUid()).isEqualTo(9999);
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag9999");
        assertThat(attrChain.get(1).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(1).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(100);
        assertThat(atom.getLongField()).isEqualTo(Long.MIN_VALUE);
        assertThat(atom.getFloatField()).isEqualTo(-2.5f);
        assertThat(atom.getStringField()).isEqualTo("Test null uid");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.UNKNOWN_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();

        atom = data.get(2).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEqualTo("tag1");

        assertThat(atom.getIntField()).isEqualTo(-256);
        assertThat(atom.getLongField()).isEqualTo(-1234567890L);
        assertThat(atom.getFloatField()).isEqualTo(42.01f);
        assertThat(atom.getStringField()).isEqualTo("Test non chained");
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList())
                .containsExactly(1L, 2L, 3L).inOrder();

        atom = data.get(3).getAtom().getTestAtomReported();
        attrChain = atom.getAttributionNodeList();
        assertThat(attrChain).hasSize(1);
        assertThat(attrChain.get(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(attrChain.get(0).getTag()).isEmpty();

        assertThat(atom.getIntField()).isEqualTo(0);
        assertThat(atom.getLongField()).isEqualTo(0L);
        assertThat(atom.getFloatField()).isEqualTo(0f);
        assertThat(atom.getStringField()).isEmpty();
        assertThat(atom.getBooleanField()).isTrue();
        assertThat(atom.getState().getNumber()).isEqualTo(TestAtomReported.State.OFF_VALUE);
        assertThat(atom.getBytesField().getExperimentIdList()).isEmpty();
    }
/*
    public void testMobileBytesTransfer() throws Throwable {
        final int appUid = getUid();

        // Verify MobileBytesTransfer, passing a ThrowingPredicate that verifies contents of
        // corresponding atom type to prevent code duplication. The passed predicate returns
        // true if the atom of appUid is found, false otherwise, and throws an exception if
        // contents are not expected.
        doTestMobileBytesTransferThat(Atom.MOBILE_BYTES_TRANSFER_FIELD_NUMBER, (atom) -> {
            final AtomsProto.MobileBytesTransfer data = ((Atom) atom).getMobileBytesTransfer();
            if (data.getUid() == appUid) {
                assertDataUsageAtomDataExpected(data.getRxBytes(), data.getTxBytes(),
                        data.getRxPackets(), data.getTxPackets());
                return true; // found
            }
            return false;
        });
    }
*/
/*
    public void testMobileBytesTransferByFgBg() throws Throwable {
        final int appUid = getUid();

        doTestMobileBytesTransferThat(Atom.MOBILE_BYTES_TRANSFER_BY_FG_BG_FIELD_NUMBER, (atom) -> {
            final AtomsProto.MobileBytesTransferByFgBg data =
                    ((Atom) atom).getMobileBytesTransferByFgBg();
            if (data.getUid() == appUid && data.getIsForeground()) {
                assertDataUsageAtomDataExpected(data.getRxBytes(), data.getTxBytes(),
                        data.getRxPackets(), data.getTxPackets());
                return true; // found
            }
            return false;
        });
    }

    private void assertSubscriptionInfo(AtomsProto.DataUsageBytesTransfer data) {
        assertThat(data.getSimMcc()).matches("^\\d{3}$");
        assertThat(data.getSimMnc()).matches("^\\d{2,3}$");
        assertThat(data.getCarrierId()).isNotEqualTo(-1); // TelephonyManager#UNKNOWN_CARRIER_ID
    }

    private void doTestDataUsageBytesTransferEnabled(boolean enable) throws Throwable {
        // Set value to enable/disable combine subtype.
        setNetworkStatsCombinedSubTypeEnabled(enable);

        doTestMobileBytesTransferThat(Atom.DATA_USAGE_BYTES_TRANSFER_FIELD_NUMBER, (atom) -> {
            final AtomsProto.DataUsageBytesTransfer data =
                    ((Atom) atom).getDataUsageBytesTransfer();
            final boolean ratTypeEqualsToUnknown =
                    (data.getRatType() == NetworkTypeEnum.NETWORK_TYPE_UNKNOWN_VALUE);
            final boolean ratTypeGreaterThanUnknown =
                    (data.getRatType() > NetworkTypeEnum.NETWORK_TYPE_UNKNOWN_VALUE);

            if ((data.getState() == 1) // NetworkStats.SET_FOREGROUND
                    && ((enable && ratTypeEqualsToUnknown)
                    || (!enable && ratTypeGreaterThanUnknown))) {
                assertDataUsageAtomDataExpected(data.getRxBytes(), data.getTxBytes(),
                        data.getRxPackets(), data.getTxPackets());
                // Assert that subscription info is valid.
                assertSubscriptionInfo(data);

                return true; // found
            }
            return false;
        });
    }

    public void testDataUsageBytesTransfer() throws Throwable {
        final boolean oldSubtypeCombined = getNetworkStatsCombinedSubTypeEnabled();

        doTestDataUsageBytesTransferEnabled(true);

        // Remove config from memory and disk to clear the history.
        removeConfig(CONFIG_ID);
        getReportList(); // Clears data.

        doTestDataUsageBytesTransferEnabled(false);

        // Restore to original default value.
        setNetworkStatsCombinedSubTypeEnabled(oldSubtypeCombined);
    }
    // TODO(b/157651730): Determine how to test tag and metered state within atom.
    public void testBytesTransferByTagAndMetered() throws Throwable {
        final int appUid = getUid();
        final int atomId = Atom.BYTES_TRANSFER_BY_TAG_AND_METERED_FIELD_NUMBER;

        doTestMobileBytesTransferThat(atomId, (atom) -> {
            final AtomsProto.BytesTransferByTagAndMetered data =
                    ((Atom) atom).getBytesTransferByTagAndMetered();
            if (data.getUid() == appUid && data.getTag() == 0) { // app traffic generated on tag 0
                assertDataUsageAtomDataExpected(data.getRxBytes(), data.getTxBytes(),
                        data.getRxPackets(), data.getTxPackets());
                return true; // found
            }
            return false;
        });
    }
*/
    public void testPushedBlobStoreStats() throws Exception {
        StatsdConfig.Builder conf = ConfigUtils.createConfigBuilder(
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf,
                Atom.BLOB_COMMITTED_FIELD_NUMBER, /*useUidAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf,
                Atom.BLOB_LEASED_FIELD_NUMBER, /*useUidAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.addEventMetricForUidAtom(conf,
                Atom.BLOB_OPENED_FIELD_NUMBER, /*useUidAttributionChain=*/false,
                DeviceUtils.STATSD_ATOM_TEST_PKG);
        ConfigUtils.uploadConfig(getDevice(), conf);

        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testBlobStore");

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).hasSize(3);

        BlobCommitted blobCommitted = data.get(0).getAtom().getBlobCommitted();
        final long blobId = blobCommitted.getBlobId();
        final long blobSize = blobCommitted.getSize();
        assertThat(blobCommitted.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(blobId).isNotEqualTo(0);
        assertThat(blobSize).isNotEqualTo(0);
        assertThat(blobCommitted.getResult()).isEqualTo(BlobCommitted.Result.SUCCESS);

        BlobLeased blobLeased = data.get(1).getAtom().getBlobLeased();
        assertThat(blobLeased.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(blobLeased.getBlobId()).isEqualTo(blobId);
        assertThat(blobLeased.getSize()).isEqualTo(blobSize);
        assertThat(blobLeased.getResult()).isEqualTo(BlobLeased.Result.SUCCESS);

        BlobOpened blobOpened = data.get(2).getAtom().getBlobOpened();
        assertThat(blobOpened.getUid()).isEqualTo(DeviceUtils.getStatsdTestAppUid(getDevice()));
        assertThat(blobOpened.getBlobId()).isEqualTo(blobId);
        assertThat(blobOpened.getSize()).isEqualTo(blobSize);
        assertThat(blobOpened.getResult()).isEqualTo(BlobOpened.Result.SUCCESS);
    }

    // Constants that match the constants for AtomTests#testBlobStore
    private static final long BLOB_COMMIT_CALLBACK_TIMEOUT_SEC = 5;
    private static final long BLOB_EXPIRY_DURATION_MS = 24 * 60 * 60 * 1000;
    private static final long BLOB_FILE_SIZE_BYTES = 23 * 1024L;
    private static final long BLOB_LEASE_EXPIRY_DURATION_MS = 60 * 60 * 1000;

    public void testPulledBlobStoreStats() throws Exception {
        ConfigUtils.uploadConfigForPulledAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.BLOB_INFO_FIELD_NUMBER);

        final long testStartTimeMs = getDeviceTimeMs();
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);
        DeviceUtils.runDeviceTestsOnStatsdApp(getDevice(), ".AtomTests", "testBlobStore");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
        AtomTestUtils.sendAppBreadcrumbReportedAtom(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_SHORT);

        // Add commit callback time to test end time to account for async execution
        final long testEndTimeMs =
                getDeviceTimeMs() + BLOB_COMMIT_CALLBACK_TIMEOUT_SEC * 1000;

        // Find the BlobInfo for the blob created in the test run
        AtomsProto.BlobInfo blobInfo = null;
        for (Atom atom : ReportUtils.getGaugeMetricAtoms(getDevice())) {
            if (atom.hasBlobInfo()) {
                final AtomsProto.BlobInfo temp = atom.getBlobInfo();
                if (temp.getCommitters().getCommitter(0).getUid()
                        == DeviceUtils.getStatsdTestAppUid(getDevice())) {
                    blobInfo = temp;
                    break;
                }
            }
        }
        assertThat(blobInfo).isNotNull();

        assertThat(blobInfo.getSize()).isEqualTo(BLOB_FILE_SIZE_BYTES);

        // Check that expiry time is reasonable
        assertThat(blobInfo.getExpiryTimestampMillis()).isGreaterThan(
                testStartTimeMs + BLOB_EXPIRY_DURATION_MS);
        assertThat(blobInfo.getExpiryTimestampMillis()).isLessThan(
                testEndTimeMs + BLOB_EXPIRY_DURATION_MS);

        // Check that commit time is reasonable
        final long commitTimeMs = blobInfo.getCommitters().getCommitter(
                0).getCommitTimestampMillis();
        assertThat(commitTimeMs).isGreaterThan(testStartTimeMs);
        assertThat(commitTimeMs).isLessThan(testEndTimeMs);

        // Check that WHITELIST and PRIVATE access mode flags are set
        assertThat(blobInfo.getCommitters().getCommitter(0).getAccessMode()).isEqualTo(0b1001);
        assertThat(blobInfo.getCommitters().getCommitter(0).getNumWhitelistedPackage()).isEqualTo(
                1);

        assertThat(blobInfo.getLeasees().getLeaseeCount()).isGreaterThan(0);
        assertThat(blobInfo.getLeasees().getLeasee(0).getUid()).isEqualTo(
                DeviceUtils.getStatsdTestAppUid(getDevice()));

        // Check that lease expiry time is reasonable
        final long leaseExpiryMs = blobInfo.getLeasees().getLeasee(
                0).getLeaseExpiryTimestampMillis();
        assertThat(leaseExpiryMs).isGreaterThan(testStartTimeMs + BLOB_LEASE_EXPIRY_DURATION_MS);
        assertThat(leaseExpiryMs).isLessThan(testEndTimeMs + BLOB_LEASE_EXPIRY_DURATION_MS);
    }

    private void assertDataUsageAtomDataExpected(long rxb, long txb, long rxp, long txp) {
        assertThat(rxb).isGreaterThan(0L);
        assertThat(txb).isGreaterThan(0L);
        assertThat(rxp).isGreaterThan(0L);
        assertThat(txp).isGreaterThan(0L);
    }

//    private void doTestMobileBytesTransferThat(int atomTag, ThrowingPredicate p)
//            throws Throwable {
//        if (!hasFeature(FEATURE_TELEPHONY, true)) return;
//
//        // Get MobileBytesTransfer as a simple gauge metric.
//        final StatsdConfig.Builder config = getPulledConfig();
//        addGaugeAtomWithDimensions(config, atomTag, null);
//        uploadConfig(config);
//        Thread.sleep(WAIT_TIME_SHORT);
//
//        // Generate some traffic on mobile network.
//        runDeviceTests(DEVICE_SIDE_TEST_PACKAGE, ".AtomTests", "testGenerateMobileTraffic");
//        Thread.sleep(WAIT_TIME_SHORT);
//
//        // Force polling NetworkStatsService to get most updated network stats from lower layer.
//        runActivity("StatsdCtsForegroundActivity", "action", "action.poll_network_stats");
//        Thread.sleep(WAIT_TIME_SHORT);
//
//        // Pull a report
//        setAppBreadcrumbPredicate();
//        Thread.sleep(WAIT_TIME_SHORT);
//
//        final List<Atom> atoms = getGaugeMetricDataList(/*checkTimestampTruncated=*/true);
//        assertThat(atoms.size()).isAtLeast(1);
//
//        boolean foundAppStats = false;
//        for (final Atom atom : atoms) {
//            if (p.accept(atom)) {
//                foundAppStats = true;
//            }
//        }
//        assertWithMessage("uid " + getUid() + " is not found in " + atoms.size() + " atoms")
//                .that(foundAppStats).isTrue();
//    }

    @FunctionalInterface
    private interface ThrowingPredicate<S, T extends Throwable> {
        boolean accept(S s) throws T;
    }

    public void testAppForegroundBackground() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_FOREGROUND_VALUE));
        Set<Integer> offStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_BACKGROUND_VALUE));

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        ConfigUtils.uploadConfigForPushedAtomWithUid(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                Atom.APP_USAGE_EVENT_OCCURRED_FIELD_NUMBER, /*useUidAttributionChain=*/false);

        // Overlay may need to sit there a while.
        final int waitTime = 10_500;
        DeviceUtils.runActivity(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                "StatsdCtsForegroundActivity", "action", ACTION_SHOW_APPLICATION_OVERLAY, waitTime);

        List<EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        Function<Atom, Integer> appUsageStateFunction =
                atom -> atom.getAppUsageEventOccurred().getEventType().getNumber();
        // clear out initial appusage states
        AtomTestUtils.popUntilFind(data, onStates, appUsageStateFunction);
        AtomTestUtils.assertStatesOccurred(stateSet, data, 0, appUsageStateFunction);
    }
/*
    public void testAppForceStopUsageEvent() throws Exception {
        Set<Integer> onStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_FOREGROUND_VALUE));
        Set<Integer> offStates = new HashSet<>(Arrays.asList(
                AppUsageEventOccurred.EventType.MOVE_TO_BACKGROUND_VALUE));

        List<Set<Integer>> stateSet = Arrays.asList(onStates, offStates); // state sets, in order
        createAndUploadConfig(Atom.APP_USAGE_EVENT_OCCURRED_FIELD_NUMBER, false);
        Thread.sleep(WAIT_TIME_FOR_CONFIG_UPDATE_MS);

        getDevice().executeShellCommand(String.format(
                "am start -n '%s' -e %s %s",
                "com.android.server.cts.device.statsd/.StatsdCtsForegroundActivity",
                "action", ACTION_LONG_SLEEP_WHILE_TOP));
        final int waitTime = EXTRA_WAIT_TIME_MS + 5_000;
        Thread.sleep(waitTime);

        getDevice().executeShellCommand(String.format(
                "am force-stop %s",
                "com.android.server.cts.device.statsd/.StatsdCtsForegroundActivity"));
        Thread.sleep(waitTime + STATSD_REPORT_WAIT_TIME_MS);

        List<EventMetricData> data = getEventMetricDataList();
        Function<Atom, Integer> appUsageStateFunction =
                atom -> atom.getAppUsageEventOccurred().getEventType().getNumber();
        popUntilFind(data, onStates, appUsageStateFunction); // clear out initial appusage states.
        assertStatesOccurred(stateSet, data, 0, appUsageStateFunction);
    }
*/

    private int getScreenBrightness() throws Exception {
        return Integer.parseInt(
                getDevice().executeShellCommand("settings get system screen_brightness").trim());
    }

    private boolean isScreenBrightnessModeManual() throws Exception {
        String mode = getDevice().executeShellCommand("settings get system screen_brightness_mode");
        return Integer.parseInt(mode.trim()) == 0;
    }

    private void setScreenBrightnessMode(boolean manual) throws Exception {
        getDevice().executeShellCommand(
                "settings put system screen_brightness_mode " + (manual ? 0 : 1));
    }

    private void setScreenBrightness(int brightness) throws Exception {
        getDevice().executeShellCommand("settings put system screen_brightness " + brightness);
    }

    private long getDeviceTimeMs() throws Exception {
        String timeMs = getDevice().executeShellCommand("date +%s%3N");
        return Long.parseLong(timeMs.trim());
    }
}
