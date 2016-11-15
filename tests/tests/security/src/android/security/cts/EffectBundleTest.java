/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.cts;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.media.MediaPlayer;
import android.test.InstrumentationTestCase;
import android.util.Log;
import com.android.cts.security.R;

public class EffectBundleTest extends InstrumentationTestCase {
    private static final String TAG = "EffectBundleTest";
    private static final int[] BAND_ARRAY = {Integer.MIN_VALUE, -10000, -100, -2, -1};
    private static final int mValue0 = 9999; //unlikely values. Should not change
    private static final int mValue1 = 13877;

    //Testing security bug: 32436341
    public void testEqualizer_getParamCenterFreq() throws Exception {
        testGetParam(Equalizer.PARAM_CENTER_FREQ, BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32438598
    public void testEqualizer_getParamBandLevel() throws Exception {
        testGetParam(Equalizer.PARAM_BAND_LEVEL, BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32247948
    public void testEqualizer_getParamFreqRange() throws Exception {
        testGetParam(Equalizer.PARAM_BAND_FREQ_RANGE, BAND_ARRAY, mValue0, mValue1);
    }

    private void testGetParam(int command, int[] bandArray, int value0, int value1) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), R.raw.good);
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            for (int band : bandArray)
            {
                AudioEffect af = eq;
                int cmd[] = {command, band};
                int reply[] = {value0, value1};

                AudioEffect.class.getDeclaredMethod("getParameter", int[].class,
                        int[].class).invoke(af, cmd, reply);

                //values should remain the same
                assertEquals("getParam should not change value0", value0, reply[0]);
                assertEquals("getParam should not change value1", value1, reply[1]);
            }

        } catch (Exception e) {
            Log.w(TAG,"Problem testing equalizer");
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
    }
}
