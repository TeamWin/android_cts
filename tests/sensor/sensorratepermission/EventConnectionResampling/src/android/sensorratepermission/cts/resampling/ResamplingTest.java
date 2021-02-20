/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.sensorratepermission.cts.resampling;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.hardware.cts.SensorTestCase;
import android.hardware.cts.helpers.SensorRatePermissionEventConnectionTestHelper;
import android.hardware.cts.helpers.SensorStats;
import android.hardware.cts.helpers.TestSensorEnvironment;
import android.hardware.cts.helpers.TestSensorEvent;
import android.hardware.cts.helpers.sensoroperations.TestSensorOperation;
import android.hardware.cts.helpers.sensorverification.EventGapVerification;
import android.hardware.cts.helpers.sensorverification.EventOrderingVerification;
import android.hardware.cts.helpers.sensorverification.EventTimestampSynchronizationVerification;
import android.hardware.cts.helpers.sensorverification.InitialValueVerification;
import android.hardware.cts.helpers.sensorverification.JitterVerification;
import android.hardware.cts.helpers.sensorverification.MagnitudeVerification;
import android.hardware.cts.helpers.sensorverification.MeanVerification;
import android.hardware.cts.helpers.sensorverification.StandardDeviationVerification;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assert;

import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Test the resampler in SensorEventConnection class.
 *
 * The resampler is to prevent the attack where an app without permission might get a sampling
 * rate higher than our capped rate, because at the time it registers a listener, there is another
 * app with the permission and requests a higher sampling rate.
 *
 * Test cases: Two apps register sensor listeners at the same time.
 * - An app targets API 31 w/o HIGH_SAMPLING_RATE_SENSORS, hence being capped.
 * - The other app targets API 25, hence not being capped
 *
 * Expected behaviors:
 * - The one that should be capped is capped
 * - The default verifications of returned sensor events meet the expectations as in other sensor
 * tests
 */
public class ResamplingTest extends SensorTestCase {
    private static final int NUM_EVENTS_COUNT = 1024;
    private static SensorRatePermissionEventConnectionTestHelper mEventConnectionTestHelper;
    private static Context mContext;
    private static TestSensorEnvironment mTestEnvironment;

    @Override
    protected void setUp() throws Exception {
        mContext = InstrumentationRegistry.getInstrumentation().getContext();
        SensorManager sensorManager = mContext.getSystemService(SensorManager.class);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (sensor == null) {
            return;
        }
        mTestEnvironment = new TestSensorEnvironment(
                mContext,
                sensor,
                SensorManager.SENSOR_DELAY_FASTEST,
                (int) TimeUnit.SECONDS.toMicros(5));
        mEventConnectionTestHelper = new SensorRatePermissionEventConnectionTestHelper(
                mTestEnvironment);
    }

    public void testResamplingEventConnections() throws Exception {
        if (mTestEnvironment == null || mEventConnectionTestHelper == null) {
            return;
        }
        // Start an app that registers a listener with high sampling rate
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(
                "android.sensorratepermission.cts.mictoggleoffapi25",
                "android.sensorratepermission.cts.mictoggleoffapi25.MainService"));
        mContext.startService(intent);

        // At the same time, register a listener and obtain its sampling rate.
        List<TestSensorEvent> events = mEventConnectionTestHelper.getSensorEvents(
                true,
                NUM_EVENTS_COUNT);
        double obtainedRate = SensorRatePermissionEventConnectionTestHelper.computeAvgRate(events,
                Long.MIN_VALUE, Long.MAX_VALUE);

        Assert.assertTrue(mEventConnectionTestHelper.errorWhenExceedCappedRate(),
                obtainedRate
                        <= SensorRatePermissionEventConnectionTestHelper.CAPPED_SAMPLE_RATE_HZ);
    }

    public void testSensorDefaultVerifications() throws Exception {
        if (mTestEnvironment == null) {
            return;
        }
        TestSensorOperation op = TestSensorOperation.createOperation(
                mTestEnvironment,
                NUM_EVENTS_COUNT);
        op.addVerification(StandardDeviationVerification.getDefault(mTestEnvironment));
        op.addVerification(EventGapVerification.getDefault(mTestEnvironment));
        op.addVerification(EventOrderingVerification.getDefault(mTestEnvironment));
        op.addVerification(JitterVerification.getDefault(mTestEnvironment));
        op.addVerification(MagnitudeVerification.getDefault(mTestEnvironment));
        op.addVerification(MeanVerification.getDefault(mTestEnvironment));
        op.addVerification(EventTimestampSynchronizationVerification.getDefault(mTestEnvironment));
        op.addVerification(InitialValueVerification.getDefault(mTestEnvironment));
        try {
            // Start an app that registers a listener with high sampling rate
            Intent intent = new Intent();
            intent.setComponent(new ComponentName(
                    "android.sensorratepermission.cts.mictoggleoffapi25",
                    "android.sensorratepermission.cts.mictoggleoffapi25.MainService"));
            mContext.startService(intent);
            op.execute(getCurrentTestNode());
        } finally {
            SensorStats stats = op.getStats();
            String fileName = String.format(
                    "single_%s_%s.txt",
                    SensorStats.getSanitizedSensorName(mTestEnvironment.getSensor()),
                    mTestEnvironment.getFrequencyString());
            stats.logToFile(mTestEnvironment.getContext(), fileName);
        }
    }
}