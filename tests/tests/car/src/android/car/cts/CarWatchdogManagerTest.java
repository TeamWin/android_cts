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

package android.car.cts;

import android.car.Car;
import android.car.watchdog.CarWatchdogManager;

import org.junit.Before;
import org.junit.Test;

public class CarWatchdogManagerTest extends CarApiTestBase {
    private static String TAG = CarWatchdogManagerTest.class.getSimpleName();

    private CarWatchdogManager mCarWatchdogManager;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mCarWatchdogManager = (CarWatchdogManager) getCar().getCarManager(Car.CAR_WATCHDOG_SERVICE);
    }

    @Test
    public void testListenIoOveruse() {
        /**
         * TODO(b/178199164): Listen for disk I/O overuse.
         *  1. Add resource overuse listener for I/O resource.
         *  2. Write huge amount of data to disk such that it exceeds the threshold.
         *  3. Fetch the I/O overuse stats and check whether the written bytes are >= total written
         *     bytes.
         *  4. Check whether the resource overuse listener is called and the provided written bytes
         *     are >= total written bytes.
         *  5. Remove the resource overuse listener.
         */
    }
}
