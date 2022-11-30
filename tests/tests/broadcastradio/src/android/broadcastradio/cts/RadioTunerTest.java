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

import static com.google.common.truth.TruthJUnit.assume;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.hardware.radio.ProgramList;
import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager;
import android.hardware.radio.RadioTuner;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;

/**
 * CTS test for broadcast radio.
 */
@RunWith(AndroidJUnit4.class)
public final class RadioTunerTest extends AbstractRadioTestCase {

    private static final int TEST_CONFIG_FLAG = RadioManager.CONFIG_FORCE_ANALOG;

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#close"})
    public void close_twice() {
        openAmFmTuner();

        mRadioTuner.close();
        mRadioTuner.close();

        verify(mCallback, never()).onError(anyInt());
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#cancel",
            "android.hardware.radio.RadioTuner#close"})
    public void cancel_afterTunerCloses_returnsInvalidOperationStatus() {
        openAmFmTuner();
        mRadioTuner.close();

        int cancelResult = mRadioTuner.cancel();

        mExpect.withMessage("Result of cancel operation after tuner closed")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_INVALID_OPERATION);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute"})
    public void getMute_returnsFalse() {
        openAmFmTuner();

        boolean isMuted = mRadioTuner.getMute();

        mExpect.withMessage("Mute status of tuner without audio").that(isMuted).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute",
            "android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withTrue_MuteSucceeds() {
        openAmFmTuner();

        int result = mRadioTuner.setMute(true);

        mExpect.withMessage("Result of setting mute with true")
                .that(result).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Mute status after setting mute with true")
                .that(mRadioTuner.getMute()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute",
            "android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withFalse_MuteSucceeds() {
        openAmFmTuner();
        mRadioTuner.setMute(true);

        int result = mRadioTuner.setMute(false);

        mExpect.withMessage("Result of setting mute with false")
                .that(result).isEqualTo(RadioManager.STATUS_OK);
        mExpect.withMessage("Mute status after setting mute with false")
                .that(mRadioTuner.getMute()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setMute"})
    public void setMute_withTunerWithoutAudio_returnsError() {
        openAmFmTuner(/* withAudio= */ false);

        int result = mRadioTuner.setMute(false);

        mExpect.withMessage("Status of setting mute on tuner without audio")
                .that(result).isEqualTo(RadioManager.STATUS_ERROR);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getMute"})
    public void isMuted_withTunerWithoutAudio_returnsTrue() {
        openAmFmTuner(/* withAudio= */ false);

        boolean isMuted = mRadioTuner.getMute();

        mExpect.withMessage("Mute status of tuner without audio").that(isMuted).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void step_withDownDirection() {
        openAmFmTuner();

        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ true);

        mExpect.withMessage("Result of step operation with down direction")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
        verify(mCallback, timeout(TUNE_CALLBACK_TIMEOUT_MS)).onProgramInfoChanged(any());
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void step_withUpDirection() {
        openAmFmTuner();

        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_UP, /* skipSubChannel= */ false);

        mExpect.withMessage("Result of step operation with up direction")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
        verify(mCallback, timeout(TUNE_CALLBACK_TIMEOUT_MS)).onProgramInfoChanged(any());
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void step_withMultipleTimes() {
        openAmFmTuner();

        for (int index = 0; index < 10; index++) {
            int stepResult =
                    mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ true);

            mExpect.withMessage("Result of step operation at iteration %s", index)
                    .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
            verify(mCallback, timeout(TUNE_CALLBACK_TIMEOUT_MS)).onProgramInfoChanged(any());

            resetCallback();
        }
    }

    @Test
    @ApiTest(apis = {
            "android.hardware.radio.RadioTuner#tune(android.hardware.radio.ProgramSelector)",
            "android.hardware.radio.RadioTuner.Callback#onProgramInfoChanged"})
    public void tune_withFmSelector_onProgramInfoChangedInvoked() {
        openAmFmTuner();
        int freq = mFmBandConfig.getLowerLimit() + mFmBandConfig.getSpacing();
        ProgramSelector sel = ProgramSelector.createAmFmSelector(RadioManager.BAND_FM, freq,
                /* subChannel= */ 0);
        ArgumentCaptor<RadioManager.ProgramInfo> infoCaptor =
                ArgumentCaptor.forClass(RadioManager.ProgramInfo.class);

        mRadioTuner.tune(sel);

        verify(mCallback, timeout(TUNE_CALLBACK_TIMEOUT_MS))
                .onProgramInfoChanged(infoCaptor.capture());
        mExpect.withMessage("Program selector tuned to")
                .that(infoCaptor.getValue().getSelector()).isEqualTo(sel);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#cancel"})
    public void cancel_afterNoOperations() {
        openAmFmTuner();

        int cancelResult = mRadioTuner.cancel();

        mExpect.withMessage("Result of cancel operation after no operations")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_OK);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#step",
            "android.hardware.radio.RadioTuner#cancel"})
    public void cancel_afterStepCompletes() {
        openAmFmTuner();
        int stepResult = mRadioTuner.step(RadioTuner.DIRECTION_DOWN, /* skipSubChannel= */ false);
        mExpect.withMessage("Result of step operation")
                .that(stepResult).isEqualTo(RadioManager.STATUS_OK);
        verify(mCallback, timeout(TUNE_CALLBACK_TIMEOUT_MS)).onProgramInfoChanged(any());

        int cancelResult = mRadioTuner.cancel();

        mExpect.withMessage("Result of cancel operation after step completed")
                .that(cancelResult).isEqualTo(RadioManager.STATUS_OK);
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#getDynamicProgramList"})
    public void getDynamicProgramList_programListUpdated() {
        openAmFmTuner();
        ProgramList.OnCompleteListener completeListener =
                mock(ProgramList.OnCompleteListener.class);

        ProgramList list = mRadioTuner.getDynamicProgramList(/* filter= */ null);
        assume().withMessage("Dynamic program list supported").that(list).isNotNull();
        try {
            list.addOnCompleteListener(completeListener);

            verify(completeListener, timeout(PROGRAM_LIST_COMPLETE_TIMEOUT_MS)).onComplete();
        } finally {
            list.close();
        }
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#isConfigFlagSupported",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void isConfigFlagSet_forTunerNotSupported_throwsException() {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeFalse("Config flag supported", isSupported);

        assertThrows(UnsupportedOperationException.class,
                () -> mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG));
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag"})
    public void setConfigFlag_forTunerNotSupported_throwsException() {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeFalse("Config flag supported", isSupported);

        assertThrows(UnsupportedOperationException.class,
                () -> mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ true));
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void setConfigFlag_withTrue_succeeds() {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeTrue("Config flag supported", isSupported);

        mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ true);

        mExpect.withMessage("Config flag with true value")
                .that(mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG)).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#setConfigFlag",
            "android.hardware.radio.RadioTuner#isConfigFlagSet"})
    public void setConfigFlag_withFalse_succeeds() {
        openAmFmTuner();
        boolean isSupported = mRadioTuner.isConfigFlagSupported(TEST_CONFIG_FLAG);
        assumeTrue("Config flag supported", isSupported);

        mRadioTuner.setConfigFlag(TEST_CONFIG_FLAG, /* value= */ false);

        mExpect.withMessage("Config flag with false value")
                .that(mRadioTuner.isConfigFlagSet(TEST_CONFIG_FLAG)).isFalse();
    }
}
