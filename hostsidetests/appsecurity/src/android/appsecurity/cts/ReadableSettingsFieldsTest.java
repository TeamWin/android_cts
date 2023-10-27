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

package android.appsecurity.cts;


import static org.junit.Assert.assertTrue;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileNotFoundException;

/**
 * Test that:
 * 1) all the public fields annotated with @Readable in Settings.Secure, Settings.System,
 * Settings.Global classes are readable.
 * 2) hidden fields added before S are also readable, via their raw Settings key String values.
 * 3) public fields without the @Readable annotation will not be readable.
 *
 * Run with:
 * atest android.appsecurity.cts.ReadableSettingsFieldsTest
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class ReadableSettingsFieldsTest extends BaseAppSecurityTest {
    private static final String TEST_PACKAGE = "com.android.cts.readsettingsfieldsapp";
    private static final String TEST_CLASS = TEST_PACKAGE + ".ReadSettingsFieldsTest";
    private static final String TEST_APK = "CtsReadSettingsFieldsApp.apk";
    private static final String TEST_APK_TEST_ONLY = "CtsReadSettingsFieldsAppTestOnly.apk";
    private static final String TEST_APK_TARGET_Q = "CtsReadSettingsFieldsAppTargetQ.apk";
    private static final String TEST_APK_TARGET_R = "CtsReadSettingsFieldsAppTargetR.apk";
    private static final String TEST_APK_TARGET_S = "CtsReadSettingsFieldsAppTargetS.apk";

    private DeviceTestRunOptions options;

    @Before
    public void setUp() throws Exception {
        new InstallMultiple().addFile(TEST_APK).run();
        assertTrue(getDevice().isPackageInstalled(TEST_PACKAGE));
        options = new DeviceTestRunOptions(TEST_PACKAGE)
            .setDevice(getDevice())
            .setTestClassName(TEST_CLASS)
            .setDisableHiddenApiCheck(true);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_PACKAGE);
    }

    @Test
    public void testSecureNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testSecureNonHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testSystemNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testSystemNonHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testGlobalNonHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testGlobalNonHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testSecureSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testSecureSomeHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testSystemSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testSystemSomeHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testGlobalSomeHiddenSettingsKeysAreReadable() throws DeviceNotAvailableException {
        options.setTestMethodName("testGlobalSomeHiddenSettingsKeysAreReadable");
        runDeviceTests(options);
    }

    @Test
    public void testGlobalHiddenSettingsKeyNotReadableWithoutPermissions() throws
            DeviceNotAvailableException {
        options.setTestMethodName("testGlobalHiddenSettingsKeyNotReadableWithoutPermissions");
        runDeviceTests(options);
    }

    @Test
    public void testSecureHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testSecureHiddenSettingsKeysNotReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testSystemHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testSystemHiddenSettingsKeysNotReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testGlobalHiddenSettingsKeysNotReadableWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testGlobalHiddenSettingsKeysNotReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testSecureHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        options.setTestMethodName("testSecureHiddenSettingsKeysReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testSystemHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        options.setTestMethodName("testSystemHiddenSettingsKeysReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testGlobalHiddenSettingsKeysReadableWhenTestOnly()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TEST_ONLY).addArg("-t").run();
        options.setTestMethodName("testGlobalHiddenSettingsKeysReadableWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testSettingsKeysNotReadableForAfterR()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TARGET_S).run();
        options.setTestMethodName("testSettingsKeysNotReadableForAfterR");
        runDeviceTests(options);
    }

    @Test
    public void testSettingsKeysReadableForRMinus()
            throws DeviceNotAvailableException, FileNotFoundException {
        new InstallMultiple().addFile(TEST_APK_TARGET_R).run();
        options.setTestMethodName("testSettingsKeysReadableForRMinus");
        runDeviceTests(options);
        new InstallMultiple().addFile(TEST_APK_TARGET_Q).run();
        options.setTestMethodName("testSettingsKeysReadableForRMinus");
        runDeviceTests(options);
    }

    @Test
    public void testQueryGlobalSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testQueryGlobalSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testQuerySystemSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testQuerySystemSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testQuerySecureSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testQuerySecureSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testListGlobalSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testListGlobalSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testListSystemSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testListSystemSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }

    @Test
    public void testListSecureSettingsNoHiddenKeysWithoutAnnotation()
            throws DeviceNotAvailableException {
        options.setTestMethodName("testListSecureSettingsNoHiddenKeysWithoutAnnotation");
        runDeviceTests(options);
    }
}
