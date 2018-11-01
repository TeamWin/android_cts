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

import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.android.ddmlib.Log;

import java.util.Scanner;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests ANGLE Debug Option Opt-In/Out functionality.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsAngleDebugOptionHostTest extends BaseHostJUnit4Test implements IDeviceTest {

    private static final String TAG = CtsAngleDebugOptionHostTest.class.getSimpleName();

    // System Properties
    private static final String PROPERTY_DISABLE_OPENGL_PRELOADING = "ro.zygote.disable_gl_preload";
    private static final String PROPERTY_GFX_DRIVER = "ro.gfx.driver.0";

    // ANGLE
    private static final String ANGLE_DEBUG_OPTION_PKG = "com.android.angleIntegrationTest.debugOption";
    private static final String ANGLE_DEBUG_OPTION_CLASS = "AngleDebugOptionActivityTest";
    private static final String ANGLE_DEBUG_OPTION_ON_METHOD = "testDebugOptionOn";
    private static final String ANGLE_DEBUG_OPTION_OFF_METHOD = "testDebugOptionOff";
    private static final String ANGLE_DEBUG_OPTION_APP = "CtsAngleDebugOptionTestCases.apk";

    private boolean isAngleLoadable() throws Exception {
        String propDisablePreloading = getDevice().getProperty(PROPERTY_DISABLE_OPENGL_PRELOADING);
        String propGfxDriver = getDevice().getProperty(PROPERTY_GFX_DRIVER);

        // This logic is attempting to mimic ZygoteInit.java::ZygoteInit#preloadOpenGL()
        if (((propDisablePreloading != null) && propDisablePreloading.equals("true")) &&
            ((propGfxDriver == null) || propGfxDriver.isEmpty())) {
            return false;
        }

        return true;
    }

    private void enableAngle() throws Exception {
        String developerOptionCmd = String.format("settings put global angle_enabled_app %s",
                ANGLE_DEBUG_OPTION_PKG);
        getDevice().executeShellCommand(developerOptionCmd);
    }

    private void disableAngle() throws Exception {
        // FIXME -- b/117554536
        // Once b/117555066 is fixed, the workaround here to set angle_enabled_app to the empty
        // string can be removed and the deletion of the setting can be used instead.
//        getDevice().executeShellCommand("settings delete global angle_enabled_app");
        getDevice().executeShellCommand("settings put global angle_enabled_app ''");
    }

    /**
     * Set up the Manifest file test environment.
     */
    @Before
    public void setUp() throws Exception {
        // Clear any Developer Option values
        disableAngle();

        // Uninstall old apps
        uninstallPackage(getDevice(), ANGLE_DEBUG_OPTION_PKG);
    }

    /**
     * Test ANGLE is loaded when the Debug Option is On.
     */
    @Test
    public void testDebugOptionOn() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        // Disable ANGLE so we have a fresh enable to check
        disableAngle();

        enableAngle();

        installPackage(ANGLE_DEBUG_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEBUG_OPTION_PKG,
                ANGLE_DEBUG_OPTION_PKG + "." + ANGLE_DEBUG_OPTION_CLASS,
                ANGLE_DEBUG_OPTION_ON_METHOD);
    }

    /**
     * Test ANGLE is loaded when the Debug Option is Off.
     */
    @Test
    public void testDebugOptionOff() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        // Enable ANGLE so that disabling it actually has something to disable.
        enableAngle();

        disableAngle();

        installPackage(ANGLE_DEBUG_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEBUG_OPTION_PKG,
                ANGLE_DEBUG_OPTION_PKG + "." + ANGLE_DEBUG_OPTION_CLASS,
                ANGLE_DEBUG_OPTION_OFF_METHOD);
    }
}
