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

    private String getDevOption(String devOption) throws Exception {
        return getDevice().getSetting("global", devOption);
    }

    private void setAndValidateAngleDevOptionPkgDriver(String pkgName, String driverValue) throws Exception {
        CLog.logAndDisplay(LogLevel.INFO, "Updating Global.Settings: pkgName = '" +
                pkgName + "', driverValue = '" + driverValue + "'");

        getDevice().setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS, pkgName);
        getDevice().setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES, driverValue);

        String devOption = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
        Assert.assertEquals(
                "Developer option '" + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS +
                        "' was not set correctly: '" + devOption + "'",
                pkgName, devOption);

        devOption = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
        Assert.assertEquals(
                "Developer option '" + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES +
                        "' was not set correctly: '" + driverValue + "'",
                driverValue, devOption);
    }

    private void setAndValidatePkgDriver(String pkgName, CtsAngleCommon.OpenGlDriverChoice driver) throws Exception {
        CtsAngleCommon.stopPackage(getDevice(), pkgName);

        setAndValidateAngleDevOptionPkgDriver(pkgName, CtsAngleCommon.sDriverGlobalSettingMap.get(driver));

        CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

        CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                driver + ") with method '" + CtsAngleCommon.sDriverTestMethodMap.get(driver) + "'");

        runDeviceTests(
                pkgName,
                pkgName + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.sDriverTestMethodMap.get(driver));
    }

    @Before
    public void setUp() throws Exception {
        CtsAngleCommon.clearSettings(getDevice());

        CtsAngleCommon.stopPackage(getDevice(), CtsAngleCommon.ANGLE_PKG);
        CtsAngleCommon.stopPackage(getDevice(), CtsAngleCommon.ANGLE_DRIVER_TEST_PKG);
        CtsAngleCommon.stopPackage(getDevice(), CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG);
    }

    @After
    public void tearDown() throws Exception {
        CtsAngleCommon.clearSettings(getDevice());
    }

    /**
     * Test ANGLE is loaded when the 'Use ANGLE for all' Developer Option is enabled.
     */
    @Test
    public void testEnableAngleForAll() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.DEFAULT));
        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.DEFAULT));

        getDevice().setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_ALL_USE_ANGLE, "1");

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_ANGLE_METHOD);
        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_ANGLE_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is set to 'default'.
     */
    @Test
    public void testUseDefaultDriver() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.DEFAULT));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_DEFAULT_METHOD);
    }

    /**
     * Test ANGLE is loaded when the Developer Option is set to 'angle'.
     */
    @Test
    public void testUseAngleDriver() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_ANGLE_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is set to 'native'.
     */
    @Test
    public void testUseNativeDriver() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.NATIVE));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_NATIVE_METHOD);
    }

    /**
     * Test ANGLE is not loaded for any apps when the Developer Option list lengths mismatch.
     */
    @Test
    public void testSettingsLengthMismatch() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," +
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_DEFAULT_METHOD);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_DEFAULT_METHOD);
    }

    /**
     * Test ANGLE is not loaded when the Developer Option is invalid.
     */
    @Test
    public void testUseInvalidDriver() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG, "timtim");

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        runDeviceTests(
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                CtsAngleCommon.ANGLE_DRIVER_TEST_DEFAULT_METHOD);
    }

    /**
     * Test the Developer Options can be updated to/from each combination.
     */
    @Test
    public void testUpdateDriverValues() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        for (CtsAngleCommon.OpenGlDriverChoice firstDriver : CtsAngleCommon.OpenGlDriverChoice.values()) {
            for (CtsAngleCommon.OpenGlDriverChoice secondDriver : CtsAngleCommon.OpenGlDriverChoice.values()) {
                CLog.logAndDisplay(LogLevel.INFO, "Testing updating Global.Settings from '" +
                        firstDriver + "' to '" + secondDriver + "'");

                setAndValidatePkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG, firstDriver);
                setAndValidatePkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG, secondDriver);
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
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," +
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE) + "," +
                        CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.NATIVE));

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

    /**
     * Test the Developer Options for a second PKG can be updated to/from each combination.
     */
    @Test
    public void testMultipleUpdateDriverValues() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        // Set the first PKG to always use ANGLE
        setAndValidatePkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG, CtsAngleCommon.OpenGlDriverChoice.ANGLE);

        for (CtsAngleCommon.OpenGlDriverChoice firstDriver : CtsAngleCommon.OpenGlDriverChoice.values()) {
            for (CtsAngleCommon.OpenGlDriverChoice secondDriver : CtsAngleCommon.OpenGlDriverChoice.values()) {
                CLog.logAndDisplay(LogLevel.INFO, "Testing updating Global.Settings from '" +
                        firstDriver + "' to '" + secondDriver + "'");

                setAndValidateAngleDevOptionPkgDriver(
                        CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," + CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                        CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE) + "," +
                                CtsAngleCommon.sDriverGlobalSettingMap.get(firstDriver));

                CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

                CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                        firstDriver + ") with method '" + CtsAngleCommon.sDriverTestMethodMap.get(firstDriver) + "'");

                runDeviceTests(
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                        CtsAngleCommon.sDriverTestMethodMap.get(firstDriver));

                setAndValidateAngleDevOptionPkgDriver(
                        CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," + CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                        CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE) + "," +
                                CtsAngleCommon.sDriverGlobalSettingMap.get(secondDriver));

                CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

                CLog.logAndDisplay(LogLevel.INFO, "Validating driver selection (" +
                        secondDriver + ") with method '" + CtsAngleCommon.sDriverTestMethodMap.get(secondDriver) + "'");

                runDeviceTests(
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                        CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                        CtsAngleCommon.sDriverTestMethodMap.get(secondDriver));

                // Make sure the first PKG's driver value was not modified
                CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

                String devOptionPkg = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
                String devOptionValue = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
                CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                        devOptionPkg + "', driver value = '" + devOptionValue + "'");

                runDeviceTests(
                        CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                        CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "." + CtsAngleCommon.ANGLE_DRIVER_TEST_CLASS,
                        CtsAngleCommon.ANGLE_DRIVER_TEST_ANGLE_METHOD);
            }
        }
    }

    /**
     * Test setting a driver to 'default' does not keep the value in the settings when the ANGLE
     * activity runs and cleans things up.
     */
    @Test
    public void testDefaultNotInSettings() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.DEFAULT));

        // Install the package so the setting isn't removed because the package isn't present.
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                "", devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
                "", devOptionValue);
    }

    /**
     * Test uninstalled PKGs have their settings removed.
     */
    @Test
    public void testUninstalledPkgsNotInSettings() throws Exception {
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        uninstallPackage(getDevice(), CtsAngleCommon.ANGLE_DRIVER_TEST_PKG);

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.NATIVE));

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                "", devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
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
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," + CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE) + "," +
                        CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.DEFAULT));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_APP, new String[0]);
        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

        String devOption = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOption + "'",
                CtsAngleCommon.ANGLE_DRIVER_TEST_PKG, devOption);

        devOption = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOption + "'",
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE), devOption);
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
        Assume.assumeTrue(CtsAngleCommon.isAngleLoadable(getDevice()));

        setAndValidateAngleDevOptionPkgDriver(CtsAngleCommon.ANGLE_DRIVER_TEST_PKG + "," + CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG,
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.ANGLE) + "," +
                        CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.NATIVE));

        installPackage(CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_APP, new String[0]);

        // Run the ANGLE activity so it'll clear up any 'default' settings.
        CtsAngleCommon.startActivity(getDevice(), CtsAngleCommon.ANGLE_MAIN_ACTIVTY);

        String devOptionPkg = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS);
        String devOptionValue = getDevOption(CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES);
        CLog.logAndDisplay(LogLevel.INFO, "Validating: PKG name = '" +
                devOptionPkg + "', driver value = '" + devOptionValue + "'");

        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS + " = '" + devOptionPkg + "'",
                CtsAngleCommon.ANGLE_DRIVER_TEST_SEC_PKG, devOptionPkg);
        Assert.assertEquals(
                "Invalid developer option: " + CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES + " = '" + devOptionValue + "'",
                CtsAngleCommon.sDriverGlobalSettingMap.get(CtsAngleCommon.OpenGlDriverChoice.NATIVE), devOptionValue);
    }
}
