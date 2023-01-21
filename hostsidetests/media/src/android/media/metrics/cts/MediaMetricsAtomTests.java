/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.media.metrics.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.metrics.proto.MetricMeasurement;
import com.android.tradefed.result.CollectingTestListener;
import com.android.tradefed.result.TestDescription;
import com.android.tradefed.result.TestResult;
import com.android.tradefed.result.TestRunResult;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import com.google.common.truth.Correspondence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@RunWith(DeviceJUnit4ClassRunner.class)
public class MediaMetricsAtomTests extends BaseHostJUnit4Test {

    private static final String TEST_RUNNER = "androidx.test.runner.AndroidJUnitRunner";
    private static final String TAG = "MediaMetricsAtomTests";
    public static final String TEST_APK = "CtsMediaMetricsHostTestApp.apk";
    public static final String TEST_PKG = "android.media.metrics.cts";
    private static final String FEATURE_AUDIO_OUTPUT = "android.hardware.audio.output";
    private static final String FEATURE_MICROPHONE = "android.hardware.microphone";
    private static final String FEATURE_MIDI = "android.software.midi";
    private static final int MAX_BUFFER_CAPACITY = 30 * 1024 * 1024; // 30M

    @BeforeClassWithInfo
    public static void installApp(TestInformation testInfo)
            throws DeviceNotAvailableException, FileNotFoundException {
        assertThat(testInfo.getBuildInfo()).isNotNull();
        DeviceUtils.installTestApp(testInfo.getDevice(), TEST_APK, TEST_PKG,
                testInfo.getBuildInfo());
    }

    @Before
    public void setUp() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @After
    public void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
    }

    @AfterClassWithInfo
    public static void uninstallApp(TestInformation testInfo) throws Exception {
        DeviceUtils.uninstallTestApp(testInfo.getDevice(), TEST_PKG);
    }

    @Test
    public void testPlaybackStateEvent_default() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackStateEvent_default", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackStateChanged()).isTrue();
        AtomsProto.MediaPlaybackStateChanged result = data.get(
                0).getAtom().getMediaPlaybackStateChanged();
        assertThat(result.getPlaybackState().toString()).isEqualTo("NOT_STARTED");
        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(-1L);
    }

    @Test
    public void testPlaybackStateEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackStateEvent", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackStateChanged()).isTrue();
        AtomsProto.MediaPlaybackStateChanged result = data.get(
                0).getAtom().getMediaPlaybackStateChanged();
        assertThat(result.getPlaybackState().toString()).isEqualTo("JOINING_FOREGROUND");
        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(1763L);
    }

    // same as testPlaybackStateEvent, but we use the BundleSession transport.
    @Test
    public void testBundleSessionPlaybackStateEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testBundleSessionPlaybackStateEvent", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackStateChanged()).isTrue();
        AtomsProto.MediaPlaybackStateChanged result = data.get(
                0).getAtom().getMediaPlaybackStateChanged();
        assertThat(result.getPlaybackState().toString()).isEqualTo("JOINING_FOREGROUND");
        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(1763L);
    }

    @Test
    public void testPlaybackErrorEvent_default() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_ERROR_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackErrorEvent_default", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackErrorReported()).isTrue();
        AtomsProto.MediaPlaybackErrorReported result = data.get(
                0).getAtom().getMediaPlaybackErrorReported();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(-1L);
        assertThat(result.getErrorCode().toString()).isEqualTo("ERROR_CODE_UNKNOWN");
        assertThat(result.getSubErrorCode()).isEqualTo(0);
        assertThat(result.getExceptionStack().startsWith(
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests"
                        + ".testPlaybackErrorEvent")).isTrue();
    }

    @Test
    public void testPlaybackErrorEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_ERROR_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackErrorEvent", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackErrorReported()).isTrue();
        AtomsProto.MediaPlaybackErrorReported result = data.get(
                0).getAtom().getMediaPlaybackErrorReported();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(17630000L);
        assertThat(result.getErrorCode().toString()).isEqualTo("ERROR_CODE_RUNTIME");
        assertThat(result.getSubErrorCode()).isEqualTo(378);
        assertThat(result.getExceptionStack().startsWith(
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests"
                        + ".testPlaybackErrorEvent")).isTrue();
    }

    @Test
    public void testTrackChangeEvent_default() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_TRACK_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTrackChangeEvent_default", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackTrackChanged()).isTrue();
        AtomsProto.MediaPlaybackTrackChanged result = data.get(
                0).getAtom().getMediaPlaybackTrackChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(-1L);
        assertThat(result.getState().toString()).isEqualTo("OFF");
        assertThat(result.getReason().toString()).isEqualTo("REASON_UNKNOWN");
        assertThat(result.getContainerMimeType()).isEqualTo("");
        assertThat(result.getSampleMimeType()).isEqualTo("");
        assertThat(result.getCodecName()).isEqualTo("");
        assertThat(result.getBitrate()).isEqualTo(-1);
        assertThat(result.getType().toString()).isEqualTo("AUDIO");
        assertThat(result.getLanguage()).isEqualTo("");
        assertThat(result.getLanguageRegion()).isEqualTo("");
        assertThat(result.getSampleRate()).isEqualTo(-1);
        assertThat(result.getChannelCount()).isEqualTo(-1);
    }

    @Test
    public void testTrackChangeEvent_text() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_TRACK_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTrackChangeEvent_text", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackTrackChanged()).isTrue();
        AtomsProto.MediaPlaybackTrackChanged result = data.get(
                0).getAtom().getMediaPlaybackTrackChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(37278L);
        assertThat(result.getState().toString()).isEqualTo("ON");
        assertThat(result.getReason().toString()).isEqualTo("REASON_MANUAL");
        assertThat(result.getContainerMimeType()).isEqualTo("text/foo");
        assertThat(result.getSampleMimeType()).isEqualTo("text/plain");
        assertThat(result.getCodecName()).isEqualTo("codec_1");
        assertThat(result.getBitrate()).isEqualTo(1024);
        assertThat(result.getType().toString()).isEqualTo("TEXT");
        assertThat(result.getLanguage()).isEqualTo("EN");
        assertThat(result.getLanguageRegion()).isEqualTo("US");
    }

    @Test
    public void testTrackChangeEvent_audio() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_TRACK_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTrackChangeEvent_audio", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackTrackChanged()).isTrue();
        AtomsProto.MediaPlaybackTrackChanged result = data.get(
                0).getAtom().getMediaPlaybackTrackChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(37278L);
        assertThat(result.getState().toString()).isEqualTo("OFF");
        assertThat(result.getReason().toString()).isEqualTo("REASON_INITIAL");
        assertThat(result.getContainerMimeType()).isEqualTo("audio/foo");
        assertThat(result.getSampleMimeType()).isEqualTo("audio/avc");
        assertThat(result.getCodecName()).isEqualTo("codec_2");
        assertThat(result.getBitrate()).isEqualTo(1025);
        assertThat(result.getType().toString()).isEqualTo("AUDIO");
        assertThat(result.getLanguage()).isEqualTo("EN");
        assertThat(result.getLanguageRegion()).isEqualTo("US");
        assertThat(result.getSampleRate()).isEqualTo(89);
        assertThat(result.getChannelCount()).isEqualTo(3);
    }

    @Test
    public void testTrackChangeEvent_video() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_TRACK_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTrackChangeEvent_video", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackTrackChanged()).isTrue();
        AtomsProto.MediaPlaybackTrackChanged result = data.get(
                0).getAtom().getMediaPlaybackTrackChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(37278L);
        assertThat(result.getState().toString()).isEqualTo("OFF");
        assertThat(result.getReason().toString()).isEqualTo("REASON_INITIAL");
        assertThat(result.getContainerMimeType()).isEqualTo("video/foo");
        assertThat(result.getSampleMimeType()).isEqualTo("video/mpeg");
        assertThat(result.getCodecName()).isEqualTo("codec_3");
        assertThat(result.getBitrate()).isEqualTo(1025);
        assertThat(result.getType().toString()).isEqualTo("VIDEO");
        assertThat(result.getLanguage()).isEqualTo("EN");
        assertThat(result.getLanguageRegion()).isEqualTo("US");
        assertThat(result.getHeight()).isEqualTo(1080);
        assertThat(result.getWidth()).isEqualTo(1440);
        assertThat(result.getVideoFrameRate()).isEqualTo(60);
    }

    @Test
    public void testNetworkEvent_default() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_NETWORK_INFO_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testNetworkEvent_default", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaNetworkInfoChanged()).isTrue();
        AtomsProto.MediaNetworkInfoChanged result = data.get(
                0).getAtom().getMediaNetworkInfoChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(-1L);
        assertThat(result.getType().toString()).isEqualTo("NETWORK_TYPE_UNKNOWN");
    }

    @Test
    public void testNetworkEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_NETWORK_INFO_CHANGED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testNetworkEvent",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaNetworkInfoChanged()).isTrue();
        AtomsProto.MediaNetworkInfoChanged result = data.get(
                0).getAtom().getMediaNetworkInfoChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(3032L);
        assertThat(result.getType().toString()).isEqualTo("NETWORK_TYPE_WIFI");
    }

    @Test
    public void testPlaybackMetrics_default() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackMetrics_default", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        int appUid = DeviceUtils.getAppUid(getDevice(), TEST_PKG);

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediametricsPlaybackReported()).isTrue();
        AtomsProto.MediametricsPlaybackReported result = data.get(
                0).getAtom().getMediametricsPlaybackReported();

        assertThat(result.getUid()).isEqualTo(appUid);
        assertThat(result.getMediaDurationMillis()).isEqualTo(-1L);
        assertThat(result.getStreamSource().toString()).isEqualTo("STREAM_SOURCE_UNKNOWN");
        assertThat(result.getStreamType().toString()).isEqualTo("STREAM_TYPE_UNKNOWN");
        assertThat(result.getPlaybackType().toString()).isEqualTo("PLAYBACK_TYPE_UNKNOWN");
        assertThat(result.getDrmType().toString()).isEqualTo("DRM_TYPE_NONE");
        assertThat(result.getContentType().toString()).isEqualTo("CONTENT_TYPE_UNKNOWN");
        assertThat(result.getPlayerName()).isEqualTo("");
        assertThat(result.getPlayerVersion()).isEqualTo("");
        assertThat(result.getVideoFramesPlayed()).isEqualTo(-1);
        assertThat(result.getVideoFramesDropped()).isEqualTo(-1);
        assertThat(result.getAudioUnderrunCount()).isEqualTo(-1);
        assertThat(result.getNetworkBytesRead()).isEqualTo(-1);
        assertThat(result.getLocalBytesRead()).isEqualTo(-1);
        assertThat(result.getNetworkTransferDurationMillis()).isEqualTo(-1);
        assertThat(result.getExperimentIds().getExperimentsList().size()).isEqualTo(0);
        assertThat(result.getDrmSessionId().length()).isEqualTo(0);
    }

    @Test
    public void testPlaybackMetrics() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testPlaybackMetrics",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        int appUid = DeviceUtils.getAppUid(getDevice(), TEST_PKG);

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediametricsPlaybackReported()).isTrue();
        AtomsProto.MediametricsPlaybackReported result = data.get(
                0).getAtom().getMediametricsPlaybackReported();

        assertThat(result.getUid()).isEqualTo(appUid);
        assertThat(result.getMediaDurationMillis()).isEqualTo(233L);
        assertThat(result.getStreamSource().toString()).isEqualTo("STREAM_SOURCE_NETWORK");
        assertThat(result.getStreamType().toString()).isEqualTo("STREAM_TYPE_OTHER");
        assertThat(result.getPlaybackType().toString()).isEqualTo("PLAYBACK_TYPE_LIVE");
        assertThat(result.getDrmType().toString()).isEqualTo("DRM_TYPE_WV_L1");
        assertThat(result.getContentType().toString()).isEqualTo("CONTENT_TYPE_MAIN");
        assertThat(result.getPlayerName()).isEqualTo("ExoPlayer");
        assertThat(result.getPlayerVersion()).isEqualTo("1.01x");
        assertThat(result.getVideoFramesPlayed()).isEqualTo(1024);
        assertThat(result.getVideoFramesDropped()).isEqualTo(32);
        assertThat(result.getAudioUnderrunCount()).isEqualTo(22);
        assertThat(result.getNetworkBytesRead()).isEqualTo(102400);
        assertThat(result.getLocalBytesRead()).isEqualTo(2000);
        assertThat(result.getNetworkTransferDurationMillis()).isEqualTo(6000);
        // TODO: fix missing experiment ID impl
        assertThat(result.getExperimentIds()).isNotEqualTo(null);
        // TODO: needs Base64 decoders to verify the data
        assertThat(result.getDrmSessionId()).isNotEqualTo(null);
    }

    @Test
    public void testSessionId() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testSessionId",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isEmpty();
    }

    @Test
    public void testRecordingSession() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testRecordingSession",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isEmpty();
    }

    @Test
    public void testEditingSession() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testEditingSession",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isEmpty();
    }

    @Test
    public void testTranscodingSession() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTranscodingSession", new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isEmpty();
    }

    @Test
    public void testBundleSession() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testBundleSession",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertThat(data).isEmpty();
    }

    @Test
    public void testAppBlocklist() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        LogSessionIdListener listener = new LogSessionIdListener();
        runDeviceTests(getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testAppBlocklist",
                listener);
        String logSessionId = listener.getLogSessionId();
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        assertWithMessage("log session id").that(logSessionId).isNotEmpty();
        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.MediametricsPlaybackReported> playbackReportedList = toMyAtoms(data,
                AtomsProto.Atom::getMediametricsPlaybackReported);
        assertThat(playbackReportedList).comparingElementsUsing(Correspondence.transforming(
                AtomsProto.MediametricsPlaybackReported::getLogSessionId,
                "getLogSessionId")).doesNotContain(logSessionId);
    }

    @Test
    public void testAttributionBlocklist() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        LogSessionIdListener listener = new LogSessionIdListener();
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testAttributionBlocklist", listener);
        String logSessionId = listener.getLogSessionId();
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("log session id").that(logSessionId).isNotEmpty();
        List<AtomsProto.MediametricsPlaybackReported> playbackReportedList = toMyAtoms(data,
                AtomsProto.Atom::getMediametricsPlaybackReported);
        assertThat(playbackReportedList).comparingElementsUsing(Correspondence.transforming(
                AtomsProto.MediametricsPlaybackReported::getLogSessionId,
                "getLogSessionId")).contains(logSessionId);

        AtomsProto.MediametricsPlaybackReported result = playbackReportedList.stream().filter(
                a -> a.getLogSessionId().equals(logSessionId)).findFirst().orElseThrow();

        assertThat(result.getUid()).isEqualTo(0); // UID is not logged. Should be 0.
        assertThat(result.getMediaDurationMillis()).isEqualTo(233L);
        assertThat(result.getStreamSource().toString()).isEqualTo("STREAM_SOURCE_NETWORK");
        assertThat(result.getStreamType().toString()).isEqualTo("STREAM_TYPE_OTHER");
        assertThat(result.getPlaybackType().toString()).isEqualTo("PLAYBACK_TYPE_LIVE");
        assertThat(result.getDrmType().toString()).isEqualTo("DRM_TYPE_WV_L1");
        assertThat(result.getContentType().toString()).isEqualTo("CONTENT_TYPE_MAIN");
        assertThat(result.getPlayerName()).isEqualTo("ExoPlayer");
        assertThat(result.getPlayerVersion()).isEqualTo("1.01x");
        assertThat(result.getVideoFramesPlayed()).isEqualTo(1024);
        assertThat(result.getVideoFramesDropped()).isEqualTo(32);
        assertThat(result.getAudioUnderrunCount()).isEqualTo(22);
        assertThat(result.getNetworkBytesRead()).isEqualTo(102400);
        assertThat(result.getLocalBytesRead()).isEqualTo(2000);
        assertThat(result.getNetworkTransferDurationMillis()).isEqualTo(6000);
    }

    @Test
    public void testAppAllowlist() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        LogSessionIdListener listener = new LogSessionIdListener();
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testAppAllowlist", listener);
        String logSessionId = listener.getLogSessionId();
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("log session id").that(logSessionId).isNotEmpty();
        List<AtomsProto.MediaPlaybackStateChanged> stateChangedList = toMyAtoms(data,
                AtomsProto.Atom::getMediaPlaybackStateChanged);
        assertThat(stateChangedList).comparingElementsUsing(
                Correspondence.transforming(AtomsProto.MediaPlaybackStateChanged::getLogSessionId,
                        "getLogSessionId")).contains(logSessionId);

        AtomsProto.MediaPlaybackStateChanged result = stateChangedList.stream().filter(
                a -> a.getLogSessionId().equals(logSessionId)).findFirst().orElseThrow();
        assertThat(result.getPlaybackState().toString()).isEqualTo("JOINING_FOREGROUND");
        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(1763L);
    }

    @Test
    public void testAttributionAllowlist() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        LogSessionIdListener listener = new LogSessionIdListener();
        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testAttributionAllowlist", listener);
        String logSessionId = listener.getLogSessionId();
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        assertWithMessage("log session id").that(logSessionId).isNotEmpty();
        List<AtomsProto.MediametricsPlaybackReported> playbackReportedList = toMyAtoms(data,
                AtomsProto.Atom::getMediametricsPlaybackReported);
        assertThat(playbackReportedList).comparingElementsUsing(Correspondence.transforming(
                AtomsProto.MediametricsPlaybackReported::getLogSessionId,
                "getLogSessionId")).contains(logSessionId);

        AtomsProto.MediametricsPlaybackReported result = playbackReportedList.stream().filter(
                a -> a.getLogSessionId().equals(logSessionId)).findFirst().orElseThrow();

        assertThat(result.getUid()).isEqualTo(0); // UID is not logged. Should be 0.
        assertThat(result.getMediaDurationMillis()).isEqualTo(233L);
        assertThat(result.getStreamSource().toString()).isEqualTo("STREAM_SOURCE_NETWORK");
        assertThat(result.getStreamType().toString()).isEqualTo("STREAM_TYPE_OTHER");
        assertThat(result.getPlaybackType().toString()).isEqualTo("PLAYBACK_TYPE_LIVE");
        assertThat(result.getDrmType().toString()).isEqualTo("DRM_TYPE_WV_L1");
        assertThat(result.getContentType().toString()).isEqualTo("CONTENT_TYPE_MAIN");
        assertThat(result.getPlayerName()).isEqualTo("ExoPlayer");
        assertThat(result.getPlayerVersion()).isEqualTo("1.01x");
        assertThat(result.getVideoFramesPlayed()).isEqualTo(1024);
        assertThat(result.getVideoFramesDropped()).isEqualTo(32);
        assertThat(result.getAudioUnderrunCount()).isEqualTo(22);
        assertThat(result.getNetworkBytesRead()).isEqualTo(102400);
        assertThat(result.getLocalBytesRead()).isEqualTo(2000);
        assertThat(result.getNetworkTransferDurationMillis()).isEqualTo(6000);
    }

    private void validateAAudioStreamAtom(int direction) throws Exception {
        Set<Integer> directionSet = new HashSet<>(Arrays.asList(direction));
        List<Set<Integer>> directionList = Arrays.asList(directionSet);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        AtomTestUtils.assertStatesOccurredInOrder(directionList, data, 0,
                atom -> atom.getMediametricsAaudiostreamReported().getDirection().getNumber());

        for (StatsLog.EventMetricData event : data) {
            AtomsProto.MediametricsAAudioStreamReported atom =
                    event.getAtom().getMediametricsAaudiostreamReported();
            assertThat(atom.getBufferCapacity()).isGreaterThan(0);
            assertThat(atom.getBufferCapacity()).isLessThan(MAX_BUFFER_CAPACITY);
            assertThat(atom.getBufferSize()).isGreaterThan(0);
            assertThat(atom.getBufferSize()).isAtMost(atom.getBufferCapacity());
            assertThat(atom.getFramesPerBurst()).isGreaterThan(0);
            assertThat(atom.getFramesPerBurst()).isLessThan(atom.getBufferCapacity());
        }
    }

    private void runAAudioTestAndValidate(String requiredFeature, int direction,
            String testFunctionName) throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), requiredFeature)) {
            return;
        }
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_AAUDIOSTREAM_REPORTED_FIELD_NUMBER);

        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", testFunctionName,
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        validateAAudioStreamAtom(direction);
    }

    /**
     * The test try to create and then close aaudio input stream with low latency via media metrics
     * atom host side test app on the DUT.
     * After that, the event metric data for MediametricsAAudioStreamReported is pushed to verify
     * the data is collected correctly.
     */
    @Test
    public void testAAudioLowLatencyInputStream() throws Exception {
        runAAudioTestAndValidate(FEATURE_MICROPHONE,
                AtomsProto.MediametricsAAudioStreamReported.Direction.DIRECTION_INPUT_VALUE,
                "testAAudioLowLatencyInputStream");
    }

    /**
     * The test try to create and then close aaudio output stream with low latency via media metrics
     * atom host side test app on the DUT.
     * After that, the event metric data for MediametricsAAudioStreamReported is pushed to verify
     * the data is collected correctly.
     */
    @Test
    public void testAAudioLowLatencyOutputStream() throws Exception {
        runAAudioTestAndValidate(FEATURE_AUDIO_OUTPUT,
                AtomsProto.MediametricsAAudioStreamReported.Direction.DIRECTION_OUTPUT_VALUE,
                "testAAudioLowLatencyOutputStream");
    }

    /**
     * The test try to create and then close aaudio input stream with legacy path via media metrics
     * atom host side test app on the DUT.
     * After that, the event metric data for MediametricsAAudioStreamReported is pushed to verify
     * the data is collected correctly.
     */
    @Test
    public void testAAudioLegacyInputStream() throws Exception {
        runAAudioTestAndValidate(FEATURE_MICROPHONE,
                AtomsProto.MediametricsAAudioStreamReported.Direction.DIRECTION_INPUT_VALUE,
                "testAAudioLegacyInputStream");
    }

    /**
     * The test try to create and then close aaudio output stream with legacy path via media metrics
     * atom host side test app on the DUT.
     * After that, the event metric data for MediametricsAAudioStreamReported is pushed to verify
     * the data is collected correctly.
     */
    @Test
    public void testAAudioLegacyOutputStream() throws Exception {
        runAAudioTestAndValidate(FEATURE_AUDIO_OUTPUT,
                AtomsProto.MediametricsAAudioStreamReported.Direction.DIRECTION_OUTPUT_VALUE,
                "testAAudioLegacyOutputStream");
    }

    /**
     * The test try to create and then close a midi stream via media metrics
     * atom host side test app on the DUT.
     * After that, the event metric data for MediametricsMidiDeviceCloseReported is pushed to verify
     * the data is collected correctly.
     */
    @Test
    public void testMidiMetrics() throws Exception {
        if (!DeviceUtils.hasFeature(getDevice(), FEATURE_MIDI)) {
            return;
        }
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), DeviceUtils.STATSD_ATOM_TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_MIDI_DEVICE_CLOSE_REPORTED_FIELD_NUMBER);

        runDeviceTests(getDevice(), TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests", "testMidiMetrics",
                new LogSessionIdListener());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        List<AtomsProto.MediametricsMidiDeviceCloseReported> eventList = toMyAtoms(data,
                AtomsProto.Atom::getMediametricsMidiDeviceCloseReported);

        assertThat(eventList).isNotEmpty();

        int appUid = DeviceUtils.getAppUid(getDevice(), TEST_PKG);
        AtomsProto.MediametricsMidiDeviceCloseReported result = eventList.get(0);

        assertThat(result.getUid()).isEqualTo(appUid);
        assertThat(result.getMidiDeviceId()).isGreaterThan(0);
        assertThat(result.getInputPortCount()).isEqualTo(1);
        assertThat(result.getOutputPortCount()).isEqualTo(1);
        assertThat(result.getDeviceType().toString()).isEqualTo("MIDI_DEVICE_INFO_TYPE_VIRTUAL");
        assertThat(result.getIsShared()).isTrue();
        assertThat(result.getSupportsUmp()).isFalse();
        assertThat(result.getUsingAlsa()).isFalse();
        assertThat(result.getDurationNs()).isGreaterThan(0);
        assertThat(result.getOpenedCount()).isGreaterThan(0);
        assertThat(result.getClosedCount()).isGreaterThan(0);
        assertThat(result.getDeviceDisconnected()).isFalse();
        assertThat(result.getTotalInputBytes()).isGreaterThan(0);
        assertThat(result.getTotalOutputBytes()).isGreaterThan(0);
    }

    private static <T> List<T> toMyAtoms(List<StatsLog.EventMetricData> data,
            Function<AtomsProto.Atom, T> mapper) {
        return data.stream().map(StatsLog.EventMetricData::getAtom).map(mapper).collect(
                Collectors.toUnmodifiableList());
    }

    // TODO(b/265208340): update DeviceUtils to accept listeners.

    /**
     * Runs device side tests.
     *
     * @param device         Can be retrieved by running getDevice() in a class that extends
     *                       DeviceTestCase
     * @param pkgName        Test package name, such as "com.android.server.cts.statsdatom"
     * @param testClassName  Test class name which can either be a fully qualified name or "." + a
     *                       class name; if null, all test in the package will be run
     * @param testMethodName Test method name; if null, all tests in class or package will be run
     * @return {@link TestRunResult} of this invocation
     */
    @Nonnull
    private static TestRunResult runDeviceTests(ITestDevice device, String pkgName,
            @Nullable String testClassName, @Nullable String testMethodName,
            LogSessionIdListener listener)
            throws DeviceNotAvailableException {
        if (testClassName != null && testClassName.startsWith(".")) {
            testClassName = pkgName + testClassName;
        }

        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(
                pkgName, TEST_RUNNER, device.getIDevice());
        if (testClassName != null && testMethodName != null) {
            testRunner.setMethodName(testClassName, testMethodName);
        } else if (testClassName != null) {
            testRunner.setClassName(testClassName);
        }

        assertThat(device.runInstrumentationTests(testRunner, listener)).isTrue();

        final TestRunResult result = listener.getCurrentRunResults();
        if (result.isRunFailure()) {
            throw new Error("Failed to successfully run device tests for "
                    + result.getName() + ": " + result.getRunFailureMessage());
        }
        if (result.getNumTests() == 0) {
            throw new Error("No tests were run on the device");
        }
        if (result.hasFailedTests()) {
            StringBuilder errorBuilder = new StringBuilder("On-device tests failed:\n");
            for (Map.Entry<TestDescription, TestResult> resultEntry :
                    result.getTestResults().entrySet()) {
                if (!resultEntry.getValue().getStatus().equals(
                        com.android.ddmlib.testrunner.TestResult.TestStatus.PASSED)) {
                    errorBuilder.append(resultEntry.getKey().toString());
                    errorBuilder.append(":\n");
                    errorBuilder.append(resultEntry.getValue().getStackTrace());
                }
            }
            throw new AssertionError(errorBuilder.toString());
        }

        return result;
    }

    private static final class LogSessionIdListener extends CollectingTestListener {

        @Nullable
        private String mLogSessionId;

        @Nullable
        public String getLogSessionId() {
            return mLogSessionId;
        }

        @Override
        public void testEnded(TestDescription test, long endTime,
                HashMap<String, MetricMeasurement.Metric> testMetrics) {
            super.testEnded(test, endTime, testMetrics);
            LogUtil.CLog.i("testEnded  MetricMeasurement.Metric " + testMetrics);
            // TODO(b/265311058): use a common constant for metrics keys.
            MetricMeasurement.Metric metric = testMetrics.get("log_session_id");
            mLogSessionId = metric == null ? null : metric.getMeasurements().getSingleString();
        }
    }
}
