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

package android.voiceinteraction.cts;

import static com.google.common.truth.Truth.assertThat;

import android.media.MediaSyncEvent;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.service.voice.HotwordDetectedResult;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class HotwordDetectedResultTest {

    @Test
    public void testHotwordDetectedResult_getMaxBundleSize() throws Exception {
        assertThat(HotwordDetectedResult.getMaxBundleSize()).isEqualTo(50);
    }

    @Test
    public void testHotwordDetectedResult_getMaxHotwordPhraseId() throws Exception {
        assertThat(HotwordDetectedResult.getMaxHotwordPhraseId()).isEqualTo(63);
    }

    @Test
    public void testHotwordDetectedResult_getMaxScore() throws Exception {
        assertThat(HotwordDetectedResult.getMaxScore()).isEqualTo(255);
    }

    @Test
    public void testHotwordDetectedResultBuilder() throws Exception {
        final HotwordDetectedResult hotwordDetectedResult =
                buildHotwordDetectedResult(
                        HotwordDetectedResult.CONFIDENCE_LEVEL_LOW,
                        MediaSyncEvent.createEvent(MediaSyncEvent.SYNC_EVENT_PRESENTATION_COMPLETE),
                        /* hotwordOffsetMillis= */ 100,
                        /* hotwordDurationMillis= */ 1000,
                        /* audioChannel= */ 1,
                        /* hotwordDetectionPersonalized= */ true,
                        /* score= */ 100,
                        /* personalizedScore= */ 100,
                        /* hotwordPhraseId= */ 1,
                        new PersistableBundle());

        assertHotwordDetectedResult(hotwordDetectedResult);
    }

    @Test
    public void testHotwordDetectedResultParcelizeDeparcelize() throws Exception {
        final HotwordDetectedResult hotwordDetectedResult =
                buildHotwordDetectedResult(
                        HotwordDetectedResult.CONFIDENCE_LEVEL_LOW,
                        MediaSyncEvent.createEvent(MediaSyncEvent.SYNC_EVENT_PRESENTATION_COMPLETE),
                        /* hotwordOffsetMillis= */ 100,
                        /* hotwordDurationMillis= */ 1000,
                        /* audioChannel= */ 1,
                        /* hotwordDetectionPersonalized= */ true,
                        /* score= */ 100,
                        /* personalizedScore= */ 100,
                        /* hotwordPhraseId= */ 1,
                        new PersistableBundle());

        final Parcel p = Parcel.obtain();
        hotwordDetectedResult.writeToParcel(p, 0);
        p.setDataPosition(0);

        final HotwordDetectedResult targetHotwordDetectedResult =
                HotwordDetectedResult.CREATOR.createFromParcel(p);
        p.recycle();

        assertHotwordDetectedResult(targetHotwordDetectedResult);
    }

    private HotwordDetectedResult buildHotwordDetectedResult(
            int confidenceLevel,
            MediaSyncEvent mediaSyncEvent,
            int hotwordOffsetMillis,
            int hotwordDurationMillis,
            int audioChannel,
            boolean hotwordDetectionPersonalized,
            int score,
            int personalizedScore,
            int hotwordPhraseId,
            PersistableBundle extras) {
        return new HotwordDetectedResult.Builder()
                .setConfidenceLevel(confidenceLevel)
                .setMediaSyncEvent(mediaSyncEvent)
                .setHotwordOffsetMillis(hotwordOffsetMillis)
                .setHotwordDurationMillis(hotwordDurationMillis)
                .setAudioChannel(audioChannel)
                .setHotwordDetectionPersonalized(hotwordDetectionPersonalized)
                .setScore(score)
                .setPersonalizedScore(personalizedScore)
                .setHotwordPhraseId(hotwordPhraseId)
                .setExtras(extras)
                .build();
    }

    private void assertHotwordDetectedResult(HotwordDetectedResult hotwordDetectedResult) {
        assertThat(hotwordDetectedResult.getConfidenceLevel()).isEqualTo(
                HotwordDetectedResult.CONFIDENCE_LEVEL_LOW);
        assertThat(hotwordDetectedResult.getMediaSyncEvent()).isNotNull();
        assertThat(hotwordDetectedResult.getHotwordOffsetMillis()).isEqualTo(100);
        assertThat(hotwordDetectedResult.getHotwordDurationMillis()).isEqualTo(1000);
        assertThat(hotwordDetectedResult.getAudioChannel()).isEqualTo(1);
        assertThat(hotwordDetectedResult.isHotwordDetectionPersonalized()).isTrue();
        assertThat(hotwordDetectedResult.getScore()).isEqualTo(100);
        assertThat(hotwordDetectedResult.getPersonalizedScore()).isEqualTo(100);
        assertThat(hotwordDetectedResult.getHotwordPhraseId()).isEqualTo(1);
        assertThat(hotwordDetectedResult.getExtras()).isNotNull();
    }
}
