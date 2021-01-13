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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.os.CombinedVibrationEffect;
import android.os.SystemClock;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.os.VibratorManager;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(AndroidJUnit4.class)
@LargeTest
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

    private static final VibrationAttributes VIBRATION_ATTRIBUTES =
            new VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_TOUCH)
                    .build();
    private static final long VIBRATION_TIMEOUT_MILLIS = 200;

    private VibratorManager mVibratorManager;
    @Mock
    private OnVibratorStateChangedListener mListener1;
    @Mock
    private OnVibratorStateChangedListener mListener2;

    @Before
    public void setUp() {
        mVibratorManager =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        VibratorManager.class);
    }

    @Test
    public void testCancel() {
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(
                VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE)));
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);

        mVibratorManager.cancel();
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::noneVibrating);
    }

    @Test
    public void testVibrateOneShot() {
        VibrationEffect oneShot =
                VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE);
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(oneShot));
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);

        SystemClock.sleep(150);
        assertTrue(noneVibrating());

        oneShot = VibrationEffect.createOneShot(500, 255 /* Max amplitude */);
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(oneShot));
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);

        mVibratorManager.cancel();
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::noneVibrating);

        oneShot = VibrationEffect.createOneShot(100, 1 /* Min amplitude */);
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(oneShot),
                VIBRATION_ATTRIBUTES);
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);
    }

    @Test
    public void testVibrateWaveform() {
        final long[] timings = new long[]{100, 200, 300, 400, 500};
        final int[] amplitudes = new int[]{64, 128, 255, 128, 64};
        VibrationEffect waveform = VibrationEffect.createWaveform(timings, amplitudes, -1);
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(waveform));
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);

        SystemClock.sleep(1500);
        assertTrue(noneVibrating());

        waveform = VibrationEffect.createWaveform(timings, amplitudes, 0);
        mVibratorManager.vibrate(CombinedVibrationEffect.createSynced(waveform));
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::allVibrating);

        SystemClock.sleep(2000);
        assertTrue(allVibrating());

        mVibratorManager.cancel();
        PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, this::noneVibrating);
    }

    @Test
    public void testGetVibratorIds() {
        // Just make sure it doesn't crash or return null when this is called; we don't really have
        // a way to test which vibrators will be returned.
        assertNotNull(mVibratorManager.getVibratorIds());
    }

    @Test
    public void testGetDefaultVibrator() {
        Vibrator systemVibrator =
                InstrumentationRegistry.getInstrumentation().getContext().getSystemService(
                        Vibrator.class);
        assertSame(systemVibrator, mVibratorManager.getDefaultVibrator());
    }

    @Test
    public void testVibrator() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            Vibrator vibrator = mVibratorManager.getVibrator(vibratorId);
            assertNotNull(vibrator);
            assertEquals(vibratorId, vibrator.getId());
            assertTrue(vibrator.hasVibrator());

            // Just check these methods will not crash.
            // We don't really have a way to test if the device supports each effect or not.
            vibrator.hasAmplitudeControl();

            // Just check these methods return valid support arrays.
            // We don't really have a way to test if the device supports each effect or not.
            assertEquals(2, vibrator.areEffectsSupported(
                    VibrationEffect.EFFECT_TICK, VibrationEffect.EFFECT_CLICK).length);
            assertEquals(2, vibrator.arePrimitivesSupported(
                    VibrationEffect.Composition.PRIMITIVE_CLICK,
                    VibrationEffect.Composition.PRIMITIVE_TICK).length);

            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE));
            PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, () -> vibrator.isVibrating());

            vibrator.cancel();
            PollingCheck.waitFor(VIBRATION_TIMEOUT_MILLIS, () -> !vibrator.isVibrating());
        }
    }

    private boolean allVibrating() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            if (!mVibratorManager.getVibrator(vibratorId).isVibrating()) {
                return false;
            }
        }
        return true;
    }

    private boolean noneVibrating() {
        for (int vibratorId : mVibratorManager.getVibratorIds()) {
            if (mVibratorManager.getVibrator(vibratorId).isVibrating()) {
                return false;
            }
        }
        return true;
    }
}
