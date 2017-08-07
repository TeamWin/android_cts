/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.hardware.cts;

import android.content.Context;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorAdditionalInfo;
import android.hardware.SensorDirectChannel;
import android.hardware.SensorEventCallback;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.cts.helpers.SensorCtsHelper;
import android.hardware.cts.helpers.SensorCtsHelper.TestResultCollector;
import android.os.MemoryFile;
import android.os.SystemClock;
import android.util.Log;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Checks Sensor Direct Report functionality
 *
 * This testcase tests operation of:
 *   - SensorManager.createDirectChannel()
 *   - SensorDirectChannel.*
 *   - Sensor.getHighestDirectReportRateLevel()
 *   - Sensor.isDirectChannelTypeSupported()
 *
 * Tests:
 *   - test<Sensor><SharedMemoryType><RateLevel>
 *     tests basic operation of sensor in direct report mode at various rate level specification.
 *   - testRateIndependency<Sensor1><Sensor2>SingleChannel
 *     tests two sensors in the same direct channel are able to run at different rate.
 *   - testRateIndependency<Sensor>MultiChannel
 *     tests a sensor is able to be configured to different rate level for multiple channels.
 *   - testRateIndependency<Sensor>MultiMode
 */
public class SensorDirectReportTest extends SensorTestCase {
    private static final String TAG = "SensorDirectReportTest";
    // nominal rates of each rate level supported
    private static final float RATE_NORMAL_NOMINAL = 50;
    private static final float RATE_FAST_NOMINAL = 200;
    private static final float RATE_VERY_FAST_NOMINAL = 800;

    // actuall value is allowed to be 55% to 220% of nominal value
    private static final float FREQ_LOWER_BOUND = 0.55f;
    private static final float FREQ_UPPER_BOUND = 2.2f;

    // actuall value is allowed to be 90% to 200% of nominal value in poll() interface
    private static final float FREQ_LOWER_BOUND_POLL = 0.90f;
    private static final float FREQ_UPPER_BOUND_POLL = 2.00f;

    // sensor reading assumption
    private static final float GRAVITY_MIN = 9.81f - 0.5f;
    private static final float GRAVITY_MAX = 9.81f + 0.5f;
    private static final float GYRO_NORM_MAX = 0.1f;

    // test constants
    private static final int REST_PERIOD_BEFORE_TEST_MILLISEC = 3000;
    private static final int TEST_RUN_TIME_PERIOD_MILLISEC = 5000;
    private static final int ALLOWED_SENSOR_INIT_TIME_MILLISEC = 500;
    private static final int SENSORS_EVENT_SIZE = 104;
    private static final int ATOMIC_COUNTER_OFFSET = 12;
    private static final int ATOMIC_COUNTER_SIZE = 4;
    private static final int SENSORS_EVENT_COUNT = 10240; // 800Hz * 2.2 * 5 sec + extra
    private static final int SHARED_MEMORY_SIZE = SENSORS_EVENT_COUNT * SENSORS_EVENT_SIZE;
    private static final float MERCY_FACTOR = 0.1f;
    private static final boolean CHECK_ABSOLUTE_LATENCY = false;

    // list of rate levels being tested
    private static final int[] POSSIBLE_RATE_LEVELS = new int[] {
            SensorDirectChannel.RATE_NORMAL,
            SensorDirectChannel.RATE_FAST,
            SensorDirectChannel.RATE_VERY_FAST
        };

    // list of channel types being tested
    private static final int[] POSSIBLE_CHANNEL_TYPES = new int [] {
            SensorDirectChannel.TYPE_MEMORY_FILE,
            SensorDirectChannel.TYPE_HARDWARE_BUFFER
        };

    // list of sensor types being tested
    private static final int[] POSSIBLE_SENSOR_TYPES = new int [] {
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_GYROSCOPE,
            Sensor.TYPE_MAGNETIC_FIELD
        };

    // list of sampling period being tested
    private static final int[] POSSIBLE_SAMPLE_PERIOD_US = new int [] {
            200_000, // Normal 5 Hz
            66_667,  // UI    15 Hz
            20_000,  // Game  50 Hz
            5_000,   // 200Hz
            0        // fastest
        };

    private static final ByteOrder NATIVE_BYTE_ORDER = ByteOrder.nativeOrder();

    private static native boolean nativeReadHardwareBuffer(HardwareBuffer hardwareBuffer,
            byte[] buffer, int srcOffset, int destOffset, int count);

    private boolean mNeedMemoryFile;
    private MemoryFile mMemoryFile;
    private MemoryFile mMemoryFileSecondary;
    private boolean mNeedHardwareBuffer;
    private HardwareBuffer mHardwareBuffer;
    private HardwareBuffer mHardwareBufferSecondary;
    private ByteBuffer mByteBuffer;
    private byte[] mBuffer;

    private SensorManager mSensorManager;
    private SensorDirectChannel mChannel;
    private SensorDirectChannel mChannelSecondary;

    private EventPool mEventPool;

    static {
        System.loadLibrary("cts-sensors-ndk-jni");
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mByteBuffer = ByteBuffer.allocate(SHARED_MEMORY_SIZE);
        mBuffer = mByteBuffer.array();
        mByteBuffer.order(ByteOrder.nativeOrder());

        mEventPool = new EventPool(10 * SENSORS_EVENT_COUNT);
        mSensorManager = (SensorManager) getContext().getSystemService(Context.SENSOR_SERVICE);

        mNeedMemoryFile = isMemoryTypeNeeded(SensorDirectChannel.TYPE_MEMORY_FILE);
        mNeedHardwareBuffer = isMemoryTypeNeeded(SensorDirectChannel.TYPE_HARDWARE_BUFFER);

        allocateSharedMemory();
    }

    @Override
    protected void tearDown() throws Exception {
        if (mChannel != null) {
            mChannel.close();
            mChannel = null;
        }

        if (mChannelSecondary != null) {
            mChannelSecondary.close();
            mChannelSecondary = null;
        }

        freeSharedMemory();
        super.tearDown();
    }

    public void testSharedMemoryAllocation() throws AssertionError {
        assertTrue("allocating MemoryFile returned null: "
                        + (mMemoryFile == null) + ", " + (mMemoryFileSecondary == null),
                   !mNeedMemoryFile || (mMemoryFile != null && mMemoryFileSecondary != null));
        assertTrue("allocating HardwareBuffer returned null: "
                        + (mHardwareBuffer == null) + ", " + (mHardwareBufferSecondary == null),
                   !mNeedHardwareBuffer ||
                       (mHardwareBuffer != null && mHardwareBufferSecondary != null));
    }

    public void testAccelerometerAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testGyroscopeAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testMagneticFieldAshmemNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testAccelerometerAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);

    }

    public void testGyroscopeAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);
    }

    public void testMagneticFieldAshmemFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_FAST);
    }

    public void testAccelerometerAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);

    }

    public void testGyroscopeAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testMagneticFieldAshmemVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_MEMORY_FILE,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testAccelerometerHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testGyroscopeHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testMagneticFieldHardwareBufferNormal() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_NORMAL);
    }

    public void testAccelerometerHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testGyroscopeHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testMagneticFieldHardwareBufferFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_FAST);
    }

    public void testAccelerometerHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_ACCELEROMETER,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testGyroscopeHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_GYROSCOPE,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testMagneticFieldHardwareBufferVeryFast() {
        runSensorDirectReportTest(
                Sensor.TYPE_MAGNETIC_FIELD,
                SensorDirectChannel.TYPE_HARDWARE_BUFFER,
                SensorDirectChannel.RATE_VERY_FAST);
    }

    public void testRateIndependencyAccelGyroSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_ACCELEROMETER,
                                                  Sensor.TYPE_GYROSCOPE);
    }

    public void testRateIndependencyAccelMagSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_ACCELEROMETER,
                                                  Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testRateIndependencyGyroMagSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_GYROSCOPE,
                                                  Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testRateIndependencyAccelUncalAccelSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_ACCELEROMETER,
                                             Sensor.TYPE_ACCELEROMETER_UNCALIBRATED);
    }

    public void testRateIndependencyGyroUncalGyroSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_GYROSCOPE,
                                             Sensor.TYPE_GYROSCOPE_UNCALIBRATED);
    }

    public void testRateIndependencyMagUncalMagSingleChannel() {
        runSingleChannelRateIndependencyTestGroup(Sensor.TYPE_MAGNETIC_FIELD,
                                             Sensor.TYPE_MAGNETIC_FIELD_UNCALIBRATED);
    }

    public void testRateIndependencyAccelMultiChannel() {
        runMultiChannelRateIndependencyTestGroup(Sensor.TYPE_ACCELEROMETER);
    }

    public void testRateIndependencyGyroMultiChannel() {
        runMultiChannelRateIndependencyTestGroup(Sensor.TYPE_GYROSCOPE);
    }

    public void testRateIndependencyMagMultiChannel() {
        runMultiChannelRateIndependencyTestGroup(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void testRateIndependencyAccelMultiMode() {
        runMultiModeRateIndependencyTestGroup(Sensor.TYPE_ACCELEROMETER);
    }

    public void testRateIndependencyGyroMultiMode() {
        runMultiModeRateIndependencyTestGroup(Sensor.TYPE_GYROSCOPE);
    }

    public void testRateIndependencyMagMultiMode() {
        runMultiModeRateIndependencyTestGroup(Sensor.TYPE_MAGNETIC_FIELD);
    }

    private void runSingleChannelRateIndependencyTestGroup(int type1, int type2) {
        if (type1 == type2) {
            throw new IllegalArgumentException("Cannot run single channel rate independency test "
                    + "on type " + type1 + " and " + type2);
        }
        String stype1 = SensorCtsHelper.sensorTypeShortString(type1);
        String stype2 = SensorCtsHelper.sensorTypeShortString(type2);

        TestResultCollector c =
                new TestResultCollector(
                    "testRateIndependency" + stype1 + stype2 + "SingleChannel", TAG);

        for (int rate1 : POSSIBLE_RATE_LEVELS) {
            for (int rate2 : POSSIBLE_RATE_LEVELS) {
                for (int memType : POSSIBLE_CHANNEL_TYPES) {
                    c.perform(
                        () -> {
                            runSingleChannelRateIndependencyTest(
                                    type1, rate1, type2, rate2,
                                    SensorDirectChannel.TYPE_MEMORY_FILE);
                        },
                        String.format("(%s rate %d, %s rate %d, mem %d)",
                                      stype1, rate1, stype2, rate2, memType));
                }
            }
        }
        c.judge();
    }

    public void runMultiChannelRateIndependencyTestGroup(int sensorType) {
        TestResultCollector c = new TestResultCollector(
                "testRateIndependency" + SensorCtsHelper.sensorTypeShortString(sensorType)
                    + "MultiChannel", TAG);

        for (int rate1 : POSSIBLE_RATE_LEVELS) {
            for (int rate2 : POSSIBLE_RATE_LEVELS) {
                for (int type1 : POSSIBLE_CHANNEL_TYPES) {
                    for (int type2 : POSSIBLE_CHANNEL_TYPES) {
                        // only test upper triangle
                        if (rate1 > rate2 || type1 > type2) {
                            continue;
                        }
                        c.perform(() -> {
                                runMultiChannelRateIndependencyTest(
                                        sensorType, rate1, rate2, type1, type2);},
                                String.format("rate1 %d, rate2 %d, type1 %d, type2 %d",
                                              rate1, rate2, type1, type2));
                    }
                }
            }
        }
        c.judge();
    }

    public void runMultiModeRateIndependencyTestGroup(int sensorType) {
        TestResultCollector c = new TestResultCollector(
                "testRateIndependency" + SensorCtsHelper.sensorTypeShortString(sensorType)
                    + "MultiMode", TAG);

        for (int rate : POSSIBLE_RATE_LEVELS) {
            for (int type : POSSIBLE_CHANNEL_TYPES) {
                for (int samplingPeriodUs : POSSIBLE_SAMPLE_PERIOD_US) {
                    c.perform(() -> {runMultiModeRateIndependencyTest(
                                        sensorType, rate, type, samplingPeriodUs);},
                              String.format("rateLevel %d, memType %d, period %d",
                                            rate, type, samplingPeriodUs));
                }
            }
        }
        c.judge();
    }

    private void runSensorDirectReportTest(int sensorType, int memType, int rateLevel)
            throws AssertionError {
        Sensor s = mSensorManager.getDefaultSensor(sensorType);
        if (s == null
                || s.getHighestDirectReportRateLevel() < rateLevel
                || !s.isDirectChannelTypeSupported(memType)) {
            return;
        }
        resetEvent();

        mChannel = prepareDirectChannel(memType, false /* secondary */);
        assertTrue("createDirectChannel failed", mChannel != null);

        try {
            assertTrue("Shared memory is not formatted", isSharedMemoryFormatted(memType));
            waitBeforeStartSensor();

            int token = mChannel.configure(s, rateLevel);
            assertTrue("configure direct mChannel failed", token > 0);

            waitSensorCollection();

            //stop sensor and analyze content
            mChannel.configure(s, SensorDirectChannel.RATE_STOP);
            checkSharedMemoryContent(s, memType, rateLevel, token);
        } finally {
            mChannel.close();
            mChannel = null;
        }
    }

    private void runSingleChannelRateIndependencyTest(
            int type1, int rateLevel1, int type2, int rateLevel2, int memType)
                throws AssertionError {
        Sensor s1 = mSensorManager.getDefaultSensor(type1);
        Sensor s2 = mSensorManager.getDefaultSensor(type2);
        if (s1 == null
                || s1.getHighestDirectReportRateLevel() < rateLevel1
                || !s1.isDirectChannelTypeSupported(memType)) {
            return;
        }

        if (s2 == null
                || s2.getHighestDirectReportRateLevel() < rateLevel2
                || !s2.isDirectChannelTypeSupported(memType)) {
            return;
        }
        resetEvent();

        mChannel = prepareDirectChannel(memType, false /* secondary */);
        assertTrue("createDirectChannel failed", mChannel != null);

        try {
            assertTrue("Shared memory is not formatted", isSharedMemoryFormatted(memType));
            waitBeforeStartSensor();

            int token1 = mChannel.configure(s1, rateLevel1);
            int token2 = mChannel.configure(s2, rateLevel2);
            assertTrue("configure direct mChannel failed, token1 = " + token1, token1 > 0);
            assertTrue("configure direct mChannel failed, token2 = " + token2, token2 > 0);

            // run half amount of time so buffer is enough for both sensors
            try {
                SensorCtsHelper.sleep(TEST_RUN_TIME_PERIOD_MILLISEC / 2, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            //stop sensor and analyze content
            mChannel.configure(s1, SensorDirectChannel.RATE_STOP);
            mChannel.configure(s2, SensorDirectChannel.RATE_STOP);

            readSharedMemory(memType, false /*secondary*/);
            checkEventRate(TEST_RUN_TIME_PERIOD_MILLISEC / 2, parseEntireBuffer(mBuffer, token1),
                           type1, rateLevel1);
            checkEventRate(TEST_RUN_TIME_PERIOD_MILLISEC / 2, parseEntireBuffer(mBuffer, token2),
                           type2, rateLevel2);
        } finally {
            mChannel.close();
            mChannel = null;
        }
    }

    private void runMultiChannelRateIndependencyTest(
            int type, int rateLevel1, int rateLevel2, int memType1, int memType2)
                throws AssertionError {
        Sensor s = mSensorManager.getDefaultSensor(type);
        if (s == null
                || s.getHighestDirectReportRateLevel() < Math.max(rateLevel1, rateLevel2)
                || !s.isDirectChannelTypeSupported(memType1)
                || !s.isDirectChannelTypeSupported(memType2)) {
            return;
        }
        resetEvent();

        mChannel = prepareDirectChannel(memType1, false /* secondary */);
        mChannelSecondary = prepareDirectChannel(memType2, true /* secondary */);

        try {
            assertTrue("createDirectChannel failed", mChannel != null);
            assertTrue("Shared memory is not formatted",
                       isSharedMemoryFormatted(memType1));

            assertTrue("createDirectChannel(secondary) failed", mChannelSecondary != null);
            assertTrue("Shared memory(secondary) is not formatted",
                       isSharedMemoryFormatted(memType2, true));

            waitBeforeStartSensor();

            int token1 = mChannel.configure(s, rateLevel1);
            int token2 = mChannelSecondary.configure(s, rateLevel2);
            assertTrue("configure direct mChannel failed", token1 > 0);
            assertTrue("configure direct mChannelSecondary failed", token2 > 0);

            waitSensorCollection();

            //stop sensor and analyze content
            mChannel.configure(s, SensorDirectChannel.RATE_STOP);
            mChannelSecondary.configure(s, SensorDirectChannel.RATE_STOP);

            // check rate
            readSharedMemory(memType1, false /*secondary*/);
            checkEventRate(TEST_RUN_TIME_PERIOD_MILLISEC, parseEntireBuffer(mBuffer, token1),
                           type, rateLevel1);

            readSharedMemory(memType2, true /*secondary*/);
            checkEventRate(TEST_RUN_TIME_PERIOD_MILLISEC, parseEntireBuffer(mBuffer, token2),
                           type, rateLevel2);
        } finally {
            if (mChannel != null) {
                mChannel.close();
                mChannel = null;
            }
            if (mChannelSecondary != null) {
                mChannelSecondary.close();
                mChannelSecondary = null;
            }
        }
    }

    private void runMultiModeRateIndependencyTest(
            int type , int rateLevel, int memType, int samplingPeriodUs)
                throws AssertionError {
        final Sensor s = mSensorManager.getDefaultSensor(type);
        if (s == null
                || s.getHighestDirectReportRateLevel() < rateLevel
                || !s.isDirectChannelTypeSupported(memType)) {
            return;
        }

        if (samplingPeriodUs == 0) {
            samplingPeriodUs = s.getMinDelay();
        }

        if (samplingPeriodUs < s.getMinDelay()) {
            return;
        }
        resetEvent();

        mChannel = prepareDirectChannel(memType, false /* secondary */);
        assertTrue("createDirectChannel failed", mChannel != null);
        SensorEventCollection listener = new SensorEventCollection(s);

        try {
            waitBeforeStartSensor();
            int token = mChannel.configure(s, rateLevel);
            boolean registerRet = mSensorManager.registerListener(listener, s, samplingPeriodUs);
            assertTrue("Register listener failed", registerRet);

            waitSensorCollection();

            mChannel.configure(s, SensorDirectChannel.RATE_STOP);
            mSensorManager.unregisterListener(listener);

            // check direct report rate
            readSharedMemory(memType, false /*secondary*/);
            List<DirectReportSensorEvent> events = parseEntireBuffer(mBuffer, token);
            checkEventRate(TEST_RUN_TIME_PERIOD_MILLISEC, events, type, rateLevel);

            // check callback interface rate
            checkEventRateUs(TEST_RUN_TIME_PERIOD_MILLISEC, listener.getEvents(), type,
                             samplingPeriodUs);
        } finally {
            mChannel.close();
            mChannel = null;
            mSensorManager.unregisterListener(listener);
        }
    }

    private void waitBeforeStartSensor() {
        // wait for sensor system to come to a rest after previous test to avoid flakiness.
        try {
            SensorCtsHelper.sleep(REST_PERIOD_BEFORE_TEST_MILLISEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void waitSensorCollection() {
        // wait for sensor collection to finish
        try {
            SensorCtsHelper.sleep(TEST_RUN_TIME_PERIOD_MILLISEC, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private MemoryFile allocateMemoryFile() {
        MemoryFile memFile = null;
        try {
            memFile = new MemoryFile("Sensor Channel", SHARED_MEMORY_SIZE);
        } catch (IOException e) {
            Log.e(TAG, "IOException when allocating MemoryFile");
        }
        return memFile;
    }

    private HardwareBuffer allocateHardwareBuffer() {
        HardwareBuffer hardwareBuffer;

        hardwareBuffer = HardwareBuffer.create(
                SHARED_MEMORY_SIZE, 1 /* height */, HardwareBuffer.BLOB, 1 /* layer */,
                HardwareBuffer.USAGE_CPU_READ_OFTEN | HardwareBuffer.USAGE_GPU_DATA_BUFFER
                    | HardwareBuffer.USAGE_SENSOR_DIRECT_DATA);
        return hardwareBuffer;
    }

    private SensorDirectChannel prepareDirectChannel(int memType, boolean secondary) {
        SensorDirectChannel channel = null;

        try {
            switch(memType) {
                case SensorDirectChannel.TYPE_MEMORY_FILE: {
                    MemoryFile memoryFile = secondary ? mMemoryFileSecondary : mMemoryFile;
                    assertTrue("MemoryFile" + (secondary ? "(secondary)" : "") + " is null",
                               memoryFile != null);
                    channel = mSensorManager.createDirectChannel(memoryFile);
                    break;
                }
                case SensorDirectChannel.TYPE_HARDWARE_BUFFER: {
                    HardwareBuffer hardwareBuffer
                            = secondary ? mHardwareBufferSecondary : mHardwareBuffer;
                    assertTrue("HardwareBuffer" + (secondary ? "(secondary)" : "") + " is null",
                               hardwareBuffer != null);
                    channel = mSensorManager.createDirectChannel(hardwareBuffer);
                    break;
                }
                default:
                    Log.e(TAG, "Specified illegal memory type " + memType);
            }
        } catch (IllegalStateException | UncheckedIOException e) {
            Log.e(TAG, "Cannot initialize channel for memory type " + memType
                    + ", details:" + e);
            channel = null;
        }
        return channel;
    }

    private boolean readSharedMemory(int memType, boolean secondary, int offset, int length) {
        switch(memType) {
            case SensorDirectChannel.TYPE_MEMORY_FILE:
                try {
                    MemoryFile f = secondary ? mMemoryFileSecondary : mMemoryFile;
                    if (f.readBytes(mBuffer, offset, offset, length) != length) {
                        Log.e(TAG, "cannot read entire MemoryFile");
                        return false;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "accessing MemoryFile causes IOException");
                    return false;
                }
                return true;
            case SensorDirectChannel.TYPE_HARDWARE_BUFFER:
                return nativeReadHardwareBuffer(
                        secondary ? mHardwareBufferSecondary : mHardwareBuffer,
                        mBuffer, offset, offset, length);
            default:
                return false;
        }
    }

    private boolean readSharedMemory(int memType, boolean secondary) {
        return readSharedMemory(memType, secondary, 0, SHARED_MEMORY_SIZE);
    }

    private boolean readSharedMemory(int memType) {
        return readSharedMemory(memType, false /*secondary*/);
    }

    private boolean isMemoryTypeNeeded(int memType) {
        List<Sensor> sensorList = mSensorManager.getSensorList(Sensor.TYPE_ALL);
        for (Sensor s : sensorList) {
            if (s.isDirectChannelTypeSupported(memType)) {
                return true;
            }
        }
        return false;
    }

    private boolean isSharedMemoryFormatted(int memType) {
        return isSharedMemoryFormatted(memType, false /* secondary */);
    }

    private boolean isSharedMemoryFormatted(int memType, boolean secondary) {
        readSharedMemory(memType, secondary);

        for (byte b : mBuffer) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    private void checkSharedMemoryContent(Sensor s, int memType, int rateLevel, int token) {
        assertTrue("read mem type " + memType + " content failed", readSharedMemory(memType));

        int offset = 0;
        int nextSerial = 1;
        DirectReportSensorEvent e = getEvent();
        while (offset <= SHARED_MEMORY_SIZE - SENSORS_EVENT_SIZE) {
            parseSensorEvent(offset, e);

            if (e.serial == 0) {
                // reaches end of events
                break;
            }

            assertTrue("incorrect size " + e.size + "  at offset " + offset,
                    e.size == SENSORS_EVENT_SIZE);
            assertTrue("incorrect token " + e.token + " at offset " + offset,
                    e.token == token);
            assertTrue("incorrect serial " + e.serial + " at offset " + offset,
                    e.serial == nextSerial);
            assertTrue("incorrect type " + e.type + " offset " + offset,
                    e.type == s.getType());

            switch(s.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    double accNorm = Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z);
                    assertTrue("incorrect gravity norm " + accNorm + " at offset " + offset,
                            accNorm < GRAVITY_MAX && accNorm > GRAVITY_MIN);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    double gyroNorm = Math.sqrt(e.x * e.x + e.y * e.y + e.z * e.z);
                    assertTrue("gyro norm too large (" + gyroNorm + ") at offset " + offset,
                            gyroNorm < GYRO_NORM_MAX);
                    break;
            }

            ++nextSerial;
            offset += SENSORS_EVENT_SIZE;
        }

        int nEvents = nextSerial - 1;
        float nominalFreq = 0;

        switch (rateLevel) {
            case SensorDirectChannel.RATE_NORMAL:
                nominalFreq = RATE_NORMAL_NOMINAL;
                break;
            case SensorDirectChannel.RATE_FAST:
                nominalFreq = RATE_FAST_NOMINAL;
                break;
            case SensorDirectChannel.RATE_VERY_FAST:
                nominalFreq = RATE_VERY_FAST_NOMINAL;
                break;
        }

        if (nominalFreq != 0) {
            int minEvents;
            int maxEvents;
            minEvents = (int) Math.floor(
                    nominalFreq
                    * FREQ_LOWER_BOUND
                    * (TEST_RUN_TIME_PERIOD_MILLISEC - ALLOWED_SENSOR_INIT_TIME_MILLISEC)
                    * (1 - MERCY_FACTOR)
                    / 1000);
            maxEvents = (int) Math.ceil(
                    nominalFreq
                    * FREQ_UPPER_BOUND
                    * TEST_RUN_TIME_PERIOD_MILLISEC
                    * (1 + MERCY_FACTOR)
                    / 1000);

            assertTrue("nEvent is " + nEvents + " not between " + minEvents + " and " + maxEvents,
                    nEvents >= minEvents && nEvents <=maxEvents);
        }
    }

    private void checkEventRate(int testTimeMs, List<DirectReportSensorEvent> events,
                                int type, int rateLevel) {
        assertTrue("insufficient events of type " + type, events.size() > 1);
        for (DirectReportSensorEvent e : events) {
            assertTrue("incorrect type " + e.type + " expecting " + type, e.type == type);
        }

        // check number of events
        int[] minMax = calculateExpectedNEvents(testTimeMs, rateLevel);
        assertTrue(
                "Number of event of type " + type + " is " + events.size()
                    + ", which is not in range [" + minMax[0] + ", " + minMax[1] + "].",
                minMax[0] <= events.size() && events.size() <= minMax[1]);

        // intervals
        List<Long> intervals = new ArrayList<>(events.size() - 1);
        long minInterval = Long.MAX_VALUE;
        long maxInterval = Long.MIN_VALUE;
        long averageInterval = 0;
        for (int i = 1; i < events.size(); ++i) {
            long d = events.get(i).ts - events.get(i-1).ts;
            averageInterval += d;
            minInterval = Math.min(d, minInterval);
            maxInterval = Math.max(d, maxInterval);
            intervals.add(d);
        }
        averageInterval /= (events.size() - 1);

        // average rate
        float averageFreq = 1e9f / averageInterval;
        float nominalFreq = getNominalFreq(rateLevel);
        Log.d(TAG, String.format(
                "checkEventRate type %d: averageFreq %f, nominalFreq %f, lbound %f, ubound %f",
                type, averageFreq, nominalFreq,
                nominalFreq * FREQ_LOWER_BOUND,
                nominalFreq * FREQ_UPPER_BOUND));
        assertTrue("Average frequency of type " + type + " rateLevel " + rateLevel
                        + " is " + averageFreq,
                   nominalFreq * FREQ_LOWER_BOUND * (1 - MERCY_FACTOR) <= averageFreq &&
                       averageFreq <= nominalFreq * FREQ_UPPER_BOUND * (1 + MERCY_FACTOR));

        // jitter variance
        List<Long> percentileValues =
                SensorCtsHelper.getPercentileValue(intervals, 0.025f, (1 - 0.025f));
        assertTrue("Timestamp jitter of type " + type + " rateLevel " + rateLevel + " is "
                        + (percentileValues.get(1) - percentileValues.get(0) / 1000) + " us, "
                        + "while average interval is " + (averageInterval / 1000) + "us, over-range",
                   (percentileValues.get(1) - percentileValues.get(0)) / averageInterval < 0.05);
        Log.d(TAG, String.format(
                "checkEventRate type %d, timestamp interval range %f - %f ms, " +
                    "span %f ms, %.2f%% of averageInterval",
                    type, percentileValues.get(0)/1e6f, percentileValues.get(1)/1e6f,
                    (percentileValues.get(1) - percentileValues.get(0))/1e6f,
                    (percentileValues.get(1) - percentileValues.get(0)) / averageInterval * 100.f));

    }

    private void checkEventRateUs(int testTimeMs, List<DirectReportSensorEvent> events,
                                  int type, int samplingPeriodUs) {
        // samplingPeriodUs must be a valid one advertised by sensor
        assertTrue("insufficient events of type " + type, events.size() > 1);
        for (DirectReportSensorEvent e : events) {
            assertTrue("incorrect type " + e.type + " expecting " + type, e.type == type);
        }

        // check number of events
        int[] minMax = calculateExpectedNEventsUs(testTimeMs, samplingPeriodUs);
        assertTrue(
                "Number of event of type " + type + " is " + events.size()
                    + ", which is not in range [" + minMax[0] + ", " + minMax[1] + "].",
                minMax[0] <= events.size() && events.size() <= minMax[1]);

        // intervals
        List<Long> intervals = new ArrayList<>(events.size() - 1);
        long minInterval = Long.MAX_VALUE;
        long maxInterval = Long.MIN_VALUE;
        long averageInterval = 0;
        for (int i = 1; i < events.size(); ++i) {
            long d = events.get(i).ts - events.get(i-1).ts;
            averageInterval += d;
            minInterval = Math.min(d, minInterval);
            maxInterval = Math.max(d, maxInterval);
            intervals.add(d);
        }
        averageInterval /= (events.size() - 1);

        // average rate
        float averageFreq = 1e9f / averageInterval;
        float nominalFreq = 1e6f / samplingPeriodUs;
        Log.d(TAG, String.format(
                "checkEventRateUs type %d: averageFreq %f, nominalFreq %f, lbound %f, ubound %f",
                type, averageFreq, nominalFreq,
                nominalFreq * FREQ_LOWER_BOUND_POLL,
                nominalFreq * FREQ_UPPER_BOUND_POLL));
        assertTrue("Average frequency of type " + type
                        + " is " + averageFreq,
                   nominalFreq * FREQ_LOWER_BOUND_POLL * (1 - MERCY_FACTOR) <= averageFreq &&
                       averageFreq <= nominalFreq * FREQ_UPPER_BOUND_POLL * (1 + MERCY_FACTOR));

        // jitter variance
        List<Long> percentileValues =
                SensorCtsHelper.getPercentileValue(intervals, 0.025f, (1 - 0.025f));
        assertTrue("Timestamp jitter of type " + type + " is "
                        + (percentileValues.get(1) - percentileValues.get(0) / 1000) + " us, "
                        + "while average interval is " + (averageInterval / 1000) + "us, over-range",
                   (percentileValues.get(1) - percentileValues.get(0)) / averageInterval < 0.05);
        Log.d(TAG, String.format(
                "checkEventRateUs type %d, timestamp interval range %f - %f ms, " +
                    "span %f ms, %.2f%% of averageInterval",
                    type, percentileValues.get(0)/1e6f, percentileValues.get(1)/1e6f,
                    (percentileValues.get(1) - percentileValues.get(0)) / 1e6f,
                    (percentileValues.get(1) - percentileValues.get(0)) / averageInterval * 100.f));
    }

    private void allocateSharedMemory() {
        if (mNeedMemoryFile) {
            mMemoryFile = allocateMemoryFile();
            mMemoryFileSecondary = allocateMemoryFile();
        }

        if (mNeedHardwareBuffer) {
            mHardwareBuffer = allocateHardwareBuffer();
            mHardwareBufferSecondary = allocateHardwareBuffer();
        }
    }

    private void freeSharedMemory() {
        if (mMemoryFile != null) {
            mMemoryFile.close();
            mMemoryFile = null;
        }

        if (mMemoryFileSecondary != null) {
            mMemoryFileSecondary.close();
            mMemoryFileSecondary = null;
        }

        if (mHardwareBuffer != null) {
            mHardwareBuffer.close();
            mHardwareBuffer = null;
        }

        if (mHardwareBufferSecondary != null) {
            mHardwareBufferSecondary.close();
            mHardwareBufferSecondary = null;
        }
    }

    private float getNominalFreq(int rateLevel) {
        float nominalFreq = 0;
        switch (rateLevel) {
            case SensorDirectChannel.RATE_NORMAL:
                nominalFreq = RATE_NORMAL_NOMINAL;
                break;
            case SensorDirectChannel.RATE_FAST:
                nominalFreq = RATE_FAST_NOMINAL;
                break;
            case SensorDirectChannel.RATE_VERY_FAST:
                nominalFreq = RATE_VERY_FAST_NOMINAL;
                break;
        }
        return nominalFreq;
    }

    private int[] calculateExpectedNEvents(int timeMs, int rateLevel) {
        int[] minMax = new int[] { -1, Integer.MAX_VALUE };
        float nominalFreq = getNominalFreq(rateLevel);
        if (nominalFreq != 0) {
            // min
            if (timeMs > ALLOWED_SENSOR_INIT_TIME_MILLISEC) {
                minMax[0] = (int) Math.floor(
                        nominalFreq
                        * FREQ_LOWER_BOUND
                        * (timeMs - ALLOWED_SENSOR_INIT_TIME_MILLISEC)
                        * (1 - MERCY_FACTOR)
                        / 1000);
            }
            // max
            minMax[1] = (int) Math.ceil(
                    nominalFreq
                    * FREQ_UPPER_BOUND
                    * timeMs
                    * (1 + MERCY_FACTOR)
                    / 1000);
        }
        return minMax;
    }

    private int[] calculateExpectedNEventsUs(int timeMs, int samplingPeriodUs) {
        int[] minMax = new int[2];
        minMax[0] = Math.max((int) Math.floor(
                (timeMs - ALLOWED_SENSOR_INIT_TIME_MILLISEC) * 1000/ samplingPeriodUs), 0);
        minMax[1] = (int) Math.ceil(timeMs * 1000 * 2 / samplingPeriodUs);
        return minMax;
    }

    private static class DirectReportSensorEvent {
        public int size;
        public int token;
        public int type;
        public long serial;
        public long ts;
        public float x;
        public float y;
        public float z;
        public long arrivalTs;
    };

    // EventPool to avoid allocating too many event objects and hitting GC during test
    private static class EventPool {
        public EventPool(int n) {
            mEvents = Arrays.asList(new DirectReportSensorEvent[n]);
            for (int i = 0; i < n; ++i) {
                mEvents.set(i, new DirectReportSensorEvent());
            }
            reset();
        }

        public synchronized void reset() {
            Log.d(TAG, "Reset EventPool (" + mIndex + " events used)");
            mIndex = 0;
        }

        public synchronized DirectReportSensorEvent get() {
            if (mIndex < mEvents.size()) {
                return mEvents.get(mIndex++);
            } else {
                throw new IllegalStateException("EventPool depleted");
            }
        }

        private List<DirectReportSensorEvent> mEvents;
        private int mIndex;
    };

    private DirectReportSensorEvent getEvent() {
        return mEventPool.get();
    }

    private DirectReportSensorEvent getEvent(DirectReportSensorEvent e) {
        DirectReportSensorEvent event = mEventPool.get();
        event.size = e.size;
        event.token = e.token;
        event.type = e.type;
        event.serial = e.serial;
        event.ts = e.ts;
        event.x = e.x;
        event.y = e.y;
        event.z = e.z;
        event.arrivalTs = e.arrivalTs;
        return event;
    }

    private void resetEvent() {
        mEventPool.reset();
    }

    private class SensorEventCollection implements SensorEventListener {
        List<DirectReportSensorEvent> mEvents = new ArrayList<>();
        Sensor mSensor;

        public SensorEventCollection(Sensor s) {
            mSensor = s;
        }

        List<DirectReportSensorEvent> getEvents() {
            return mEvents;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            if (mSensor == null || event.sensor == mSensor) {
                DirectReportSensorEvent e = mEventPool.get();
                e.size = SENSORS_EVENT_SIZE;
                e.token = event.sensor.getType();
                e.type = e.token;
                e.serial = -1;
                e.ts = event.timestamp;
                e.arrivalTs = SystemClock.elapsedRealtimeNanos();

                e.x = event.values[0];
                if (event.values.length > 1) {
                    e.y = event.values[1];
                }
                if (event.values.length > 2) {
                    e.z = event.values[2];
                }
                mEvents.add(e);
            }
        }

        @Override
        public void onAccuracyChanged(Sensor s, int accuracy) {
            // do nothing
        }
    };

    private List<DirectReportSensorEvent> parseEntireBuffer(byte[] buffer, int token) {
        int offset = 0;
        int nextSerial = 1;
        List<DirectReportSensorEvent> events = new ArrayList<>();

        while (offset <= SHARED_MEMORY_SIZE - SENSORS_EVENT_SIZE) {
            DirectReportSensorEvent e = getEvent();
            parseSensorEvent(offset, e);

            if (e.serial == 0) {
                // reaches end of events
                break;
            }

            assertTrue("incorrect size " + e.size + "  at offset " + offset,
                    e.size == SENSORS_EVENT_SIZE);
            assertTrue("incorrect serial " + e.serial + " at offset " + offset,
                    e.serial == nextSerial);

            if (e.token == token) {
                events.add(e);
            }

            ++nextSerial;
            offset += SENSORS_EVENT_SIZE;
        }

        return events;
    }

    // parse sensors_event_t from mBuffer and fill information into DirectReportSensorEvent
    private void parseSensorEvent(int offset, DirectReportSensorEvent ev) {
        mByteBuffer.position(offset);

        ev.size = mByteBuffer.getInt();
        ev.token = mByteBuffer.getInt();
        ev.type = mByteBuffer.getInt();
        ev.serial = ((long) mByteBuffer.getInt()) & 0xFFFFFFFFl; // signed=>unsigned
        ev.ts = mByteBuffer.getLong();
        ev.arrivalTs = SystemClock.elapsedRealtimeNanos();
        ev.x = mByteBuffer.getFloat();
        ev.y = mByteBuffer.getFloat();
        ev.z = mByteBuffer.getFloat();
    }
}
