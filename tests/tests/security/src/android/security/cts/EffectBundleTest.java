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

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import com.android.cts.security.R;

public class EffectBundleTest extends InstrumentationTestCase {
    private static final String TAG = "EffectBundleTest";
    private static final int[] INVALID_BAND_ARRAY = {Integer.MIN_VALUE, -10000, -100, -2, -1};
    private static final int mValue0 = 9999; //unlikely values. Should not change
    private static final int mValue1 = 13877;
    private static final int PRESET_CUSTOM = -1; //keep in sync AudioEqualizer.h

    private static final int MEDIA_SHORT = 0;
    private static final int MEDIA_LONG = 1;

    //Testing security bug: 32436341
    public void testEqualizer_getParamCenterFreq() throws Exception {
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_CENTER_FREQ, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32588352
    public void testEqualizer_getParamCenterFreq_long() throws Exception {
        testGetParam(MEDIA_LONG, Equalizer.PARAM_CENTER_FREQ, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32438598
    public void testEqualizer_getParamBandLevel() throws Exception {
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_BAND_LEVEL, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32584034
    public void testEqualizer_getParamBandLevel_long() throws Exception {
        testGetParam(MEDIA_LONG, Equalizer.PARAM_BAND_LEVEL, INVALID_BAND_ARRAY, mValue0, mValue1);
    }

    //Testing security bug: 32247948
    public void testEqualizer_getParamFreqRange() throws Exception {
        testGetParam(MEDIA_SHORT, Equalizer.PARAM_BAND_FREQ_RANGE, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32588756
    public void testEqualizer_getParamFreqRange_long() throws Exception {
        testGetParam(MEDIA_LONG, Equalizer.PARAM_BAND_FREQ_RANGE, INVALID_BAND_ARRAY, mValue0,
                mValue1);
    }

    //Testing security bug: 32448258
    public void testEqualizer_getParamPresetName() throws Exception {
        testParamPresetName(MEDIA_SHORT);
    }

    //Testing security bug: 32588016
    public void testEqualizer_getParamPresetName_long() throws Exception {
        testParamPresetName(MEDIA_LONG);
    }

    private void testParamPresetName(int media) {
        final int command = Equalizer.PARAM_GET_PRESET_NAME;
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            final byte testValue = 7;
            byte reply[] = new byte[Equalizer.PARAM_STRING_SIZE_MAX];
            Arrays.fill(reply, testValue);
            if (!eqGetParam(media, command, invalidBand, reply)) {
                fail("getParam PARAM_GET_PRESET_NAME did not complete successfully");
            }
            //Compare
            if (invalidBand == PRESET_CUSTOM) {
                final String expectedName = "Custom";
                int length = 0;
                while (reply[length] != 0) length++;
                try {
                    final String presetName =  new String(reply, 0, length,
                            StandardCharsets.ISO_8859_1.name());
                    assertEquals("getPresetName custom preset name failed", expectedName,
                            presetName);
                } catch (Exception e) {
                    Log.w(TAG,"Problem creating reply string.");
                }
            } else {
                for (int i = 0; i< reply.length; i++) {
                    assertEquals(String.format("getParam should not change reply at byte %d", i),
                            testValue, reply[i]);
                }
            }
        }
    }

    //testing security bug: 32095626
    public void testEqualizer_setParamBandLevel() throws Exception {
        final int command = Equalizer.PARAM_BAND_LEVEL;
        short[] value = { 1000 };
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqSetParam(MEDIA_SHORT, command, invalidBand, value)) {
                fail("setParam PARAM_BAND_LEVEL did not complete successfully");
            }
        }
    }

    //testing security bug: 32585400
    public void testEqualizer_setParamBandLevel_long() throws Exception {
        final int command = Equalizer.PARAM_BAND_LEVEL;
        short[] value = { 1000 };
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqSetParam(MEDIA_LONG, command, invalidBand, value)) {
                fail("setParam PARAM_BAND_LEVEL did not complete successfully");
            }
        }
    }

    private boolean eqGetParam(int media, int command, int band, byte[] reply) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("getParameter", int[].class,
                    byte[].class).invoke(af, cmd, reply);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem testing equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private boolean eqGetParam(int media, int command, int band, int[] reply) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(), getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("getParameter", int[].class,
                    int[].class).invoke(af, cmd, reply);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem getting parameter from equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private void testGetParam(int media, int command, int[] bandArray, int value0, int value1) {
        int reply[] = {value0, value1};
        for (int invalidBand : INVALID_BAND_ARRAY)
        {
            if (!eqGetParam(media, command, invalidBand, reply)) {
                fail(String.format("getParam for command %d did not complete successfully",
                        command));
            }
            assertEquals("getParam should not change value0", value0, reply[0]);
            assertEquals("getParam should not change value1", value1, reply[1]);
        }
    }

    private boolean eqSetParam(int media, int command, int band, short[] value) {
        MediaPlayer mp = null;
        Equalizer eq = null;
        boolean status = false;
        try {
            mp = MediaPlayer.create(getInstrumentation().getContext(),  getMediaId(media));
            eq = new Equalizer(0 /*priority*/, mp.getAudioSessionId());

            AudioEffect af = eq;
            int cmd[] = {command, band};

            AudioEffect.class.getDeclaredMethod("setParameter", int[].class,
                    short[].class).invoke(af, cmd, value);
            status = true;
        } catch (Exception e) {
            Log.w(TAG,"Problem setting parameter in equalizer");
            status = false;
        } finally {
            if (eq != null) {
                eq.release();
            }
            if (mp != null) {
                mp.release();
            }
        }
        return status;
    }

    private int getMediaId(int media) {
        switch (media) {
            default:
            case MEDIA_SHORT:
                return R.raw.good;
            case MEDIA_LONG:
                return R.raw.onekhzsine_90sec;
        }
    }
}
