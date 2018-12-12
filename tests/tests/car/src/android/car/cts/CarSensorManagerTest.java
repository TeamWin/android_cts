/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;

import android.car.Car;
import android.car.hardware.CarSensorEvent;
import android.car.hardware.CarSensorManager;
import android.platform.test.annotations.RequiresDevice;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.compatibility.common.util.CddTest;

import java.util.stream.IntStream;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RequiresDevice
@RunWith(AndroidJUnit4.class)
public class CarSensorManagerTest extends CarApiTestBase {

    private int[] mSupportedSensors;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        CarSensorManager carSensorManager =
                (CarSensorManager) getCar().getCarManager(Car.SENSOR_SERVICE);
        mSupportedSensors = carSensorManager.getSupportedSensors();
        assertNotNull(mSupportedSensors);
    }

    @CddTest(requirement="2.5.1")
    @Test
    @Ignore // Enable when b/120125891 is fixed
    public void testRequiredSensorsForDrivingState() throws Exception {
        boolean foundSpeed = false;
        boolean foundGear = false;
        for (int sensor: mSupportedSensors) {
            if (sensor == CarSensorManager.SENSOR_TYPE_CAR_SPEED) {
                foundSpeed = true;
            } else if ( sensor == CarSensorManager.SENSOR_TYPE_GEAR) {
                foundGear = true;
            }
            if (foundGear && foundSpeed) {
                break;
            }
        }
        assertTrue(foundGear && foundSpeed);
    }

    @CddTest(requirement="2.5.1")
    @Test
    public void testMustSupportNightSensor() {
        assertTrue("Must support SENSOR_TYPE_NIGHT",
                IntStream.of(mSupportedSensors)
                        .anyMatch(x -> x == CarSensorManager.SENSOR_TYPE_NIGHT));
    }
}
