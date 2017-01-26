/*
 * Copyright (C) 2014 The Android Open Source Project
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

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.testtype.DeviceTestCase;
import com.android.tradefed.testtype.IAbi;
import com.android.tradefed.testtype.IAbiReceiver;
import com.android.tradefed.testtype.IBuildReceiver;

/**
 * Tests that exercise various storage APIs.
 */
public class StorageHostTest extends DeviceTestCase implements IAbiReceiver, IBuildReceiver {
    private static final String PKG_STATS = "com.android.cts.storagestatsapp";
    private static final String PKG_A = "com.android.cts.storageapp_a";
    private static final String PKG_B = "com.android.cts.storageapp_b";
    private static final String APK_STATS = "CtsStorageStatsApp.apk";
    private static final String APK_A = "CtsStorageAppA.apk";
    private static final String APK_B = "CtsStorageAppB.apk";
    private static final String CLASS_STATS = "com.android.cts.storagestatsapp.StorageStatsTest";
    private static final String CLASS = "com.android.cts.storageapp.StorageTest";

    private IAbi mAbi;
    private IBuildInfo mCtsBuild;

    @Override
    public void setAbi(IAbi abi) {
        mAbi = abi;
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_STATS), false));
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_A), false));
        assertNull(getDevice().installPackage(buildHelper.getTestFile(APK_B), false));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);
    }

    public void testVerifyAppStats() throws Exception {
        runDeviceTests(PKG_A, CLASS, "testAllocate");
        runDeviceTests(PKG_A, CLASS, "testVerifySpaceManual");
        runDeviceTests(PKG_A, CLASS, "testVerifySpaceApi");
    }

    public void testVerifyAppQuota() throws Exception {
        runDeviceTests(PKG_A, CLASS, "testVerifyQuotaApi");
    }

    public void testVerifyStats() throws Exception {
        runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStats");
    }

    public void testVerifyStatsMultiple() throws Exception {
        runDeviceTests(PKG_A, CLASS, "testAllocate");
        runDeviceTests(PKG_A, CLASS, "testAllocate");

        runDeviceTests(PKG_B, CLASS, "testAllocate");

        runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsMultiple");
    }

    public void testVerifyStatsExternal() throws Exception {
        runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternal");
    }

    public void testVerifyCategory() throws Exception {
        runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyCategory");
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName)
            throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName);
    }
}
