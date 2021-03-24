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

package android.scopedstorage.cts.signature;

import static android.scopedstorage.cts.lib.TestUtils.adoptShellPermissionIdentity;
import static android.scopedstorage.cts.lib.TestUtils.assertCanAccessPrivateAppAndroidDataDir;
import static android.scopedstorage.cts.lib.TestUtils.assertCanAccessPrivateAppAndroidObbDir;
import static android.scopedstorage.cts.lib.TestUtils.assertMountMode;
import static android.scopedstorage.cts.lib.TestUtils.dropShellPermissionIdentity;

import static androidx.test.InstrumentationRegistry.getContext;

import android.os.storage.StorageManager;

import androidx.test.runner.AndroidJUnit4;

import com.android.cts.install.lib.TestApp;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Runs the scoped storage tests as Signature app on primary external storage.
 *
 * <p>These tests are also run on a public volume by {@link PublicVolumeTest}.
 */
@RunWith(AndroidJUnit4.class)
public class SignatureStorageTest {
    static final String THIS_PACKAGE_NAME = getContext().getPackageName();
    // An app with no permissions
    private static final TestApp APP_B_NO_PERMS = new TestApp("TestAppB",
            "android.scopedstorage.cts.testapp.B.noperms", 1, false,
            "CtsScopedStorageTestAppB.apk");
    /**
     * To help avoid flaky tests, give ourselves a unique nonce to be used for
     * all filesystem paths, so that we don't risk conflicting with previous
     * test runs.
     */
    static final String NONCE = String.valueOf(System.nanoTime());

    static final String NONMEDIA_FILE_NAME = "SignatureStorageTest_file_" + NONCE + ".pdf";
    /**
     * Test that signature apps with ACCESS_MTP can access app's private directories in
     * Android/data and Android/obb
     */
    @Test
    @Ignore("b/183377919")
    public void testMTPAppWithPlatformSignatureCanAccessAndroidDirs() throws Exception {
        adoptShellPermissionIdentity(android.Manifest.permission.ACCESS_MTP);
        try {
            assertCanAccessPrivateAppAndroidDataDir(true /*canAccess*/, APP_B_NO_PERMS,
                    THIS_PACKAGE_NAME, NONMEDIA_FILE_NAME);
            assertCanAccessPrivateAppAndroidObbDir(true /*canAccess*/, APP_B_NO_PERMS,
                    THIS_PACKAGE_NAME, NONMEDIA_FILE_NAME);
            final int uid = getContext().getPackageManager().getPackageUid(THIS_PACKAGE_NAME, 0);
            assertMountMode(THIS_PACKAGE_NAME, uid,
                    StorageManager.MOUNT_MODE_EXTERNAL_ANDROID_WRITABLE);
        } finally {
            dropShellPermissionIdentity();
        }
    }
}
