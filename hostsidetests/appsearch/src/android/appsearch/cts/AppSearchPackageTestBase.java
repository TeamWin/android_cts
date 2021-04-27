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

package android.appsearch.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchPackageTestBase extends AppSearchHostTestBase {

    private int mPrimaryUserId;

    @Before
    public void setUp() throws Exception {
        mPrimaryUserId = getDevice().getPrimaryUserId();
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mPrimaryUserId);
        installPackageAsUser(TARGET_APK_B, /* grantPermission= */true, mPrimaryUserId);
        runDeviceTestAsUserInPkgA("clearTestData", mPrimaryUserId);
    }

    @Test
    public void testPackageRemove() throws Exception {
        // package A grants visibility to package B.
        runDeviceTestAsUserInPkgA("testPutDocuments", mPrimaryUserId);
        // query the document from another package.
        runDeviceTestAsUserInPkgB("testGlobalGetDocuments_exist", mPrimaryUserId);
        // remove the package.
        uninstallPackage(TARGET_PKG_A);
        // query the document from another package, verify the document of package A is removed
        runDeviceTestAsUserInPkgB("testGlobalGetDocuments_nonexist", mPrimaryUserId);
    }
}
