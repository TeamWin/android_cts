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
import static android.voiceinteraction.common.Utils.FAKE_HOTWORD_TRAINING_DATA_TIMEOUT_STAGE;
import static com.google.common.truth.Truth.assertThat;
import static org.testng.Assert.assertThrows;

import android.os.Parcel;
import android.service.voice.HotwordTrainingAudio;
import android.service.voice.HotwordTrainingData;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HotwordTrainingDataTest {
    @Test
    public void testHotwordTrainingData_whenExceedsSizeLimit_shouldThrowException()
            throws Exception {
        // Create hotword training audio with more than 1MB of data.
        HotwordTrainingAudio hotwordTrainingAudio =
                new HotwordTrainingAudio.Builder(new byte[1024 * 1024 * 2],
                        FAKE_AUDIO_FORMAT).build();

        assertThrows(
                IllegalArgumentException.class,
                () -> new HotwordTrainingData.Builder().addTrainingAudio(
                        hotwordTrainingAudio).build());
    }

    @Test
    public void testHotwordTrainingDataBuilder() throws Exception {
        final HotwordTrainingData hotwordTrainingData = constructSimpleHotwordTrainingData();

        assertHotwordTrainingData(hotwordTrainingData);
    }

    @Test
    public void testHotwordTrainingDataParceling() throws Exception {
        final HotwordTrainingData hotwordTrainingData = constructSimpleHotwordTrainingData();

        final Parcel p = Parcel.obtain();
        hotwordTrainingData.writeToParcel(p, 0);
        p.setDataPosition(0);

        final HotwordTrainingData targetHotwordTrainingData =
                HotwordTrainingData.CREATOR.createFromParcel(p);
        p.recycle();

        assertHotwordTrainingData(targetHotwordTrainingData);
    }

    @Test
    public void testHotwordTrainingDataHashCode() throws Exception {
        final HotwordTrainingData hotwordTrainingData1 = constructSimpleHotwordTrainingData();
        final HotwordTrainingData hotwordTrainingData2 = constructSimpleHotwordTrainingData();

        assertThat(hotwordTrainingData1.hashCode()).isEqualTo(hotwordTrainingData2.hashCode());
    }

    @Test
    public void testHotwordTrainingDataEquals() throws Exception {
        final HotwordTrainingData hotwordTrainingData1 = constructSimpleHotwordTrainingData();
        final HotwordTrainingData hotwordTrainingData2 = constructSimpleHotwordTrainingData();

        assertThat(hotwordTrainingData1).isEqualTo(hotwordTrainingData2);
    }

    private HotwordTrainingData constructSimpleHotwordTrainingData() {
        final HotwordTrainingAudio hotwordTrainingAudio =
                new HotwordTrainingAudio.Builder(FAKE_INITIAL_AUDIO_DATA,
                        FAKE_AUDIO_FORMAT).build();
        return new HotwordTrainingData.Builder()
                .addTrainingAudio(hotwordTrainingAudio)
                .setTimeoutStage(FAKE_HOTWORD_TRAINING_DATA_TIMEOUT_STAGE)
                .build();
    }

    private void assertHotwordTrainingData(HotwordTrainingData hotwordTrainingData) {
        assertThat(hotwordTrainingData.getTimeoutStage()).isEqualTo(
                FAKE_HOTWORD_TRAINING_DATA_TIMEOUT_STAGE);
        for (HotwordTrainingAudio hotwordTrainingAudio :
                hotwordTrainingData.getTrainingAudioList()) {
            assertHotwordTrainingAudio(hotwordTrainingAudio);
        }
    }

    private void assertHotwordTrainingAudio(HotwordTrainingAudio hotwordTrainingAudio) {
        assertThat(hotwordTrainingAudio.getHotwordAudio()).isEqualTo(FAKE_INITIAL_AUDIO_DATA);
        assertThat(hotwordTrainingAudio.getAudioFormat()).isEqualTo(FAKE_AUDIO_FORMAT);
    }
}
