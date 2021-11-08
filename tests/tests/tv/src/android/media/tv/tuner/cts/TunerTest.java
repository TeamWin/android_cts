/*
 * Copyright 2019 The Android Open Source Project
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

package android.media.tv.tuner.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.tv.tuner.DemuxCapabilities;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.Lnb;
import android.media.tv.tuner.LnbCallback;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.TunerVersionChecker;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrRecorder;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.dvr.OnRecordStatusChangedListener;
import android.media.tv.tuner.filter.AlpFilterConfiguration;
import android.media.tv.tuner.filter.AudioDescriptor;
import android.media.tv.tuner.filter.AvSettings;
import android.media.tv.tuner.filter.DownloadEvent;
import android.media.tv.tuner.filter.DownloadSettings;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterConfiguration;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.IpCidChangeEvent;
import android.media.tv.tuner.filter.IpFilterConfiguration;
import android.media.tv.tuner.filter.IpPayloadEvent;
import android.media.tv.tuner.filter.MediaEvent;
import android.media.tv.tuner.filter.MmtpFilterConfiguration;
import android.media.tv.tuner.filter.MmtpRecordEvent;
import android.media.tv.tuner.filter.PesEvent;
import android.media.tv.tuner.filter.PesSettings;
import android.media.tv.tuner.filter.RecordSettings;
import android.media.tv.tuner.filter.RestartEvent;
import android.media.tv.tuner.filter.ScramblingStatusEvent;
import android.media.tv.tuner.filter.SectionEvent;
import android.media.tv.tuner.filter.SectionSettingsWithSectionBits;
import android.media.tv.tuner.filter.SectionSettingsWithTableInfo;
import android.media.tv.tuner.filter.Settings;
import android.media.tv.tuner.filter.SharedFilter;
import android.media.tv.tuner.filter.SharedFilterCallback;
import android.media.tv.tuner.filter.TemiEvent;
import android.media.tv.tuner.filter.TimeFilter;
import android.media.tv.tuner.filter.TlvFilterConfiguration;
import android.media.tv.tuner.filter.TsFilterConfiguration;
import android.media.tv.tuner.filter.TsRecordEvent;
import android.media.tv.tuner.frontend.AnalogFrontendCapabilities;
import android.media.tv.tuner.frontend.AnalogFrontendSettings;
import android.media.tv.tuner.frontend.Atsc3FrontendCapabilities;
import android.media.tv.tuner.frontend.Atsc3FrontendSettings;
import android.media.tv.tuner.frontend.Atsc3PlpInfo;
import android.media.tv.tuner.frontend.AtscFrontendCapabilities;
import android.media.tv.tuner.frontend.AtscFrontendSettings;
import android.media.tv.tuner.frontend.DtmbFrontendCapabilities;
import android.media.tv.tuner.frontend.DtmbFrontendSettings;
import android.media.tv.tuner.frontend.DvbcFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbcFrontendSettings;
import android.media.tv.tuner.frontend.DvbsFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbsFrontendSettings;
import android.media.tv.tuner.frontend.DvbtFrontendCapabilities;
import android.media.tv.tuner.frontend.DvbtFrontendSettings;
import android.media.tv.tuner.frontend.FrontendCapabilities;
import android.media.tv.tuner.frontend.FrontendInfo;
import android.media.tv.tuner.frontend.FrontendSettings;
import android.media.tv.tuner.frontend.FrontendStatus;
import android.media.tv.tuner.frontend.FrontendStatus.Atsc3PlpTuningInfo;
import android.media.tv.tuner.frontend.Isdbs3FrontendCapabilities;
import android.media.tv.tuner.frontend.Isdbs3FrontendSettings;
import android.media.tv.tuner.frontend.IsdbsFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbsFrontendSettings;
import android.media.tv.tuner.frontend.IsdbtFrontendCapabilities;
import android.media.tv.tuner.frontend.IsdbtFrontendSettings;
import android.media.tv.tuner.frontend.OnTuneEventListener;
import android.media.tv.tuner.frontend.ScanCallback;
import android.media.tv.tunerresourcemanager.TunerFrontendInfo;
import android.media.tv.tunerresourcemanager.TunerFrontendRequest;
import android.media.tv.tunerresourcemanager.TunerResourceManager;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.compatibility.common.util.RequiredFeatureRule;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerTest {
    private static final String TAG = "MediaTunerTest";

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_TUNER);

    private static final int TIMEOUT_MS = 10 * 1000;  // 10 seconds
    private static final int SCAN_TIMEOUT_MS = 2 * 60 * 1000; // 2 minutes
    private static final long TIMEOUT_BINDER_SERVICE_SEC = 2;

    private Context mContext;
    private Tuner mTuner;
    private CountDownLatch mLockLatch = new CountDownLatch(1);
    private TunerResourceManager mTunerResourceManager = null;
    private TestServiceConnection mConnection;
    private ISharedFilterTestServer mSharedFilterTestServer;

    private class TestServiceConnection implements ServiceConnection {
        private BlockingQueue<IBinder> mBlockingQueue = new LinkedBlockingQueue<>();

        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBlockingQueue.offer(service);
        }

        public void onServiceDisconnected(ComponentName componentName) {}

        public IBinder getService() throws Exception {
            final IBinder service =
                    mBlockingQueue.poll(TIMEOUT_BINDER_SERVICE_SEC, TimeUnit.SECONDS);
            return service;
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
        mTuner = new Tuner(mContext, null, 100);

        mConnection = new TestServiceConnection();
        mContext.bindService(new Intent(mContext, SharedFilterTestService.class), mConnection,
                Context.BIND_AUTO_CREATE);
        mSharedFilterTestServer =
                ISharedFilterTestServer.Stub.asInterface(mConnection.getService());
    }

    @After
    public void tearDown() {
        if (mTuner != null) {
          mTuner.close();
          mTuner = null;
        }
        mContext.unbindService(mConnection);
    }

    @Test
    public void testTunerConstructor() throws Exception {
        assertNotNull(mTuner);
    }

    @Test
    public void testTunerVersion() {
        assertNotNull(mTuner);
        int version = TunerVersionChecker.getTunerVersion();
        assertTrue(version >= TunerVersionChecker.TUNER_VERSION_1_0);
        assertTrue(version <= TunerVersionChecker.TUNER_VERSION_2_0);
    }

    @Test
    public void testTuning() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.setLnaEnabled(false);
        assertTrue((res == Tuner.RESULT_SUCCESS) || (res == Tuner.RESULT_UNAVAILABLE));
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testMultiTuning() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // Tune again with the same frontend.
        mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        for (int i = 1; i < ids.size(); i++) {
            FrontendInfo info2 = mTuner.getFrontendInfoById(ids.get(i));
            if (info2.getType() != info.getType()) {
                res = mTuner.tune(createFrontendSettings(info2));
                assertEquals(Tuner.RESULT_INVALID_STATE, res);
            }
        }
    }

    @Test
    public void testScanning() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());
        for (int id : ids) {
            FrontendInfo info = mTuner.getFrontendInfoById(id);
            if (info != null) {
                mLockLatch = new CountDownLatch(1);
                int res = mTuner.scan(
                        createFrontendSettings(info),
                        Tuner.SCAN_TYPE_AUTO,
                        getExecutor(),
                        getScanCallback());
               assertEquals(Tuner.RESULT_SUCCESS, res);
               assertTrue(mLockLatch.await(SCAN_TIMEOUT_MS, TimeUnit.MILLISECONDS));
               res = mTuner.cancelScanning();
               assertEquals(Tuner.RESULT_SUCCESS, res);
            }
        }
        mLockLatch = null;
    }

    @Test
    public void testFrontendStatus() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        for (int id : ids) {
            Tuner tuner = new Tuner(mContext, null, 100);
            FrontendInfo info = tuner.getFrontendInfoById(id);
            int res = tuner.tune(createFrontendSettings(info));

            int[] statusCapabilities = info.getStatusCapabilities();
            assertNotNull(statusCapabilities);
            FrontendStatus status = tuner.getFrontendStatus(statusCapabilities);
            assertNotNull(status);

            for (int i = 0; i < statusCapabilities.length; i++) {
                switch (statusCapabilities[i]) {
                    case FrontendStatus.FRONTEND_STATUS_TYPE_DEMOD_LOCK:
                        status.isDemodLocked();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SNR:
                        status.getSnr();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BER:
                        status.getBer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PER:
                        status.getPer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PRE_BER:
                        status.getPerBer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_QUALITY:
                        status.getSignalQuality();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SIGNAL_STRENGTH:
                        status.getSignalStrength();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SYMBOL_RATE:
                        status.getSymbolRate();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_FEC:
                        status.getInnerFec();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MODULATION:
                        status.getModulation();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_SPECTRAL:
                        status.getSpectralInversion();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LNB_VOLTAGE:
                        status.getLnbVoltage();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_PLP_ID:
                        status.getPlpId();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_EWBS:
                        status.isEwbs();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_AGC:
                        status.getAgc();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LNA:
                        status.isLnaOn();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_LAYER_ERROR:
                        status.getLayerErrors();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MER:
                        status.getMer();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_FREQ_OFFSET:
                        status.getFreqOffsetLong();
                        status.getFreqOffset();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_HIERARCHY:
                        status.getHierarchy();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_RF_LOCK:
                        status.isRfLocked();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ATSC3_PLP_INFO:
                        Atsc3PlpTuningInfo[] tuningInfos = status.getAtsc3PlpTuningInfo();
                        if (tuningInfos != null) {
                            for (Atsc3PlpTuningInfo tuningInfo : tuningInfos) {
                                tuningInfo.getPlpId();
                                tuningInfo.isLocked();
                                tuningInfo.getUec();
                            }
                        }
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BERS:
                        status.getBers();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_CODERATES:
                        status.getCodeRates();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_BANDWIDTH:
                        status.getBandwidth();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_GUARD_INTERVAL:
                        status.getGuardInterval();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_TRANSMISSION_MODE:
                        status.getTransmissionMode();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_UEC:
                        status.getUec();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_T2_SYSTEM_ID:
                        status.getSystemId();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_INTERLEAVINGS:
                        status.getInterleaving();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_SEGMENTS:
                        status.getIsdbtSegment();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_TS_DATA_RATES:
                        status.getTsDataRate();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_MODULATIONS_EXT:
                        status.getExtendedModulations();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ROLL_OFF:
                        status.getRollOff();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_MISO_ENABLED:
                        status.isMisoEnabled();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_LINEAR:
                        status.isLinear();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_IS_SHORT_FRAMES_ENABLED:
                        status.isShortFramesEnabled();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_MODE:
                        status.getIsdbtMode();
                        break;
                    case FrontendStatus.FRONTEND_STATUS_TYPE_ISDBT_PARTIAL_RECEPTION_FLAG:
                        status.getIsdbtPartialReceptionFlag();
                        break;
                }
            }
            tuner.close();
            tuner = null;
        }
    }

    @Test
    public void testLnb() throws Exception {
        Lnb lnb = mTuner.openLnb(getExecutor(), getLnbCallback());
        if (lnb == null) return;
        assertEquals(lnb.setVoltage(Lnb.VOLTAGE_5V), Tuner.RESULT_SUCCESS);
        assertEquals(lnb.setTone(Lnb.TONE_NONE), Tuner.RESULT_SUCCESS);
        assertEquals(
                lnb.setSatellitePosition(Lnb.POSITION_A), Tuner.RESULT_SUCCESS);
        lnb.sendDiseqcMessage(new byte[] {1, 2});
        lnb.close();
    }

    @Test
    public void testOpenLnbByname() throws Exception {
        Lnb lnb = mTuner.openLnbByName("default", getExecutor(), getLnbCallback());
        if (lnb != null) {
            lnb.close();
        }
    }

    @Test
    public void testCiCam() throws Exception {
    // open filter to get demux resource
        mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());

        mTuner.connectCiCam(1);
        mTuner.disconnectCiCam();
    }

    @Test
    public void testFrontendToCiCam() throws Exception {
        // tune to get frontend resource
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            // TODO: get real CiCam id from MediaCas
            res = mTuner.connectFrontendToCiCam(0);
        } else {
            assertEquals(Tuner.INVALID_LTS_ID, mTuner.connectFrontendToCiCam(0));
        }

        if (res != Tuner.INVALID_LTS_ID) {
            assertEquals(mTuner.disconnectFrontendToCiCam(0), Tuner.RESULT_SUCCESS);
        } else {
            // Make sure the connectFrontendToCiCam only fails because the current device
            // does not support connecting frontend to cicam
            assertEquals(mTuner.disconnectFrontendToCiCam(0), Tuner.RESULT_UNAVAILABLE);
        }
    }

    @Test
    public void testAvSyncId() throws Exception {
    // open filter to get demux resource
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());
        Settings settings = AvSettings
                .builder(Filter.TYPE_TS, true)
                .setPassthrough(false)
                .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        int id = mTuner.getAvSyncHwId(f);
        if (id != Tuner.INVALID_AV_SYNC_ID) {
            assertNotEquals(Tuner.INVALID_TIMESTAMP, mTuner.getAvSyncTime(id));
        }
    }

    @Test
    public void testReadFilter() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());
        if (TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertNotEquals(Tuner.INVALID_FILTER_ID_LONG, f.getIdLong());
        } else {
            assertEquals(Tuner.INVALID_FILTER_ID_LONG, f.getIdLong());
        }

        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        f.setMonitorEventMask(
                Filter.MONITOR_EVENT_SCRAMBLING_STATUS | Filter.MONITOR_EVENT_IP_CID_CHANGE);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.flush();
        f.read(new byte[3], 0, 3);
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testAudioFilterStreamTypeConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        Settings settings = AvSettings
                .builder(Filter.TYPE_TS, true)
                .setPassthrough(false)
                .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.flush();
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testTimeFilter() throws Exception {
        if (!mTuner.getDemuxCapabilities().isTimeFilterSupported()) return;
        TimeFilter f = mTuner.openTimeFilter();
        assertNotNull(f);
        f.setCurrentTimestamp(0);
        assertNotEquals(Tuner.INVALID_TIMESTAMP, f.getTimeStamp());
        assertNotEquals(Tuner.INVALID_TIMESTAMP, f.getSourceTime());
        f.clearTimestamp();
        f.close();
    }

    @Test
    public void testIpFilter() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_IP, Filter.SUBTYPE_IP, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        FilterConfiguration config = IpFilterConfiguration
                .builder()
                .setSrcIpAddress(new byte[] {(byte) 0xC0, (byte) 0xA8, 0, 1})
                .setDstIpAddress(new byte[] {(byte) 0xC0, (byte) 0xA8, 3, 4})
                .setSrcPort(33)
                .setDstPort(23)
                .setPassthrough(false)
                .setSettings(null)
                .setIpFilterContextId(1)
                .build();
        f.configure(config);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.start();
        f.stop();
        f.close();

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testAlpSectionFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_ALP, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        SectionSettingsWithSectionBits settings =
                SectionSettingsWithSectionBits
                        .builder(Filter.TYPE_TS)
                        .setCrcEnabled(true)
                        .setRepeat(false)
                        .setRaw(false)
                        .setFilter(new byte[]{2, 3, 4})
                        .setMask(new byte[]{7, 6, 5, 4})
                        .setMode(new byte[]{22, 55, 33})
                        .build();
        AlpFilterConfiguration config =
                AlpFilterConfiguration
                        .builder()
                        .setPacketType(AlpFilterConfiguration.PACKET_TYPE_COMPRESSED)
                        .setLengthType(AlpFilterConfiguration.LENGTH_TYPE_WITH_ADDITIONAL_HEADER)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testMmtpPesFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_MMTP, Filter.SUBTYPE_PES, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        PesSettings settings =
                PesSettings
                        .builder(Filter.TYPE_TS)
                        .setStreamId(3)
                        .setRaw(false)
                        .build();
        MmtpFilterConfiguration config =
                MmtpFilterConfiguration
                        .builder()
                        .setMmtpPacketId(3)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testMmtpDownloadFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_MMTP, Filter.SUBTYPE_DOWNLOAD,
                1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        DownloadSettings settings =
                DownloadSettings
                        .builder(Filter.TYPE_MMTP)
                        .setDownloadId(2)
                        .build();
        MmtpFilterConfiguration config =
                MmtpFilterConfiguration
                        .builder()
                        .setMmtpPacketId(3)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTsAvFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_AUDIO, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        AvSettings settings =
                AvSettings
                        .builder(Filter.TYPE_TS, true) // is Audio
                        .setPassthrough(false)
                        .setAudioStreamType(AvSettings.AUDIO_STREAM_TYPE_MPEG1)
                        .build();
        TsFilterConfiguration config =
                TsFilterConfiguration
                        .builder()
                        .setTpid(521)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTsRecordFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_RECORD, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        RecordSettings settings =
                RecordSettings
                        .builder(Filter.TYPE_TS)
                        .setTsIndexMask(
                                RecordSettings.TS_INDEX_FIRST_PACKET
                                        | RecordSettings.TS_INDEX_PRIVATE_DATA)
                        .setScIndexType(RecordSettings.INDEX_TYPE_SC)
                        .setScIndexMask(RecordSettings.SC_INDEX_B_SLICE)
                        .build();
        TsFilterConfiguration config =
                TsFilterConfiguration
                        .builder()
                        .setTpid(521)
                        .setSettings(settings)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testTlvTlvFilterConfig() throws Exception {
        Filter f = mTuner.openFilter(
                Filter.TYPE_TLV, Filter.SUBTYPE_TLV, 1000, getExecutor(), getFilterCallback());
        if (f == null) return;
        assertNotEquals(Tuner.INVALID_FILTER_ID, f.getId());

        TlvFilterConfiguration config =
                TlvFilterConfiguration
                        .builder()
                        .setPacketType(TlvFilterConfiguration.PACKET_TYPE_IPV4)
                        .setCompressedIpPacket(true)
                        .setPassthrough(false)
                        .setSettings(null)
                        .build();
        f.configure(config);
        f.start();
        f.stop();
        f.close();
    }

    @Test
    public void testDescrambler() throws Exception {
        Descrambler d = mTuner.openDescrambler();
        byte[] keyToken = new byte[] {1, 3, 2};
        assertNotNull(d);
        Filter f = mTuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertTrue(d.isValidKeyToken(keyToken));
        d.setKeyToken(keyToken);
        d.addPid(Descrambler.PID_TYPE_T, 1, f);
        d.removePid(Descrambler.PID_TYPE_T, 1, f);
        f.close();
        d.close();
    }

    @Test
    public void testDescramblerKeyTokenValidator() throws Exception {
        byte[] invalidToken = new byte[17];
        byte[] validToken = new byte[] {1, 3, 2};
        assertTrue(Descrambler.isValidKeyToken(validToken));
        assertTrue(Descrambler.isValidKeyToken(Tuner.VOID_KEYTOKEN));
        assertFalse(Descrambler.isValidKeyToken(invalidToken));
    }

    @Test
    public void testOpenDvrRecorder() throws Exception {
        DvrRecorder d = mTuner.openDvrRecorder(100, getExecutor(), getRecordListener());
        assertNotNull(d);
        d.close();
    }

    @Test
    public void testOpenDvPlayback() throws Exception {
        DvrPlayback d = mTuner.openDvrPlayback(100, getExecutor(), getPlaybackListener());
        assertNotNull(d);
        d.close();
    }

    @Test
    public void testDemuxCapabilities() throws Exception {
        DemuxCapabilities d = mTuner.getDemuxCapabilities();
        assertNotNull(d);

        d.getDemuxCount();
        d.getRecordCount();
        d.getPlaybackCount();
        d.getTsFilterCount();
        d.getSectionFilterCount();
        d.getAudioFilterCount();
        d.getVideoFilterCount();
        d.getPesFilterCount();
        d.getPcrFilterCount();
        d.getSectionFilterLength();
        d.getFilterCapabilities();
        d.getLinkCapabilities();
        d.isTimeFilterSupported();
    }

    @Test
    public void testResourceLostListener() throws Exception {
        mTuner.setResourceLostListener(getExecutor(), new Tuner.OnResourceLostListener() {
            @Override
            public void onResourceLost(Tuner tuner) {
            }
        });
        mTuner.clearResourceLostListener();
    }

    @Test
    public void testOnTuneEventListener() throws Exception {
        mTuner.setOnTuneEventListener(getExecutor(), new OnTuneEventListener() {
            @Override
            public void onTuneEvent(int tuneEvent) {
            }
        });
        mTuner.clearOnTuneEventListener();
    }

    @Test
    public void testUpdateResourcePriority() throws Exception {
        mTuner.updateResourcePriority(100, 20);
    }

    @Test
    public void testResourceReclaimed() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // first tune with mTuner to acquire resource
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now tune with a higher priority tuner to have mTuner's resource reclaimed
        Tuner higherPrioTuner = new Tuner(mContext, null, 200);
        res = higherPrioTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(higherPrioTuner.getFrontendInfo());

        higherPrioTuner.close();
    }

    @Test
    public void testResourceReclaimedDifferentThread() throws Exception {
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);

        // first tune with mTuner to acquire resource
        int res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());

        // now tune with a higher priority tuner to have mTuner's resource reclaimed
        TunerHandler tunerHandler = createTunerHandler(null);
        Message msgCreate = new Message();
        msgCreate.what = MSG_TUNER_HANDLER_CREATE;
        msgCreate.arg1 = 200;
        tunerHandler.sendMessage(msgCreate);
        mTunerHandlerTaskComplete.block();
        mTunerHandlerTaskComplete.close();

        Message msgTune = new Message();
        msgTune.what = MSG_TUNER_HANDLER_TUNE;
        msgTune.obj = (Object) feSettings;
        tunerHandler.sendMessage(msgTune);

        mTunerHandlerTaskComplete.block();
        mTunerHandlerTaskComplete.close();
        res = tunerHandler.getResult();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        Tuner higherPrioTuner = tunerHandler.getTuner();
        assertNotNull(higherPrioTuner.getFrontendInfo());

        Message msgClose = new Message();
        msgClose.what = MSG_TUNER_HANDLER_CLOSE;
        tunerHandler.sendMessage(msgClose);

        // call mTuner.close in parallel
        mTuner.close();
        mTuner = null;
    }

    @Test
    public void testShareFrontendFromTuner() throws Exception {
        Tuner tuner100 = new Tuner(mContext, null, 100);
        List<Integer> ids = tuner100.getFrontendIds();
        assertFalse(ids.isEmpty());
        FrontendInfo info = tuner100.getFrontendInfoById(ids.get(0));
        FrontendSettings feSettings = createFrontendSettings(info);
        int[] statusTypes = {1};
        boolean exceptionThrown = false;
        int res;

        // CASE1: check resource reclaim while sharee's priority < owner's priority
        // let tuner100 share from tuner200
        Tuner tuner200 = new Tuner(mContext, null, 200);
        res = tuner200.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner100 = new Tuner(mContext, null, 100);
        tuner100.shareFrontendFromTuner(tuner200);
        // call openFilter to trigger ITunerDemux.setFrontendDataSourceById()
        Filter f = tuner100.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);

        // now try to steal the resource from a higher priority
        Tuner tuner300 = new Tuner(mContext, null, 300);
        res = tuner300.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        // confirm owner & sharee's resource gets reclaimed by confirming an exception is thrown
        exceptionThrown = false;
        try {
            tuner200.getFrontendStatus(statusTypes);
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        exceptionThrown = false;
        try {
            tuner100.getFrontendStatus(statusTypes);
        } catch (Exception e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);

        tuner100.close();
        tuner200.close();
        tuner300.close();


        // CASE2: check resource reclaim fail when sharee's priority > new requester
        tuner100 = new Tuner(mContext, null, 100);
        res = tuner100.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner300 = new Tuner(mContext, null, 300);
        tuner300.shareFrontendFromTuner(tuner100);
        f = tuner100.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);

        tuner200 = new Tuner(mContext, null, 200);
        res = tuner200.tune(feSettings);
        assertNotEquals(Tuner.RESULT_SUCCESS, res);

        // confirm the original tuner is still intact
        res = tuner100.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);

        tuner100.close();
        tuner200.close();
        tuner300.close();
    }

    @Test
    public void testClose() throws Exception {
        Tuner other = new Tuner(mContext, null, 100);

        List<Integer> ids = other.getFrontendIds();
        if (ids == null) return;
        assertFalse(ids.isEmpty());
        FrontendInfo info = other.getFrontendInfoById(ids.get(0));

        FrontendSettings feSettings = createFrontendSettings(info);
        int res = other.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(other.getFrontendInfo());

        other.close();

        // make sure pre-existing tuner is still functional
        res = mTuner.tune(feSettings);
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertNotNull(mTuner.getFrontendInfo());
    }

    @Test
    public void testHasUnusedFrontend1() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
            infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // check before anyone holds resource
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_UNDEFINED));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_ATSC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            // check when one of the two exclusive groups are held
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertTrue(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            // let C hold the resource
            assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                             true /* expectedResult */, 5 /* expectedHandle */);

            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertFalse(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(C.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            // let go of B's resource
            B.close();

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            assertTrue(B.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(C.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));

            C.close();
            A.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testHasUnusedFrontend2() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[5];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBT, 2);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBC, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBS, 3);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let C hold the resource
            assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                             true /* expectedResult */, 4 /* expectedHandle */);

            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertFalse(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            B.close();
            C.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testHasUnusedFrontend3() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
            // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
            infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
            infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
            infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
            infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
            infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
            infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

            mTunerResourceManager.setFrontendInfoList(infos);

            Tuner A = new Tuner(mContext, null, 100);
            Tuner B = new Tuner(mContext, null, 100);
            Tuner C = new Tuner(mContext, null, 100);

            // let B hold resource
            assignFeResource(B.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            // let C share from B
            mTunerResourceManager.shareFrontend(C.getClientId(), B.getClientId());

            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBT));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBC));
            assertTrue(A.hasUnusedFrontend(FrontendSettings.TYPE_DVBS));

            A.close();
            C.close();
            B.close();
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityCornerCases() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupSingleTunerSetupForIsLowestPriority();

            // must return true when non existing frontend type is specified
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_UNDEFINED));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_ATSC));

            // must return true when no one is holding the resource
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));

            // must return true when the callee is the only one holding the resource
            assignFeResource(mTuner.getClientId(), FrontendSettings.TYPE_DVBT,
                             true /* expectedResult */, 1 /* expectedHandle */);
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(mTuner.isLowestPriority(FrontendSettings.TYPE_DVBT));

        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityTwoClients() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupSingleTunerSetupForIsLowestPriority();
            testTwoClientsForIsLowestPriority(200, 100); // A > B
            testTwoClientsForIsLowestPriority(100, 200); // A < B
            testTwoClientsForIsLowestPriority(100, 100); // A = B

            setupDualTunerSetupForIsLowestPriority();
            testTwoClientsForIsLowestPriority(200, 100); // A > B
            testTwoClientsForIsLowestPriority(100, 200); // A < B
            testTwoClientsForIsLowestPriority(100, 100); // A = B
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    @Test
    public void testIsLowestPriorityThreeClients() throws Exception {
        prepTRMCustomFeResourceMapTest();

        // Use try block to ensure restoring the TunerResourceManager
        // Note: the handles will be changed from the original value, but should be OK
        try {
            setupDualTunerSetupForIsLowestPriority();
            testThreeClientsForIsLowestPriority(300, 200, 100); // A > B > C
            testThreeClientsForIsLowestPriority(300, 100, 200); // A > C > B
            testThreeClientsForIsLowestPriority(200, 300, 100); // B > A > C
            testThreeClientsForIsLowestPriority(200, 100, 300); // C > A > B
            testThreeClientsForIsLowestPriority(100, 300, 200); // B > C > A
            testThreeClientsForIsLowestPriority(100, 200, 300); // C > B > A
            testThreeClientsForIsLowestPriority(100, 100, 100); // A = B = C
            testThreeClientsForIsLowestPriority(200, 200, 100); // A = B > C
            testThreeClientsForIsLowestPriority(200, 100, 100); // A > B = C
            testThreeClientsForIsLowestPriority(200, 100, 200); // A = C > B
            testThreeClientsForIsLowestPriority(200, 300, 200); // B > A = C
            testThreeClientsForIsLowestPriority(100, 100, 200); // C > A = B
            testThreeClientsForIsLowestPriority(100, 200, 200); // B = C > A
        } catch (Exception e) {
            throw (e);
        } finally {
            cleanupTRMCustomFeResourceMapTest();
        }
    }

    private TunerFrontendInfo tunerFrontendInfo(
            int handle, int frontendType, int exclusiveGroupId) {
        TunerFrontendInfo info = new TunerFrontendInfo();
        info.handle = handle;
        info.type = frontendType;
        info.exclusiveGroupId = exclusiveGroupId;
        return info;
    }

    /**
     * Prep function for TunerTest that requires custom frontend resource map
     */
    private void prepTRMCustomFeResourceMapTest() {
        if (mTunerResourceManager == null) {
            mTunerResourceManager = (TunerResourceManager)
                    mContext.getSystemService(Context.TV_TUNER_RESOURCE_MGR_SERVICE);
        }
        mTunerResourceManager.storeResourceMap(TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        mTunerResourceManager.clearResourceMap(TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
    }

    /**
     * Clean up function for TunerTest that requires custom frontend resource map
     */
    private void cleanupTRMCustomFeResourceMapTest() {
        // first close mTuner in case a frontend resource is opened
        if (mTuner != null) {
            mTuner.close();
            mTuner = null;
        }

        // now restore the original frontend resource map
        if (mTunerResourceManager != null) {
            mTunerResourceManager.restoreResourceMap(
                    TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        }
    }

    private void clearFrontendInfoList() {
        if (mTunerResourceManager != null) {
            mTunerResourceManager.clearResourceMap(
                    TunerResourceManager.TUNER_RESOURCE_TYPE_FRONTEND);
        }
    }

    private void assignFeResource(int clientId, int frontendType,
                                  boolean expectedResult, int expectedHandle) {
        int[] feHandle = new int[1];
        TunerFrontendRequest request = new TunerFrontendRequest();
        request.clientId = clientId;
        request.frontendType = frontendType;
        boolean granted = mTunerResourceManager.requestFrontend(request, feHandle);
        assertEquals(granted, expectedResult);
        assertEquals(feHandle[0], expectedHandle);
    }

    private void setupSingleTunerSetupForIsLowestPriority() {
        // first clear the frontend resource to register new set of resources
        clearFrontendInfoList();

        TunerFrontendInfo[] infos = new TunerFrontendInfo[3];
        // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
        infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
        infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
        infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);

        mTunerResourceManager.setFrontendInfoList(infos);
    }

    private void setupDualTunerSetupForIsLowestPriority() {
        // first clear the frontend resource to register new set of resources
        clearFrontendInfoList();

        TunerFrontendInfo[] infos = new TunerFrontendInfo[6];
        // tunerFrontendInfo(handle, FrontendSettings.TYPE_*, exclusiveGroupId
        infos[0] = tunerFrontendInfo(1, FrontendSettings.TYPE_DVBT, 1);
        infos[1] = tunerFrontendInfo(2, FrontendSettings.TYPE_DVBC, 1);
        infos[2] = tunerFrontendInfo(3, FrontendSettings.TYPE_DVBS, 1);
        infos[3] = tunerFrontendInfo(4, FrontendSettings.TYPE_DVBT, 2);
        infos[4] = tunerFrontendInfo(5, FrontendSettings.TYPE_DVBC, 2);
        infos[5] = tunerFrontendInfo(6, FrontendSettings.TYPE_DVBS, 2);

        mTunerResourceManager.setFrontendInfoList(infos);
    }


    private void testTwoClientsForIsLowestPriority(int prioA, int prioB) {

        Tuner A = new Tuner(mContext, null, prioA);
        Tuner B = new Tuner(mContext, null, prioB);

        // all should return true
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));

        // let A hold resource
        assignFeResource(A.getClientId(), FrontendSettings.TYPE_DVBT,
                         true /* expectedResult */, 1 /* expectedHandle */);

        // should return true for A as A is the sole holder
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        // should return false for B only if A < B
        if ( prioA < prioB ) {
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        } else {
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        }

        A.close();
        B.close();
    }

    private void testThreeClientsForIsLowestPriority(int prioA, int prioB, int prioC) {

        Tuner A = new Tuner(mContext, null, prioA);
        Tuner B = new Tuner(mContext, null, prioB);
        Tuner C = new Tuner(mContext, null, prioC);

        // all should return true
        assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBT));
        assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBC));
        assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBS));

        // let A & C hold resource
        assignFeResource(A.getClientId(), FrontendSettings.TYPE_DVBT,
                         true /* expectedResult */, 1 /* expectedHandle */);

        assignFeResource(C.getClientId(), FrontendSettings.TYPE_DVBC,
                         true /* expectedResult */, 5 /* expectedHandle */);

        // should return false for B only if A < B
        if (prioA > prioB && prioB > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioC && prioC > prioB) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioC && prioC > prioB) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioA && prioA > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioA && prioA > prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioC && prioC > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioB && prioB > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioB && prioB == prioC) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioB && prioB > prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA > prioB && prioB == prioC) {
            assertFalse(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioA == prioC && prioC > prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB > prioA && prioA == prioC) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertTrue(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioC > prioA && prioA == prioB) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertTrue(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        } else if (prioB == prioC && prioC > prioA) {
            assertTrue(A.isLowestPriority(FrontendSettings.TYPE_DVBC));
            assertFalse(B.isLowestPriority(FrontendSettings.TYPE_DVBS));
            assertFalse(C.isLowestPriority(FrontendSettings.TYPE_DVBT));
        }

        A.close();
        B.close();
        C.close();
    }

    @Test
    public void testSharedFilterOneProcess() throws Exception {
        Filter f = createFilterForSharedFilterTest(mTuner, getExecutor(), getFilterCallback());
        assertTrue(f != null);

        String token1 = f.createSharedFilter();
        assertTrue(token1 != null);

        String token2 = f.createSharedFilter();
        assertTrue(token2 == null);

        // Tune a frontend before start the filter
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);

        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();

        assertEquals(f.configure(config), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.setMonitorEventMask(Filter.MONITOR_EVENT_SCRAMBLING_STATUS),
                Tuner.RESULT_INVALID_STATE);
        assertEquals(f.setDataSource(null), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.start(), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.flush(), Tuner.RESULT_INVALID_STATE);
        assertEquals(f.read(new byte[3], 0, 3), 0);
        assertEquals(f.stop(), Tuner.RESULT_INVALID_STATE);

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.releaseSharedFilter(token1);
        f.close();
        f = null;
    }

    @Test
    public void testSharedFilterTwoProcessesCloseInSharedFilter() throws Exception {
        String token = mSharedFilterTestServer.createSharedFilter();
        assertTrue(token != null);
        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        assertTrue(f.read(new byte[3], 0, 3) != 0);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        f.close();
        f = null;
        mSharedFilterTestServer.closeFilter();
        Thread.sleep(2000);
        assertEquals(mLockLatch.getCount(), 1);
        mLockLatch = null;
    }

    @Test
    public void testSharedFilterTwoProcessesCloseInFilter() throws Exception {
        String token = mSharedFilterTestServer.createSharedFilter();
        assertTrue(token != null);

        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        assertTrue(f.read(new byte[3], 0, 3) != 0);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        mSharedFilterTestServer.closeFilter();
        assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mLockLatch = null;
        f.close();
        f = null;
    }

    @Test
    public void testSharedFilterTwoProcessesReleaseInFilter() throws Exception {
        String token = mSharedFilterTestServer.createSharedFilter();
        assertTrue(token != null);

        SharedFilter f =
                Tuner.openSharedFilter(mContext, token, getExecutor(), getSharedFilterCallback());
        assertTrue(f != null);

        assertEquals(f.start(), Tuner.RESULT_SUCCESS);
        assertEquals(f.flush(), Tuner.RESULT_SUCCESS);
        assertTrue(f.read(new byte[3], 0, 3) != 0);
        assertEquals(f.stop(), Tuner.RESULT_SUCCESS);

        mLockLatch = new CountDownLatch(1);
        mSharedFilterTestServer.releaseSharedFilter(token);
        assertTrue(mLockLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        mLockLatch = null;

        mSharedFilterTestServer.closeFilter();
        f.close();
        f = null;
    }

    @Test
    public void testSharedFilterTwoProcessesVerifySharedFilter() throws Exception {
        Filter f = createFilterForSharedFilterTest(mTuner, getExecutor(), getFilterCallback());
        assertTrue(f != null);

        String token = f.createSharedFilter();
        assertTrue(token != null);

        // Tune a frontend before start the shared filter
        List<Integer> ids = mTuner.getFrontendIds();
        assertFalse(ids.isEmpty());

        FrontendInfo info = mTuner.getFrontendInfoById(ids.get(0));
        int res = mTuner.tune(createFrontendSettings(info));
        assertEquals(Tuner.RESULT_SUCCESS, res);
        assertTrue(mSharedFilterTestServer.verifySharedFilter(token));

        res = mTuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);

        f.releaseSharedFilter(token);
        f.close();
        f = null;
    }

    static public Filter createFilterForSharedFilterTest(
            Tuner tuner, Executor e, FilterCallback cb) {
        Filter f = tuner.openFilter(Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, e, cb);
        Settings settings = SectionSettingsWithTableInfo
                .builder(Filter.TYPE_TS)
                .setTableId(2)
                .setVersion(1)
                .setCrcEnabled(true)
                .setRaw(false)
                .setRepeat(false)
                .build();
        FilterConfiguration config = TsFilterConfiguration
                .builder()
                .setTpid(10)
                .setSettings(settings)
                .build();
        f.configure(config);
        f.setMonitorEventMask(
                Filter.MONITOR_EVENT_SCRAMBLING_STATUS | Filter.MONITOR_EVENT_IP_CID_CHANGE);

        return f;
    }

    private boolean hasTuner() {
        return mContext.getPackageManager().hasSystemFeature("android.hardware.tv.tuner");
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private LnbCallback getLnbCallback() {
        return new LnbCallback() {
            @Override
            public void onEvent(int lnbEventType) {}
            @Override
            public void onDiseqcMessage(byte[] diseqcMessage) {}
        };
    }

    private FilterCallback getFilterCallback() {
        return new FilterCallback() {
            @Override
            public void onFilterEvent(Filter filter, FilterEvent[] events) {
                for (FilterEvent e : events) {
                    if (e instanceof DownloadEvent) {
                        testDownloadEvent(filter, (DownloadEvent) e);
                    } else if (e instanceof IpPayloadEvent) {
                        testIpPayloadEvent(filter, (IpPayloadEvent) e);
                    } else if (e instanceof MediaEvent) {
                        testMediaEvent(filter, (MediaEvent) e);
                    } else if (e instanceof MmtpRecordEvent) {
                        testMmtpRecordEvent(filter, (MmtpRecordEvent) e);
                    } else if (e instanceof PesEvent) {
                        testPesEvent(filter, (PesEvent) e);
                    } else if (e instanceof SectionEvent) {
                        testSectionEvent(filter, (SectionEvent) e);
                    } else if (e instanceof TemiEvent) {
                        testTemiEvent(filter, (TemiEvent) e);
                    } else if (e instanceof TsRecordEvent) {
                        testTsRecordEvent(filter, (TsRecordEvent) e);
                    } else if (e instanceof ScramblingStatusEvent) {
                        testScramblingStatusEvent(filter, (ScramblingStatusEvent) e);
                    } else if (e instanceof IpCidChangeEvent) {
                        testIpCidChangeEvent(filter, (IpCidChangeEvent) e);
                    } else if (e instanceof RestartEvent) {
                        testRestartEvent(filter, (RestartEvent) e);
                    }
                }
            }
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
    }

    private SharedFilterCallback getSharedFilterCallback() {
        return new SharedFilterCallback() {
            @Override
            public void onFilterEvent(SharedFilter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(SharedFilter filter, int status) {
                if (status == SharedFilter.STATUS_INACCESSIBLE) {
                    if (mLockLatch != null) {
                        mLockLatch.countDown();
                    }
                }
            }
        };
    }

    private void testDownloadEvent(Filter filter, DownloadEvent e) {
        e.getItemId();
        e.getMpuSequenceNumber();
        e.getItemFragmentIndex();
        e.getLastItemFragmentIndex();
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testIpPayloadEvent(Filter filter, IpPayloadEvent e) {
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testMediaEvent(Filter filter, MediaEvent e) {
        e.getStreamId();
        e.isPtsPresent();
        e.getPts();
        e.getDataLength();
        e.getOffset();
        e.getLinearBlock();
        e.isSecureMemory();
        e.getAvDataId();
        e.getAudioHandle();
        e.getMpuSequenceNumber();
        e.isPrivateData();
        AudioDescriptor ad = e.getExtraMetaData();
        if (ad != null) {
            ad.getAdFade();
            ad.getAdPan();
            ad.getAdVersionTextTag();
            ad.getAdGainCenter();
            ad.getAdGainFront();
            ad.getAdGainSurround();
        }
        e.release();
    }

    private void testMmtpRecordEvent(Filter filter, MmtpRecordEvent e) {
        e.getScHevcIndexMask();
        e.getDataLength();
        int mpuSequenceNumber = e.getMpuSequenceNumber();
        long pts = e.getPts();
        int firstMbInSlice = e.getFirstMacroblockInSlice();
        int tsIndexMask = e.getTsIndexMask();
        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(mpuSequenceNumber, Tuner.INVALID_MMTP_RECORD_EVENT_MPT_SEQUENCE_NUM);
            assertEquals(pts, Tuner.INVALID_TIMESTAMP);
            assertEquals(firstMbInSlice, Tuner.INVALID_FIRST_MACROBLOCK_IN_SLICE);
            assertEquals(tsIndexMask, 0);
        }
    }

    private void testPesEvent(Filter filter, PesEvent e) {
        e.getStreamId();
        e.getMpuSequenceNumber();
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testSectionEvent(Filter filter, SectionEvent e) {
        e.getTableId();
        e.getVersion();
        e.getSectionNumber();
        long length = e.getDataLength();
        if (length > 0) {
            byte[] buffer = new byte[(int) length];
            assertNotEquals(0, filter.read(buffer, 0, length));
        }
    }

    private void testTemiEvent(Filter filter, TemiEvent e) {
        e.getPts();
        e.getDescriptorTag();
        e.getDescriptorData();
    }

    private void testTsRecordEvent(Filter filter, TsRecordEvent e) {
        e.getPacketId();
        e.getTsIndexMask();
        e.getScIndexMask();
        e.getDataLength();
        long pts = e.getPts();
        int firstMbInSlice = e.getFirstMacroblockInSlice();
        if (!TunerVersionChecker.isHigherOrEqualVersionTo(TunerVersionChecker.TUNER_VERSION_1_1)) {
            assertEquals(pts, Tuner.INVALID_TIMESTAMP);
            assertEquals(firstMbInSlice, Tuner.INVALID_FIRST_MACROBLOCK_IN_SLICE);
        }
    }

    private void testScramblingStatusEvent(Filter filter, ScramblingStatusEvent e) {
        e.getScramblingStatus();
    }

    private void testIpCidChangeEvent(Filter filter, IpCidChangeEvent e) {
        e.getIpCid();
    }

    private void testRestartEvent(Filter filter, RestartEvent e) {
        e.getStartId();
    }

    private OnRecordStatusChangedListener getRecordListener() {
        return new OnRecordStatusChangedListener() {
            @Override
            public void onRecordStatusChanged(int status) {}
        };
    }

    private OnPlaybackStatusChangedListener getPlaybackListener() {
        return new OnPlaybackStatusChangedListener() {
            @Override
            public void onPlaybackStatusChanged(int status) {}
        };
    }

    static public FrontendSettings createFrontendSettings(FrontendInfo info) {
            FrontendCapabilities caps = info.getFrontendCapabilities();
            long minFreq = info.getFrequencyRangeLong().getLower();
            long maxFreq = info.getFrequencyRangeLong().getUpper();
            FrontendCapabilities feCaps = info.getFrontendCapabilities();
            switch(info.getType()) {
                case FrontendSettings.TYPE_ANALOG: {
                    AnalogFrontendCapabilities analogCaps = (AnalogFrontendCapabilities) caps;
                    int signalType = getFirstCapable(analogCaps.getSignalTypeCapability());
                    int sif = getFirstCapable(analogCaps.getSifStandardCapability());
                    return AnalogFrontendSettings
                            .builder()
                            .setFrequencyLong(minFreq)
                            .setSignalType(signalType)
                            .setSifStandard(sif)
                            .build();
                }
                case FrontendSettings.TYPE_ATSC3: {
                    Atsc3FrontendCapabilities atsc3Caps = (Atsc3FrontendCapabilities) caps;
                    int bandwidth = getFirstCapable(atsc3Caps.getBandwidthCapability());
                    int demod = getFirstCapable(atsc3Caps.getDemodOutputFormatCapability());
                    Atsc3FrontendSettings settings =
                            Atsc3FrontendSettings
                                    .builder()
                                    .setFrequencyLong(minFreq)
                                    .setBandwidth(bandwidth)
                                    .setDemodOutputFormat(demod)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ATSC: {
                    AtscFrontendCapabilities atscCaps = (AtscFrontendCapabilities) caps;
                    int modulation = getFirstCapable(atscCaps.getModulationCapability());
                    return AtscFrontendSettings
                            .builder()
                            .setFrequencyLong(minFreq)
                            .setModulation(modulation)
                            .build();
                }
                case FrontendSettings.TYPE_DVBC: {
                    DvbcFrontendCapabilities dvbcCaps = (DvbcFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbcCaps.getModulationCapability());
                    int fec = getFirstCapable(dvbcCaps.getFecCapability());
                    int annex = getFirstCapable(dvbcCaps.getAnnexCapability());
                    DvbcFrontendSettings settings =
                            DvbcFrontendSettings
                                    .builder()
                                    .setFrequencyLong(minFreq)
                                    .setModulation(modulation)
                                    .setInnerFec(fec)
                                    .setAnnex(annex)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DVBS: {
                    DvbsFrontendCapabilities dvbsCaps = (DvbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dvbsCaps.getModulationCapability());
                    int standard = getFirstCapable(dvbsCaps.getStandardCapability());
                    DvbsFrontendSettings settings =
                            DvbsFrontendSettings
                                    .builder()
                                    .setFrequencyLong(minFreq)
                                    .setModulation(modulation)
                                    .setStandard(standard)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DVBT: {
                    DvbtFrontendCapabilities dvbtCaps = (DvbtFrontendCapabilities) caps;
                    int transmission = getFirstCapable(dvbtCaps.getTransmissionModeCapability());
                    int bandwidth = getFirstCapable(dvbtCaps.getBandwidthCapability());
                    int constellation = getFirstCapable(dvbtCaps.getConstellationCapability());
                    int codeRate = getFirstCapable(dvbtCaps.getCodeRateCapability());
                    int hierarchy = getFirstCapable(dvbtCaps.getHierarchyCapability());
                    int guardInterval = getFirstCapable(dvbtCaps.getGuardIntervalCapability());
                    DvbtFrontendSettings settings = DvbtFrontendSettings
                            .builder()
                            .setFrequencyLong(minFreq)
                            .setTransmissionMode(transmission)
                            .setBandwidth(bandwidth)
                            .setConstellation(constellation)
                            .setHierarchy(hierarchy)
                            .setHighPriorityCodeRate(codeRate)
                            .setLowPriorityCodeRate(codeRate)
                            .setGuardInterval(guardInterval)
                            .setStandard(DvbtFrontendSettings.STANDARD_T)
                            .setMiso(false)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBS3: {
                    Isdbs3FrontendCapabilities isdbs3Caps = (Isdbs3FrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbs3Caps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbs3Caps.getCodeRateCapability());
                    Isdbs3FrontendSettings settings = Isdbs3FrontendSettings
                            .builder()
                            .setFrequencyLong(minFreq)
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBS: {
                    IsdbsFrontendCapabilities isdbsCaps = (IsdbsFrontendCapabilities) caps;
                    int modulation = getFirstCapable(isdbsCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbsCaps.getCodeRateCapability());
                    IsdbsFrontendSettings settings = IsdbsFrontendSettings
                            .builder()
                            .setFrequencyLong(minFreq)
                            .setModulation(modulation)
                            .setCodeRate(codeRate)
                            .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_ISDBT: {
                    IsdbtFrontendCapabilities isdbtCaps = (IsdbtFrontendCapabilities) caps;
                    int mode = getFirstCapable(isdbtCaps.getModeCapability());
                    int bandwidth = getFirstCapable(isdbtCaps.getBandwidthCapability());
                    int modulation = getFirstCapable(isdbtCaps.getModulationCapability());
                    int codeRate = getFirstCapable(isdbtCaps.getCodeRateCapability());
                    int guardInterval = getFirstCapable(isdbtCaps.getGuardIntervalCapability());
                    int timeInterleaveMode =
                            getFirstCapable(isdbtCaps.getTimeInterleaveModeCapability());
                    boolean isSegmentAutoSupported = isdbtCaps.isSegmentAutoSupported();
                    boolean isFullSegmentSupported = isdbtCaps.isFullSegmentSupported();

                    IsdbtFrontendSettings.Builder builder = IsdbtFrontendSettings.builder();
                    builder.setFrequencyLong(minFreq);
                    builder.setBandwidth(bandwidth);
                    builder.setMode(mode);
                    builder.setGuardInterval(guardInterval);

                    if (!TunerVersionChecker.isHigherOrEqualVersionTo(
                                TunerVersionChecker.TUNER_VERSION_2_0)) {
                        builder.setModulation(modulation);
                        builder.setCodeRate(codeRate);
                    } else {
                        IsdbtFrontendSettings.IsdbtLayerSettings.Builder layerBuilder =
                                IsdbtFrontendSettings.IsdbtLayerSettings.builder();
                        layerBuilder.setTimeInterleaveMode(timeInterleaveMode);
                        layerBuilder.setModulation(modulation);
                        layerBuilder.setCodeRate(codeRate);
                        if (isSegmentAutoSupported) {
                            layerBuilder.setNumOfSegment(0xFF);
                        } else {
                            if (isFullSegmentSupported) {
                                layerBuilder.setNumOfSegment(13);
                            } else {
                                layerBuilder.setNumOfSegment(1);
                            }
                        }
                        IsdbtFrontendSettings.IsdbtLayerSettings layer = layerBuilder.build();
                        builder.setLayerSettings(
                                new IsdbtFrontendSettings.IsdbtLayerSettings[] {layer});
                        builder.setPartialReceptionFlag(
                                IsdbtFrontendSettings.PARTIAL_RECEPTION_FLAG_TRUE);
                    }
                    IsdbtFrontendSettings settings = builder.build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                case FrontendSettings.TYPE_DTMB: {
                    DtmbFrontendCapabilities dtmbCaps = (DtmbFrontendCapabilities) caps;
                    int modulation = getFirstCapable(dtmbCaps.getModulationCapability());
                    int transmissionMode = getFirstCapable(
                            dtmbCaps.getTransmissionModeCapability());
                    int guardInterval = getFirstCapable(dtmbCaps.getGuardIntervalCapability());
                    int timeInterleaveMode = getFirstCapable(
                            dtmbCaps.getTimeInterleaveModeCapability());
                    int codeRate = getFirstCapable(dtmbCaps.getCodeRateCapability());
                    int bandwidth = getFirstCapable(dtmbCaps.getBandwidthCapability());
                    DtmbFrontendSettings settings =
                            DtmbFrontendSettings
                                    .builder()
                                    .setFrequencyLong(minFreq)
                                    .setModulation(modulation)
                                    .setTransmissionMode(transmissionMode)
                                    .setBandwidth(bandwidth)
                                    .setCodeRate(codeRate)
                                    .setGuardInterval(guardInterval)
                                    .setTimeInterleaveMode(timeInterleaveMode)
                                    .build();
                    settings.setEndFrequencyLong(maxFreq);
                    return settings;
                }
                default:
                    break;
            }
        return null;
    }

    static public int getFirstCapable(int caps) {
        if (caps == 0) return 0;
        int mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    static public long getFirstCapable(long caps) {
        if (caps == 0) return 0;
        long mask = 1;
        while ((mask & caps) == 0) {
            mask = mask << 1;
        }
        return (mask & caps);
    }

    private ScanCallback getScanCallback() {
        return new ScanCallback() {
            @Override
            public void onLocked() {
                if (mLockLatch != null) {
                    mLockLatch.countDown();
                }
            }

            @Override
            public void onScanStopped() {}

            @Override
            public void onProgress(int percent) {}

            @Override
            public void onFrequenciesReported(int[] frequency) {}

            @Override
            public void onFrequenciesLongReported(long[] frequencies) {
                ScanCallback.super.onFrequenciesLongReported(frequencies);
            }

            @Override
            public void onSymbolRatesReported(int[] rate) {}

            @Override
            public void onPlpIdsReported(int[] plpIds) {}

            @Override
            public void onGroupIdsReported(int[] groupIds) {}

            @Override
            public void onInputStreamIdsReported(int[] inputStreamIds) {}

            @Override
            public void onDvbsStandardReported(int dvbsStandard) {}

            @Override
            public void onDvbtStandardReported(int dvbtStandard) {}

            @Override
            public void onAnalogSifStandardReported(int sif) {}

            @Override
            public void onAtsc3PlpInfosReported(Atsc3PlpInfo[] atsc3PlpInfos) {
                for (Atsc3PlpInfo info : atsc3PlpInfos) {
                    if (info != null) {
                        info.getPlpId();
                        info.getLlsFlag();
                    }
                }
            }

            @Override
            public void onHierarchyReported(int hierarchy) {}

            @Override
            public void onSignalTypeReported(int signalType) {}

            @Override
            public void onModulationReported(int modulation) {
                ScanCallback.super.onModulationReported(modulation);
            }

            @Override
            public void onPriorityReported(boolean isHighPriority) {
                ScanCallback.super.onPriorityReported(isHighPriority);
            }

            @Override
            public void onDvbcAnnexReported(int dvbcAnnext) {
                ScanCallback.super.onDvbcAnnexReported(dvbcAnnext);
            }
        };
    }

    // TunerHandler utility for testing Tuner api calls in a different thread
    private static final int MSG_TUNER_HANDLER_CREATE = 1;
    private static final int MSG_TUNER_HANDLER_TUNE = 2;
    private static final int MSG_TUNER_HANDLER_CLOSE = 3;

    private ConditionVariable mTunerHandlerTaskComplete = new ConditionVariable();

    private TunerHandler createTunerHandler(Looper looper) {
        if (looper != null) {
            return new TunerHandler(looper);
        } else if ((looper = Looper.myLooper()) != null) {
            return new TunerHandler(looper);
        } else if ((looper = Looper.getMainLooper()) != null) {
            return new TunerHandler(looper);
        }
        return null;
    }

    private class TunerHandler extends Handler {
        Object mLock = new Object();
        Tuner mHandlersTuner;
        int mResult;

        private TunerHandler(Looper looper) {
            super(looper);
        }

        public Tuner getTuner() {
            synchronized (mLock) {
                return mHandlersTuner;
            }
        }

        public int getResult() {
            synchronized (mLock) {
                return mResult;
            }
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_TUNER_HANDLER_CREATE: {
                    synchronized (mLock) {
                        int useCase = msg.arg1;
                        mHandlersTuner = new Tuner(mContext, null, useCase);
                    }
                    break;
                }
                case MSG_TUNER_HANDLER_TUNE: {
                    synchronized (mLock) {
                        FrontendSettings feSettings = (FrontendSettings) msg.obj;
                        mResult = mHandlersTuner.tune(feSettings);
                    }
                    break;
                }
                case MSG_TUNER_HANDLER_CLOSE: {
                    synchronized (mLock) {
                        mHandlersTuner.close();
                    }
                    break;
                }
                default:
                    break;
            }
            mTunerHandlerTaskComplete.open();
        }
    }
}
