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

import com.android.ddmlib.Log.LogLevel;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.PackageInfo;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IDeviceTest;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import com.android.ddmlib.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.junit.After;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests ANGLE Developer Option Opt-In/Out functionality.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class CtsAngleDeveloperOptionHostTest extends BaseHostJUnit4Test implements IDeviceTest {

    private static final String TAG = CtsAngleDeveloperOptionHostTest.class.getSimpleName();

    // Settings.Global
    private static final String SETTINGS_GLOBAL_ALL_USE_ANGLE = "angle_gl_driver_all_angle";
    private static final String SETTINGS_GLOBAL_DRIVER_PKGS = "angle_gl_driver_selection_pkgs";
    private static final String SETTINGS_GLOBAL_DRIVER_VALUES = "angle_gl_driver_selection_values";

    // System Properties
    private static final String PROPERTY_DISABLE_OPENGL_PRELOADING = "ro.zygote.disable_gl_preload";
    private static final String PROPERTY_GFX_DRIVER = "ro.gfx.driver.0";

    // ANGLE
    private static final String ANGLE_PKG = "com.google.android.angle";
    private static final String ANGLE_DEV_OPTION_PKG = "com.android.angleIntegrationTest.developerOption";
    private static final String ANGLE_DEV_OPTION_SEC_PKG = "com.android.angleIntegrationTest.developerOptionSecondary";
    private static final String ANGLE_DEV_OPTION_CLASS = "AngleDeveloperOptionActivityTest";
    private static final String ANGLE_DEV_OPTION_DEFAULT_METHOD = "testUseDefaultDriver";
    private static final String ANGLE_DEV_OPTION_ANGLE_METHOD = "testUseAngleDriver";
    private static final String ANGLE_DEV_OPTION_NATIVE_METHOD = "testUseNativeDriver";
    private static final String ANGLE_DEV_OPTION_APP = "CtsAngleDeveloperOptionTestCases.apk";
    private static final String ANGLE_DEV_OPTION_SEC_APP = "CtsAngleDeveloperOptionSecondaryTestCases.apk";
    private static final String ANGLE_DEV_OPTION_ACTIVITY =
            ANGLE_DEV_OPTION_PKG + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";
    private static final String ANGLE_DEV_OPTION_SEC_ACTIVITY =
            ANGLE_DEV_OPTION_SEC_PKG + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";
    private static final String ANGLE_MAIN_ACTIVTY = ANGLE_PKG + "/.MainActivity";

    enum OpenGlDriverChoice {
        DEFAULT,
        NATIVE,
        ANGLE
    }

    private static final Map<OpenGlDriverChoice, String> sDriverGlobalSettingMap = buildDriverGlobalSettingMap();
    private static Map<OpenGlDriverChoice, String> buildDriverGlobalSettingMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, "default");
        map.put(OpenGlDriverChoice.ANGLE, "angle");
        map.put(OpenGlDriverChoice.NATIVE, "native");

        return map;
    }

    private static final Map<OpenGlDriverChoice, String> sDriverTestMethodMap = buildDriverTestMethodMap();
    private static Map<OpenGlDriverChoice, String> buildDriverTestMethodMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, ANGLE_DEV_OPTION_DEFAULT_METHOD);
        map.put(OpenGlDriverChoice.ANGLE, ANGLE_DEV_OPTION_ANGLE_METHOD);
        map.put(OpenGlDriverChoice.NATIVE, ANGLE_DEV_OPTION_NATIVE_METHOD);

        return map;
    }

    private boolean isAngleLoadable() throws Exception {
        PackageInfo anglePkgInfo = getDevice().getAppPackageInfo(ANGLE_PKG);
        String propDisablePreloading = getDevice().getProperty(PROPERTY_DISABLE_OPENGL_PRELOADING);
        String propGfxDriver = getDevice().getProperty(PROPERTY_GFX_DRIVER);

        // Make sure ANGLE exists on the device
        if(anglePkgInfo == null) {
            return false;
        }

        // This logic is attempting to mimic ZygoteInit.java::ZygoteInit#preloadOpenGL()
        if (((propDisablePreloading != null) && propDisablePreloading.equals("false")) &&
            ((propGfxDriver == null) || propGfxDriver.isEmpty())) {
            return false;
        }

        return true;
    }

    private String getDevOption(String devOption) throws Exception {
        return getDevice().getSetting("global", devOption);
    }

    private void setAndValidateAngleDevOptionPkgDriver(String pkgName, String driverValue) throws Exception {
        CLog.logAndDisplay(LogLevel.INFO, "Updating Global.Settings: pkgName = '" +
                pkgName + "', driverValue = '" + driverValue + "'");

        getDevice().setSetting("global", SETTINGS_GLOBAL_DRIVER_PKGS, pkgName);
        getDevice().setSetting("global", SETTINGS_GLOBAL_DRIVER_VALUES, driverValue);

        String devOption = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
        Assert.assertEquals(
                "Developer option '" + SETTINGS_GLOBAL_DRIVER_PKGS +
                        "' was not set correctly: '" + devOption + "'",
                pkgName, devOption);

        devOption = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
        Assert.assertEquals(
                "Developer option '" + SETTINGS_GLOBAL_DRIVER_VALUES +
                        "' was not set correctly: '" + driverValue + "'",
                driverValue, devOption);
    }

    private void startActivity(String activity) throws Exception {
        // Run the ANGLE activity so it'll clear up any 'default' settings.
        getDevice().executeShellCommand("am start -S -W -n \"" + activity + "\"");
    }

    private void stopPackage(String pkgName) throws Exception {
        getDevice().executeShellCommand("am force-stop " + pkgName);
    }

    private void setAndValidatePkgDriver(String pkgName, OpenGlDriverChoice driver) throws Exception {
        stopPackage(pkgName);

        setAndValidateAngleDevOptionPkgDriver(pkgName, sDriverGlobalSettingMap.get(driver));

        startActivity(ANGLE_MAIN_ACTIVTY);

        CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                driver + ") with method '" + sDriverTestMethodMap.get(driver) + "'");

        runDeviceTests(
                pkgName,
                pkgName + "." + ANGLE_DEV_OPTION_CLASS,
                sDriverTestMethodMap.get(driver));
    }

    @Before
    public void setUp() throws Exception {
        stopPackage(ANGLE_PKG);
        stopPackage(ANGLE_DEV_OPTION_PKG);
        stopPackage(ANGLE_DEV_OPTION_SEC_PKG);
        getDevice().setSetting("global", SETTINGS_GLOBAL_ALL_USE_ANGLE, "0");
    }

    /**
     * Test ANGLE is loaded when the 'Use ANGLE for all' Developer Option is enabled.
     */
    @Test
    public void testEnableAngleForAll() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.DEFAULT));
        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_SEC_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.DEFAULT));

        getDevice().setSetting("global", SETTINGS_GLOBAL_ALL_USE_ANGLE, "1");

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);
        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_ANGLE_METHOD);
        runDeviceTests(
                ANGLE_DEV_OPTION_SEC_PKG,
                ANGLE_DEV_OPTION_SEC_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_ANGLE_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is set to 'default'.
     */
    @Test
    public void testUseDefaultDriver() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.DEFAULT));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_DEFAULT_METHOD);
    }

    /**
     * Test ANGLE is loaded when the Developer Option is set to 'angle'.
     */
    @Test
    public void testUseAngleDriver() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_ANGLE_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is set to 'native'.
     */
    @Test
    public void testUseNativeDriver() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.NATIVE));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_NATIVE_METHOD);
    }

    /**
     * Test ANGLE is not loaded for any apps when the Developer Option list lengths mismatch.
     */
    @Test
    public void testSettingsLengthMismatch() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);
        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_DEFAULT_METHOD);

        runDeviceTests(
                ANGLE_DEV_OPTION_SEC_PKG,
                ANGLE_DEV_OPTION_SEC_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_DEFAULT_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is invalid.
     */
    @Test
    public void testUseInvalidDriver() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG, "timtim");

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_DEFAULT_METHOD);
    }

    /**
     * Test the Developer Options can be updated to/from each combination.
     */
    @Test
    public void testUpdateDriverValues() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        for (OpenGlDriverChoice firstDriver : OpenGlDriverChoice.values()) {
            for (OpenGlDriverChoice secondDriver : OpenGlDriverChoice.values()) {
                CLog.logAndDisplay(LogLevel.INFO, "Testing updating Global.Settings from '" +
                        firstDriver + "' to '" + secondDriver + "'");

                setAndValidatePkgDriver(ANGLE_DEV_OPTION_PKG, firstDriver);
                setAndValidatePkgDriver(ANGLE_DEV_OPTION_PKG, secondDriver);
            }
        }
    }

    /**
     * Test different PKGs can have different developer option values.
     * Primary: ANGLE
     * Secondary: Native
     */
    @Test
    public void testMultipleDevOptionsAngleNative() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE) + "," +
                        sDriverGlobalSettingMap.get(OpenGlDriverChoice.NATIVE));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);
        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        runDeviceTests(
                ANGLE_DEV_OPTION_PKG,
                ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_ANGLE_METHOD);

        runDeviceTests(
                ANGLE_DEV_OPTION_SEC_PKG,
                ANGLE_DEV_OPTION_SEC_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                ANGLE_DEV_OPTION_NATIVE_METHOD);
    }

    /**
     * Test the Developer Options for a second PKG can be updated to/from each combination.
     */
    @Test
    public void testMultipleUpdateDriverValues() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);
        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        // Set the first PKG to always use ANGLE
        setAndValidatePkgDriver(ANGLE_DEV_OPTION_PKG, OpenGlDriverChoice.ANGLE);

        for (OpenGlDriverChoice firstDriver : OpenGlDriverChoice.values()) {
            for (OpenGlDriverChoice secondDriver : OpenGlDriverChoice.values()) {
                CLog.logAndDisplay(LogLevel.INFO, "Testing updating Global.Settings from '" +
                        firstDriver + "' to '" + secondDriver + "'");

                setAndValidateAngleDevOptionPkgDriver(
                        ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                        sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE) + "," +
                                sDriverGlobalSettingMap.get(firstDriver));

                startActivity(ANGLE_MAIN_ACTIVTY);

                CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                        firstDriver + ") with method '" + sDriverTestMethodMap.get(firstDriver) + "'");

                runDeviceTests(
                        ANGLE_DEV_OPTION_SEC_PKG,
                        ANGLE_DEV_OPTION_SEC_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                        sDriverTestMethodMap.get(firstDriver));

                setAndValidateAngleDevOptionPkgDriver(
                        ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                        sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE) + "," +
                                sDriverGlobalSettingMap.get(secondDriver));

                startActivity(ANGLE_MAIN_ACTIVTY);

                CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                        secondDriver + ") with method '" + sDriverTestMethodMap.get(secondDriver) + "'");

                runDeviceTests(
                        ANGLE_DEV_OPTION_SEC_PKG,
                        ANGLE_DEV_OPTION_SEC_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                        sDriverTestMethodMap.get(secondDriver));

                // Make sure the first PKG's driver value was not modified
                startActivity(ANGLE_MAIN_ACTIVTY);

                String devOptionPkg = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
                String devOptionValue = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
                CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                        devOptionPkg + "', driver value = '" + devOptionValue + "'");

                runDeviceTests(
                        ANGLE_DEV_OPTION_PKG,
                        ANGLE_DEV_OPTION_PKG + "." + ANGLE_DEV_OPTION_CLASS,
                        ANGLE_DEV_OPTION_ANGLE_METHOD);
            }
        }
    }

    /**
     * Test setting a driver to 'default' does not keep the value in the settings when the ANGLE
     * activity runs and cleans things up.
     */
    @Test
    public void testDefaultNotInSettings() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.DEFAULT));

        // Install the package so the setting isn't removed because the package isn't present.
        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        startActivity(ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                "", devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
                "", devOptionValue);
    }

    /**
     * Test uninstalled PKGs have their settings removed.
     */
    @Test
    public void testUninstalledPkgsNotInSettings() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        uninstallPackage(getDevice(), ANGLE_DEV_OPTION_PKG);

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.NATIVE));

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        startActivity(ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                "", devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
                "", devOptionValue);
    }

    /**
     * Test different PKGs can have different developer option values.
     * Primary: ANGLE
     * Secondary: Default
     *
     * Verify the PKG set to 'default' is removed from the settings.
     */
    @Test
    public void testMultipleDevOptionsAngleDefault() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE) + "," +
                        sDriverGlobalSettingMap.get(OpenGlDriverChoice.DEFAULT));

        installPackage(ANGLE_DEV_OPTION_APP, new String[0]);
        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        startActivity(ANGLE_MAIN_ACTIVTY);

        String devOption = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOption + "'",
                ANGLE_DEV_OPTION_PKG, devOption);

        devOption = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOption + "'",
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE), devOption);
    }

    /**
     * Test different PKGs can have different developer option values.
     * Primary: ANGLE
     * Secondary: Default
     *
     * Verify the uninstalled PKG is removed from the settings.
     */
    @Test
    public void testMultipleDevOptionsAngleNativeUninstall() throws Exception {
        Assume.assumeTrue(isAngleLoadable());

        setAndValidateAngleDevOptionPkgDriver(ANGLE_DEV_OPTION_PKG + "," + ANGLE_DEV_OPTION_SEC_PKG,
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.ANGLE) + "," +
                        sDriverGlobalSettingMap.get(OpenGlDriverChoice.NATIVE));

        installPackage(ANGLE_DEV_OPTION_SEC_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        startActivity(ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                ANGLE_DEV_OPTION_SEC_PKG, devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
                sDriverGlobalSettingMap.get(OpenGlDriverChoice.NATIVE), devOptionValue);
    }
}
