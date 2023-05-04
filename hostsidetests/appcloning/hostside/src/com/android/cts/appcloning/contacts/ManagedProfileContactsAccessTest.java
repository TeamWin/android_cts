/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.cts.appcloning.contacts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.cts.appcloning.AppCloningBaseHostTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.log.LogUtil;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ManagedProfileContactsAccessTest extends AppCloningBaseHostTest  {

    private static final String LAUNCHABLE_CLONE_PROFILE_APP =
            "CtsAppCloningLaunchableCloneProfileApp.apk";
    private static final String CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST =
            "com.android.cts.launchable.cloneprofile.contacts.app";
    private static final String CONTACTS_SHARING_TEST_CLASS = "CloneContactsSharingTest";

    public static final String FEATURE_DEVICE_ADMIN = "android.software.device_admin";
    public static final String FEATURE_MANAGED_USERS = "android.software.managed_users";

    private static final String TEST_ACCOUNT_TYPE = "test.com";
    private static final String TEST_CONTACT_PHONE_NUMBER_MIMETYPE =
            "vnd.android.cursor.item/phone_v2";
    private static final String PARENT_USER_ID = "0";
    private static String sManagedProfileUserId = "";

    private static TestContactsDataManager.TestContact sManagedProfileTestContactDetails;
    private static TestContactsDataManager sTestContactsDataManager;

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        AppCloningBaseHostTest.setDevice(testInfo.getDevice());

        assumeTrue(isAtLeastU(testInfo.getDevice()));
        assumeTrue("Device does not support more than two users together",
                supportsMoreThanTwoUsers());
        assumeRequiredManagedProfileFeaturesSupported();
        // TODO(b/253449368): Run the tests only if the device supports app-cloning. This would
        // require adding the app-cloning building blocks config and using it in framework code.
        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
        waitForBroadcastIdle();
        switchAppCloningBuildingBlocksFlag(true);

        sTestContactsDataManager = new TestContactsDataManager(testInfo.getDevice());
        createAndStartManagedProfile();
        insertManagedProfileContact();
    }

    @AfterClassWithInfo
    public static void afterClass(TestInformation testInfo) throws Exception {
        if (isAtLeastU(testInfo.getDevice())
                && supportsMoreThanTwoUsers()
                && isAppCloningSupportedOnDevice()
                && areRequiredManagedProfileFeaturesSupported()) {
            removeManagedProfile();
            switchAppCloningBuildingBlocksFlag(false);
            AppCloningBaseHostTest.baseHostTeardown();
        }
    }

    private static boolean areRequiredManagedProfileFeaturesSupported()
            throws DeviceNotAvailableException {
        return doesDeviceHaveFeature(FEATURE_DEVICE_ADMIN)
                && doesDeviceHaveFeature(FEATURE_MANAGED_USERS);
    }

    private static void assumeRequiredManagedProfileFeaturesSupported()
            throws DeviceNotAvailableException {
        assumeHasDeviceFeature(FEATURE_DEVICE_ADMIN);
        assumeHasDeviceFeature(FEATURE_MANAGED_USERS);
    }

    private static void createAndStartManagedProfile() throws Exception {
        sManagedProfileUserId = String.valueOf(createManagedProfile(PARENT_USER_ID));
        startUserAndWait(sManagedProfileUserId);
        waitForBroadcastIdle();
    }

    private static void removeManagedProfile() throws Exception {
        removeUser(sManagedProfileUserId);
    }

    protected static int createManagedProfile(String parentUserId)
            throws DeviceNotAvailableException {
        String commandOutput = getCreateManagedProfileCommandOutput(parentUserId);
        return getUserIdFromCreateUserCommandOutput(commandOutput);
    }

    private static int getUserIdFromCreateUserCommandOutput(String commandOutput) {
        // Extract the id of the new user.
        String[] tokens = commandOutput.split("\\s+");
        assertWithMessage(commandOutput + " expected to have format \"Success: {USER_ID}\"")
                .that(tokens.length > 0).isTrue();
        assertWithMessage(commandOutput + " expected to have format \"Success: {USER_ID}\"")
                .that("Success:").isEqualTo(tokens[0]);
        return Integer.parseInt(tokens[tokens.length - 1]);
    }

    private static String getCreateManagedProfileCommandOutput(String parentUserId)
            throws DeviceNotAvailableException {
        String command = "pm create-user --profileOf " + parentUserId + " --managed "
                + "TestProfile_" + System.currentTimeMillis();
        String commandOutput = sDevice.executeShellCommand(command);
        LogUtil.CLog.d("Output for command " + command + ": " + commandOutput);
        return commandOutput;
    }

    private static void insertManagedProfileContact() throws DeviceNotAvailableException {
        sManagedProfileTestContactDetails =
                sTestContactsDataManager.insertTestContactForManagedProfile(sManagedProfileUserId,
                        TEST_ACCOUNT_TYPE);
        assertThat(sManagedProfileTestContactDetails.rawContact).isNotNull();
        assertThat(sManagedProfileTestContactDetails.rawContactDataList).isNotNull();
        assertThat(sManagedProfileTestContactDetails.rawContactDataList.length).isNotEqualTo(0);
    }

    /**
     * Enables/Disables the contact sharing feature flag
     * @throws Exception
     */
    private static void switchAppCloningBuildingBlocksFlag(boolean value) throws Exception {
        setFeatureFlagValue("app_cloning", "enable_app_cloning_building_blocks",
                String.valueOf(value));
    }

    @Test
    public void testClonedAppsAccessManagedProfileContacts_contactReadSuccessfully()
            throws Exception {
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        TestContactsDataManager.TestRawContact rawContactDetails =
                sManagedProfileTestContactDetails.rawContact;
        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", rawContactDetails.accountType);
        args.put("test_contact_account_name", rawContactDetails.accountName);
        args.put("test_contact_custom_ringtone", rawContactDetails.customRingtone);

        for (TestContactsDataManager.TestRawContactData testContactData:
                sManagedProfileTestContactDetails.rawContactDataList) {
            if (testContactData.mimeType.equals(TEST_CONTACT_PHONE_NUMBER_MIMETYPE)) {
                args.put("test_contact_phone_number", testContactData.data1);
            }
        }

        // Check that cross-profile reads are allowed for cloned app with a launch-able activity
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testAccessManagedProfileContacts_contactReadSuccessfully",
                Integer.parseInt(sCloneUserId), args);
    }
}
