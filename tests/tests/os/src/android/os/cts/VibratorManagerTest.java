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

import static android.os.VibrationEffect.VibrationParameter.targetAmplitude;
import static android.os.VibrationEffect.VibrationParameter.targetFrequency;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.os.CombinedVibration;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.os.VibratorManager;
import android.os.vibrator.VibratorFrequencyProfile;
import android.util.SparseArray;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.Arrays;

@RunWith(AndroidJUnit4.class)
public class VibratorManagerTest {
    @Rule
    public ActivityTestRule<SimpleTestActivity> mActivityRule = new ActivityTestRule<>(
            SimpleTestActivity.class);

    @Rule
    public final AdoptShellPermissionsRule mAdoptShellPermissionsRule =
            new AdoptShellPermissionsRule(
                    InstrumentationRegistry.getInstrumentation().getUiAutomation(),
                    android.Manifest.permission.ACCESS_VIBRATOR_STATE);
    
    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final float TEST_TOLERANCE = 1e-5f;

    private static final float MINIMUM_ACCEPTED_MEASUREMENT_INTERVAL_FREQUENCY = 1f;
    private static final float MINIMUM_ACCEPTED_FREQUENCY = 1f;
    private static final float MAXIMUM_ACCEPTED_FREQUENCY = 1_000f;

    private static final long CALLBACK_TIMEOUT_MILLIS = 5_000;
    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();

    /**
     * These listeners are used for test helper methods like asserting it starts/stops vibrating.
     * It's not strongly required that the interactions with these mocks are validated by all tests.
     */
    private final SparseArray<OnVibratorStateChangedListener> mStateListeners = new SparseArray<>();

    private VibratorManager mVibratorManager;

    @Before
    public void setUp() {
        mVibratorManager =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        VibratorManager.class);

        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            OnVibratorStateChangedListener listener = mock(OnVibratorStateChangedListener.class);
            mVibratorManager.getVibrator(vibratorId).addVibratorStateListener(listener);
            mStateListeners.put(vibratorId, listener);
            // Adding a listener to the Vibrator should trigger the callback once with the current
            // vibrator state, so reset mocks to clear it for tests.
            assertVibratorState(false);
            clearInvocations(listener);
        }
    }

    @After
    public void cleanUp() {
        // Clearing invocations so we can use these listeners to wait for the vibrator to
        // asynchronously cancel the ongoing vibration, if any was left pending by a test.
        for (int i = 0; i < mStateListeners.size(); i++) {
            clearInvocations(mStateListeners.valueAt(i));
        }
        mVibratorManager.cancel();

        for (int i = 0; i < mStateListeners.size(); i++) {
            int vibratorId = mStateListeners.keyAt(i);

            // Wait for cancel to take effect, if device is still vibrating.
            if (mVibratorManager.getVibrator(vibratorId).isVibrating()) {
                assertStopsVibrating(vibratorId);
            }

            // Remove all listeners added by the tests.
            mVibratorManager.getVibrator(vibratorId).removeVibratorStateListener(
                    mStateListeners.valueAt(i));
        }
    }

    @Test
    public void testCancel() {
        mVibratorManager.vibrate(CombinedVibration.createParallel(
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE)));
        assertStartsVibrating();

        mVibratorManager.cancel();
        assertStopsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateOneShotStartsAndFinishesVibration() {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertStartsThenStopsVibrating(300);
    }

    @Test
    public void testVibrateOneShotMaxAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(500, 255 /* Max amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot));
        assertStartsVibrating();

        mVibratorManager.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibrateOneShotMinAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(100, 1 /* Min amplitude */);
        mVibratorManager.vibrate(CombinedVibration.createParallel(oneShot),
                VIBRATION_ATTRIBUTES);
        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateWaveformStartsAndFinishesVibration() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsThenStopsVibrating(1500);
    }

    @LargeTest
    @Test
    public void testVibrateWaveformRepeats() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibratorManager.vibrate(CombinedVibration.createParallel(waveform));
        assertStartsVibrating();

        SystemClock.sleep(2000);
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        for (int vibratorId : vibratorIds) {
            assertTrue(mVibratorManager.getVibrator(vibratorId).isVibrating());
        }

        mVibratorManager.cancel();
        assertStopsVibrating();
    }


    @LargeTest
    @Test
    public void testVibrateWaveformWithFrequencyStartsAndFinishesVibration() {
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        for (int vibratorId : vibratorIds) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            if (!vibrator.hasFrequencyControl()) {
                continue;
            }
            VibratorFrequencyProfile frequencyProfile = vibrator.getFrequencyProfile();

            float minFrequency = frequencyProfile.getMinFrequency();
            float maxFrequency = frequencyProfile.getMaxFrequency();
            float resonantFrequency = vibrator.getResonantFrequency();
            float sustainFrequency = Float.isNaN(resonantFrequency)
                    ? (maxFrequency + minFrequency) / 2
                    : resonantFrequency;

            // Then ramp to zero amplitude at fixed frequency.
            VibrationEffect waveform =
                    VibrationEffect.startWaveform(targetAmplitude(0), targetFrequency(minFrequency))
                            // Ramp from min to max frequency and from zero to max amplitude.
                            .addTransition(Duration.ofMillis(10),
                                    targetAmplitude(1), targetFrequency(maxFrequency))
                            // Ramp back to min frequency and zero amplitude.
                            .addTransition(Duration.ofMillis(10),
                                    targetAmplitude(0), targetFrequency(minFrequency))
                            // Then sustain at a fixed frequency and half amplitude.
                            .addTransition(Duration.ZERO,
                                    targetAmplitude(0.5f), targetFrequency(sustainFrequency))
                            .addSustain(Duration.ofMillis(20))
                            // Ramp from min to max frequency and at max amplitude.
                            .addTransition(Duration.ZERO,
                                    targetAmplitude(1), targetFrequency(minFrequency))
                            .addTransition(Duration.ofMillis(10), targetFrequency(maxFrequency))
                            // Ramp from max to min amplitude at max frequency.
                            .addTransition(Duration.ofMillis(10), targetAmplitude(0))
                            .build();
            vibrator.vibrate(waveform);
            assertStartsVibrating(vibratorId);
            assertStopsVibrating();
        }
    }

    @Test
    public void testVibrateSingleVibrator() {
        int[] vibratorIds = mVibratorManager.getVibratorIds();
        if (vibratorIds.length < 2) {
            return;
        }

        VibrationEffect oneShot =
                VibrationEffect.createOneShot(10_000, VibrationEffect.DEFAULT_AMPLITUDE);

        for (int vibratorId : vibratorIds) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            mVibratorManager.vibrate(
                    CombinedVibration.startParallel()
                            .addVibrator(vibratorId, oneShot)
                            .combine());
            assertStartsVibrating(vibratorId);

            for (int otherVibratorId : vibratorIds) {
                if (otherVibratorId != vibratorId) {
                    assertFalse(mVibratorManager.getVibrator(otherVibratorId).isVibrating());
                }
            }

            vibrator.cancel();
            assertStopsVibrating(vibratorId);
        }
    }

    @Test
    public void testGetVibratorIds() {
        // Just make sure it doesn't crash or return null when this is called; we don't really have
        // a way to test which vibrators will be returned.
        assertNotNull(mVibratorManager.getVibratorIds());
    }

    @Test
    public void testGetNonExistentVibratorId() {
        int missingId = Arrays.stream(mVibratorManager.getVibratorIds()).max().orElse(0) + 1;
        Vibrator vibrator = mVibratorManager.getVibrator(missingId);
        assertNotNull(vibrator);
        assertFalse(vibrator.hasVibrator());
    }

    @Test
    public void testGetDefaultVibrator() {
        Vibrator systemVibrator =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        Vibrator.class);
        assertSame(systemVibrator, mVibratorManager.getDefaultVibrator());
    }

    @Test
    public void testSingleVibratorIsPresent() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            assertNotNull(vibrator);
            assertEquals(vibratorId, vibrator.getId());
            assertTrue(vibrator.hasVibrator());
        }
    }

    @Test
    public void testSingleVibratorAmplitudeAndFrequencyControls() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            assertNotNull(vibrator);

            // Just check this method will not crash.
            vibrator.hasAmplitudeControl();

            // Single vibrators should return the frequency profile when it has frequency control.
            assertEquals(vibrator.hasFrequencyControl(),
                    vibrator.getFrequencyProfile() != null);
        }
    }

    @Test
    public void testSingleVibratorFrequencyProfile() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            VibratorFrequencyProfile frequencyProfile = vibrator.getFrequencyProfile();
            if (frequencyProfile == null) {
                continue;
            }

            float measurementIntervalHz = frequencyProfile.getMaxAmplitudeMeasurementInterval();
            assertTrue(measurementIntervalHz >= MINIMUM_ACCEPTED_MEASUREMENT_INTERVAL_FREQUENCY);

            float resonantFrequency = vibrator.getResonantFrequency();
            float minFrequencyHz = frequencyProfile.getMinFrequency();
            float maxFrequencyHz = frequencyProfile.getMaxFrequency();

            assertTrue(minFrequencyHz >= MINIMUM_ACCEPTED_FREQUENCY);
            assertTrue(maxFrequencyHz > minFrequencyHz);
            assertTrue(maxFrequencyHz <= MAXIMUM_ACCEPTED_FREQUENCY);

            if (!Float.isNaN(resonantFrequency)) {
                // If the device has a resonant frequency, then it should be within the supported
                // frequency range described by the profile.
                assertTrue(resonantFrequency >= minFrequencyHz);
                assertTrue(resonantFrequency <= maxFrequencyHz);
            }

            float[] measurements = frequencyProfile.getMaxAmplitudeMeasurements();

            // There should be at least 3 points for a valid profile.
            assertTrue(measurements.length > 2);
            assertEquals(maxFrequencyHz,
                    minFrequencyHz + ((measurements.length - 1) * measurementIntervalHz),
                    TEST_TOLERANCE);

            boolean hasPositiveMeasurement = false;
            for (float measurement : measurements) {
                assertTrue(measurement >= 0);
                assertTrue(measurement <= 1);
                hasPositiveMeasurement |= measurement > 0;
            }
            assertTrue(hasPositiveMeasurement);
        }
    }

    @Test
    public void testSingleVibratorEffectAndPrimitiveSupport() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            assertNotNull(vibrator);

            // Just check these methods return valid support arrays.
            // We don't really have a way to test if the device supports each effect or not.
            assertEquals(2, vibrator.areEffectsSupported(
                    VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK).length);
            assertEquals(2, vibrator.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK).length);
        }
    }

    @Test
    public void testSingleVibratorVibrateAndCancel() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            assertNotNull(vibrator);

            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            assertStartsVibrating(vibratorId);
            assertTrue(vibrator.isVibrating());

            vibrator.cancel();
            assertStopsVibrating(vibratorId);
        }
    }

    private void assertStartsThenStopsVibrating(long duration) {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertVibratorState(mStateListeners.keyAt(i), true);
        }
        SystemClock.sleep(duration);
        assertVibratorState(false);
    }

    private void assertStartsVibrating() {
        assertVibratorState(true);
    }

    private void assertStartsVibrating(int vibratorId) {
        assertVibratorState(vibratorId, true);
    }

    private void assertStopsVibrating() {
        assertVibratorState(false);
    }

    private void assertStopsVibrating(int vibratorId) {
        assertVibratorState(vibratorId, false);
    }

    private void assertVibratorState(boolean expected) {
        for (int i = 0; i < mStateListeners.size(); i++) {
            assertVibratorState(mStateListeners.keyAt(i), expected);
        }
    }

    private void assertVibratorState(int vibratorId, boolean expected) {
        OnVibratorStateChangedListener listener = mStateListeners.get(vibratorId);
        verify(listener, timeout(CALLBACK_TIMEOUT_MILLIS).atLeastOnce())
                .onVibratorStateChanged(eq(expected));
    }
}
