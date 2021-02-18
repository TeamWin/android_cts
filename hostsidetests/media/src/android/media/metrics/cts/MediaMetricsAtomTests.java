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

import android.cts.statsdatom.lib.AtomTestUtils;
import android.cts.statsdatom.lib.ConfigUtils;
import android.cts.statsdatom.lib.DeviceUtils;
import android.cts.statsdatom.lib.ReportUtils;

import com.android.os.AtomsProto;
import com.android.os.StatsLog;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.util.ArrayList;
import java.util.List;

public class MediaMetricsAtomTests extends DeviceTestCase implements IBuildReceiver {
    public static final String TEST_APK = "CtsMediaMetricsHostTestApp.apk";
    public static final String TEST_PKG = "android.media.metrics.cts";
    private IBuildInfo mCtsBuild;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        assertThat(mCtsBuild).isNotNull();
        DeviceUtils.installTestApp(getDevice(), TEST_APK, TEST_PKG, mCtsBuild);
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);
    }

    @Override
    protected void tearDown() throws Exception {
        ConfigUtils.removeConfig(getDevice());
        ReportUtils.clearReports(getDevice());
        DeviceUtils.uninstallTestApp(getDevice(), TEST_PKG);
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    public void testPlaybackStateEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_STATE_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackStateEvent");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackStateChanged()).isTrue();
        AtomsProto.MediaPlaybackStateChanged result =
                data.get(0).getAtom().getMediaPlaybackStateChanged();
        assertThat(result.getPlaybackState().toString()).isEqualTo("JOINING_FOREGROUND");
        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(1763L);
    }

    public void testPlaybackErrorEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_ERROR_REPORTED_FIELD_NUMBER);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackErrorEvent");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackErrorReported()).isTrue();
        AtomsProto.MediaPlaybackErrorReported result =
                data.get(0).getAtom().getMediaPlaybackErrorReported();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(17630000L);
        assertThat(result.getErrorCode().toString()).isEqualTo("ERROR_CODE_RUNTIME");
        assertThat(result.getSubErrorCode()).isEqualTo(378);
        assertThat(result.getExceptionStack().startsWith(
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests.testPlaybackErrorEvent"))
                        .isTrue();
    }

    public void testTrackChangeEvent_text() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_PLAYBACK_TRACK_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testTrackChangeEvent_text");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaPlaybackTrackChanged()).isTrue();
        AtomsProto.MediaPlaybackTrackChanged result =
                data.get(0).getAtom().getMediaPlaybackTrackChanged();

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

    public void testNetworkEvent() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIA_NETWORK_INFO_CHANGED_FIELD_NUMBER);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testNetworkEvent");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediaNetworkInfoChanged()).isTrue();
        AtomsProto.MediaNetworkInfoChanged result =
                data.get(0).getAtom().getMediaNetworkInfoChanged();

        assertThat(result.getTimeSincePlaybackCreatedMillis()).isEqualTo(3032L);
        assertThat(result.getType().toString()).isEqualTo("NETWORK_TYPE_WIFI");
    }

    public void testPlaybackMetrics() throws Exception {
        ConfigUtils.uploadConfigForPushedAtom(getDevice(), TEST_PKG,
                AtomsProto.Atom.MEDIAMETRICS_PLAYBACK_REPORTED_FIELD_NUMBER);
        DeviceUtils.runDeviceTests(
                getDevice(),
                TEST_PKG,
                "android.media.metrics.cts.MediaMetricsAtomHostSideTests",
                "testPlaybackMetrics");
        Thread.sleep(AtomTestUtils.WAIT_TIME_LONG);

        List<StatsLog.EventMetricData> data = ReportUtils.getEventMetricDataList(getDevice());
        int appUid = DeviceUtils.getAppUid(getDevice(), TEST_PKG);

        assertThat(data.size()).isEqualTo(1);
        assertThat(data.get(0).getAtom().hasMediametricsPlaybackReported()).isTrue();
        AtomsProto.MediametricsPlaybackReported result =
                data.get(0).getAtom().getMediametricsPlaybackReported();

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
    }
}
