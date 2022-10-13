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

package android.voiceinteraction.cts.unittests;

import static android.voiceinteraction.common.AudioStreamHelper.FAKE_AUDIO_FORMAT;
import static android.voiceinteraction.common.AudioStreamHelper.FAKE_HOTWORD_AUDIO_DATA;
import static android.voiceinteraction.common.AudioStreamHelper.assertAudioStream;
import static android.voiceinteraction.common.AudioStreamHelper.closeAudioStreamPipe;
import static android.voiceinteraction.common.AudioStreamHelper.createFakeAudioStreamPipe;

import static com.google.common.truth.Truth.assertThat;

import android.media.AudioTimestamp;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.service.voice.HotwordAudioStream;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HotwordAudioStreamTest {

    @Test
    public void testHotwordAudioStreamParcelizeDeparcelize() throws Exception {
        final ParcelFileDescriptor[] fakeAudioStreamPipe = createFakeAudioStreamPipe(
                FAKE_HOTWORD_AUDIO_DATA);
        final AudioTimestamp timestamp = new AudioTimestamp();
        timestamp.framePosition = 0;
        timestamp.nanoTime = 1000;
        final String key = "testKey";
        final String value = "testValue";
        final PersistableBundle metadata = new PersistableBundle();
        metadata.putString(key, value);
        try {
            HotwordAudioStream audioStream = new HotwordAudioStream.Builder(FAKE_AUDIO_FORMAT,
                    fakeAudioStreamPipe[0])
                    .setTimestamp(timestamp)
                    .setMetadata(metadata)
                    .build();

            final Parcel p = Parcel.obtain();
            audioStream.writeToParcel(p, 0);
            p.setDataPosition(0);

            final HotwordAudioStream targetHotwordAudioStream =
                    HotwordAudioStream.CREATOR.createFromParcel(p);
            p.recycle();
            assertThat(targetHotwordAudioStream.getAudioFormat()).isNotNull();
            assertThat(targetHotwordAudioStream.getAudioStream()).isNotNull();
            assertThat(targetHotwordAudioStream.getAudioFormat()).isEqualTo(FAKE_AUDIO_FORMAT);
            assertAudioStream(targetHotwordAudioStream.getAudioStream(),
                    FAKE_HOTWORD_AUDIO_DATA);
            assertThat(targetHotwordAudioStream.getTimestamp().framePosition).isEqualTo(
                    timestamp.framePosition);
            assertThat(targetHotwordAudioStream.getTimestamp().nanoTime).isEqualTo(
                    timestamp.nanoTime);
            assertThat(targetHotwordAudioStream.getMetadata().size()).isEqualTo(1);
            assertThat(targetHotwordAudioStream.getMetadata().getString(key)).isEqualTo(value);
        } finally {
            closeAudioStreamPipe(fakeAudioStreamPipe);
        }
    }
}
