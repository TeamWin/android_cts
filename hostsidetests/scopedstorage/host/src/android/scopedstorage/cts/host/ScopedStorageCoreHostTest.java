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

package android.scopedstorage.cts.host;

import static org.junit.Assert.assertTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the core ScopedStorageTest tests.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ScopedStorageCoreHostTest extends BaseHostJUnit4Test {
    private boolean mIsExternalStorageSetup = false;

    /**
     * Runs the given phase of ScopedStorageTest by calling into the device.
     * Throws an exception if the test phase fails.
     */
    void runDeviceTest(String phase) throws Exception {
        assertTrue(runDeviceTests("android.scopedstorage.cts",
                "android.scopedstorage.cts.ScopedStorageTest", phase));

    }

    String executeShellCommand(String cmd) throws Exception {
        return getDevice().executeShellCommand(cmd);
    }

    private void setupExternalStorage() throws Exception {
        if (!mIsExternalStorageSetup) {
            runDeviceTest("setupExternalStorage");
            mIsExternalStorageSetup = true;
        }
    }

    @Before
    public void setup() throws Exception {
        setupExternalStorage();
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell -m 2770");
        executeShellCommand("mkdir /sdcard/Android/data/com.android.shell/files -m 2770");
    }

    @Before
    public void revokeStoragePermissions() throws Exception {
        revokePermissions("android.permission.WRITE_EXTERNAL_STORAGE",
                "android.permission.READ_EXTERNAL_STORAGE");
    }

    @After
    public void tearDown() throws Exception {
        executeShellCommand("rm -r /sdcard/Android/data/com.android.shell");
    }

    @Test
    public void testTypePathConformity() throws Exception {
        runDeviceTest("testTypePathConformity");
    }

    @Test
    public void testCreateFileInAppExternalDir() throws Exception {
        runDeviceTest("testCreateFileInAppExternalDir");
    }

    @Test
    public void testCreateFileInOtherAppExternalDir() throws Exception {
        runDeviceTest("testCreateFileInOtherAppExternalDir");
    }

    @Test
    public void testContributeMediaFile() throws Exception {
        runDeviceTest("testContributeMediaFile");
    }

    @Test
    public void testCreateAndDeleteEmptyDir() throws Exception {
        runDeviceTest("testCreateAndDeleteEmptyDir");
    }

    @Test
    public void testOpendirRestrictions() throws Exception {
        runDeviceTest("testOpendirRestrictions");
    }

    @Test
    public void testLowLevelFileIO() throws Exception {
        runDeviceTest("testLowLevelFileIO");
    }

    @Test
    public void testListDirectoriesWithMediaFiles() throws Exception {
        runDeviceTest("testListDirectoriesWithMediaFiles");
    }

    @Test
    public void testListFilesFromExternalMediaDirectory() throws Exception {
        runDeviceTest("testListFilesFromExternalMediaDirectory");
    }

    @Test
    public void testMetaDataRedaction() throws Exception {
        runDeviceTest("testMetaDataRedaction");
    }

    @Test
    public void testVfsCacheConsistency() throws Exception {
        runDeviceTest("testOpenFilePathFirstWriteContentResolver");
        runDeviceTest("testOpenContentResolverFirstWriteContentResolver");
        runDeviceTest("testOpenFilePathFirstWriteFilePath");
        runDeviceTest("testOpenContentResolverFirstWriteFilePath");
        runDeviceTest("testOpenContentResolverWriteOnly");
        runDeviceTest("testOpenContentResolverDup");
        runDeviceTest("testContentResolverDelete");
        runDeviceTest("testContentResolverUpdate");
        runDeviceTest("testOpenContentResolverClose");
    }

    @Test
    public void testCaseInsensitivity() throws Exception {
        runDeviceTest("testCreateLowerCaseDeleteUpperCase");
        runDeviceTest("testCreateUpperCaseDeleteLowerCase");
        runDeviceTest("testCreateMixedCaseDeleteDifferentMixedCase");
        runDeviceTest("testAndroidDataObbDoesNotForgetMount");
        runDeviceTest("testCacheConsistencyForCaseInsensitivity");
    }

    @Test
    public void testRenameAndReplaceFile() throws Exception {
        runDeviceTest("testRenameAndReplaceFile");
    }

    @Test
    public void testRenameDirectory() throws Exception {
        runDeviceTest("testRenameDirectory");
    }

    @Test
    public void testSystemGalleryAppHasFullAccessToImages() throws Exception {
        runDeviceTest("testSystemGalleryAppHasFullAccessToImages");
    }

    @Test
    public void testSystemGalleryAppHasNoFullAccessToAudio() throws Exception {
        runDeviceTest("testSystemGalleryAppHasNoFullAccessToAudio");
    }

    @Test
    public void testSystemGalleryCanRenameImageAndVideoDirs() throws Exception {
        runDeviceTest("testSystemGalleryCanRenameImageAndVideoDirs");
    }

    @Test
    public void testManageExternalStorageCanCreateFilesAnywhere() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageCanCreateFilesAnywhere");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testManageExternalStorageReaddir() throws Exception {
        allowAppOps("android:manage_external_storage");
        try {
            runDeviceTest("testManageExternalStorageReaddir");
        } finally {
            denyAppOps("android:manage_external_storage");
        }
    }

    @Test
    public void testHiddenFiles() throws Exception {
        runDeviceTest("testCanCreateHiddenFile");
        runDeviceTest("testCanRenameHiddenFile");
        runDeviceTest("testHiddenDirectory");
    }

    @Test
    public void testCreateCanRestoreDeletedRowId() throws Exception {
        runDeviceTest("testCreateCanRestoreDeletedRowId");
    }

    @Test
    public void testRenameCanRestoreDeletedRowId() throws Exception {
        runDeviceTest("testRenameCanRestoreDeletedRowId");
    }

    @Test
    public void testQueryOtherAppsFiles() throws Exception {
        runDeviceTest("testQueryOtherAppsFiles");
    }

    @Test
    public void testAccess_file() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testAccess_file");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE");
        }
    }

    @Test
    public void testAccess_directory() throws Exception {
        grantPermissions("android.permission.READ_EXTERNAL_STORAGE",
                "android.permission.WRITE_EXTERNAL_STORAGE");
        try {
            runDeviceTest("testAccess_directory");
        } finally {
            revokePermissions("android.permission.READ_EXTERNAL_STORAGE",
                    "android.permission.WRITE_EXTERNAL_STORAGE");
        }
    }

    private void grantPermissions(String... perms) throws Exception {
        for (String perm : perms) {
            executeShellCommand("pm grant android.scopedstorage.cts " + perm);
        }
    }

    private void revokePermissions(String... perms) throws Exception {
        for (String perm : perms) {
            executeShellCommand("pm revoke android.scopedstorage.cts " + perm);
        }
    }

    private void allowAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts " + op + " allow");
        }
    }

    private void denyAppOps(String... ops) throws Exception {
        for (String op : ops) {
            executeShellCommand("cmd appops set --uid android.scopedstorage.cts " + op + " deny");
        }
    }
}
