/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.os.cts;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.ddmlib.testrunner.RemoteAndroidTestRunner;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IBuildReceiver;

public class StaticSharedLibsHostTests extends DeviceTestCase implements IBuildReceiver {
    private static final String ANDROID_JUNIT_RUNNER_CLASS =
            "android.support.test.runner.AndroidJUnitRunner";

    private static final String STATIC_LIB_PROVIDER1_APK = "CtsStaticSharedLibProviderApp1.apk";
    private static final String STATIC_LIB_PROVIDER1_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER2_APK = "CtsStaticSharedLibProviderApp2.apk";
    private static final String STATIC_LIB_PROVIDER2_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER3_APK = "CtsStaticSharedLibProviderApp3.apk";
    private static final String STATIC_LIB_PROVIDER3_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER4_APK = "CtsStaticSharedLibProviderApp4.apk";
    private static final String STATIC_LIB_PROVIDER4_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER5_APK = "CtsStaticSharedLibProviderApp5.apk";
    private static final String STATIC_LIB_PROVIDER5_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_PROVIDER6_APK = "CtsStaticSharedLibProviderApp6.apk";
    private static final String STATIC_LIB_PROVIDER6_PKG = "android.os.lib.provider";

    private static final String STATIC_LIB_CONSUMER1_APK = "CtsStaticSharedLibConsumerApp1.apk";
    private static final String STATIC_LIB_CONSUMER1_PKG = "android.os.lib.consumer1";

    private static final String STATIC_LIB_CONSUMER2_APK = "CtsStaticSharedLibConsumerApp2.apk";
    private static final String STATIC_LIB_CONSUMER2_PKG = "android.os.lib.consumer2";

    private CompatibilityBuildHelper mBuildHelper;

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mBuildHelper = new CompatibilityBuildHelper(buildInfo);
    }

    public void testInstallSharedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        try {
            // Install version 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install version 2
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Uninstall version 1
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall version 2
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        }
    }

    public void testLoadCodeAndResourcesFromSharedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        try {
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Try to load code and resources
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        }
    }

    public void testCannotUninstallUsedSharedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        try {
            // Install the library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // The library cannot be uninstalled
            assertNotNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
            // Uninstall the client
            assertNull(getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG));
            // Now the library can be uninstalled
            assertNull(getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        }
    }

    public void testLibraryVersionsAndVersionCodesSameOrder() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
        try {
            // Install library version 1 with version code 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install library version 2 with version code 4
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Shouldn't be able to install library version 3 with version code 3
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER3_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER3_PKG);
        }
    }

    public void testCannotInstallAppWithMissingLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        try {
            // Shouldn't be able to install an app if a dependency lib is missing
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        }
    }

    public void testCanReplaceLibraryIfVersionAndVersionCodeSame() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        try {
            // Install a library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Cannot install the library (need to reinstall)
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Can reinstall the library if version and version code same
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), true, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        }
    }

    public void testUninstallSpecificLibraryVersion() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        try {
            // Install library version 1 with version code 1
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install library version 2 with version code 4
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Uninstall the library package with version code 4 (version 2)
            assertTrue(getDevice().executeShellCommand("pm uninstall --versionCode 4 "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success"));
            // Uninstall the library package with version code 1 (version 1)
            assertTrue(getDevice().executeShellCommand("pm uninstall "
                    + STATIC_LIB_PROVIDER1_PKG).startsWith("Success"));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        }
    }

    public void testKeyRotation() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        try {
            // Install a library version specifying an upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install a newer library signed with the upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
            // Install a client that depends on the upgraded key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER2_APK), false, false));
            // Ensure code and resources can be loaded
            runDeviceTests(STATIC_LIB_CONSUMER2_PKG,
                    "android.os.lib.consumer2.UseSharedLibraryTest",
                    "testLoadCodeAndResources");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        }
    }

    public void testCannotInstallIncorrectlySignedLibrary() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        try {
            // Install a library version not specifying an upgrade key set
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Shouldn't be able to install a newer version signed differently
            assertNotNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        }
    }

    public void testLibraryAndPackageNameCanMatch() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        try {
            // Install a library with same name as package should work.
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER5_APK), false, false));
            // Install a library with same name as package should work.
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER6_APK), true, false));
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER5_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER6_PKG);
        }
    }

    public void testGetSharedLibraries() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        try {
            // Install the first library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the second library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install the third library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER4_APK), false, false));
            // Install the first client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Install the second client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER2_APK), false, false));
            // Ensure libraries are properly reported
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testSharedLibrariesProperlyReported");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER4_PKG);
        }
    }

    public void testAppCanSeeOnlyLibrariesItDependOn() throws Exception {
        getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
        getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        try {
            // Install the first library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER1_APK), false, false));
            // Install the second library
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_PROVIDER2_APK), false, false));
            // Install the client
            assertNull(getDevice().installPackage(mBuildHelper.getTestFile(
                    STATIC_LIB_CONSUMER1_APK), false, false));
            // Ensure the client can see only the lib it depends on
            runDeviceTests(STATIC_LIB_CONSUMER1_PKG,
                    "android.os.lib.consumer1.UseSharedLibraryTest",
                    "testAppCanSeeOnlyLibrariesItDependOn");
        } finally {
            getDevice().uninstallPackage(STATIC_LIB_CONSUMER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER1_PKG);
            getDevice().uninstallPackage(STATIC_LIB_PROVIDER2_PKG);
        }
    }

    private void runDeviceTests(String packageName, String testClassName,
            String testMethodName) throws DeviceNotAvailableException {
        RemoteAndroidTestRunner testRunner = new RemoteAndroidTestRunner(packageName,
                ANDROID_JUNIT_RUNNER_CLASS, getDevice().getIDevice());
        testRunner.setMethodName(testClassName, testMethodName);
        getDevice().runInstrumentationTests(testRunner);
    }
}
