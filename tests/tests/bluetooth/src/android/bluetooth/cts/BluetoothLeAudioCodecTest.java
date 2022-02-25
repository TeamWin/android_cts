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

package android.bluetooth.cts;

import android.bluetooth.BluetoothLeAudioCodecConfig;
import android.os.Parcel;
import android.test.AndroidTestCase;

public class BluetoothLeAudioCodecTest extends AndroidTestCase {
    private int[] mCodecTypeArray = new int[] {
        BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3,
        BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID,
    };

    private int[] mCodecPriorityArray = new int[] {
        BluetoothLeAudioCodecConfig.CODEC_PRIORITY_DISABLED,
        BluetoothLeAudioCodecConfig.CODEC_PRIORITY_DEFAULT,
        BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST
    };

    private int[] mSampleRateArray = new int[] {
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_NONE,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_8000,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_16000,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_32000,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_44100,
        BluetoothLeAudioCodecConfig.SAMPLE_RATE_48000
    };

    private int[] mBitsPerSampleArray = new int[] {
        BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_NONE,
        BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_16,
        BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24,
        BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_32
    };

    private int[] mChannelModeArray = new int[] {
        BluetoothLeAudioCodecConfig.CHANNEL_MODE_NONE,
        BluetoothLeAudioCodecConfig.CHANNEL_MODE_MONO,
        BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO
    };

    private int[] mFrameDurationArray = new int[] {
        BluetoothLeAudioCodecConfig.FRAME_DURATION_NONE,
        BluetoothLeAudioCodecConfig.FRAME_DURATION_7500,
        BluetoothLeAudioCodecConfig.FRAME_DURATION_10000
    };

    public void testGetCodecNameAndType() {
        try {
            for (int codecIdx = 0; codecIdx < mCodecTypeArray.length; codecIdx++) {
                int codecType = mCodecTypeArray[codecIdx];

                BluetoothLeAudioCodecConfig leAudioCodecConfig =
                        new BluetoothLeAudioCodecConfig.Builder()
                            .setCodecType(codecType)
                            .build();

                if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3) {
                    assertEquals("LC3", leAudioCodecConfig.getCodecName());
                }
                if (codecType == BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_INVALID) {
                    assertEquals("INVALID CODEC", leAudioCodecConfig.getCodecName());
                }

                assertEquals(codecType, leAudioCodecConfig.getCodecType());
            }
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }

    public void testGetCodecPriority() {
        for (int priorityIdx = 0; priorityIdx < mCodecPriorityArray.length; priorityIdx++) {
            int codecPriority = mCodecPriorityArray[priorityIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                        .setCodecPriority(codecPriority)
                        .build();

            assertEquals(codecPriority, leAudioCodecConfig.getCodecPriority());
        }
    }

    public void testGetSampleRate() {
        for (int sampleRateIdx = 0; sampleRateIdx < mSampleRateArray.length; sampleRateIdx++) {
            int sampleRate = mSampleRateArray[sampleRateIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                        .setSampleRate(sampleRate)
                        .build();

            assertEquals(sampleRate, leAudioCodecConfig.getSampleRate());
        }
    }

    public void testGetBitsPerSample() {
        for (int bitsPerSampleIdx = 0; bitsPerSampleIdx < mBitsPerSampleArray.length;
                bitsPerSampleIdx++) {
            int bitsPerSample = mBitsPerSampleArray[bitsPerSampleIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                        .setBitsPerSample(bitsPerSampleIdx)
                        .build();

            assertEquals(bitsPerSampleIdx, leAudioCodecConfig.getBitsPerSample());
        }
    }

    public void testGetChannelMode() {
        for (int channelModeIdx = 0; channelModeIdx < mChannelModeArray.length; channelModeIdx++) {
            int channelMode = mChannelModeArray[channelModeIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                        .setChannelMode(channelMode)
                        .build();

            assertEquals(channelMode, leAudioCodecConfig.getChannelMode());
        }
    }

    public void testGetFrameDuration() {
        for (int frameDurationIdx = 0; frameDurationIdx < mFrameDurationArray.length;
                frameDurationIdx++) {
            int frameDuration = mFrameDurationArray[frameDurationIdx];

            BluetoothLeAudioCodecConfig leAudioCodecConfig =
                    new BluetoothLeAudioCodecConfig.Builder()
                        .setFrameDuration(frameDurationIdx)
                        .build();

            assertEquals(frameDuration, leAudioCodecConfig.getFrameDuration());
        }
    }

    public void testGetOctetsPerFrame() {
        final int octetsPerFrame = 100;
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder()
                    .setOctetsPerFrame(octetsPerFrame)
                    .build();

        assertEquals(octetsPerFrame, leAudioCodecConfig.getOctetsPerFrame());
    }

    public void testDescribeContents() {
        BluetoothLeAudioCodecConfig leAudioCodecConfig =
            new BluetoothLeAudioCodecConfig.Builder().build();
        assertEquals(0, leAudioCodecConfig.describeContents());
    }

    public void testReadWriteParcel() {
        final int octetsPerFrame = 100;
        Parcel parcel = Parcel.obtain();
        BluetoothLeAudioCodecConfig leAudioCodecConfig = new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setCodecPriority(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST)
                .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000)
                .setBitsPerSample(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24)
                .setChannelMode(BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO)
                .setFrameDuration(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500)
                .setOctetsPerFrame(octetsPerFrame)
                .build();
        leAudioCodecConfig.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        BluetoothLeAudioCodecConfig leAudioCodecConfigFromParcel =
                BluetoothLeAudioCodecConfig.CREATOR.createFromParcel(parcel);
        assertEquals(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3,
                leAudioCodecConfigFromParcel.getCodecType());
        assertEquals(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST,
                leAudioCodecConfigFromParcel.getCodecPriority());
        assertEquals(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000,
                leAudioCodecConfigFromParcel.getSampleRate());
        assertEquals(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24,
                leAudioCodecConfigFromParcel.getBitsPerSample());
        assertEquals(BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO,
                leAudioCodecConfigFromParcel.getChannelMode());
        assertEquals(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500,
                leAudioCodecConfigFromParcel.getFrameDuration());
        assertEquals(octetsPerFrame, leAudioCodecConfigFromParcel.getOctetsPerFrame());
    }

    public void testBuilderWithExistingObject() {
        final int octetsPerFrame = 100;
        BluetoothLeAudioCodecConfig oriLeAudioCodecConfig =
            new BluetoothLeAudioCodecConfig.Builder()
                .setCodecType(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3)
                .setCodecPriority(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST)
                .setSampleRate(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000)
                .setBitsPerSample(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24)
                .setChannelMode(BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO)
                .setFrameDuration(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500)
                .setOctetsPerFrame(octetsPerFrame)
                .build();
        BluetoothLeAudioCodecConfig toBuilderCodecConfig =
                new BluetoothLeAudioCodecConfig.Builder(oriLeAudioCodecConfig).build();
        assertEquals(BluetoothLeAudioCodecConfig.SOURCE_CODEC_TYPE_LC3,
                toBuilderCodecConfig.getCodecType());
        assertEquals(BluetoothLeAudioCodecConfig.CODEC_PRIORITY_HIGHEST,
                toBuilderCodecConfig.getCodecPriority());
        assertEquals(BluetoothLeAudioCodecConfig.SAMPLE_RATE_24000,
                toBuilderCodecConfig.getSampleRate());
        assertEquals(BluetoothLeAudioCodecConfig.BITS_PER_SAMPLE_24,
                toBuilderCodecConfig.getBitsPerSample());
        assertEquals(BluetoothLeAudioCodecConfig.CHANNEL_MODE_STEREO,
                toBuilderCodecConfig.getChannelMode());
        assertEquals(BluetoothLeAudioCodecConfig.FRAME_DURATION_7500,
                toBuilderCodecConfig.getFrameDuration());
        assertEquals(octetsPerFrame, toBuilderCodecConfig.getOctetsPerFrame());
    }
}
