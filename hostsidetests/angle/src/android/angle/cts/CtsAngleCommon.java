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

import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.device.PackageInfo;

import java.util.HashMap;
import java.util.Map;

public class CtsAngleCommon {
    // Settings.Global
    static final String SETTINGS_GLOBAL_ALL_USE_ANGLE = "angle_gl_driver_all_angle";
    static final String SETTINGS_GLOBAL_DRIVER_PKGS = "angle_gl_driver_selection_pkgs";
    static final String SETTINGS_GLOBAL_DRIVER_VALUES = "angle_gl_driver_selection_values";

    // System Properties
    static final String PROPERTY_BUILD_TYPE = "ro.build.type";
    static final String PROPERTY_DISABLE_OPENGL_PRELOADING = "ro.zygote.disable_gl_preload";
    static final String PROPERTY_GFX_DRIVER = "ro.gfx.driver.0";
    static final String PROPERTY_TEMP_RULES_FILE = "debug.angle.rules";
    static final String PROPERTY_ENABLE_RULES_FILE = "debug.angle.enable";

    // Rules File
    static final String DEVICE_TEMP_RULES_FILE_DIRECTORY = "/data/local/tmp";
    static final String DEVICE_TEMP_RULES_FILE_FILENAME = "a4a_rules.json";
    static final String DEVICE_TEMP_RULES_FILE_PATH = DEVICE_TEMP_RULES_FILE_DIRECTORY + "/" + DEVICE_TEMP_RULES_FILE_FILENAME;

    // ANGLE
    static final String ANGLE_PKG = "com.google.android.angle";
    static final String ANGLE_DRIVER_TEST_PKG = "com.android.angleIntegrationTest.driverTest";
    static final String ANGLE_DRIVER_TEST_SEC_PKG = "com.android.angleIntegrationTest.driverTestSecondary";
    static final String ANGLE_DRIVER_TEST_CLASS = "AngleDriverTestActivity";
    static final String ANGLE_DRIVER_TEST_DEFAULT_METHOD = "testUseDefaultDriver";
    static final String ANGLE_DRIVER_TEST_ANGLE_METHOD = "testUseAngleDriver";
    static final String ANGLE_DRIVER_TEST_NATIVE_METHOD = "testUseNativeDriver";
    static final String ANGLE_DRIVER_TEST_APP = "CtsAngleDriverTestCases.apk";
    static final String ANGLE_DRIVER_TEST_SEC_APP = "CtsAngleDriverTestCasesSecondary.apk";
    static final String ANGLE_DRIVER_TEST_ACTIVITY =
            ANGLE_DRIVER_TEST_PKG + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";
    static final String ANGLE_DRIVER_TEST_SEC_ACTIVITY =
            ANGLE_DRIVER_TEST_SEC_PKG + "/com.android.angleIntegrationTest.common.AngleIntegrationTestActivity";
    static final String ANGLE_MAIN_ACTIVTY = "android.app.action.ANGLE_FOR_ANDROID";

    enum OpenGlDriverChoice {
        DEFAULT,
        NATIVE,
        ANGLE
    }

    static final Map<OpenGlDriverChoice, String> sDriverGlobalSettingMap = buildDriverGlobalSettingMap();
    static Map<OpenGlDriverChoice, String> buildDriverGlobalSettingMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, "default");
        map.put(OpenGlDriverChoice.ANGLE, "angle");
        map.put(OpenGlDriverChoice.NATIVE, "native");

        return map;
    }

    static final Map<OpenGlDriverChoice, String> sDriverTestMethodMap = buildDriverTestMethodMap();
    static Map<OpenGlDriverChoice, String> buildDriverTestMethodMap() {
        Map<OpenGlDriverChoice, String> map = new HashMap<>();
        map.put(OpenGlDriverChoice.DEFAULT, ANGLE_DRIVER_TEST_DEFAULT_METHOD);
        map.put(OpenGlDriverChoice.ANGLE, ANGLE_DRIVER_TEST_ANGLE_METHOD);
        map.put(OpenGlDriverChoice.NATIVE, ANGLE_DRIVER_TEST_NATIVE_METHOD);

        return map;
    }

    static void clearSettings(ITestDevice device) throws Exception {
        device.setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_ALL_USE_ANGLE, "0");
        device.setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_PKGS, "\"\"");
        device.setSetting("global", CtsAngleCommon.SETTINGS_GLOBAL_DRIVER_VALUES, "\"\"");
        CtsAngleCommon.setProperty(device, CtsAngleCommon.PROPERTY_TEMP_RULES_FILE, "\"\"");
        CtsAngleCommon.setProperty(device, CtsAngleCommon.PROPERTY_ENABLE_RULES_FILE, "0");
    }

    static boolean isAngleLoadable(ITestDevice device) throws Exception {
        PackageInfo anglePkgInfo = device.getAppPackageInfo(ANGLE_PKG);
        String propDisablePreloading = device.getProperty(PROPERTY_DISABLE_OPENGL_PRELOADING);
        String propGfxDriver = device.getProperty(PROPERTY_GFX_DRIVER);

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

    static void startActivity(ITestDevice device, String action) throws Exception {
        // Run the ANGLE activity so it'll clear up any 'default' settings.
        device.executeShellCommand("am start -S -W -a \"" + action + "\"");
    }

    static void stopPackage(ITestDevice device, String pkgName) throws Exception {
        device.executeShellCommand("am force-stop " + pkgName);
    }

    /**
     * Work around the fact that INativeDevice.enableAdbRoot() is not supported.
     */
    static void setProperty(ITestDevice device, String property, String value) throws Exception {
        device.executeShellCommand("setprop " + property + " " + value);
    }
}
