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

package android.voiceinteraction.cts.unittests;

import static android.voiceinteraction.common.AudioStreamHelper.FAKE_AUDIO_FORMAT;
import static android.voiceinteraction.common.AudioStreamHelper.FAKE_INITIAL_AUDIO_DATA;
import static android.voiceinteraction.common.Utils.FAKE_HOTWORD_OFFSET_MILLIS;
import static android.voiceinteraction.common.Utils.FAKE_HOTWORD_TRAINING_AUDIO_TYPE;

import static com.google.common.truth.Truth.assertThat;

import android.os.Parcel;
import android.service.voice.HotwordTrainingAudio;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HotwordTrainingAudioTest {
    @Test
    public void testHotwordTrainingAudioBuilder() throws Exception {
        final HotwordTrainingAudio hotwordTrainingAudio = constructHotwordTrainingAudio();

        assertHotwordTrainingAudio(hotwordTrainingAudio);
    }

    @Test
    public void testHotwordTrainingAudioParceling() throws Exception {
        final HotwordTrainingAudio hotwordTrainingAudio = constructHotwordTrainingAudio();

        final Parcel p = Parcel.obtain();
        hotwordTrainingAudio.writeToParcel(p, 0);
        p.setDataPosition(0);

        final HotwordTrainingAudio targetHotwordTrainingAudio =
                HotwordTrainingAudio.CREATOR.createFromParcel(p);
        p.recycle();

        assertHotwordTrainingAudio(targetHotwordTrainingAudio);
    }

    @Test
    public void testHotwordTrainingAudioHashCode() throws Exception {
        final HotwordTrainingAudio hotwordTrainingAudio1 = constructHotwordTrainingAudio();
        final HotwordTrainingAudio hotwordTrainingAudio2 = constructHotwordTrainingAudio();

        assertThat(hotwordTrainingAudio1.hashCode()).isEqualTo(hotwordTrainingAudio2.hashCode());
    }

    @Test
    public void testHotwordTrainingAudioEquals() throws Exception {
        final HotwordTrainingAudio hotwordTrainingAudio1 = constructHotwordTrainingAudio();
        final HotwordTrainingAudio hotwordTrainingAudio2 = constructHotwordTrainingAudio();

        assertThat(hotwordTrainingAudio1).isEqualTo(hotwordTrainingAudio2);
    }

    private HotwordTrainingAudio constructHotwordTrainingAudio() {
        return new HotwordTrainingAudio.Builder(FAKE_INITIAL_AUDIO_DATA, FAKE_AUDIO_FORMAT)
                .setAudioType(FAKE_HOTWORD_TRAINING_AUDIO_TYPE)
                .setHotwordOffsetMillis(FAKE_HOTWORD_OFFSET_MILLIS)
                .build();
    }

    private void assertHotwordTrainingAudio(HotwordTrainingAudio hotwordTrainingAudio) {
        assertThat(hotwordTrainingAudio.getHotwordAudio()).isEqualTo(FAKE_INITIAL_AUDIO_DATA);
        assertThat(hotwordTrainingAudio.getAudioFormat()).isEqualTo(FAKE_AUDIO_FORMAT);
        assertThat(hotwordTrainingAudio.getHotwordOffsetMillis()).isEqualTo(
                FAKE_HOTWORD_OFFSET_MILLIS);
    }
}
