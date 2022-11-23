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

import android.hardware.radio.RadioManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * CTS test for broadcast radio.
 */
@RunWith(AndroidJUnit4.class)
public final class RadioManagerTest extends AbstractRadioTestCase {

    @Test
    public void listModules_returnsNonEmptyModules() {
        List<RadioManager.ModuleProperties> modules = new ArrayList<>();

        int status = mRadioManager.listModules(modules);

        assertWithMessage("Result of listing modules")
                .that(status).isEqualTo(RadioManager.STATUS_OK);
        assertWithMessage("Modules in radio manager").that(modules).isNotEmpty();
    }

    @Test
    public void openTuner_withAmFmBand_succeeds() {
        setAmFmConfig();
        // skip tests for radio manager not supporting AM/FM
        assume().withMessage("AM/FM radio module exists").that(mModule).isNotNull();

        mRadioTuner = mRadioManager.openTuner(mModule.getId(), mFmBandConfig,
                /* withAudio= */ true, mCallback, /* handler= */ null);

        assertWithMessage("Radio tuner opened").that(mRadioTuner).isNotNull();
    }

    @Test
    public void openTuner_withAmFmBandAfterCloseTuner_succeeds() throws Throwable {
        setAmFmConfig();
        // skip tests for radio manager not supporting AM/FM
        assume().withMessage("AM/FM radio module exists").that(mModule).isNotNull();
        mRadioTuner = mRadioManager.openTuner(mModule.getId(), mFmBandConfig,
                /* withAudio= */ true, mCallback, /* handler= */ null);
        assertWithMessage("Radio tuner opened").that(mRadioTuner).isNotNull();
        resetCallback();

        mRadioTuner.close();
        mRadioTuner = mRadioManager.openTuner(mModule.getId(), mFmBandConfig,
                /* withAudio= */ true, mCallback, /* handler= */ null);

        assertWithMessage("Radio tuner opened").that(mRadioTuner).isNotNull();
    }
}
