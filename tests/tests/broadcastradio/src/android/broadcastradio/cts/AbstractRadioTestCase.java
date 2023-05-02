/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.broadcastradio.cts;

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.SafeCleanerRule;

import com.google.common.truth.Expect;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractRadioTestCase {

    protected static final int HANDLER_CALLBACK_MS = 100;
    protected static final int CANCEL_TIMEOUT_MS = 1_000;
    protected static final int TUNE_CALLBACK_TIMEOUT_MS = 30_000;
    protected static final int PROGRAM_LIST_COMPLETE_TIMEOUT_MS = 60_000;

    private Context mContext;

    protected RadioManager mRadioManager;
    protected RadioTuner mRadioTuner;
    @Mock
    protected RadioTuner.Callback mCallback;

    protected RadioManager.BandConfig mAmBandConfig;
    protected RadioManager.BandConfig mFmBandConfig;
    protected RadioManager.ModuleProperties mModule;

    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final Expect mExpect = Expect.create();
    @Rule
    public SafeCleanerRule mSafeCleanerRule = new SafeCleanerRule()
            .run(() -> {
                if (mRadioTuner != null) {
                    mRadioTuner.close();
                }
                resetCallback();
            });

    @Before
    public void setup() {
        InstrumentationRegistry.getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(Manifest.permission.ACCESS_BROADCAST_RADIO);

        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        PackageManager packageManager = mContext.getPackageManager();
        boolean isRadioSupported = packageManager.hasSystemFeature(
                PackageManager.FEATURE_BROADCAST_RADIO);

        assumeTrue("Radio supported", isRadioSupported);

        mRadioManager = mContext.getSystemService(RadioManager.class);

        assertWithMessage("Radio manager exists").that(mRadioManager).isNotNull();
    }

    @After
    public void cleanUp() {
        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    protected void resetCallback() {
        verify(mCallback, never()).onError(anyInt());
        verify(mCallback, never()).onTuneFailed(anyInt(), any());
        verify(mCallback, never()).onControlChanged(anyBoolean());
        Mockito.reset(mCallback);
    }

    protected void openAmFmTuner() {
        openAmFmTuner(/* withAudio= */ true);
    }

    protected void openAmFmTuner(boolean withAudio) {
        setAmFmConfig();

        assume().withMessage("AM/FM radio module exists").that(mModule).isNotNull();

        mRadioTuner = mRadioManager.openTuner(mModule.getId(), mFmBandConfig, withAudio, mCallback,
                /* handler= */ null);

        if (!withAudio) {
            assume().withMessage("Non-audio radio tuner").that(mRadioTuner).isNotNull();
        }

        assertWithMessage("Radio tuner opened").that(mRadioTuner).isNotNull();
        verify(mCallback, after(HANDLER_CALLBACK_MS).atMost(1)).onProgramInfoChanged(any());

        resetCallback();
    }

    protected void setAmFmConfig() {
        mModule = null;
        List<RadioManager.ModuleProperties> modules = new ArrayList<>();
        mRadioManager.listModules(modules);
        RadioManager.AmBandDescriptor amBandDescriptor = null;
        RadioManager.FmBandDescriptor fmBandDescriptor = null;
        for (int moduleIndex = 0; moduleIndex < modules.size(); moduleIndex++) {
            for (RadioManager.BandDescriptor band : modules.get(moduleIndex).getBands()) {
                int bandType = band.getType();
                if (bandType == RadioManager.BAND_AM || bandType == RadioManager.BAND_AM_HD) {
                    amBandDescriptor = (RadioManager.AmBandDescriptor) band;
                }
                if (bandType == RadioManager.BAND_FM || bandType == RadioManager.BAND_FM_HD) {
                    fmBandDescriptor = (RadioManager.FmBandDescriptor) band;
                }
            }
            if (amBandDescriptor != null && fmBandDescriptor != null) {
                mModule = modules.get(moduleIndex);
                mAmBandConfig = new RadioManager.AmBandConfig.Builder(amBandDescriptor).build();
                mFmBandConfig = new RadioManager.FmBandConfig.Builder(fmBandDescriptor).build();
                break;
            }
        }
    }
}
