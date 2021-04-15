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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;
import com.android.tradefed.testtype.junit4.DeviceTestRunOptions;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Test to mock multi-user interacting with AppSearch.
 *
 * <p>This test is split into two distinct parts: The first part is the test-apps that runs on the
 * device and interactive with AppSearch.This class is the second part that runs on the host and
 * triggers tests in the first part for different users.
 *
 * <p>To trigger a device test, call runDeviceTestAsUser with a specific the test name and specific
 * user.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchMultiUserTest extends BaseHostJUnit4Test {
    private static final String TARGET_APK = "CtsAppSearchHostTestHelper.apk";
    private static final String TARGET_PKG = "android.appsearch.app";
    private static final String TEST_CLASS = TARGET_PKG + ".UserDataTest";

    private static final long DEFAULT_INSTRUMENTATION_TIMEOUT_MS = 600_000; // 10min

    private int mInitialUserId;
    private int mSecondaryUserId;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                getDevice().isMultiUserSupported());

        mInitialUserId = getDevice().getCurrentUser();
        mSecondaryUserId = getDevice().createUser("Test_User");
        assertThat(getDevice().startUser(mSecondaryUserId)).isTrue();

        installPackageAsUser(TARGET_APK, /* grantPermissions */true, mInitialUserId);
        installPackageAsUser(TARGET_APK, /* grantPermissions */true, mSecondaryUserId);

        runDeviceTestAsUser("clearTestData", mInitialUserId);
        runDeviceTestAsUser("clearTestData", mSecondaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        runDeviceTestAsUser("clearTestData", mInitialUserId);
        if (mSecondaryUserId > 0) {
            getDevice().removeUser(mSecondaryUserId);
        }
    }

    private void runDeviceTestAsUser(String testMethod, int userId) throws Exception {
        runDeviceTests(getDevice(), TARGET_PKG, TEST_CLASS, testMethod, userId,
                DEFAULT_INSTRUMENTATION_TIMEOUT_MS);
    }

    @Test
    public void testMultiUser_documentAccess() throws Exception {
        runDeviceTestAsUser("testPutDocuments", mSecondaryUserId);
        runDeviceTestAsUser("testGetDocuments_exist", mSecondaryUserId);
        // Cannot get the document from another user.
        runDeviceTestAsUser("testGetDocuments_nonexist", mInitialUserId);
    }
}
