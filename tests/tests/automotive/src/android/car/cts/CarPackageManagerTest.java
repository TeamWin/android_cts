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

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.telecom.TelecomManager;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class CarPackageManagerTest extends CarApiTestBase {

    private CarPackageManager mCarPm;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mCarPm = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
    }

   public void testActivityAllowedWhileDriving() throws Exception {
       assertFalse(mCarPm.isActivityAllowedWhileDriving("com.basic.package", "DummyActivity"));
       // Real system activity is not allowed as well.
       assertFalse(mCarPm.isActivityAllowedWhileDriving("com.android.phone", "CallActivity"));
       assertTrue(mCarPm.isActivityAllowedWhileDriving(
               "com.google.android.car.media", "com.google.android.car.media.MediaProxyActivity"));

       try {
           mCarPm.isActivityAllowedWhileDriving("com.android.settings", null);
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
       try {
           mCarPm.isActivityAllowedWhileDriving(null, "Any");
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
       try {
           mCarPm.isActivityAllowedWhileDriving(null, null);
           fail();
       } catch (IllegalArgumentException expected) {
           // Expected.
       }
   }

    public void testServiceAllowedWhileDriving() throws Exception {
        assertFalse(mCarPm.isServiceAllowedWhileDriving("com.basic.package", ""));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", "Any"));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", ""));
        assertTrue(mCarPm.isServiceAllowedWhileDriving("com.android.settings", null));

        try {
            mCarPm.isServiceAllowedWhileDriving(null, "Any");
            fail();
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
