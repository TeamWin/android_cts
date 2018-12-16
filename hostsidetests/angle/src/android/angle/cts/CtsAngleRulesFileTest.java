/*
 * Copyright (C) 2018 The Android Open Source Project
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
package android.angle.cts;

import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.util.FileUtil;

import java.io.File;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.Test;

/**
 * Tests ANGLE Rules File Opt-In/Out functionality.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsAngleRulesFileTest extends BaseHostJUnit4Test {

    private final String TAG = this.getClass().getSimpleName();

    File mRulesFile;

    // Rules Files
    private static final String RULES_FILE_EMPTY = "emptyRules.json";
    private static final String RULES_FILE_ENABLE_ANGLE = "enableAngleRules.json";

    private void pushRulesFile(String hostFilename) throws Exception {
        byte[] rulesFileBytes =
                ByteStreams.toByteArray(getClass().getResourceAsStream("/" + hostFilename));

        Assert.assertTrue("Loaded empty rules file", rulesFileBytes.length > 0); // sanity check
        mRulesFile = File.createTempFile("rulesFile", "tempRules.json");
        Files.write(rulesFileBytes, mRulesFile);

        Assert.assertTrue(getDevice().pushFile(mRulesFile, CtsAngleCommon.DEVICE_TEMP_RULES_FILE_PATH));

        CtsAngleCommon.setProperty(getDevice(), CtsAngleCommon.PROPERTY_TEMP_RULES_FILE,
                CtsAngleCommon.DEVICE_TEMP_RULES_FILE_PATH);
    }

    @Before
    public void setUp() throws Exception {
        CtsAngleCommon.clearSettings(getDevice());

        // Enable checking the rules file
        CtsAngleCommon.setProperty(getDevice(), CtsAngleCommon.PROPERTY_ENABLE_RULES_FILE, "1");
    }

    @After
    public void tearDown() throws Exception {
        CtsAngleCommon.clearSettings(getDevice());

        FileUtil.deleteFile(mRulesFile);
    }

    /**
     * Test ANGLE is not loaded when an empty rules file is used.
     */
    @Test
    public void testEmptyRulesFile() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        pushRulesFile(RULES_FILE_EMPTY);

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_NATIVE_METHOD);
    }

    /**
     * Test ANGLE is loaded for only the PKG the rules file specifies.
     */
    @Test
    public void testEnableAngleRulesFile() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        pushRulesFile(RULES_FILE_ENABLE_ANGLE);

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_ANGLE_METHOD);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_NATIVE_METHOD);
    }
}