/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.car.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeNotNull;
import static org.testng.Assert.assertThrows;

import android.car.Car;
import android.car.VehicleAreaSeat;
import android.car.VehicleAreaType;
import android.car.VehiclePropertyIds;
import android.car.VehicleAreaWheel;
import android.car.hardware.CarPropertyConfig;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresDevice;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.ArraySet;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "Instant apps cannot get car related permissions.")
public class CarPropertyManagerTest extends CarApiTestBase {

    private static final String TAG = CarPropertyManagerTest.class.getSimpleName();
    private static final long WAIT_CALLBACK = 1500L;
    private static final int NO_EVENTS = 0;
    private static final int ONCHANGE_RATE_EVENT_COUNTER = 1;
    private static final int UI_RATE_EVENT_COUNTER = 5;
    private static final int FAST_OR_FASTEST_EVENT_COUNTER = 10;
    private CarPropertyManager mCarPropertyManager;
    /** contains property Ids for the properties required by CDD*/
    private ArraySet<Integer> mPropertyIds = new ArraySet<>();


    private static class CarPropertyEventCounter implements CarPropertyEventCallback {
        private final Object mLock = new Object();
        private int mCounter = FAST_OR_FASTEST_EVENT_COUNTER;
        private CountDownLatch mCountDownLatch = new CountDownLatch(mCounter);

        @GuardedBy("mLock")
        private SparseArray<Integer> mEventCounter = new SparseArray<>();

        @GuardedBy("mLock")
        private SparseArray<Integer> mErrorCounter = new SparseArray<>();

        @GuardedBy("mLock")
        private SparseArray<Integer> mErrorWithErrorCodeCounter = new SparseArray<>();

        public int receivedEvent(int propId) {
            int val;
            synchronized (mLock) {
                val = mEventCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedError(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorCounter.get(propId, 0);
            }
            return val;
        }

        public int receivedErrorWithErrorCode(int propId) {
            int val;
            synchronized (mLock) {
                val = mErrorWithErrorCodeCounter.get(propId, 0);
            }
            return val;
        }

        @Override
        public void onChangeEvent(CarPropertyValue value) {
            synchronized (mLock) {
                int val = mEventCounter.get(value.getPropertyId(), 0) + 1;
                mEventCounter.put(value.getPropertyId(), val);
            }
            mCountDownLatch.countDown();
        }

        @Override
        public void onErrorEvent(int propId, int zone) {
            synchronized (mLock) {
                int val = mErrorCounter.get(propId, 0) + 1;
                mErrorCounter.put(propId, val);
            }
        }

        @Override
        public void onErrorEvent(int propId, int areaId, int errorCode) {
            synchronized (mLock) {
                int val = mErrorWithErrorCodeCounter.get(propId, 0) + 1;
                mErrorWithErrorCodeCounter.put(propId, val);
            }
        }

        public void resetCountDownLatch(int counter) {
            mCountDownLatch = new CountDownLatch(counter);
            mCounter = counter;
        }

        public void assertOnChangeEventCalled() throws InterruptedException {
            if (!mCountDownLatch.await(WAIT_CALLBACK, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is not called:" + mCounter + "times in "
                        + WAIT_CALLBACK + " ms.");
            }
        }

        public void assertOnChangeEventNotCalled() throws InterruptedException {
            // Once get an event, fail the test.
            mCountDownLatch = new CountDownLatch(1);
            if (mCountDownLatch.await(WAIT_CALLBACK, TimeUnit.MILLISECONDS)) {
                throw new IllegalStateException("Callback is called in "
                        + WAIT_CALLBACK + " ms.");
            }
        }

    }

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarPropertyManager = (CarPropertyManager) getCar().getCarManager(Car.PROPERTY_SERVICE);
        mPropertyIds.add(VehiclePropertyIds.PERF_VEHICLE_SPEED);
        mPropertyIds.add(VehiclePropertyIds.GEAR_SELECTION);
        mPropertyIds.add(VehiclePropertyIds.NIGHT_MODE);
        mPropertyIds.add(VehiclePropertyIds.PARKING_BRAKE_ON);
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList()}
     */
    @Test
    public void testGetPropertyList() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        assertThat(allConfigs).isNotNull();
    }

    /**
     * Test for {@link CarPropertyManager#getPropertyList(ArraySet)}
     */
    @Test
    public void testGetPropertyListWithArraySet() {
        List<CarPropertyConfig> requiredConfigs = mCarPropertyManager.getPropertyList(mPropertyIds);
        // Vehicles need to implement all of those properties
        assertThat(requiredConfigs.size()).isEqualTo(mPropertyIds.size());
    }

    /**
     * Test for {@link CarPropertyManager#getCarPropertyConfig(int)}
     */
    @Test
    public void testGetPropertyConfig() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            assertThat(mCarPropertyManager.getCarPropertyConfig(cfg.getPropertyId())).isNotNull();
        }
    }

    /**
     * Test for {@link CarPropertyManager#getAreaId(int, int)}
     */
    @Test
    public void testGetAreaId() {
        // For global properties, getAreaId should always return 0.
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.isGlobalProperty()) {
                assertThat(mCarPropertyManager.getAreaId(cfg.getPropertyId(),
                        VehicleAreaSeat.SEAT_ROW_1_LEFT))
                        .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
            } else {
                int[] areaIds = cfg.getAreaIds();
                // Because areaId in propConfig must not be overlapped with each other.
                // The result should be itself.
                for (int areaIdInConfig : areaIds) {
                    int areaIdByCarPropertyManager =
                            mCarPropertyManager.getAreaId(cfg.getPropertyId(), areaIdInConfig);
                    assertThat(areaIdByCarPropertyManager).isEqualTo(areaIdInConfig);
                }
            }
        }
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportGearSelection() throws Exception {
        verifyOnchangeCarPropertyConfig(/*requiredProperty=*/true,
                                        VehiclePropertyIds.GEAR_SELECTION,
                                        Integer.class);
        CarPropertyConfig gearSelectionConfig =
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.GEAR_SELECTION);
        List<Integer> gearSelectionArray = gearSelectionConfig.getConfigArray();
        assertWithMessage("GEAR_SELECTION config array must specify supported gears")
                .that(gearSelectionArray.size())
                .isGreaterThan(0);

        verifyCarPropertyValue(VehiclePropertyIds.GEAR_SELECTION, Integer.class);
        CarPropertyValue<Integer> gearSelectionValue =
                mCarPropertyManager.getProperty(
                VehiclePropertyIds.GEAR_SELECTION, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        assertWithMessage("GEAR_SELECTION Integer value must be in configArray")
                .that(gearSelectionArray.contains(gearSelectionValue.getValue())).isTrue();
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportNightMode() {
        assertWithMessage("Must support NIGHT_MODE")
                .that(mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.NIGHT_MODE))
                .isNotNull();
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportPerfVehicleSpeed() throws Exception {
        verifyContinuousCarPropertyConfig(/*requiredProperty=*/true,
                                          VehiclePropertyIds.PERF_VEHICLE_SPEED,
                                          Float.class);

        verifyCarPropertyValue(VehiclePropertyIds.PERF_VEHICLE_SPEED, Float.class);
    }

    @CddTest(requirement = "2.5.1")
    @Test
    public void testMustSupportParkingBrakeOn() throws Exception {
        assertWithMessage("Must support PARKING_BRAKE_ON")
                .that(mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.PARKING_BRAKE_ON))
                .isNotNull();

    }

    @Test
    public void testWheelTickIfSupported() throws Exception {
        verifyContinuousCarPropertyConfig(/*requiredProperty=*/false,
                                          VehiclePropertyIds.WHEEL_TICK,
                                          Long[].class);

        CarPropertyConfig wheelTickConfig =
                mCarPropertyManager.getCarPropertyConfig(VehiclePropertyIds.WHEEL_TICK);
        List<Integer> wheelTickConfigArray = wheelTickConfig.getConfigArray();
        assertWithMessage("WHEEL_TICK config array must be size 5")
                .that(wheelTickConfigArray.size())
                .isEqualTo(5);

        int supportedWheels = wheelTickConfigArray.get(0);
        assertWithMessage(
                "WHEEL_TICK config array first element specifies which wheels are supported")
                .that(supportedWheels).isGreaterThan(VehicleAreaWheel.WHEEL_UNKNOWN);
        assertWithMessage(
                "WHEEL_TICK config array first element specifies which wheels are supported")
                .that(supportedWheels)
                .isAtMost(VehicleAreaWheel.WHEEL_LEFT_FRONT | VehicleAreaWheel.WHEEL_RIGHT_FRONT |
                          VehicleAreaWheel.WHEEL_LEFT_REAR | VehicleAreaWheel.WHEEL_RIGHT_REAR);

        if((supportedWheels & VehicleAreaWheel.WHEEL_LEFT_FRONT) != 0) {
                assertWithMessage(
                         "WHEEL_TICK config array second element specifies ticks to micrometers for front left wheel")
                         .that(wheelTickConfigArray.get(1))
                         .isGreaterThan(0);
        } else {
                assertWithMessage(
                         "WHEEL_TICK config array second element should be zero since front left wheel is not supported")
                         .that(wheelTickConfigArray.get(1))
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_RIGHT_FRONT) != 0) {
                assertWithMessage(
                         "WHEEL_TICK config array third element specifies ticks to micrometers for front right wheel")
                         .that(wheelTickConfigArray.get(2))
                         .isGreaterThan(0);
        } else {
                assertWithMessage(
                         "WHEEL_TICK config array third element should be zero since front right wheel is not supported")
                         .that(wheelTickConfigArray.get(2))
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_RIGHT_REAR) != 0) {
                assertWithMessage(
                         "WHEEL_TICK config array fourth element specifies ticks to micrometers for rear right wheel")
                         .that(wheelTickConfigArray.get(3))
                         .isGreaterThan(0);
        } else {
                assertWithMessage(
                         "WHEEL_TICK config array fourth element should be zero since rear right wheel is not supported")
                         .that(wheelTickConfigArray.get(3))
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_LEFT_REAR) != 0) {
                assertWithMessage(
                         "WHEEL_TICK config array fifth element specifies ticks to micrometers for rear left wheel")
                         .that(wheelTickConfigArray.get(4))
                         .isGreaterThan(0);
        } else {
                assertWithMessage(
                         "WHEEL_TICK config array fifth element should be zero since rear left wheel is not supported")
                         .that(wheelTickConfigArray.get(4))
                         .isEqualTo(0);
        }

        verifyCarPropertyValue(VehiclePropertyIds.WHEEL_TICK, Long[].class);
        CarPropertyValue<Long[]> wheelTickValue =
                mCarPropertyManager.getProperty(
                VehiclePropertyIds.WHEEL_TICK, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        Long[] wheelTicks = wheelTickValue.getValue();
        assertWithMessage("WHEEL_TICK Long[] value must be size 5").that(wheelTicks.length)
                .isEqualTo(5);

        if((supportedWheels & VehicleAreaWheel.WHEEL_LEFT_FRONT) == 0) {
                assertWithMessage(
                         "WHEEL_TICK value array second element should be zero since front left wheel is not supported")
                         .that(wheelTicks[1])
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_RIGHT_FRONT) == 0) {
                assertWithMessage(
                         "WHEEL_TICK value array third element should be zero since front right wheel is not supported")
                         .that(wheelTicks[2])
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_RIGHT_REAR) == 0) {
                assertWithMessage(
                         "WHEEL_TICK value array fourth element should be zero since rear right wheel is not supported")
                         .that(wheelTicks[3])
                         .isEqualTo(0);
        }

        if((supportedWheels & VehicleAreaWheel.WHEEL_LEFT_REAR) == 0) {
                assertWithMessage(
                         "WHEEL_TICK value array fifth element should be zero since rear left wheel is not supported")
                         .that(wheelTicks[4])
                         .isEqualTo(0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testGetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ) {
                int[] areaIds = getAreaIdsHelper(cfg);
                int propId = cfg.getPropertyId();
                // no guarantee if we can get values, just call and check if it throws exception.
                if (cfg.getPropertyType() == Boolean.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getBooleanProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getIntProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Float.class) {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getFloatProperty(propId, areaId);
                    }
                } else if (cfg.getPropertyType() == Integer[].class) {
                    for (int areId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(propId, areId);
                    }
                } else {
                    for (int areaId : areaIds) {
                        mCarPropertyManager.getProperty(
                                cfg.getPropertyType(), propId, areaId);;
                    }
                }
            }
        }
    }

    @Test
    public void testGetIntArrayProperty() {
        List<CarPropertyConfig> allConfigs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : allConfigs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_NONE
                    || cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_WRITE
                    || cfg.getPropertyType() != Integer[].class) {
                // skip the test if the property is not readable or not an int array type property.
                continue;
            }
            switch (cfg.getPropertyId()) {
                case VehiclePropertyIds.INFO_FUEL_TYPE:
                    int[] fuelTypes = mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(),
                            VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_FUEL_TYPES, fuelTypes);
                    break;
                case VehiclePropertyIds.INFO_MULTI_EV_PORT_LOCATIONS:
                    int[] evPortLocations = mCarPropertyManager.getIntArrayProperty(
                            cfg.getPropertyId(),VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
                    verifyEnumsRange(EXPECTED_PORT_LOCATIONS, evPortLocations);
                    break;
                default:
                    int[] areaIds = getAreaIdsHelper(cfg);
                    for(int areaId : areaIds) {
                        mCarPropertyManager.getIntArrayProperty(cfg.getPropertyId(), areaId);
                    }
            }
        }
    }

    private void verifyEnumsRange(List<Integer> expectedResults, int[] results) {
        assertThat(results).isNotNull();
        // If the property is not implemented in cars, getIntArrayProperty returns an empty array.
        if (results.length == 0) {
            return;
        }
        for (int result : results) {
            assertThat(result).isIn(expectedResults);
        }
    }

    @Test
    public void testIsPropertyAvailable() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList(mPropertyIds);

        for (CarPropertyConfig cfg : configs) {
            int[] areaIds = getAreaIdsHelper(cfg);
            for (int areaId : areaIds) {
                assertThat(mCarPropertyManager.isPropertyAvailable(cfg.getPropertyId(), areaId))
                        .isTrue();
            }
        }
    }

    @Test
    public void testSetProperty() {
        List<CarPropertyConfig> configs = mCarPropertyManager.getPropertyList();
        for (CarPropertyConfig cfg : configs) {
            if (cfg.getAccess() == CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ_WRITE
                    && cfg.getPropertyType() == Boolean.class) {
                // In R, there is no property which is writable for third-party apps.
                for (int areaId : getAreaIdsHelper(cfg)) {
                    assertThrows(SecurityException.class,
                            () -> mCarPropertyManager.setBooleanProperty(
                                    cfg.getPropertyId(), areaId,true));
                }
            }
        }
    }

    @Test
    public void testRegisterCallback() throws Exception {
        //Test on registering a invalid property
        int invalidPropertyId = -1;
        boolean isRegistered = mCarPropertyManager.registerCallback(
            new CarPropertyEventCounter(), invalidPropertyId, 0);
        assertThat(isRegistered).isFalse();

        // Test for continuous properties
        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();
        CarPropertyEventCounter speedListenerFast = new CarPropertyEventCounter();

        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerUI.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedEvent(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedError(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);

        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_UI);
        mCarPropertyManager.registerCallback(speedListenerFast, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_FASTEST);
        speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
        speedListenerUI.assertOnChangeEventCalled();
        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isGreaterThan(NO_EVENTS);
        assertThat(speedListenerFast.receivedEvent(vehicleSpeed)).isGreaterThan(
                speedListenerUI.receivedEvent(vehicleSpeed));
        // The test did not change property values, it should not get error with error codes.
        assertThat(speedListenerUI.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);
        assertThat(speedListenerFast.receivedErrorWithErrorCode(vehicleSpeed)).isEqualTo(NO_EVENTS);

        mCarPropertyManager.unregisterCallback(speedListenerFast);
        mCarPropertyManager.unregisterCallback(speedListenerUI);

        // Test for on_change properties
        int nightMode = VehiclePropertyIds.NIGHT_MODE;
        CarPropertyEventCounter nightModeListener = new CarPropertyEventCounter();
        nightModeListener.resetCountDownLatch(ONCHANGE_RATE_EVENT_COUNTER);
        mCarPropertyManager.registerCallback(nightModeListener, nightMode, 0);
        nightModeListener.assertOnChangeEventCalled();
        assertThat(nightModeListener.receivedEvent(nightMode)).isEqualTo(1);
        mCarPropertyManager.unregisterCallback(nightModeListener);

    }

    @Test
    public void testUnregisterCallback() throws Exception {

        int vehicleSpeed = VehiclePropertyIds.PERF_VEHICLE_SPEED;
        CarPropertyEventCounter speedListenerNormal = new CarPropertyEventCounter();
        CarPropertyEventCounter speedListenerUI = new CarPropertyEventCounter();

        mCarPropertyManager.registerCallback(speedListenerNormal, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_NORMAL);

        // test on unregistering a callback that was never registered
        try {
            mCarPropertyManager.unregisterCallback(speedListenerUI);
        } catch (Exception e) {
            Assert.fail();
        }

        mCarPropertyManager.registerCallback(speedListenerUI, vehicleSpeed,
                CarPropertyManager.SENSOR_RATE_UI);
        speedListenerUI.resetCountDownLatch(UI_RATE_EVENT_COUNTER);
        speedListenerUI.assertOnChangeEventCalled();
        mCarPropertyManager.unregisterCallback(speedListenerNormal, vehicleSpeed);

        int currentEventNormal = speedListenerNormal.receivedEvent(vehicleSpeed);
        int currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        speedListenerNormal.assertOnChangeEventNotCalled();

        assertThat(speedListenerNormal.receivedEvent(vehicleSpeed)).isEqualTo(currentEventNormal);
        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isNotEqualTo(currentEventUI);

        mCarPropertyManager.unregisterCallback(speedListenerUI);
        speedListenerUI.assertOnChangeEventNotCalled();

        currentEventUI = speedListenerUI.receivedEvent(vehicleSpeed);
        assertThat(speedListenerUI.receivedEvent(vehicleSpeed)).isEqualTo(currentEventUI);
    }

    @Test
    public void testUnregisterWithPropertyId() throws Exception {
        // Ignores the test if wheel_tick property does not exist in the car.
        Assume.assumeTrue("WheelTick is not available, skip unregisterCallback test",
                mCarPropertyManager.isPropertyAvailable(
                        VehiclePropertyIds.WHEEL_TICK, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL));

        CarPropertyConfig wheelTickConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.WHEEL_TICK);
        CarPropertyConfig speedConfig = mCarPropertyManager.getCarPropertyConfig(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        // Ignores the test if sampleRates for properties are too low.
        Assume.assumeTrue("The SampleRates for properties are too low, "
                + "skip testUnregisterWithPropertyId test",
                wheelTickConfig.getMaxSampleRate() < FAST_OR_FASTEST_EVENT_COUNTER
                        || speedConfig.getMaxSampleRate() < FAST_OR_FASTEST_EVENT_COUNTER);

        CarPropertyEventCounter speedAndWheelTicksListener = new CarPropertyEventCounter();
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED, CarPropertyManager.SENSOR_RATE_FASTEST);
        mCarPropertyManager.registerCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.WHEEL_TICK, CarPropertyManager.SENSOR_RATE_FASTEST);
        speedAndWheelTicksListener.resetCountDownLatch(FAST_OR_FASTEST_EVENT_COUNTER);
        speedAndWheelTicksListener.assertOnChangeEventCalled();

        mCarPropertyManager.unregisterCallback(speedAndWheelTicksListener,
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        speedAndWheelTicksListener.resetCountDownLatch(FAST_OR_FASTEST_EVENT_COUNTER);
        speedAndWheelTicksListener.assertOnChangeEventCalled();
        int currentSpeedEvents = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int currentWheelTickEvents = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        speedAndWheelTicksListener.resetCountDownLatch(FAST_OR_FASTEST_EVENT_COUNTER);
        speedAndWheelTicksListener.assertOnChangeEventCalled();
        int speedEventsAfterUnregister = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.PERF_VEHICLE_SPEED);
        int wheelTicksEventsAfterUnregister = speedAndWheelTicksListener.receivedEvent(
                VehiclePropertyIds.WHEEL_TICK);

        assertThat(currentSpeedEvents).isEqualTo(speedEventsAfterUnregister);
        assertThat(wheelTicksEventsAfterUnregister).isGreaterThan(currentWheelTickEvents);
    }


    // Returns {0} if the property is global property, otherwise query areaId for CarPropertyConfig
    private int[] getAreaIdsHelper(CarPropertyConfig config) {
        if (config.isGlobalProperty()) {
            int[] areaIds = {0};
            return areaIds;
        } else {
            return config.getAreaIds();
        }
    }

    private <T> void verifyCarPropertyConfig(boolean requiredProperty,
                                             int propertyId,
                                             Class<T> propertyType) {
        String propertyName = VehiclePropertyIds.toString(propertyId);
        CarPropertyConfig carPropertyConfig = mCarPropertyManager.getCarPropertyConfig(propertyId);
        if (requiredProperty) {
                assertWithMessage("Must support " + propertyName).that(carPropertyConfig)
                        .isNotNull();
        } else {
                assumeNotNull(carPropertyConfig);
        }
        assertWithMessage(propertyName + " CarPropertyConfig must have correct property ID")
                .that(carPropertyConfig.getPropertyId())
                .isEqualTo(propertyId);
        assertWithMessage(propertyName + " must be READ access")
                .that(carPropertyConfig.getAccess())
                .isEqualTo(CarPropertyConfig.VEHICLE_PROPERTY_ACCESS_READ);
        assertWithMessage(propertyName + " must be GLOBAL area type")
                .that(carPropertyConfig.getAreaType())
                .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        assertWithMessage(propertyName + " must be " + propertyType + " type property")
                .that(carPropertyConfig.getPropertyType()).isEqualTo(propertyType);
    }

    private <T> void verifyContinuousCarPropertyConfig(boolean requiredProperty,
                                                       int propertyId,
                                                       Class<T> propertyType) {
        verifyCarPropertyConfig(requiredProperty, propertyId, propertyType);
        String propertyName = VehiclePropertyIds.toString(propertyId);
        CarPropertyConfig carPropertyConfig = mCarPropertyManager.getCarPropertyConfig(propertyId);
        assertWithMessage(propertyName + " must be CONTINUOUS change mode type")
               .that(carPropertyConfig.getChangeMode())
               .isEqualTo(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_CONTINUOUS);
        assertWithMessage(propertyName + " must define max sample rate since it is CONTINUOUS")
               .that(carPropertyConfig.getMaxSampleRate()).isGreaterThan(0);
        assertWithMessage(propertyName + " must define min sample rate since it is CONTINUOUS")
               .that(carPropertyConfig.getMinSampleRate()).isGreaterThan(0);
        assertWithMessage("PERF_VEHICLE_SPEED max sample rate must be >= min sample rate")
               .that(carPropertyConfig.getMaxSampleRate() >=
                       carPropertyConfig.getMinSampleRate())
               .isTrue();
    }

    private <T> void verifyOnchangeCarPropertyConfig(boolean requiredProperty,
                                                     int propertyId,
                                                     Class<T> propertyType) {
        verifyCarPropertyConfig(requiredProperty, propertyId, propertyType);
        String propertyName = VehiclePropertyIds.toString(propertyId);
        CarPropertyConfig carPropertyConfig = mCarPropertyManager.getCarPropertyConfig(propertyId);
        assertWithMessage(propertyName + " must be ONCHANGE change mode type")
               .that(carPropertyConfig.getChangeMode())
               .isEqualTo(CarPropertyConfig.VEHICLE_PROPERTY_CHANGE_MODE_ONCHANGE);
        assertWithMessage(propertyName + " must define max sample rate as 0 since it is ONCHANGE")
               .that(carPropertyConfig.getMaxSampleRate()).isEqualTo(0);
        assertWithMessage(propertyName + " must define min sample rate as 0 since it is ONCHANGE")
               .that(carPropertyConfig.getMinSampleRate()).isEqualTo(0);
    }

    private <T> void verifyCarPropertyValue(int propertyId, Class<?> propertyClass) {
        String propertyName = VehiclePropertyIds.toString(propertyId);
        long beforeElapsedTimestampNanos = SystemClock.elapsedRealtimeNanos();
        CarPropertyValue<T> carPropertyValue =
                mCarPropertyManager.getProperty(
                propertyId, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        long afterElapsedTimestampNanos = SystemClock.elapsedRealtimeNanos();
        assertWithMessage(propertyName + " value must have correct property ID")
                .that(carPropertyValue.getPropertyId()).isEqualTo(propertyId);
        assertWithMessage(propertyName + " value must have correct area type")
                .that(carPropertyValue.getAreaId())
                .isEqualTo(VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        assertWithMessage(propertyName + " value must be available")
                .that(carPropertyValue.getStatus()).isEqualTo(CarPropertyValue.STATUS_AVAILABLE);
        assertWithMessage(propertyName +
                " timestamp must use the SystemClock.elapsedRealtimeNanos() time base")
                .that(carPropertyValue.getTimestamp())
                .isGreaterThan(beforeElapsedTimestampNanos);
        assertWithMessage(propertyName +
                " timestamp must use the SystemClock.elapsedRealtimeNanos() time base")
                .that(carPropertyValue.getTimestamp()).isLessThan(afterElapsedTimestampNanos);
        assertWithMessage(propertyName + " must return " + propertyClass + " type value")
                .that(carPropertyValue.getValue().getClass()).isEqualTo(propertyClass);
    }
}
