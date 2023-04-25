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

import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.ColumnBindings;
import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getDeleteContactCommand;
import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getInsertTestContactCommand;
import static com.android.cts.appcloning.contacts.ContactsShellCommandHelper.getQueryTestContactsCommand;


import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.AppModeFull;

import com.android.cts.appcloning.AppCloningBaseHostTest;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.CommandResult;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tests for checking contacts sharing behaviour in clone profile.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
public class ContactsSharingTest extends AppCloningBaseHostTest {

    private static final String LAUNCHABLE_CLONE_PROFILE_APP =
            "CtsAppCloningLaunchableCloneProfileApp.apk";
    private static final String NOT_LAUNCHABLE_CLONE_PROFILE_APP =
            "CtsAppCloningNotLaunchableCloneProfileApp.apk";
    private static final String CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST =
            "com.android.cts.launchable.cloneprofile.contacts.app";
    private static final String CLONE_NOT_LAUNCHABLE_APP_TEST =
            "com.android.cts.cloneprofile.contacts.app";
    private static final String CONTACTS_SHARING_TEST_CLASS = "CloneContactsSharingTest";
    private static final String CLONE_CONTACTS_PROVIDER_DATA_TEST_CLASS =
            "CloneContactsProviderDataTest";

    private static final String CONTACTS_PROVIDER_PACKAGE_NAME = "com.android.providers.contacts";
    private static final String OWNER_USER_ID = "0";

    private static final String RAW_CONTACTS_ENDPOINT = "raw_contacts";
    private static final String TEST_ACCOUNT_NAME = "test@test.com";
    private static final String TEST_ACCOUNT_TYPE = "test.com";
    private static final String TEST_CUSTOM_RINGTONE = "custom_1";
    private static final String ACCOUNT_NAME_COLUMN = "account_name";
    private static final String ACCOUNT_TYPE_COLUMN = "account_type";
    private static final String CUSTOM_RINGTONE_COLUMN = "custom_ringtone";

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        assumeTrue(isAtLeastU(testInfo.getDevice()));
        // TODO(b/253449368) Run the tests only if the device supports app-cloning. This would
        //  require adding the app-cloning building blocks config and using it in framework code.
        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
        waitForBroadcastIdle();
        setupTestContacts();
        switchAppCloningBuildingBlocksFlag(true);
    }

    @AfterClass
    public static void afterClass() throws Exception {
        switchAppCloningBuildingBlocksFlag(false);
        AppCloningBaseHostTest.baseHostTeardown();
        if (doesDeviceSupportContactSharing()) {
            cleanupTestContacts();
        }
    }

    private static boolean doesDeviceSupportContactSharing() throws Exception {
        return isAppCloningSupportedOnDevice() && isAtLeastU(sDevice);
    }

    private static void setupTestContacts() throws Exception {
        // Insert a test raw contact through primary CP2
        sDevice.executeShellCommand(getInsertTestContactCommand(RAW_CONTACTS_ENDPOINT,
                getTestRawContactContentValues(TEST_ACCOUNT_TYPE), OWNER_USER_ID));
    }

    private static void cleanupTestContacts() throws DeviceNotAvailableException {
        // Delete the test contact inserted earlier through primary CP2
        sDevice.executeShellCommand(getDeleteContactCommand(RAW_CONTACTS_ENDPOINT,
                "\"" + ACCOUNT_TYPE_COLUMN + "='" + TEST_ACCOUNT_TYPE + "'\"",
                OWNER_USER_ID));
        assertTestContactsAreCleanedUp();
    }

    private static void assertTestContactsAreCleanedUp() throws DeviceNotAvailableException {
        String queryResult =
                sDevice.executeShellCommand(getQueryTestContactsCommand(RAW_CONTACTS_ENDPOINT,
                "\"" + ACCOUNT_TYPE_COLUMN + "='" + TEST_ACCOUNT_TYPE + "'\"",
                OWNER_USER_ID));
        assertThat(queryResult).isEqualTo("No result found.\n");
    }

    /**
     * Enables/Disables the contact sharing feature flag
     * @throws Exception
     */
    private static void switchAppCloningBuildingBlocksFlag(boolean value) throws Exception {
        setFeatureFlagValue("app_cloning", "enable_app_cloning_building_blocks",
                String.valueOf(value));
    }

    private static List<ColumnBindings> getTestRawContactContentValues(String testAccountType) {
        List<ColumnBindings> testContactBindings = new ArrayList<>();
        testContactBindings.add(new ColumnBindings(ACCOUNT_NAME_COLUMN, TEST_ACCOUNT_NAME,
                ColumnBindings.Type.STRING));
        testContactBindings.add(new ColumnBindings(ACCOUNT_TYPE_COLUMN, testAccountType,
                ColumnBindings.Type.STRING));
        testContactBindings.add(new ColumnBindings(CUSTOM_RINGTONE_COLUMN, TEST_CUSTOM_RINGTONE,
                ColumnBindings.Type.STRING));
        return testContactBindings;
    }

    @Test
    public void testTwoContactsProviderProcesses() throws Exception {
        CommandResult cloneProfileCommandResult = executeShellV2Command(
                "pm list packages --user " + sCloneUserId);
        assertTrue(isSuccessful(cloneProfileCommandResult));
        assertThat(cloneProfileCommandResult.getStdout().contains(CONTACTS_PROVIDER_PACKAGE_NAME))
                .isTrue();

        CommandResult primaryProfileCommandResult = executeShellV2Command(
                "pm list packages --user " + OWNER_USER_ID);
        assertTrue(isSuccessful(primaryProfileCommandResult));
        assertThat(primaryProfileCommandResult.getStdout().contains(CONTACTS_PROVIDER_PACKAGE_NAME))
                .isTrue();
    }

    /**
     * The test below tries to insert a raw_contact through a cloned app using the content resolver
     * insert API and verifies that no contacts are inserted in both the primary and the clone
     * contacts provider. The primary contacts provider verification is done by querying for
     * raw_contacts from a cloned app with a launcher activity to ensure the read is redirected to
     * the primary provider. The clone contacts provider verification is done by querying for
     * raw_contacts from a cloned app without a launcher activity. Please note that by design no
     * contacts should be present in the clone contacts provider.
     */
    @Test
    public void testCloneContactsProviderInsert_rawContacts_noContactsInsertedInBothProviders()
            throws Exception {
        // Install the device side test APKs
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(NOT_LAUNCHABLE_CLONE_PROFILE_APP, "--user "
                + Integer.valueOf(sCloneUserId));

        // This test tries to insert the contacts, and also verifies that none are actually inserted
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderInsert_rawContacts_doesNotInsertActually",
                Integer.parseInt(sCloneUserId),
                new HashMap<>());

        // Check that no contacts were inserted in the clone CP2 database, it should be empty
        runDeviceTestAsUser(CLONE_NOT_LAUNCHABLE_APP_TEST,
                CLONE_NOT_LAUNCHABLE_APP_TEST + "." + CLONE_CONTACTS_PROVIDER_DATA_TEST_CLASS,
                "testCloneContactsProvider_rawContactsIsEmpty", Integer.parseInt(sCloneUserId),
                new HashMap<>());
    }

    /**
     * The test below tries to bulkInsert a contact through a cloned app using the content resolver
     * bulkInsert API and verifies that no contacts are inserted in both the primary and the clone
     * contacts provider. The primary contacts provider verification is done by querying for
     * raw_contacts from a cloned app with a launcher activity to ensure the read is redirected to
     * the primary provider. The clone contacts provider verification is done by querying for
     * raw_contacts from a cloned app without a launcher activity. Please note that by design no
     * contacts should be present in the clone contacts provider.
     */
    @Test
    public void testCloneContactsProviderBulkInsert_rawContacts_noContactsInsertedInBothProviders()
            throws Exception {
        // Install the device side test APKs
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(NOT_LAUNCHABLE_CLONE_PROFILE_APP, "--user "
                + Integer.valueOf(sCloneUserId));

        // This test tries to bulkInsert the contacts, and also verifies that none are actually
        // inserted
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderBulkInsert_rawContacts_noContactsInserted",
                Integer.parseInt(sCloneUserId),
                new HashMap<>());

        // Check that no contacts were inserted in the clone CP2 database, it should be empty
        runDeviceTestAsUser(CLONE_NOT_LAUNCHABLE_APP_TEST,
                CLONE_NOT_LAUNCHABLE_APP_TEST + "." + CLONE_CONTACTS_PROVIDER_DATA_TEST_CLASS,
                "testCloneContactsProvider_rawContactsIsEmpty", Integer.parseInt(sCloneUserId),
                new HashMap<>());
    }

    /**
     * The test below tries to insert multiple raw_contacts through a cloned app using the
     * ContentResolver applyBatch API and verifies that no contacts are inserted in both the
     * primary and the clone contacts provider. The primary contacts provider verification is done
     * by querying for raw_contacts from a cloned app with a launcher activity to ensure the read is
     * redirected to the primary provider. The clone contacts provider verification is done by
     * querying for raw_contacts from a cloned app without a launcher activity. Please note that by
     * design no contacts should be present in the clone contacts provider.
     */
    @Test
    public void testCloneContactsProviderApplyBatch_rawContacts_noContactsInsertedInBothProviders()
            throws Exception {
        // Install the device side test APKs
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        installPackage(NOT_LAUNCHABLE_CLONE_PROFILE_APP, "--user "
                + Integer.valueOf(sCloneUserId));

        // This test tries to insert the contacts through applyBatch operation, and also verifies
        // that none are actually inserted
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderApplyBatch_rawContacts_noContactsInserted",
                Integer.parseInt(sCloneUserId),
                new HashMap<>());

        // Check that no contacts were inserted in the clone CP2 database, it should be empty
        runDeviceTestAsUser(CLONE_NOT_LAUNCHABLE_APP_TEST,
                CLONE_NOT_LAUNCHABLE_APP_TEST + "." + CLONE_CONTACTS_PROVIDER_DATA_TEST_CLASS,
                "testCloneContactsProvider_rawContactsIsEmpty", Integer.parseInt(sCloneUserId),
                new HashMap<>());
    }

    /**
     * This test adds a raw_contact through the primary contacts provider using the content insert
     * shell command, tries to update the contact through ContentResolver update API and verifies
     * that no contacts are actually updated through the operation.
     */
    @Test
    public void testCloneContactsProviderUpdates_rawContactsUpdate_doesNotUpdateActually()
            throws Exception {
        // Install the device side test APK
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));

        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", TEST_ACCOUNT_TYPE);
        args.put("test_contact_account_name", TEST_ACCOUNT_NAME);
        args.put("test_contact_custom_ringtone", TEST_CUSTOM_RINGTONE);

        // Check that contact updates through cloned apps are blocked
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderUpdates_rawContactsUpdate_doesNotUpdateActually",
                Integer.parseInt(sCloneUserId), args);
    }

    /**
     * This test adds a raw_contact through the primary contacts provider using the content insert
     * shell command, tries to delete the contact through ContentResolver delete API and verifies
     * that no contacts are actually updated through the operation.
     */
    @Test
    public void testCloneContactsProviderDeletes_rawContactsDelete_doesNotDeleteActually()
            throws Exception {
        // Install the device side test APK
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));

        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", TEST_ACCOUNT_TYPE);

        // Check that contact deletes through cloned apps are blocked
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderDeletes_rawContactsDelete_doesNotDeleteActually",
                Integer.parseInt(sCloneUserId), args);
    }

    /**
     * This test creates an account through a cloned app and checks that contacts syncs are disabled
     * for that account.
     */
    @Test
    public void testCloneAccountsContactsSync_syncsAreDisabled() throws Exception {
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        Map<String, String> args = new HashMap<>();

        // Check that contact syncs for cloned accounts are disabled
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testContactSyncsForCloneAccounts_syncsAreDisabled",
                Integer.parseInt(sCloneUserId), args);
    }

    /**
     * This test adds a raw_contact through the primary contacts provider using the content insert
     * shell command and verifies that the inserted contact is accessible from a cloned app
     * with a launcher activity by redirecting the requests to the primary contacts provider.
     */
    @Test
    public void testReadsForClonedAppWithLauncherActivity_rawContactReads_redirectsToPrimary()
            throws Exception {
        installPackage(LAUNCHABLE_CLONE_PROFILE_APP, "--user " + Integer.valueOf(sCloneUserId));
        Map<String, String> args = new HashMap<>();
        args.put("test_contact_account_type", TEST_ACCOUNT_TYPE);
        args.put("test_contact_account_name", TEST_ACCOUNT_NAME);
        args.put("test_contact_custom_ringtone", TEST_CUSTOM_RINGTONE);

        // Check that cross-profile reads are allowed for cloned app with a launch-able activity
        runDeviceTestAsUser(CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST,
                CLONE_LAUNCHABLE_APP_CONTACTS_SHARING_TEST + "." + CONTACTS_SHARING_TEST_CLASS,
                "testCloneContactsProviderReads_rawContactsReads_redirectsToPrimary",
                Integer.parseInt(sCloneUserId), args);
    }

    /**
     * This test adds a raw_contact through the primary contacts provider using the content insert
     * shell command and verifies that the inserted contact is not accessible from a cloned app
     * without a launcher activity
     */
    @Test
    public void testClonedAppWithoutLauncherActivityReads_rawContactReads_emptyResponse()
            throws Exception {
        installPackage(NOT_LAUNCHABLE_CLONE_PROFILE_APP, "--user "
                + Integer.valueOf(sCloneUserId));
        // Check that cross-profile results are disabled for cloned app without a launcher
        // activity. Results should be served from clone CP2 database and hence should be empty.
        runDeviceTestAsUser(CLONE_NOT_LAUNCHABLE_APP_TEST,
                CLONE_NOT_LAUNCHABLE_APP_TEST + "." + CLONE_CONTACTS_PROVIDER_DATA_TEST_CLASS,
                "testCloneContactsProvider_rawContactsIsEmpty", Integer.parseInt(sCloneUserId),
                new HashMap<>());
    }
}
