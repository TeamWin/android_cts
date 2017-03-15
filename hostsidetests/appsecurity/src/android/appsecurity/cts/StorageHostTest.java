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

    private static int[] sUsers;

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

        if (sUsers != null) {
            for (int user : sUsers) {
                getDevice().executeShellCommand("appops set --user " + user + " " + PKG_STATS
                        + " android:get_usage_stats allow");
            }
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        getDevice().uninstallPackage(PKG_STATS);
        getDevice().uninstallPackage(PKG_A);
        getDevice().uninstallPackage(PKG_B);
    }

    public void testA() throws Exception {
        sUsers = Utils.createUsersForTest(getDevice());
    }

    public void testVerifyAppStats() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
        }

        // TODO: remove this once 34723223 is fixed
        getDevice().executeShellCommand("sync");

        for (int user : sUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceManual", user);
            runDeviceTests(PKG_A, CLASS, "testVerifySpaceApi", user);
        }
    }

    public void testVerifyAppQuota() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyQuotaApi", user);
        }
    }

    public void testVerifyAppAllocate() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_A, CLASS, "testVerifyAllocateApi", user);
        }
    }

    public void testVerifySummary() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifySummary", user);
        }
    }

    public void testVerifyStats() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStats", user);
        }
    }

    public void testVerifyStatsMultiple() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);
            runDeviceTests(PKG_A, CLASS, "testAllocate", user);

            runDeviceTests(PKG_B, CLASS, "testAllocate", user);
        }

        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsMultiple", user);
        }
    }

    public void testVerifyStatsExternal() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternal", user);
        }
    }

    public void testVerifyStatsExternalConsistent() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyStatsExternalConsistent", user);
        }
    }

    public void testVerifyCategory() throws Exception {
        for (int user : sUsers) {
            runDeviceTests(PKG_STATS, CLASS_STATS, "testVerifyCategory", user);
        }
    }

    public void testCacheClearing() throws Exception {
        // To make the cache clearing logic easier to verify, ignore any cache
        // and low space reserved space.
        getDevice().executeShellCommand("settings put global sys_storage_threshold_max_bytes 0");
        getDevice().executeShellCommand("settings put global sys_storage_cache_max_bytes 0");
        try {
            for (int user : sUsers) {
                // Clear all other cached data to give ourselves a clean slate
                getDevice().executeShellCommand("pm trim-caches 4096G");
                runDeviceTests(PKG_STATS, CLASS_STATS, "testCacheClearing", user);
            }
        } finally {
            getDevice().executeShellCommand("settings delete global sys_storage_threshold_max_bytes");
            getDevice().executeShellCommand("settings delete global sys_storage_cache_max_bytes");
        }
    }

    public void testZ() throws Exception {
        Utils.removeUsersForTest(getDevice(), sUsers);
        sUsers = null;
    }

    public void runDeviceTests(String packageName, String testClassName, String testMethodName,
            int userId) throws DeviceNotAvailableException {
        Utils.runDeviceTests(getDevice(), packageName, testClassName, testMethodName, userId);
    }
}
