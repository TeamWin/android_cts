/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Set of tests that verify app data isolation works.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppDataIsolationTests extends BaseAppSecurityTest {

    private static final String APPA_APK = "CtsAppDataIsolationAppA.apk";
    private static final String APPA_PKG = "com.android.cts.appdataisolation.appa";
    private static final String APPA_CLASS =
            "com.android.cts.appdataisolation.appa.AppATests";
    private static final String APPA_METHOD_CREATE_CE_DE_DATA = "testCreateCeDeAppData";
    private static final String APPA_METHOD_CHECK_CE_DATA_EXISTS = "testAppACeDataExists";
    private static final String APPA_METHOD_CHECK_CE_DATA_DOES_NOT_EXIST =
            "testAppACeDataDoesNotExist";
    private static final String APPA_METHOD_CHECK_DE_DATA_EXISTS = "testAppADeDataExists";
    private static final String APPA_METHOD_CHECK_DE_DATA_DOES_NOT_EXIST =
            "testAppADeDataDoesNotExist";
    private static final String APPA_METHOD_CHECK_CUR_PROFILE_ACCESSIBLE =
            "testAppACurProfileDataAccessible";
    private static final String APPA_METHOD_CHECK_REF_PROFILE_NOT_ACCESSIBLE =
            "testAppARefProfileDataNotAccessible";

    private static final String APPB_APK = "CtsAppDataIsolationAppB.apk";
    private static final String APPB_PKG = "com.android.cts.appdataisolation.appb";
    private static final String APPB_CLASS =
            "com.android.cts.appdataisolation.appb.AppBTests";
    private static final String APPB_METHOD_CANNOT_ACCESS_APPA_DIR = "testCannotAccessAppADataDir";

    @Before
    public void setUp() throws Exception {
        Utils.prepareSingleUser(getDevice());
        getDevice().uninstallPackage(APPA_PKG);
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(APPA_PKG);
    }

    private void forceStopPackage(String packageName) throws Exception {
        getDevice().executeShellCommand("am force-stop " + packageName);
    }

    @Test
    public void testAppAbleToAccessItsDataAfterForceStop() throws Exception {
        // Install AppA and verify no data stored
        new InstallMultiple().addApk(APPA_APK).run();
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CE_DATA_DOES_NOT_EXIST);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_DE_DATA_DOES_NOT_EXIST);

        // Create data in CE and DE storage
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CREATE_CE_DE_DATA);

        // Verify CE and DE storage contains data, cur profile is accessible and ref profile is
        // not accessible
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CE_DATA_EXISTS);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_DE_DATA_EXISTS);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CUR_PROFILE_ACCESSIBLE);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_REF_PROFILE_NOT_ACCESSIBLE);

        // Force stop and verify CE and DE storage contains data, cur profile is accessible and
        // ref profile is not accessible, to confirm it's binding back the same data directory,
        // not binding to a wrong one / create a new one.
        forceStopPackage(APPA_PKG);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CE_DATA_EXISTS);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_DE_DATA_EXISTS);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CUR_PROFILE_ACCESSIBLE);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_REF_PROFILE_NOT_ACCESSIBLE);

    }

    @Test
    public void testAppNotAbleToAccessItsDataAfterReinstall() throws Exception {
        // Install AppA create CE DE data
        new InstallMultiple().addApk(APPA_APK).run();
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CREATE_CE_DE_DATA);

        // Reinstall AppA
        getDevice().uninstallPackage(APPA_PKG);
        new InstallMultiple().addApk(APPA_APK).run();

        // Verify CE and DE data are removed
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_CE_DATA_DOES_NOT_EXIST);
        runDeviceTests(APPA_PKG, APPA_CLASS, APPA_METHOD_CHECK_DE_DATA_DOES_NOT_EXIST);
    }

    @Test
    public void testNormalProcessCannotAccessOtherAppDataDir() throws Exception {
        new InstallMultiple().addApk(APPA_APK).run();
        new InstallMultiple().addApk(APPB_APK).run();

        runDeviceTests(APPB_PKG, APPB_CLASS, APPB_METHOD_CANNOT_ACCESS_APPA_DIR);
    }
}
