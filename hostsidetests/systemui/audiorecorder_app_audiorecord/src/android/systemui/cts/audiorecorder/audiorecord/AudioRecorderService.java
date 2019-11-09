/*
 * Copyright (C) 2019 The Android Open Source Project
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
 * limitations under the License
 */

package android.systemui.cts.audiorecorder.audiorecord;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.systemui.cts.audiorecorder.base.BaseAudioRecorderService;

public class AudioRecorderService extends BaseAudioRecorderService {
    private AudioRecord mAudioRecord = null;
    private int mBufferSizeInBytes;

    protected synchronized void startRecording() {
        final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        final int sampleRate = 32000;
        final int format = AudioFormat.ENCODING_PCM_16BIT;
        mBufferSizeInBytes = 2 * AudioRecord.getMinBufferSize(sampleRate, channelConfig, format);
        mAudioRecord =
                new AudioRecord.Builder()
                        .setAudioFormat(
                                new AudioFormat.Builder()
                                        .setEncoding(format)
                                        .setSampleRate(sampleRate)
                                        .setChannelMask(channelConfig)
                                        .build())
                        .setBufferSizeInBytes(mBufferSizeInBytes)
                        .build();

        mAudioRecord.startRecording();

        new Thread(this::readAudioRecordDataUntilStopped).start();
    }

    private void readAudioRecordDataUntilStopped() {
        while (true) {
            final short[] data = new short[mBufferSizeInBytes / 2];
            synchronized (this) {
                if (mAudioRecord == null) {
                    return;
                }

                mAudioRecord.read(data, 0, data.length);
            }
        }
    }

    protected synchronized void stopRecording() {
        mAudioRecord.stop();
        mAudioRecord.release();
        mAudioRecord = null;
    }

    protected synchronized boolean isRecording() {
        return mAudioRecord != null;
    }
}
