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

package android.os.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import android.os.CombinedVibrationEffect;
import android.os.Parcel;
import android.os.VibrationEffect;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class CombinedVibrationEffectTest {

    private static final VibrationEffect TEST_EFFECT =
            VibrationEffect.get(VibrationEffect.EFFECT_CLICK);

    private static final CombinedVibrationEffect TEST_MONO =
            CombinedVibrationEffect.createSynced(TEST_EFFECT);
    private static final CombinedVibrationEffect TEST_STEREO =
            CombinedVibrationEffect.startSynced()
                    .addVibrator(1, TEST_EFFECT)
                    .addVibrator(2, TEST_EFFECT)
                    .combine();
    private static final CombinedVibrationEffect TEST_SEQUENTIAL =
            CombinedVibrationEffect.startSequential()
                    .addNext(TEST_MONO)
                    .addNext(1, TEST_EFFECT, /* delay= */ 100)
                    .combine();

    @Test
    public void testCreateSynced() {
        CombinedVibrationEffect.Mono synced =
                (CombinedVibrationEffect.Mono) CombinedVibrationEffect.createSynced(TEST_EFFECT);
        assertEquals(TEST_EFFECT, synced.getEffect());
        assertEquals(TEST_EFFECT.getDuration(), synced.getDuration());
    }

    @Test
    public void testStartSynced() {
        CombinedVibrationEffect.Stereo synced =
                (CombinedVibrationEffect.Stereo) CombinedVibrationEffect.startSynced()
                        .addVibrator(1, TEST_EFFECT)
                        .combine();
        assertEquals(1, synced.getEffects().size());
        assertEquals(TEST_EFFECT, synced.getEffects().get(1));
        assertEquals(TEST_EFFECT.getDuration(), synced.getDuration());
    }

    @Test
    public void testStartSyncedEmptyCombinationIsInvalid() {
        try {
            CombinedVibrationEffect.startSynced().combine();
            fail("Illegal combination, should throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testSyncedEquals() {
        CombinedVibrationEffect otherMono = CombinedVibrationEffect.createSynced(
                VibrationEffect.get(VibrationEffect.EFFECT_CLICK));
        assertEquals(TEST_MONO, otherMono);
        assertEquals(TEST_MONO.hashCode(), otherMono.hashCode());

        CombinedVibrationEffect otherStereo = CombinedVibrationEffect.startSynced()
                .addVibrator(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .combine();
        assertEquals(TEST_STEREO, otherStereo);
        assertEquals(TEST_STEREO.hashCode(), otherStereo.hashCode());
    }

    @Test
    public void testSyncedNotEqualsDifferentEffect() {
        CombinedVibrationEffect otherMono = CombinedVibrationEffect.createSynced(
                VibrationEffect.get(VibrationEffect.EFFECT_TICK));
        assertNotEquals(TEST_MONO, otherMono);
    }

    @Test
    public void testSyncedNotEqualsDifferentVibrators() {
        CombinedVibrationEffect otherStereo = CombinedVibrationEffect.startSynced()
                .addVibrator(5, TEST_EFFECT)
                .combine();
        assertNotEquals(TEST_STEREO, otherStereo);
    }

    @Test
    public void testCreateSequential() {
        CombinedVibrationEffect.Sequential sequential =
                (CombinedVibrationEffect.Sequential) CombinedVibrationEffect.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(TEST_STEREO, /* delay= */ 100)
                        .addNext(1, TEST_EFFECT)
                        .combine();
        assertEquals(
                Arrays.asList(TEST_MONO, TEST_STEREO,
                        CombinedVibrationEffect.startSynced().addVibrator(1,
                                TEST_EFFECT).combine()),
                sequential.getEffects());
        assertEquals(-1, sequential.getDuration());
    }

    @Test
    public void testStartSequentialEmptyCombinationIsInvalid() {
        try {
            CombinedVibrationEffect.startSequential().combine();
            fail("Illegal combination, should throw IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    @Test
    public void testSequentialEquals() {
        CombinedVibrationEffect otherSequential =
                CombinedVibrationEffect.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(1, TEST_EFFECT, /* delay= */ 100)
                        .combine();
        assertEquals(TEST_SEQUENTIAL, otherSequential);
        assertEquals(TEST_SEQUENTIAL.hashCode(), otherSequential.hashCode());
    }

    @Test
    public void testSequentialNotEqualsDifferentEffects() {
        CombinedVibrationEffect otherSequential =
                CombinedVibrationEffect.startSequential()
                        .addNext(TEST_STEREO)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentOrder() {
        CombinedVibrationEffect otherSequential =
                CombinedVibrationEffect.startSequential()
                        .addNext(1, TEST_EFFECT, /* delay= */ 100)
                        .addNext(TEST_MONO)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentDelays() {
        CombinedVibrationEffect otherSequential =
                CombinedVibrationEffect.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(1, TEST_EFFECT, /* delay= */ 1)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testSequentialNotEqualsDifferentVibrator() {
        CombinedVibrationEffect otherSequential =
                CombinedVibrationEffect.startSequential()
                        .addNext(TEST_MONO)
                        .addNext(5, TEST_EFFECT, /* delay= */ 100)
                        .combine();
        assertNotEquals(TEST_SEQUENTIAL, otherSequential);
    }

    @Test
    public void testParcelingSyncedMono() {
        Parcel p = Parcel.obtain();
        TEST_MONO.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibrationEffect parceled = CombinedVibrationEffect.CREATOR.createFromParcel(p);
        assertEquals(TEST_MONO, parceled);
    }

    @Test
    public void testParcelingSyncedStereo() {
        Parcel p = Parcel.obtain();
        TEST_STEREO.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibrationEffect parceled = CombinedVibrationEffect.CREATOR.createFromParcel(p);
        assertEquals(TEST_STEREO, parceled);
    }

    @Test
    public void testParcelingSequential() {
        Parcel p = Parcel.obtain();
        TEST_SEQUENTIAL.writeToParcel(p, 0);
        p.setDataPosition(0);
        CombinedVibrationEffect parceled = CombinedVibrationEffect.CREATOR.createFromParcel(p);
        assertEquals(TEST_SEQUENTIAL, parceled);
    }

    @Test
    public void testDescribeContents() {
        TEST_MONO.describeContents();
        TEST_STEREO.describeContents();
        TEST_SEQUENTIAL.describeContents();
    }

    @Test
    public void testToString() {
        TEST_MONO.toString();
        TEST_STEREO.toString();
        TEST_SEQUENTIAL.toString();
    }

    @Test
    public void testSyncedMonoCombinationDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.createSynced(
                VibrationEffect.createOneShot(100, 100));
        assertEquals(100, effect.getDuration());
    }

    @Test
    public void testSyncedStereoCombinationDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSynced()
                .addVibrator(1, VibrationEffect.createOneShot(1, 100))
                .addVibrator(2, VibrationEffect.createOneShot(100, 100))
                .addVibrator(3, VibrationEffect.createOneShot(10, 100))
                .combine();
        assertEquals(100, effect.getDuration());
    }

    @Test
    public void testSyncedCombinationUnknownDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSynced()
                .addVibrator(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(2, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(-1, effect.getDuration());
    }

    @Test
    public void testSyncedCombinationRepeatingDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSynced()
                .addVibrator(1, VibrationEffect.createWaveform(new long[]{1}, new int[]{1}, 0))
                .addVibrator(2, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addVibrator(3, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(Long.MAX_VALUE, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSequential()
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .addNext(1, VibrationEffect.createOneShot(10, 100), /* delay= */ 1)
                .combine();
        assertEquals(33, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationUnknownDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSequential()
                .addNext(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addNext(1, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(-1, effect.getDuration());
    }

    @Test
    public void testSequentialCombinationRepeatingDuration() {
        CombinedVibrationEffect effect = CombinedVibrationEffect.startSequential()
                .addNext(1, VibrationEffect.createWaveform(new long[]{1}, new int[]{1}, 0))
                .addNext(1, VibrationEffect.get(VibrationEffect.EFFECT_CLICK))
                .addNext(1, VibrationEffect.createOneShot(100, 100))
                .combine();
        assertEquals(Long.MAX_VALUE, effect.getDuration());
    }
}
