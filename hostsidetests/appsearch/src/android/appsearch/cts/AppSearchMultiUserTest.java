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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Map;

/**
 * Test to cover multi-user interacting with AppSearch.
 *
 * <p>This test is split into two distinct parts: The first part is the test-apps that runs on the
 * device and interactive with AppSearch. This class is the second part that runs on the host and
 * triggers tests in the first part for different users.
 *
 * <p>To trigger a device test, call runDeviceTestAsUser with a specific the test name and specific
 * user.
 *
 * <p>Unlock your device when test locally.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public class AppSearchMultiUserTest extends AppSearchHostTestBase {

    private int mInitialUserId;
    private int mSecondaryUserId;

    @Before
    public void setUp() throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                getDevice().isMultiUserSupported());

        mInitialUserId = getDevice().getCurrentUser();
        mSecondaryUserId = getDevice().createUser("Test_User");
        assertThat(getDevice().startUser(mSecondaryUserId)).isTrue();

        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mInitialUserId);
        installPackageAsUser(TARGET_APK_A, /* grantPermission= */true, mSecondaryUserId);

        runDeviceTestAsUserInPkgA("clearTestData", mInitialUserId);
        runDeviceTestAsUserInPkgA("clearTestData", mSecondaryUserId);
    }

    @After
    public void tearDown() throws Exception {
        runDeviceTestAsUserInPkgA("clearTestData", mInitialUserId);
        if (mSecondaryUserId > 0) {
            getDevice().removeUser(mSecondaryUserId);
        }
    }

    @Test
    public void testMultiUser_documentAccess() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", mSecondaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", mSecondaryUserId);
        // Cannot get the document from another user.
        runDeviceTestAsUserInPkgA("testGetDocuments_nonexist", mInitialUserId);
    }

    @Test
    public void testCreateSessionInStoppedUser() throws Exception {
        Map<String, String> args =
                Collections.singletonMap(USER_ID_KEY, String.valueOf(mSecondaryUserId));
        getDevice().stopUser(mSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        runDeviceTestAsUserInPkgA("createSessionInStoppedUser", mInitialUserId, args);
    }

    @Test
    public void testStopUser_persistData() throws Exception {
        runDeviceTestAsUserInPkgA("testPutDocuments", mSecondaryUserId);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", mSecondaryUserId);
        getDevice().stopUser(mSecondaryUserId, /*waitFlag=*/true, /*forceFlag=*/true);
        getDevice().startUser(mSecondaryUserId, /*waitFlag=*/true);
        runDeviceTestAsUserInPkgA("testGetDocuments_exist", mSecondaryUserId);
    }
}
