/*
 * Copyright (C) 2017 The Android Open Source Project
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
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.AudioRecordingConfiguration;
import android.util.Log;

import com.android.compatibility.common.util.CtsAndroidTestCase;

import java.util.List;

public class AudioRecordConcurrencyTest extends CtsAndroidTestCase {
    static final String TAG = "AudioRecordConcurrencyTest";
    static final String REPORT_LOG_NAME = "CtsMediaTestCases";
    static final int RECORD_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    static final int RECORD_CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO;
    static final int RECORD_SAMPLE_RATE = 16000;
    static final long SLEEP_AFTER_STOP_FOR_INACTIVITY_MS = 1000;
    static final int TEST_TIMING_TOLERANCE_MS = 70;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        if (!hasMicrophone()) {
            Log.i(TAG, "AudioRecordConcurrencyTest skipped: no microphone");
            return;
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testConcurrentRecord() throws Exception {
        final String TEST_NAME = "testConcurrentRecord";

        if (!hasMicrophone()) {
            Log.i(TAG, "testConcurrentRecord() skipped: no microphone");
            return;
        }

        int[] foregroundSources = new int[] {
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.CAMCORDER,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
        };

        int[] backgroundSources = new int[] {
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
        };

        for (int source1 : backgroundSources) {
            for (int source2 : backgroundSources) {
                assertTrue(TEST_NAME+" failed background source: "+source1+
                           " background source: "+source2,
                           doConcurrencyTest(TEST_NAME, source1, source2));
            }
        }

        for (int source1 : backgroundSources) {
            for (int source2 : foregroundSources) {
                assertTrue(TEST_NAME+" failed background source: "+source1+
                           " foreground source: "+source2,
                           doConcurrencyTest(TEST_NAME, source1, source2));
            }
        }

        for (int source1 : foregroundSources) {
            for (int source2 : backgroundSources) {
                assertTrue(TEST_NAME+" failed foreground source: "+source1+
                           " background source: "+source2,
                           doConcurrencyTest(TEST_NAME, source1, source2));
            }
        }

        for (int source1 : foregroundSources) {
            for (int source2 : foregroundSources) {
                assertFalse(TEST_NAME+" failed foreground: "+ source1+
                        " foreground source: "+ source2,
                        doConcurrencyTest(TEST_NAME, source1, source2));
            }
        }
    }

    boolean doConcurrencyTest(String testName, int source1, int source2) {
        AudioRecord record1 = null;
        AudioRecord record2 = null;
        boolean active1 = false;
        boolean active2 = false;

        try {

            AudioFormat format = new AudioFormat.Builder()
                    .setSampleRate(RECORD_SAMPLE_RATE)
                    .setChannelMask(RECORD_CHANNEL_MASK)
                    .setEncoding(RECORD_ENCODING)
                    .build();

            record1 = new AudioRecord.Builder()
                    .setAudioSource(source1)
                    .setAudioFormat(format)
                    .build();
            // AudioRecord creation may have silently failed, check state now
            assertEquals(testName, AudioRecord.STATE_INITIALIZED, record1.getState());

            record2 = new AudioRecord.Builder()
                    .setAudioSource(source2)
                    .setAudioFormat(format)
                    .build();
            // AudioRecord creation may have silently failed, check state now
            assertEquals(testName, AudioRecord.STATE_INITIALIZED, record2.getState());

            final int BUFFER_FRAMES = 512;
            final int BUFFER_SAMPLES = BUFFER_FRAMES * format.getChannelCount();

            short[] shortData = new short[BUFFER_SAMPLES];

            record1.startRecording();
            record2.startRecording();
            try {
                Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            } catch (InterruptedException e) {
            }
            if (record1.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                int ret = record1.read(shortData, 0, BUFFER_SAMPLES);
                active1 = ret == BUFFER_SAMPLES;
            }
            if (record2.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                int ret = record2.read(shortData, 0, BUFFER_SAMPLES);
                active2 = ret == BUFFER_SAMPLES;
            }

            if (active1 && active2) {
                // verify we have at least 2 active recording configurations
                AudioManager am = new AudioManager(getContext());
                assertNotNull("Could not create AudioManager", am);
                List<AudioRecordingConfiguration> configs = am.getActiveRecordingConfigurations();
                assertNotNull("Invalid null array of record configurations during recording",
                        configs);
                assertTrue("no active record configurations (empty array) during recording",
                        configs.size() > 1);
            }

            record2.stop();
            record1.stop();
        } finally {
            if (record1 != null) {
                record1.release();
            }
            if (record2 != null) {
                record2.release();
            }
            // wait for stop to be completed at audio flinger level
            try {
                Thread.sleep(SLEEP_AFTER_STOP_FOR_INACTIVITY_MS);
            } catch (InterruptedException e) {
            }
        }
        return active1 && active2;
    }

    private boolean hasMicrophone() {
        return getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }
}
