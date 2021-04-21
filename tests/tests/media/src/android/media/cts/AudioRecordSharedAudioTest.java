/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaSyncEvent;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;


@NonMediaMainlineTest
@RunWith(AndroidJUnit4.class)
public class AudioRecordSharedAudioTest {
    private static final String TAG = "AudioRecordSharedAudioTest";
    private static final int SAMPLING_RATE_HZ = 16000;

    @Test
    public void testPermissionFailure() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        assertThrows(UnsupportedOperationException.class, () -> {
                    AudioRecord record = new AudioRecord.Builder().setMaxSharedAudioHistoryMillis(
                            AudioRecord.getMaxSharedAudioHistoryMillis() - 1).build();
                });

        final AudioRecord record =
                new AudioRecord.Builder()
                        .setAudioFormat(new AudioFormat.Builder()
                            .setSampleRate(SAMPLING_RATE_HZ)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                        .setBufferSizeInBytes(SAMPLING_RATE_HZ
                                * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                        .build();
        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());
        record.startRecording();
        Thread.sleep(500);

        assertThrows(SecurityException.class, () -> {
                    record.shareAudioHistory(
                            InstrumentationRegistry.getTargetContext().getPackageName(), 100);
                });

        record.stop();
        record.release();
    }

    @Test
    public void testPermissionSuccess() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();

        AudioRecord record = new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                    .setSampleRate(SAMPLING_RATE_HZ)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(SAMPLING_RATE_HZ
                        * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                .setMaxSharedAudioHistoryMillis(
                    AudioRecord.getMaxSharedAudioHistoryMillis()-1)
                .build();

        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());

        record.startRecording();
        Thread.sleep(500);
        try {
            record.shareAudioHistory(
                    InstrumentationRegistry.getTargetContext().getPackageName(), 100);
        } catch (SecurityException e) {
            fail("testPermissionSuccess shareAudioHistory be allowed");
        } finally {
            record.stop();
            record.release();
            InstrumentationRegistry.getInstrumentation().getUiAutomation()
                    .dropShellPermissionIdentity();
        }
    }

    @Test
    public void testBadValues() throws Exception {
        if (!hasMicrophone()) {
            return;
        }

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .adoptShellPermissionIdentity();

        assertThrows(IllegalArgumentException.class, () -> {
                    AudioRecord.Builder builder = new AudioRecord.Builder()
                            .setMaxSharedAudioHistoryMillis(
                                    AudioRecord.getMaxSharedAudioHistoryMillis() + 1);
                });

        assertThrows(IllegalArgumentException.class, () -> {
                    AudioRecord.Builder builder = new AudioRecord.Builder()
                            .setMaxSharedAudioHistoryMillis(-1);
                });

        final AudioRecord record =
                new AudioRecord.Builder().setAudioFormat(new AudioFormat.Builder()
                        .setSampleRate(SAMPLING_RATE_HZ)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                    .setBufferSizeInBytes(SAMPLING_RATE_HZ
                            * AudioFormat.getBytesPerSample(AudioFormat.ENCODING_PCM_16BIT))
                    .setMaxSharedAudioHistoryMillis(
                            AudioRecord.getMaxSharedAudioHistoryMillis()-1)
                    .build();

        assertEquals(AudioRecord.STATE_INITIALIZED, record.getState());

        record.startRecording();
        Thread.sleep(500);

        assertThrows(NullPointerException.class, () -> {
                    record.shareAudioHistory(null /* sharedPackage */, 100 /* startFromMillis */);
                });

        assertThrows(IllegalArgumentException.class, () -> {
                    record.shareAudioHistory(
                            InstrumentationRegistry.getTargetContext().getPackageName(),
                            -1 /* startFromMillis */);
                });

        assertThrows(IllegalArgumentException.class, () -> {
                    record.shareAudioHistory(
                            InstrumentationRegistry.getTargetContext().getPackageName(),
                            10000 /* startFromMillis */);
                });

        record.stop();
        record.release();

        InstrumentationRegistry.getInstrumentation().getUiAutomation()
                .dropShellPermissionIdentity();
    }

    private boolean hasMicrophone() {
        return InstrumentationRegistry.getTargetContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MICROPHONE);
    }
}
