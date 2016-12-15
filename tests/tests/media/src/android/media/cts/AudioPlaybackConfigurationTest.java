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

package android.media.cts;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.util.Log;
import android.media.AudioPlaybackConfiguration;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.util.ArrayList;
import java.util.List;

public class AudioPlaybackConfigurationTest extends CtsAndroidTestCase {
    private final static String TAG = "AudioPlaybackConfigurationTest";

    private final static int TEST_TIMING_TOLERANCE_MS = 50;

    // not declared inside test so it can be released in case of failure
    private MediaPlayer mMp;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mMp != null) {
            mMp.stop();
            mMp.release();
        }
    }

    public void testCallbackMediaPlayer() throws Exception {
        if (!isValidPlatform("testCallbackMediaPlayer")) return;

        AudioManager am = new AudioManager(getContext());
        assertNotNull("Could not create AudioManager", am);

        final AudioAttributes aa = (new AudioAttributes.Builder())
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build();

        mMp = MediaPlayer.create(getContext(), R.raw.sine1khzs40dblong,
                aa, am.generateAudioSessionId());

        MyAudioPlaybackCallback callback = new MyAudioPlaybackCallback();
        am.registerAudioPlaybackCallback(callback, null /*handler*/);

        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        assertEquals("onPlaybackConfigChanged call count not expected",
                1/*expected*/, callback.mCalled); //only one start call
        assertEquals("number of active players not expected",
                1/*expected*/, callback.mNbConfigs); //only one player active
        assertEquals("Audio attributes don't match", aa/*expected*/, callback.mAttrConfig);

        // stopping recording: callback is called with no match
        callback.reset();
        mMp.pause();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);

        assertEquals("onPlaybackConfigChanged call count not expected after pause",
                1/*expected*/, callback.mCalled); //only one pause call since reset
        assertEquals("number of active players not expected after pause",
                0/*expected*/, callback.mNbConfigs); //nothing should be playing now

        // unregister callback and start recording again
        am.unregisterAudioPlaybackCallback(callback);
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        callback.reset();
        mMp.start();
        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        assertEquals("onPlaybackConfigChanged call count not expected after unregister",
                0/*expected*/, callback.mCalled); //callback is unregistered

        // just call the callback once directly so it's marked as tested
        final AudioManager.AudioPlaybackCallback apc =
                (AudioManager.AudioPlaybackCallback) callback;
        apc.onPlaybackConfigChanged(new ArrayList<AudioPlaybackConfiguration>());
    }


    class MyAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        int mCalled = 0;
        int mNbConfigs = 0;
        AudioAttributes mAttrConfig;

        void reset() {
            mCalled = 0;
            mAttrConfig = null;
        }

        MyAudioPlaybackCallback() {
            mAttrConfig = null;
        }

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            mCalled++;
            mNbConfigs = configs.size();
            if (configs.isEmpty()) {
                mAttrConfig = null;
            } else {
                mAttrConfig = configs.get(0).getAudioAttributes();
            }
        }
    }

    private boolean isValidPlatform(String testName) {
        if (!getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_AUDIO_OUTPUT)) {
            Log.w(TAG,"AUDIO_OUTPUT feature not found. This system might not have a valid "
                    + "audio output HAL, skipping test " + testName);
            return false;
        }
        return true;
    }
}
