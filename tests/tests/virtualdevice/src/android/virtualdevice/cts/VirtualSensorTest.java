/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.virtualdevice.cts;

import static android.Manifest.permission.CREATE_VIRTUAL_DEVICE;
import static android.hardware.Sensor.TYPE_ACCELEROMETER;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import static java.util.concurrent.TimeUnit.MICROSECONDS;

import android.annotation.Nullable;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.sensor.VirtualSensor;
import android.companion.virtual.sensor.VirtualSensorConfig;
import android.companion.virtual.sensor.VirtualSensorEvent;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.util.FakeAssociationRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.AdoptShellPermissionsRule;
import com.android.internal.os.BackgroundThread;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class VirtualSensorTest {

    private static final String VIRTUAL_SENSOR_NAME = "VirtualAccelerometer";
    private static final String VIRTUAL_SENSOR_VENDOR = "VirtualDeviceVendor";

    private static final int SENSOR_TIMEOUT_MILLIS = 1000;

    @Rule
    public AdoptShellPermissionsRule mAdoptShellPermissionsRule = new AdoptShellPermissionsRule(
            InstrumentationRegistry.getInstrumentation().getUiAutomation(),
            CREATE_VIRTUAL_DEVICE);

    @Rule
    public FakeAssociationRule mFakeAssociationRule = new FakeAssociationRule();

    private VirtualDeviceManager mVirtualDeviceManager;
    @Nullable
    private VirtualDeviceManager.VirtualDevice mVirtualDevice;

    private SensorManager mSensorManager;
    @Mock
    private SensorManager.DynamicSensorCallback mDynamicSensorCallback;

    private SensorManager mVirtualDeviceSensorManager;
    private VirtualSensor mVirtualSensor;
    @Mock
    private VirtualSensor.SensorStateChangeCallback mVirtualSensorStateChangeCallback;
    private Executor mVirtualSensorStateChangeCallbackExecutor = BackgroundThread.getExecutor();
    private VirtualSensorEventListener mSensorEventListener = new VirtualSensorEventListener();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Context context = InstrumentationRegistry.getInstrumentation().getContext();
        assumeTrue(
                context.getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_ACTIVITIES_ON_SECONDARY_DISPLAYS));
        mSensorManager = context.getSystemService(SensorManager.class);

        mVirtualDeviceManager = context.getSystemService(VirtualDeviceManager.class);
    }

    private VirtualSensor setUpVirtualSensor(VirtualSensorConfig sensorConfig) {
        VirtualDeviceParams.Builder builder = new VirtualDeviceParams.Builder()
                .setDevicePolicy(VirtualDeviceParams.POLICY_TYPE_SENSORS,
                        VirtualDeviceParams.DEVICE_POLICY_CUSTOM);
        if (sensorConfig != null) {
            builder = builder.addVirtualSensorConfig(sensorConfig);
        }
        mVirtualDevice = mVirtualDeviceManager.createVirtualDevice(
                mFakeAssociationRule.getAssociationInfo().getId(), builder.build());
        Context deviceContext = InstrumentationRegistry.getInstrumentation().getContext()
                .createDeviceContext(mVirtualDevice.getDeviceId());
        mVirtualDeviceSensorManager = deviceContext.getSystemService(SensorManager.class);
        if (sensorConfig == null) {
            return null;
        } else {
            return mVirtualDevice.getVirtualSensor(sensorConfig.getType(), sensorConfig.getName());
        }
    }

    @After
    public void tearDown() {
        if (mVirtualDevice != null) {
            mVirtualDevice.close();
        }
    }

    @Test
    public void getSensorList_noVirtualSensors_returnsEmptyList() {
        mVirtualSensor = setUpVirtualSensor(/* sensorConfig= */ null);
        assertThat(mVirtualSensor).isNull();
        assertThat(mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER)).isEmpty();
    }

    @Test
    public void getSensorList_returnsVirtualSensor() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setVendor(VIRTUAL_SENSOR_VENDOR)
                        .build());

        assertThat(mVirtualSensor.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(mVirtualSensor.getName()).isEqualTo(VIRTUAL_SENSOR_NAME);

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        List<Sensor> sensors = mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER);
        Sensor defaultDeviceSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        assertThat(sensors).containsExactly(sensor);
        assertThat(sensor).isNotEqualTo(defaultDeviceSensor);

        assertThat(sensor.getName()).isEqualTo(VIRTUAL_SENSOR_NAME);
        assertThat(sensor.getVendor()).isEqualTo(VIRTUAL_SENSOR_VENDOR);
        assertThat(sensor.getType()).isEqualTo(TYPE_ACCELEROMETER);
        assertThat(sensor.isDynamicSensor()).isFalse();
    }

    @Test
    public void getSensorList_isCached() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        final List<Sensor> allSensors = mVirtualDeviceSensorManager.getSensorList(Sensor.TYPE_ALL);
        assertThat(allSensors).isSameInstanceAs(
                mVirtualDeviceSensorManager.getSensorList(Sensor.TYPE_ALL));

        final List<Sensor> sensors = mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER);
        assertThat(sensors).isSameInstanceAs(
                mVirtualDeviceSensorManager.getSensorList(TYPE_ACCELEROMETER));
    }

    @Test
    public void createVirtualSensor_dynamicSensorCallback_notCalled() {
        mSensorManager.registerDynamicSensorCallback(mDynamicSensorCallback);

        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        verify(mDynamicSensorCallback, after(SENSOR_TIMEOUT_MILLIS).never())
                .onDynamicSensorConnected(any());

        mSensorManager.unregisterDynamicSensorCallback(mDynamicSensorCallback);
    }

    @Test
    public void closeVirtualDevice_removesSensor() throws Exception {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());
        mVirtualDevice.close();

        // The virtual device ID is no longer valid, SensorManager falls back to default device.
        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);
        Sensor defaultDeviceSensor = mSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        assertThat(sensor.getHandle()).isEqualTo(defaultDeviceSensor.getHandle());
        assertThat(sensor.getName()).isEqualTo(defaultDeviceSensor.getName());
    }

    @Test
    public void registerListener_triggersVirtualSensorCallback() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .setStateChangeCallback(mVirtualSensorStateChangeCallbackExecutor,
                                mVirtualSensorStateChangeCallback)
                        .build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        final int samplingPeriodMicros = 2345000;
        final int maxReportLatencyMicros = 678000;
        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, samplingPeriodMicros, maxReportLatencyMicros);

        final Duration expectedSamplingPeriod =
                Duration.ofNanos(MICROSECONDS.toNanos(samplingPeriodMicros));
        final Duration expectedReportLatency =
                Duration.ofNanos(MICROSECONDS.toNanos(maxReportLatencyMicros));

        verify(mVirtualSensorStateChangeCallback, after(SENSOR_TIMEOUT_MILLIS).times(1))
                .onStateChanged(true, expectedSamplingPeriod, expectedReportLatency);

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);

        verify(mVirtualSensorStateChangeCallback, after(SENSOR_TIMEOUT_MILLIS).times(1))
                .onStateChanged(eq(false), any(Duration.class), any(Duration.class));

        verifyNoMoreInteractions(mVirtualSensorStateChangeCallback);
    }

    @Test
    public void sendSensorEvent_reachesRegisteredListeners() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME).build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        final VirtualSensorEvent firstEvent =
                new VirtualSensorEvent.Builder(new float[] {0.1f, 2.3f, 4.5f}).build();
        mVirtualSensor.sendSensorEvent(firstEvent);

        mSensorEventListener.assertReceivedSensorEvent(sensor, firstEvent);

        final VirtualSensorEvent secondEvent =
                new VirtualSensorEvent.Builder(new float[] {6.7f, 8.9f, 0.1f}).build();
        mVirtualSensor.sendSensorEvent(secondEvent);

        mSensorEventListener.assertReceivedSensorEvent(sensor, secondEvent);

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);

        final VirtualSensorEvent thirdEvent =
                new VirtualSensorEvent.Builder(new float[] {2.3f, 4.5f, 6.7f}).build();
        mVirtualSensor.sendSensorEvent(thirdEvent);

        mSensorEventListener.assertNoMoreEvents();
    }

    @Test
    public void sendSensorEvent_invalidValues_eventIsDropped() {
        mVirtualSensor = setUpVirtualSensor(
                new VirtualSensorConfig.Builder(TYPE_ACCELEROMETER, VIRTUAL_SENSOR_NAME)
                        .build());

        Sensor sensor = mVirtualDeviceSensorManager.getDefaultSensor(TYPE_ACCELEROMETER);

        mVirtualDeviceSensorManager.registerListener(
                mSensorEventListener, sensor, SensorManager.SENSOR_DELAY_NORMAL);

        final VirtualSensorEvent firstEvent =
                new VirtualSensorEvent.Builder(new float[] {0.1f}).build();
        mVirtualSensor.sendSensorEvent(firstEvent);

        final VirtualSensorEvent secondEvent =
                new VirtualSensorEvent.Builder(new float[] {2.3f, 4.5f, 6.7f, 8.9f}).build();
        mVirtualSensor.sendSensorEvent(secondEvent);

        final VirtualSensorEvent thirdEvent =
                new VirtualSensorEvent.Builder(new float[] {7.7f, 8.8f}).build();
        mVirtualSensor.sendSensorEvent(thirdEvent);

        mSensorEventListener.assertNoMoreEvents();

        mVirtualDeviceSensorManager.unregisterListener(mSensorEventListener);
    }

    private class VirtualSensorEventListener implements SensorEventListener {
        private final BlockingQueue<SensorEvent> mEvents = new LinkedBlockingQueue<>();

        @Override
        public void onSensorChanged(SensorEvent event) {
            try {
                mEvents.put(new SensorEvent(event.sensor, event.accuracy, event.timestamp,
                        event.values));
            } catch (InterruptedException ex) {
                fail("Interrupted while adding a SensorEvent to the queue");
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }

        public void assertReceivedSensorEvent(Sensor sensor, VirtualSensorEvent expected) {
            SensorEvent event = waitForEvent();
            if (event == null) {
                fail("Did not receive SensorEvent with values "
                        + Arrays.toString(expected.getValues()));
            }

            assertThat(event.sensor).isEqualTo(sensor);
            assertThat(event.values).isEqualTo(expected.getValues());
            assertThat(event.timestamp).isEqualTo(expected.getTimestampNanos());
        }

        public void assertNoMoreEvents() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SensorEvent event = waitForEvent();
            if (event != null) {
                fail("Received extra SensorEvent with values: " + Arrays.toString(event.values));
            }
        }

        private SensorEvent waitForEvent() {
            try {
                return mEvents.poll(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                fail("Interrupted while waiting for SensorEvent");
                return null;
            }
        }
    }
}
