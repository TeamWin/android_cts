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

package android.appenumeration.cts;

import static android.appenumeration.cts.Constants.TEST_PKG;

import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AppEnumerationHostsideTests extends BaseHostJUnit4Test {

    private static final String TEST_CLASS = "android.appenumeration.cts.AppEnumerationTests";

    @Test
    public void filtersVisibleAfterReboot() throws Exception {
        assertMethod("Baseline, pre-boot filter-based visibility test should pass",
                "queriesActivityAction_canSeeFilters");
        getDevice().reboot();
        assertMethod("Filter-based visibility test should pass after reboot",
                "queriesActivityAction_canSeeFilters");
    }

    private void assertMethod(String message, String method)
            throws DeviceNotAvailableException {
        assertTrue(message,
                runDeviceTests(TEST_PKG, TEST_CLASS, method));
    }

}
