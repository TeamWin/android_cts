/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.media.cts;

import android.media.cts.R;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.DynamicsProcessing;
import android.media.audiofx.DynamicsProcessing.Channel;
import android.media.audiofx.DynamicsProcessing.Eq;
import android.test.AndroidTestCase;
import android.util.Log;

public class DynamicsProcessingTest extends PostProcTestBase {

    private static final String TAG = "DynamicsProcessingTest";
    private DynamicsProcessing mDP;

    private static final int MIN_CHANNEL_COUNT = 1;
    private static final int TEST_CHANNEL = 0;
    private static final float EPSILON = 0.00001f;
    private static final int DEFAULT_VARIANT =
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION;
    private static final boolean DEFAULT_PREEQ_IN_USE = true;
    private static final int DEFAULT_PREEQ_BAND_COUNT = 2;
    private static final boolean DEFAULT_MBC_IN_USE = true;
    private static final int DEFAULT_MBC_BAND_COUNT = 2;
    private static final boolean DEFAULT_POSTEQ_IN_USE = true;
    private static final int DEFAULT_POSTEQ_BAND_COUNT = 2;
    private static final boolean DEFAULT_LIMITER_IN_USE = true;
    private static final float DEFAULT_FRAME_DURATION = 9.5f;
    private static final float DEFAULT_INPUT_GAIN = -12.5f;

    //-----------------------------------------------------------------
    // DynamicsProcessing tests:
    //----------------------------------

    //-----------------------------------------------------------------
    // 0 - constructors
    //----------------------------------

    //Test case 0.0: test constructor and release
    public void test0_0ConstructorAndRelease() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
            assertNotNull("null AudioManager", am);
            createDynamicsProcessing(AudioManager.AUDIO_SESSION_ID_GENERATE);
            releaseDynamicsProcessing();

            final int session = am.generateAudioSessionId();
            assertTrue("cannot generate new session", session != AudioManager.ERROR);
            createDynamicsProcessing(session);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test0_1ConstructorWithConfigAndRelease() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();
        } finally {
            releaseDynamicsProcessing();
        }
    }

    //-----------------------------------------------------------------
    // 1 - create with parameters
    //----------------------------------

    public void test1_0ParametersEngine() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();

            //Check Parameters:
            DynamicsProcessing.Config engineConfig = mDP.getConfig();
            final float preferredFrameDuration = engineConfig.getPreferredFrameDuration();
            assertEquals("preferredFrameDuration is different",DEFAULT_FRAME_DURATION,
                    preferredFrameDuration, EPSILON);

            final int preEqBandCount = engineConfig.getPreEqBandCount();
            assertEquals("preEqBandCount is different", DEFAULT_PREEQ_BAND_COUNT, preEqBandCount);

            final int mbcBandCount = engineConfig.getMbcBandCount();
            assertEquals("mbcBandCount is different", DEFAULT_MBC_BAND_COUNT, mbcBandCount);

            final int postEqBandCount = engineConfig.getPostEqBandCount();
            assertEquals("postEqBandCount is different", DEFAULT_POSTEQ_BAND_COUNT,
                    postEqBandCount);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test1_1ParametersChannel() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();

            //Check Parameters:
            final int channelCount = mDP.getChannelCount();
            assertTrue("unexpected channel count", channelCount >= MIN_CHANNEL_COUNT);

            Channel channel = mDP.getChannelByChannelIndex(TEST_CHANNEL);

            final float inputGain = channel.getInputGain();
            assertEquals("inputGain is different", DEFAULT_INPUT_GAIN, inputGain, EPSILON);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test1_2ParametersPreEq() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();

            DynamicsProcessing.Eq eq = mDP.getPreEqByChannelIndex(TEST_CHANNEL);

            final boolean inUse = eq.isInUse();
            assertEquals("inUse is different", DEFAULT_PREEQ_IN_USE, inUse);

            final int bandCount = eq.getBandCount();
            assertEquals("band count is different", DEFAULT_PREEQ_BAND_COUNT, bandCount);
            releaseDynamicsProcessing();
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test1_3ParametersMbc() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();

            DynamicsProcessing.Mbc mbc = mDP.getMbcByChannelIndex(TEST_CHANNEL);

            final boolean inUse = mbc.isInUse();
            assertEquals("inUse is different", DEFAULT_MBC_IN_USE, inUse);

            final int bandCount = mbc.getBandCount();
            assertEquals("band count is different", DEFAULT_MBC_BAND_COUNT, bandCount);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test1_4ParametersPostEq() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }
        try {
            createDefaultEffect();

            DynamicsProcessing.Eq eq = mDP.getPostEqByChannelIndex(TEST_CHANNEL);

            boolean inUse = eq.isInUse();
            assertEquals("inUse is different", DEFAULT_POSTEQ_IN_USE, inUse);

            int bandCount = eq.getBandCount();
            assertEquals("band count is different", DEFAULT_POSTEQ_BAND_COUNT, bandCount);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    public void test1_5ParametersLimiter() throws Exception {
        if (!hasAudioOutput()) {
            return;
        }

        try {
            createDefaultEffect();

            DynamicsProcessing.Limiter limiter = mDP.getLimiterByChannelIndex(TEST_CHANNEL);

            final boolean inUse = limiter.isInUse();
            assertEquals("inUse is different", DEFAULT_LIMITER_IN_USE, inUse);
        } finally {
            releaseDynamicsProcessing();
        }
    }

    //-----------------------------------------------------------------
    // 2 - run and change parameters
    //----------------------------------

    //TODO: runtime change of parameters.
    //-----------------------------------------------------------------
    // private methods
    //----------------------------------

    private void createDynamicsProcessing(int session) {
        createDynamicsProcessingWithConfig(session, null);
    }

    private void createDynamicsProcessingWithConfig(int session, DynamicsProcessing.Config config) {
        releaseDynamicsProcessing();
        try {
            mDP = (config == null ? new DynamicsProcessing(session) :
                new DynamicsProcessing(0 /* priority */, session, config));
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "createDynamicsProcessingWithConfig() DynamicsProcessing not found"
                    + "exception: ", e);
        } catch (UnsupportedOperationException e) {
            Log.e(TAG, "createDynamicsProcessingWithConfig() Effect library not loaded exception: ",
                    e);
        }
        assertNotNull("could not create DynamicsProcessing", mDP);
    }

    private void releaseDynamicsProcessing() {
        if (mDP != null) {
            mDP.release();
            mDP = null;
        }
    }
    private void createDefaultEffect() {
        DynamicsProcessing.Config config = getBuilderWithValues().build();
        assertNotNull("null config", config);

        AudioManager am = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        assertNotNull("null AudioManager", am);

        int session = am.generateAudioSessionId();
        assertTrue("cannot generate new session", session != AudioManager.ERROR);

        createDynamicsProcessingWithConfig(session, config);
    }

    private DynamicsProcessing.Config.Builder getBuilder() {
        //simple config
        DynamicsProcessing.Config.Builder builder = new DynamicsProcessing.Config.Builder(
                DEFAULT_VARIANT /* variant */,
                MIN_CHANNEL_COUNT /* channels */,
                DEFAULT_PREEQ_IN_USE /*enable preEQ*/,
                DEFAULT_PREEQ_BAND_COUNT /*preEq bands*/,
                DEFAULT_MBC_IN_USE /*enable mbc*/,
                DEFAULT_MBC_BAND_COUNT /*mbc bands*/,
                DEFAULT_POSTEQ_IN_USE /*enable postEq*/,
                DEFAULT_POSTEQ_BAND_COUNT /*postEq bands*/,
                DEFAULT_LIMITER_IN_USE /*enable limiter*/);

        return builder;
    }

    private DynamicsProcessing.Config.Builder getBuilderWithValues() {
        //simple config
        DynamicsProcessing.Config.Builder builder = getBuilder();

        //Set Defaults
        builder.setPreferredFrameDuration(DEFAULT_FRAME_DURATION);
        builder.setInputGainAllChannelsTo(DEFAULT_INPUT_GAIN);
        return builder;
    }
}