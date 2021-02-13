/*
 * Copyright 2020 The Android Open Source Project
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

package android.hardware.input.cts.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.hardware.Battery;
import android.hardware.input.InputManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.Vibrator.OnVibratorStateChangedListener;
import android.util.Log;
import android.view.InputDevice;

import com.android.cts.input.HidBatteryTestData;
import com.android.cts.input.HidDevice;
import com.android.cts.input.HidResultData;
import com.android.cts.input.HidTestData;
import com.android.cts.input.HidVibratorTestData;

import org.junit.Rule;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class InputHidTestCase extends InputTestCase {
    private static final String TAG = "InputHidTestCase";
    // Sync with linux uhid_event_type::UHID_OUTPUT
    private static final byte UHID_EVENT_TYPE_UHID_OUTPUT = 6;
    private static final long CALLBACK_TIMEOUT_MILLIS = 5000;

    private HidDevice mHidDevice;
    private int mDeviceId;
    private final int mRegisterResourceId;

    @Rule
    public MockitoRule rule = MockitoJUnit.rule();
    @Mock
    private OnVibratorStateChangedListener mListener;

    InputHidTestCase(int registerResourceId) {
        super(registerResourceId);
        mRegisterResourceId = registerResourceId;
    }

    /**
     * Get a vibrator from input device with specified Vendor Id and Product Id
     * from device registration command.
     * @return Vibrator object in specified InputDevice
     */
    private Vibrator getVibrator() {
        final InputManager inputManager =
                mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        final int[] inputDeviceIds = inputManager.getInputDeviceIds();
        final int vid = mParser.readVendorId(mRegisterResourceId);
        final int pid = mParser.readProductId(mRegisterResourceId);

        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
            Vibrator vibrator = inputDevice.getVibrator();
            if (vibrator.hasVibrator() && inputDevice.getVendorId() == vid
                    && inputDevice.getProductId() == pid) {
                return vibrator;
            }
        }
        fail("getVibrator() returns null");
        return null;
    }

    /**
     * Get a battery from input device with specified Vendor Id and Product Id
     * from device registration command.
     * @return Battery object in specified InputDevice
     */
    private Battery getBattery() {
        final InputManager inputManager =
                mInstrumentation.getTargetContext().getSystemService(InputManager.class);
        final int[] inputDeviceIds = inputManager.getInputDeviceIds();
        final int vid = mParser.readVendorId(mRegisterResourceId);
        final int pid = mParser.readProductId(mRegisterResourceId);

        for (int inputDeviceId : inputDeviceIds) {
            final InputDevice inputDevice = inputManager.getInputDevice(inputDeviceId);
            Battery battery = inputDevice.getBattery();
            if (battery.hasBattery() && inputDevice.getVendorId() == vid
                    && inputDevice.getProductId() == pid) {
                return battery;
            }
        }
        fail("getBattery() returns null");
        return null;
    }

    @Override
    protected void setUpDevice(int id, int vendorId, int productId, int sources,
            String registerCommand) {
        mDeviceId = id;
        mHidDevice = new HidDevice(mInstrumentation, id, vendorId, productId, sources,
                registerCommand);
        assertNotNull(mHidDevice);
    }

    @Override
    protected void tearDownDevice() {
        if (mHidDevice != null) {
            mHidDevice.close();
        }
    }

    @Override
    protected void testInputDeviceEvents(int resourceId) {
        List<HidTestData> tests = mParser.getHidTestData(resourceId);

        for (HidTestData testData: tests) {
            mCurrentTestCase = testData.name;

            // Send all of the HID reports
            for (int i = 0; i < testData.reports.size(); i++) {
                final String report = testData.reports.get(i);
                mHidDevice.sendHidReport(report);
            }
            verifyEvents(testData.events);

        }
    }

    private boolean verifyReportData(HidVibratorTestData test, HidResultData result) {
        for (Map.Entry<Integer, Integer> entry : test.verifyMap.entrySet()) {
            final int index = entry.getKey();
            final int value = entry.getValue();
            if ((result.reportData[index] & 0XFF) != value) {
                Log.v(TAG, "index=" + index + " value= " + value
                        + "actual= " + (result.reportData[index] & 0XFF));
                return false;
            }
        }
        final int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
        final int ffRight = result.reportData[test.rightFfIndex] & 0xFF;

        return ffLeft > 0 && ffRight > 0;
    }

    public void testInputVibratorEvents(int resourceId) {
        final List<HidVibratorTestData> tests = mParser.getHidVibratorTestData(resourceId);

        for (HidVibratorTestData test : tests) {
            assertEquals(test.durations.size(), test.amplitudes.size());
            assertTrue(test.durations.size() > 0);

            final long timeoutMills;
            final long totalVibrations = test.durations.size();
            final VibrationEffect effect;
            if (test.durations.size() == 1) {
                long duration = test.durations.get(0);
                int amplitude = test.amplitudes.get(0);
                effect = VibrationEffect.createOneShot(duration, amplitude);
                // Set timeout to be 2 times of the effect duration.
                timeoutMills = duration * 2;
            } else {
                long[] durations = test.durations.stream().mapToLong(Long::longValue).toArray();
                int[] amplitudes = test.amplitudes.stream().mapToInt(Integer::intValue).toArray();
                effect = VibrationEffect.createWaveform(
                    durations, amplitudes, -1);
                // Set timeout to be 2 times of the effect total duration.
                timeoutMills = Arrays.stream(durations).sum() * 2;
            }

            final Vibrator vibrator = getVibrator();
            assertNotNull(vibrator);
            vibrator.addVibratorStateListener(mListener);
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(false);
            reset(mListener);
            // Start vibration
            vibrator.vibrate(effect);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(true);
            assertTrue(vibrator.isVibrating());

            final long startTime = SystemClock.elapsedRealtime();
            List<HidResultData> results = new ArrayList<>();
            int vibrationCount = 0;
            // Check the vibration ffLeft and ffRight amplitude to be expected.
            while (vibrationCount < totalVibrations
                    && SystemClock.elapsedRealtime() - startTime < timeoutMills) {
                SystemClock.sleep(1000);
                try {
                    results = mHidDevice.getResults(mDeviceId, UHID_EVENT_TYPE_UHID_OUTPUT);
                    if (results.size() < totalVibrations) {
                        continue;
                    }
                    vibrationCount = 0;
                    for (int i = 0; i < results.size(); i++) {
                        HidResultData result = results.get(i);
                        if (result.deviceId == mDeviceId && verifyReportData(test, result)) {
                            int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
                            int ffRight = result.reportData[test.rightFfIndex] & 0xFF;
                            Log.v(TAG, "eventId=" + result.eventId + " reportType="
                                    + result.reportType + " left=" + ffLeft + " right=" + ffRight);
                            // Check the amplitudes of FF effect are expected.
                            if (ffLeft == test.amplitudes.get(vibrationCount)
                                    && ffRight == test.amplitudes.get(vibrationCount)) {
                                vibrationCount++;
                            }
                        }
                    }
                } catch (IOException ex) {
                    throw new RuntimeException("Could not get JSON results from HidDevice");
                }
            }
            assertEquals(vibrationCount, totalVibrations);
            // Verify vibrator state listener
            verify(mListener, timeout(CALLBACK_TIMEOUT_MILLIS)
                    .times(1)).onVibratorStateChanged(false);
            assertFalse(vibrator.isVibrating());
            vibrator.removeVibratorStateListener(mListener);
            reset(mListener);
        }
    }

    public void testInputVibratorManagerEvents(int resourceId) {
        final List<HidVibratorTestData> tests = mParser.getHidVibratorTestData(resourceId);

        for (HidVibratorTestData test : tests) {
            assertEquals(test.durations.size(), test.amplitudes.size());
            assertTrue(test.durations.size() > 0);

            final long timeoutMills;
            final long totalVibrations = test.durations.size();
            final VibrationEffect effect;
            if (test.durations.size() == 1) {
                long duration = test.durations.get(0);
                int amplitude = test.amplitudes.get(0);
                effect = VibrationEffect.createOneShot(duration, amplitude);
                // Set timeout to be 2 times of the effect duration.
                timeoutMills = duration * 2;
            } else {
                long[] durations = test.durations.stream().mapToLong(Long::longValue).toArray();
                int[] amplitudes = test.amplitudes.stream().mapToInt(Integer::intValue).toArray();
                effect = VibrationEffect.createWaveform(
                    durations, amplitudes, -1);
                // Set timeout to be 2 times of the effect total duration.
                timeoutMills = Arrays.stream(durations).sum() * 2;
            }

            final Vibrator vibrator = getVibrator();
            assertNotNull(vibrator);
            // Start vibration
            vibrator.vibrate(effect);
            final long startTime = SystemClock.elapsedRealtime();
            List<HidResultData> results = new ArrayList<>();
            int vibrationCount = 0;
            // Check the vibration ffLeft and ffRight amplitude to be expected.
            while (vibrationCount < totalVibrations
                    && SystemClock.elapsedRealtime() - startTime < timeoutMills) {
                SystemClock.sleep(1000);
                try {
                    results = mHidDevice.getResults(mDeviceId, UHID_EVENT_TYPE_UHID_OUTPUT);
                    if (results.size() < totalVibrations) {
                        continue;
                    }
                    vibrationCount = 0;
                    for (int i = 0; i < results.size(); i++) {
                        HidResultData result = results.get(i);
                        if (result.deviceId == mDeviceId && verifyReportData(test, result)) {
                            int ffLeft = result.reportData[test.leftFfIndex] & 0xFF;
                            int ffRight = result.reportData[test.rightFfIndex] & 0xFF;
                            Log.v(TAG, "eventId=" + result.eventId + " reportType="
                                    + result.reportType + " left=" + ffLeft + " right=" + ffRight);
                            // Check the amplitudes of FF effect are expected.
                            if (ffLeft == test.amplitudes.get(vibrationCount)
                                    && ffRight == test.amplitudes.get(vibrationCount)) {
                                vibrationCount++;
                            }
                        }
                    }
                } catch (IOException ex) {
                    throw new RuntimeException("Could not get JSON results from HidDevice");
                }
            }
            assertEquals(vibrationCount, totalVibrations);
        }
    }

    public void testInputBatteryEvents(int resourceId) {
        final Battery battery = getBattery();
        assertNotNull(battery);

        final List<HidBatteryTestData> tests = mParser.getHidBatteryTestData(resourceId);
        for (HidBatteryTestData testData : tests) {

            // Send all of the HID reports
            for (int i = 0; i < testData.reports.size(); i++) {
                final String report = testData.reports.get(i);
                mHidDevice.sendHidReport(report);
            }
            // Wait for power_supply sysfs node get updated.
            SystemClock.sleep(100);
            float capacity = battery.getCapacity();
            int status = battery.getStatus();
            assertEquals("Test: " + testData.name, testData.status, status);
            boolean capacityMatch = false;
            for (int i = 0; i < testData.capacities.length; i++) {
                if (capacity == testData.capacities[i]) {
                    capacityMatch = true;
                    break;
                }
            }
            assertTrue("Test: " + testData.name + " capacity " + capacity + " expect "
                    + Arrays.toString(testData.capacities), capacityMatch);
        }
    }

}
