/* 
 * Copyright (C) 2019 The Android Open Source Project
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

package android.content.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;

import com.android.compatibility.common.tradefed.build.CompatibilityBuildHelper;
import com.android.tradefed.build.IBuildInfo;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.IBuildReceiver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SystemUserOnly
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull(reason = "instant applications cannot see any other application")
public class ContextCrossProfileHostTest extends BaseContextCrossProfileTest
        implements IBuildReceiver {

    private static final String TEST_WITH_PERMISSION_APK =
            "CtsContextCrossProfileApp.apk";
    private static final String TEST_WITH_PERMISSION_PKG =
            "com.android.cts.context";
    private static final String TEST_SERVICE_WITH_PERMISSION_APK =
            "CtsContextCrossProfileTestServiceApp.apk";
    private static final String TEST_SERVICE_WITH_PERMISSION_PKG =
            "com.android.cts.testService";

    public static final int USER_SYSTEM = 0;

    private IBuildInfo mCtsBuild;
    private File mApkFile;

    private int mParentUserId;
    private final Map<String, String> mTestArgs = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        assumeTrue(mSupportsMultiUser);

        mParentUserId = getDevice().getCurrentUser();
        assertEquals(USER_SYSTEM, mParentUserId);

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        mApkFile = buildHelper.getTestFile(TEST_WITH_PERMISSION_APK);

        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                mParentUserId, /* extraArgs= */"-t");
    }

    @After
    public void tearDown() throws Exception {
        getDevice().uninstallPackage(TEST_WITH_PERMISSION_PKG);
        getDevice().uninstallPackage(TEST_SERVICE_WITH_PERMISSION_PKG);
        mTestArgs.clear();
        super.tearDown();
    }

    @Override
    public void setBuild(IBuildInfo buildInfo) {
        mCtsBuild = buildInfo;
    }
    
    @Test
    public void testBindServiceAsUser_differentUser_bindsServiceToCorrectUser()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_differentUser_bindsServiceToCorrectUser",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_withInteractAcrossUsersPermission_bindsService()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_sameProfileGroup_withInteractAcrossUsersPermission_bindsService",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_withInteractAcrossProfilesPermission_bindsService()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_sameProfileGroup_withInteractAcrossProfilesPermission_bindsService",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_withInteractAcrossProfilesAppOp_bindsService()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_sameProfileGroup_withInteractAcrossProfilesAppOp_bindsService",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_withInteractAcrossUsersPermission_throwsException()
            throws Exception {
        int userInDifferentProfileGroup = createUser();
        getDevice().startUser(userInDifferentProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInDifferentProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_differentProfileGroup_withInteractAcrossUsersPermission_throwsException",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesAppOp_throwsException()
            throws Exception {
        int userInDifferentProfileGroup = createUser();
        getDevice().startUser(userInDifferentProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInDifferentProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesAppOp_throwsException",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesPermission_throwsException()
            throws Exception {
        int userInDifferentProfileGroup = createUser();
        getDevice().startUser(userInDifferentProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInDifferentProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInDifferentProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_differentProfileGroup_withInteractAcrossProfilesPermission_throwsException",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testBindServiceAsUser_sameProfileGroup_withNoPermissions_throwsException()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testBindServiceAsUser_sameProfileGroup_withNoPermissions_throwsException",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testCreateContextAsUser_sameProfileGroup_withInteractAcrossProfilesPermission_throwsException()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testCreateContextAsUser_sameProfileGroup_withInteractAcrossProfilesPermission_throwsException",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }

    @Test
    public void testCreateContextAsUser_sameProfileGroup_withInteractAcrossUsersPermission_createsContext()
            throws Exception {
        int userInSameProfileGroup = createProfile(mParentUserId);
        getDevice().startUser(userInSameProfileGroup, /* waitFlag= */true);
        mTestArgs.put("testUser", Integer.toString(userInSameProfileGroup));
        getDevice().installPackageForUser(
                mApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        CompatibilityBuildHelper buildHelper = new CompatibilityBuildHelper(mCtsBuild);
        File testServiceApkFile = buildHelper.getTestFile(TEST_SERVICE_WITH_PERMISSION_APK);
        getDevice().installPackageForUser(
                testServiceApkFile, /* reinstall= */true, /* grantPermissions= */true,
                userInSameProfileGroup, /* extraArgs= */"-t");

        runDeviceTests(
                getDevice(),
                TEST_WITH_PERMISSION_PKG,
                ".ContextCrossProfileDeviceTest",
                "testCreateContextAsUser_sameProfileGroup_withInteractAcrossUsersPermission_createsContext",
                mParentUserId,
                mTestArgs,
                /* timeout= */60L,
                TimeUnit.SECONDS);
    }
}
