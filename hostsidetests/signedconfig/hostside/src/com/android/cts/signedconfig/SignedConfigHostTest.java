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
package com.android.cts.signedconfig;

import static com.google.common.truth.Truth.assertThat;

import android.util.Log;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Objects;

public class SignedConfigHostTest extends DeviceTestCase implements IBuildReceiver {

    private static final String TEST_APP_PACKAGE_NAME = "android.cts.signedconfig.app";
    private static final String TEST_APP_PACKAGE2_NAME = "android.cts.signedconfig.app2";
    private static final String TEST_APP_APK_NAME_V1 = "CtsSignedConfigTestAppV1.apk";
    private static final String TEST_APP_APK_NAME_V2 = "CtsSignedConfigTestAppV2.apk";
    private static final String TEST_APP_APK_NAME_PACKAGE2_V2 = "CtsSignedConfigTestApp2V2.apk";
    private static final String TEST_APP_APK_NAME_V1_BAD_SIGNATURE =
            "CtsSignedConfigTestAppV1_badsignature.apk";
    private static final String TEST_APP_APK_NAME_V1_BAD_B64_CONFIG =
            "CtsSignedConfigTestAppV1_badb64_config.apk";
    private static final String TEST_APP_APK_NAME_V1_BAD_B64_SIGNATURE =
            "CtsSignedConfigTestAppV1_badb64_signature.apk";
    private static final String TEST_APP_APK_NAME_V3_CONFIGV1 =
            "CtsSignedConfigTestAppV3_configv1.apk";

    private static final String SETTING_BLACKLIST_EXEMPTIONS = "hidden_api_blacklist_exemptions";
    private static final String SETTING_SIGNED_CONFIG_VERSION = "signed_config_version";

    private IBuildInfo mCtsBuild;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    private File getTestApk(String name) throws FileNotFoundException {
        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        return buildHelper.getTestFile(name);
    }

    private void deleteSetting(String name) throws DeviceNotAvailableException {
        String output = getDevice().executeShellCommand("settings delete global " + name);
        assertThat(output).containsMatch("Deleted (0|1) rows");
    }

    private void deleteConfig() throws DeviceNotAvailableException {
        deleteSetting(SETTING_BLACKLIST_EXEMPTIONS);
        deleteSetting(SETTING_SIGNED_CONFIG_VERSION);
    }

    private void uninstallTestApps() throws DeviceNotAvailableException {
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        getDevice().uninstallPackage(TEST_APP_PACKAGE2_NAME);
    }

    private void waitUntilSettingMatches(String setting, String value) throws Exception {
        int tries = 0;
        String v;
        do {
            Thread.sleep(500);
            v = getDevice().getSetting("global", setting);
            tries++;
        } while (tries < 10 && !Objects.equals(value, v));
        assertThat(v).isEqualTo(value);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        deleteConfig();
        waitForDevice();
    }

    @Override
    protected void tearDown() throws Exception {
        uninstallTestApps();
        deleteConfig();
        super.tearDown();
    }

    private void waitForDevice(int seconds) throws Exception {
        Thread.sleep(seconds * 1000);
    }

    private void waitForDevice() throws Exception {
        waitForDevice(1);
    }

    private void installPackage(String apkName)
            throws FileNotFoundException, DeviceNotAvailableException {
        assertThat(getDevice().installPackage(getTestApk(apkName), false)).isNull();
    }

    public void testConfigAppliedOnInstall() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "1");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS)).isEqualTo(
                "LClass1;->method1(,LClass1;->field1:");
    }

    public void testConfigUpgradedOnInstall() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "1");
        installPackage(TEST_APP_APK_NAME_V2);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "2");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS)).isEqualTo(
                "LClass2;->method2(,LClass2;->field2:");
    }

    public void testConfigRemainsAfterUninstall() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "1");
        getDevice().uninstallPackage(TEST_APP_PACKAGE_NAME);
        waitForDevice(5);
        assertThat(getDevice().getSetting("global", SETTING_SIGNED_CONFIG_VERSION)).isEqualTo("1");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS)).isEqualTo(
                "LClass1;->method1(,LClass1;->field1:");
    }

    public void testConfigNotDowngraded() throws Exception {
        installPackage(TEST_APP_APK_NAME_V2);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "2");
        installPackage(TEST_APP_APK_NAME_V3_CONFIGV1);
        waitForDevice(5);
        assertThat(getDevice().getSetting("global", SETTING_SIGNED_CONFIG_VERSION)).isEqualTo("2");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS)).isEqualTo(
                "LClass2;->method2(,LClass2;->field2:");
    }

    public void testConfigUpgradedOnInstallOtherPackage() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "1");
        installPackage(TEST_APP_APK_NAME_PACKAGE2_V2);
        waitUntilSettingMatches(SETTING_SIGNED_CONFIG_VERSION, "2");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS)).isEqualTo(
                "LClass2;->method2(,LClass2;->field2:");
    }

    public void testBadSignatureIgnored() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1_BAD_SIGNATURE);
        waitForDevice(5);
        assertThat(getDevice().getSetting("global", SETTING_SIGNED_CONFIG_VERSION))
                .isEqualTo("null");
        assertThat(getDevice().getSetting("global", SETTING_BLACKLIST_EXEMPTIONS))
                .isEqualTo("null");
    }

    public void testBadBase64Config() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1_BAD_B64_CONFIG);
        waitForDevice(5);
        // This test is really testing that the system server doesn't crash, but
        // this check should still do the trick.
        assertThat(getDevice().getSetting("global", SETTING_SIGNED_CONFIG_VERSION))
                .isEqualTo("null");
    }

    public void testBadBase64Signature() throws Exception {
        installPackage(TEST_APP_APK_NAME_V1_BAD_B64_SIGNATURE);
        waitForDevice(5);
        // This test is really testing that the system server doesn't crash, but
        // this check should still do the trick.
        assertThat(getDevice().getSetting("global", SETTING_SIGNED_CONFIG_VERSION))
                .isEqualTo("null");
    }

}
