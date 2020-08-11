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

import com.android.cts.input.HidDevice;
import com.android.cts.input.HidTestData;

import java.util.List;

public class InputHidTestCase extends InputTestCase {
    private static final String TAG = "InputHidTestCase";
    private HidDevice mHidDevice;

    InputHidTestCase(int registerResourceId) {
        super(registerResourceId);
    }

    @Override
    protected void setUpDevice(int deviceId, String registerCommand) {
        mHidDevice = new HidDevice(mInstrumentation, deviceId, registerCommand);
    }

    @Override
    protected void tearDownDevice() {
        mHidDevice.close();
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
}
