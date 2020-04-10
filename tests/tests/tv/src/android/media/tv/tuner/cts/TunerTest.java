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
import static org.junit.Assert.assertNotNull;

import android.content.Context;
import android.media.tv.tuner.Descrambler;
import android.media.tv.tuner.LnbCallback;
import android.media.tv.tuner.Lnb;
import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrRecorder;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.dvr.OnRecordStatusChangedListener;
import android.media.tv.tuner.filter.FilterCallback;
import android.media.tv.tuner.filter.FilterEvent;
import android.media.tv.tuner.filter.Filter;
import android.media.tv.tuner.filter.TimeFilter;
import android.media.tv.tuner.frontend.AtscFrontendSettings;
import android.media.tv.tuner.frontend.FrontendSettings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import java.util.concurrent.Executor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class TunerTest {
    private static final String TAG = "MediaTunerTest";

    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        InstrumentationRegistry
                .getInstrumentation().getUiAutomation().adoptShellPermissionIdentity();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void testTunerConstructor() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        assertNotNull(tuner);
    }

    @Test
    public void testTuning() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        int res = tuner.tune(getFrontendSettings());
        assertEquals(Tuner.RESULT_SUCCESS, res);
        res = tuner.cancelTuning();
        assertEquals(Tuner.RESULT_SUCCESS, res);
    }

    @Test
    public void testOpenLnb() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Lnb lnb = tuner.openLnb(getExecutor(), getLnbCallback());
        assertNotNull(lnb);
    }

    @Test
    public void testLnbSetVoltage() throws Exception {
        // TODO: move lnb-related tests to a separate file.
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Lnb lnb = tuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(lnb.setVoltage(Lnb.VOLTAGE_5V), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testLnbSetTone() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Lnb lnb = tuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(lnb.setTone(Lnb.TONE_NONE), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testLnbSetPosistion() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Lnb lnb = tuner.openLnb(getExecutor(), getLnbCallback());
        assertEquals(
                lnb.setSatellitePosition(Lnb.POSITION_A), Tuner.RESULT_SUCCESS);
    }

    @Test
    public void testOpenFilter() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Filter f = tuner.openFilter(
                Filter.TYPE_TS, Filter.SUBTYPE_SECTION, 1000, getExecutor(), getFilterCallback());
        assertNotNull(f);
    }

    @Test
    public void testOpenTimeFilter() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        TimeFilter f = tuner.openTimeFilter();
        assertNotNull(f);
    }

    @Test
    public void testOpenDescrambler() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        Descrambler d = tuner.openDescrambler();
        assertNotNull(d);
    }

    @Test
    public void testOpenDvrRecorder() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        DvrRecorder d = tuner.openDvrRecorder(100, getExecutor(), getRecordListener());
        assertNotNull(d);
    }

    @Test
    public void testOpenDvPlayback() throws Exception {
        if (!hasTuner()) return;
        Tuner tuner = new Tuner(mContext, null, 100);
        DvrPlayback d = tuner.openDvrPlayback(100, getExecutor(), getPlaybackListener());
        assertNotNull(d);
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
            public void onFilterEvent(Filter filter, FilterEvent[] events) {}
            @Override
            public void onFilterStatusChanged(Filter filter, int status) {}
        };
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

    private FrontendSettings getFrontendSettings() {
        return AtscFrontendSettings
                .builder()
                .setFrequency(2000)
                .setModulation(AtscFrontendSettings.MODULATION_AUTO)
                .build();
    }
}
