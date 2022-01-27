/*
 * Copyright (C) 2008 The Android Open Source Project
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
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.media.AudioAttributes;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.os.vibrator.VibratorFrequencyProfile;

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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;

@RunWith(AndroidJUnit4.class)
public class VibratorTest {
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

    private static final AudioAttributes AUDIO_ATTRIBUTES =
            new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();
    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;
    private static final int[] PREDEFINED_EFFECTS = new int[]{
            VibrationEffect.EFFECT_CLICK,
            VibrationEffect.EFFECT_DOUBLE_CLICK,
            VibrationEffect.EFFECT_TICK,
            VibrationEffect.EFFECT_THUD,
            VibrationEffect.EFFECT_POP,
            VibrationEffect.EFFECT_HEAVY_CLICK,
            VibrationEffect.EFFECT_TEXTURE_TICK,
    };
    private static final int[] PRIMITIVE_EFFECTS = new int[]{
            VibrationEffect.Composition.PRIMITIVE_CLICK,
            VibrationEffect.Composition.PRIMITIVE_TICK,
            VibrationEffect.Composition.PRIMITIVE_LOW_TICK,
            VibrationEffect.Composition.PRIMITIVE_QUICK_RISE,
            VibrationEffect.Composition.PRIMITIVE_QUICK_FALL,
            VibrationEffect.Composition.PRIMITIVE_SLOW_RISE,
            VibrationEffect.Composition.PRIMITIVE_SPIN,
            VibrationEffect.Composition.PRIMITIVE_THUD,
    };
    private static final int[] VIBRATION_USAGES = new int[] {
            VibrationAttributes.USAGE_UNKNOWN,
            VibrationAttributes.USAGE_ACCESSIBILITY,
            VibrationAttributes.USAGE_ALARM,
            VibrationAttributes.USAGE_COMMUNICATION_REQUEST,
            VibrationAttributes.USAGE_HARDWARE_FEEDBACK,
            VibrationAttributes.USAGE_MEDIA,
            VibrationAttributes.USAGE_NOTIFICATION,
            VibrationAttributes.USAGE_PHYSICAL_EMULATION,
            VibrationAttributes.USAGE_RINGTONE,
            VibrationAttributes.USAGE_TOUCH,
    };

    /**
     * This listener is used for test helper methods like asserting it starts/stops vibrating.
     * It's not strongly required that the interactions with this mock are validated by all tests.
     */
    @Mock
    private OnVibratorStateChangedListener mStateListener;

    private Vibrator mVibrator;
    /** Keep track of any listener created to be added to the vibrator, for cleanup purposes. */
    private List<OnVibratorStateChangedListener> mStateListenersCreated = new ArrayList<>();

    @Before
    public void setUp() {
        mVibrator = InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                Vibrator.class);

        mVibrator.addVibratorStateListener(mStateListener);
        // Adding a listener to the Vibrator should trigger the callback once with the current
        // vibrator state, so reset mocks to clear it for tests.
        assertVibratorState(false);
        clearInvocations(mStateListener);
    }

    @After
    public void cleanUp() {
        // Clearing invocations so we can use this listener to wait for the vibrator to
        // asynchronously cancel the ongoing vibration, if any was left pending by a test.
        clearInvocations(mStateListener);
        mVibrator.cancel();

        // Wait for cancel to take effect, if device is still vibrating.
        if (mVibrator.isVibrating()) {
            assertStopsVibrating();
        }

        // Remove all listeners added by the tests.
        mVibrator.removeVibratorStateListener(mStateListener);
        for (OnVibratorStateChangedListener listener : mStateListenersCreated) {
            mVibrator.removeVibratorStateListener(listener);
        }
    }

    @Test
    public void getDefaultVibrationIntensity_returnsValidIntensityForAllUsages() {
        for (int usage : VIBRATION_USAGES) {
            int intensity = mVibrator.getDefaultVibrationIntensity(usage);
            assertTrue("Error for usage " + usage + " with default intensity " + intensity,
                    (intensity >= Vibrator.VIBRATION_INTENSITY_OFF)
                            && (intensity <= Vibrator.VIBRATION_INTENSITY_HIGH));
        }

        assertEquals("Invalid usage expected to have same default as USAGE_UNKNOWN",
                mVibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_UNKNOWN),
                mVibrator.getDefaultVibrationIntensity(-1));
    }

    @Test
    public void testVibratorCancel() {
        mVibrator.vibrate(10_000);
        assertStartsVibrating();

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibratePattern() {
        long[] pattern = {100, 200, 400, 800, 1600};
        mVibrator.vibrate(pattern, 3);
        assertStartsVibrating();

        try {
            mVibrator.vibrate(pattern, 10);
            fail("Should throw ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    @Test
    public void testVibrateMultiThread() {
        new Thread(() -> {
            try {
                mVibrator.vibrate(500);
            } catch (Exception e) {
                fail("MultiThread fail1");
            }
        }).start();
        new Thread(() -> {
            try {
                // This test only get two threads to run vibrator at the same time for a functional
                // test, but it can not verify if the second thread get the precedence.
                mVibrator.vibrate(1000);
            } catch (Exception e) {
                fail("MultiThread fail2");
            }
        }).start();
        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateOneShotStartsAndFinishesVibration() {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(300, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibrator.vibrate(oneShot);
        assertStartsThenStopsVibrating(300);
    }

    @Test
    public void testVibrateOneShotMaxAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(10_000, 255 /* Max amplitude */);
        mVibrator.vibrate(oneShot);
        assertStartsVibrating();

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @Test
    public void testVibrateOneShotMinAmplitude() {
        VibrationEffect oneShot = VibrationEffect.createOneShot(300, 1 /* Min amplitude */);
        mVibrator.vibrate(oneShot, AUDIO_ATTRIBUTES);
        assertStartsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateWaveformStartsAndFinishesVibration() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibrator.vibrate(waveform);
        assertStartsThenStopsVibrating(1500);
    }

    @LargeTest
    @Test
    public void testVibrateWaveformRepeats() {
        final long[] timings = new long[] {100, 200, 300, 400, 500};
        final int[] amplitudes = new int[] {64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibrator.vibrate(waveform, AUDIO_ATTRIBUTES);
        assertStartsVibrating();

        SystemClock.sleep(2000);
        assertTrue(!mVibrator.hasVibrator() || mVibrator.isVibrating());

        mVibrator.cancel();
        assertStopsVibrating();
    }

    @LargeTest
    @Test
    public void testVibrateWaveformWithFrequencyStartsAndFinishesVibration() {
        assumeTrue(mVibrator.hasFrequencyControl());
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float minFrequency = Math.max(1f, frequencyProfile.getMinFrequency());
        float maxFrequency = frequencyProfile.getMaxFrequency();
        float resonantFrequency = mVibrator.getResonantFrequency();
        float sustainFrequency = Float.isNaN(resonantFrequency)
                ? (maxFrequency - minFrequency) / 2
                : resonantFrequency;

        // Ramp from min to max frequency and from zero to max amplitude.
        // Then ramp to a fixed frequency at max amplitude.
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
        mVibrator.vibrate(waveform);
        assertStartsThenStopsVibrating(50);
    }

    @Test
    public void testVibratePredefined() {
        int[] supported = mVibrator.areEffectsSupported(PREDEFINED_EFFECTS);
        for (int i = 0; i < PREDEFINED_EFFECTS.length; i++) {
            mVibrator.vibrate(VibrationEffect.createPredefined(PREDEFINED_EFFECTS[i]));
            if (supported[i] == Vibrator.VIBRATION_EFFECT_SUPPORT_YES) {
                assertStartsVibrating();
            }
        }
    }

    @Test
    public void testVibrateComposed() {
        boolean[] supported = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        for (int i = 0; i < PRIMITIVE_EFFECTS.length; i++) {
            mVibrator.vibrate(VibrationEffect.startComposition()
                    .addPrimitive(PRIMITIVE_EFFECTS[i])
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.5f)
                    .addPrimitive(PRIMITIVE_EFFECTS[i], 0.8f, 10)
                    .compose());
            if (supported[i]) {
                assertStartsVibrating();
            }
        }
    }

    @Test
    public void testVibrateWithAttributes() {
        mVibrator.vibrate(VibrationEffect.createOneShot(10, 10), VIBRATION_ATTRIBUTES);
        assertStartsVibrating();
    }

    @Test
    public void testGetId() {
        // The system vibrator should not be mapped to any physical vibrator and use a default id.
        assertEquals(-1, mVibrator.getId());
    }

    @Test
    public void testHasVibrator() {
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if the device has vibrator or not.
        mVibrator.hasVibrator();
    }

    @Test
    public void testVibratorHasAmplitudeControl() {
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if the amplitude control works or not.
        mVibrator.hasAmplitudeControl();
    }

    @Test
    public void testVibratorHasFrequencyControl() {
        // Just make sure it doesn't crash when this is called; we don't really have a way to test
        // if the frequency control works or not.
        mVibrator.hasFrequencyControl();
    }

    @Test
    public void testVibratorEffectsAreSupported() {
        // Just make sure it doesn't crash when this is called and that it returns all queries;
        // We don't really have a way to test if the device supports each effect or not.
        assertEquals(PREDEFINED_EFFECTS.length,
                mVibrator.areEffectsSupported(PREDEFINED_EFFECTS).length);
        assertEquals(0, mVibrator.areEffectsSupported().length);
    }

    @Test
    public void testVibratorAllEffectsAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        mVibrator.areAllEffectsSupported(PREDEFINED_EFFECTS);
        assertEquals(Vibrator.VIBRATION_EFFECT_SUPPORT_YES, mVibrator.areAllEffectsSupported());
    }

    @Test
    public void testVibratorPrimitivesAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        assertEquals(PRIMITIVE_EFFECTS.length,
                mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS).length);
        assertEquals(0, mVibrator.arePrimitivesSupported().length);
    }

    @Test
    public void testVibratorAllPrimitivesAreSupported() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device supports each effect or not.
        mVibrator.areAllPrimitivesSupported(PRIMITIVE_EFFECTS);
        assertTrue(mVibrator.areAllPrimitivesSupported());
    }

    @Test
    public void testVibratorPrimitivesDurations() {
        int[] durations = mVibrator.getPrimitiveDurations(PRIMITIVE_EFFECTS);
        boolean[] supported = mVibrator.arePrimitivesSupported(PRIMITIVE_EFFECTS);
        assertEquals(PRIMITIVE_EFFECTS.length, durations.length);
        for (int i = 0; i < durations.length; i++) {
            assertEquals("Primitive " + PRIMITIVE_EFFECTS[i]
                            + " expected to have " + (supported[i] ? "positive" : "zero")
                            + " duration, found " + durations[i] + "ms",
                    supported[i], durations[i] > 0);
        }
        assertEquals(0, mVibrator.getPrimitiveDurations().length);
    }

    @Test
    public void testVibratorResonantFrequency() {
        // Check that the resonant frequency provided is NaN, or if it's a reasonable value.
        float resonantFrequency = mVibrator.getResonantFrequency();
        assertTrue(Float.isNaN(resonantFrequency)
                || (resonantFrequency > 0 && resonantFrequency < MAXIMUM_ACCEPTED_FREQUENCY));
    }

    @Test
    public void testVibratorQFactor() {
        // Just make sure it doesn't crash when this is called;
        // We don't really have a way to test if the device provides the Q-factor or not.
        mVibrator.getQFactor();
    }

    @Test
    public void testVibratorVibratorFrequencyProfileFrequencyControl() {
        assumeNotNull(mVibrator.getFrequencyProfile());

        // If the frequency profile is present then the vibrator must have frequency control.
        // The other implication is not true if the default vibrator represents multiple vibrators.
        assertTrue(mVibrator.hasFrequencyControl());
    }

    @Test
    public void testVibratorFrequencyProfileMeasurementInterval() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float measurementIntervalHz = frequencyProfile.getMaxAmplitudeMeasurementInterval();
        assertTrue(measurementIntervalHz >= MINIMUM_ACCEPTED_MEASUREMENT_INTERVAL_FREQUENCY);
    }

    @Test
    public void testVibratorFrequencyProfileSupportedFrequencyRange() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float resonantFrequency = mVibrator.getResonantFrequency();
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
    }

    @Test
    public void testVibratorFrequencyProfileOutputAccelerationMeasurements() {
        VibratorFrequencyProfile frequencyProfile = mVibrator.getFrequencyProfile();
        assumeNotNull(frequencyProfile);

        float minFrequencyHz = frequencyProfile.getMinFrequency();
        float maxFrequencyHz = frequencyProfile.getMaxFrequency();
        float measurementIntervalHz = frequencyProfile.getMaxAmplitudeMeasurementInterval();
        float[] measurements = frequencyProfile.getMaxAmplitudeMeasurements();

        // There should be at least 3 points for a valid profile: min, center and max frequencies.
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

    @Test
    public void testVibratorIsVibrating() {
        assumeTrue(mVibrator.hasVibrator());

        assertFalse(mVibrator.isVibrating());

        mVibrator.vibrate(5000);
        assertStartsVibrating();
        assertTrue(mVibrator.isVibrating());

        mVibrator.cancel();
        assertStopsVibrating();
        assertFalse(mVibrator.isVibrating());
    }

    @LargeTest
    @Test
    public void testVibratorVibratesNoLongerThanDuration() {
        assumeTrue(mVibrator.hasVibrator());

        mVibrator.vibrate(1000);
        assertStartsVibrating();

        SystemClock.sleep(1500);
        assertFalse(mVibrator.isVibrating());
    }

    @LargeTest
    @Test
    public void testVibratorStateCallback() {
        assumeTrue(mVibrator.hasVibrator());

        OnVibratorStateChangedListener listener1 = newMockStateListener();
        OnVibratorStateChangedListener listener2 = newMockStateListener();
        // Add listener1 on executor
        mVibrator.addVibratorStateListener(Executors.newSingleThreadExecutor(), listener1);
        // Add listener2 on main thread.
        mVibrator.addVibratorStateListener(listener2);
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(false);

        mVibrator.vibrate(10);
        assertStartsVibrating();

        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(true);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(true);
        // The state changes back to false after vibration ends.
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS).times(2)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS).times(2)).onVibratorStateChanged(false);
    }

    @LargeTest
    @Test
    public void testVibratorStateCallbackRemoval() {
        assumeTrue(mVibrator.hasVibrator());

        OnVibratorStateChangedListener listener1 = newMockStateListener();
        OnVibratorStateChangedListener listener2 = newMockStateListener();
        // Add listener1 on executor
        mVibrator.addVibratorStateListener(Executors.newSingleThreadExecutor(), listener1);
        // Add listener2 on main thread.
        mVibrator.addVibratorStateListener(listener2);
        verify(listener1, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(false);
        verify(listener2, timeout(CALLBACK_TIMEOUT_MILLIS).times(1)).onVibratorStateChanged(false);

        // Remove listener1 & listener2
        mVibrator.removeVibratorStateListener(listener1);
        mVibrator.removeVibratorStateListener(listener2);

        mVibrator.vibrate(1000);
        assertStartsVibrating();

        // Wait the timeout to assert there was no more interactions with the removed listeners.
        verify(listener1, after(CALLBACK_TIMEOUT_MILLIS).never()).onVibratorStateChanged(true);
        // Previous call was blocking, so no need to wait for a timeout here as well.
        verify(listener2, never()).onVibratorStateChanged(true);
    }

    private OnVibratorStateChangedListener newMockStateListener() {
        OnVibratorStateChangedListener listener = mock(OnVibratorStateChangedListener.class);
        mStateListenersCreated.add(listener);
        return listener;
    }

    private void assertStartsThenStopsVibrating(long duration) {
        if (mVibrator.hasVibrator()) {
            assertVibratorState(true);
            SystemClock.sleep(duration);
            assertVibratorState(false);
        }
    }

    private void assertStartsVibrating() {
        assertVibratorState(true);
    }

    private void assertStopsVibrating() {
        assertVibratorState(false);
    }

    private void assertVibratorState(boolean expected) {
        if (mVibrator.hasVibrator()) {
            verify(mStateListener, timeout(CALLBACK_TIMEOUT_MILLIS).atLeastOnce())
                    .onVibratorStateChanged(eq(expected));
        }
    }
}
