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

package android.cts.gwp_asan;

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class EnabledTest extends BaseHostJUnit4Test {
    private static final String TEST_APK = "CtsGwpAsanEnabled.apk";
    private static final String TEST_PKG = "android.cts.gwp_asan";
    private ITestDevice mDevice;

    @Before
    public void setUp() throws Exception {
        mDevice = getDevice();
        installPackage(TEST_APK, new String[0]);
    }

    @After
    public void tearDown() throws Exception {
        uninstallPackage(mDevice, TEST_PKG);
    }

    @Test
    public void testGwpAsanEnabled() throws Exception {
        Assert.assertTrue(
                runDeviceTests(TEST_PKG, TEST_PKG + ".GwpAsanActivityTest", "testEnablement"));
        Assert.assertTrue(
                runDeviceTests(TEST_PKG, TEST_PKG + ".GwpAsanServiceTest", "testEnablement"));
    }
}
