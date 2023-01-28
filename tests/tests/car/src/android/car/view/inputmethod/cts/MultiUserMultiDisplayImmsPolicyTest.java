/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.car.view.inputmethod.cts;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireVisibleBackgroundUsers;

import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

@RequireVisibleBackgroundUsers(reason = "Policy test for multi-user / multi-display IMMS")
public final class MultiUserMultiDisplayImmsPolicyTest {

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    @Test
    public void testDriverCanLaunchIMEInDriverDisplay() {
        // STOPSHIP(b/266740047): Implement the test
        // Check if the system has the driver.
    }

    @Test
    public void testPassengerCanLaunchIMEInPassengerDisplay() {
        // STOPSHIP(b/266740047): Implement the test
        // Check if the system has the passenger.
    }

    @Test
    public void testAutofillSuggestionsShouldNotSharedAmongDriverAndPassengers() {
        // STOPSHIP(b/266740047): Implement the test
        // Check if the system has the driver and the passenger.
    }
}
