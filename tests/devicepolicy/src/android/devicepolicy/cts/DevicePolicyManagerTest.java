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

package android.devicepolicy.cts;

import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS_FULL;
import static android.app.AppOpsManager.MODE_ALLOWED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;
import static android.app.admin.ProvisioningException.ERROR_PRE_CONDITION_FAILED;
import static android.content.pm.PackageManager.FEATURE_DEVICE_ADMIN;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static android.nfc.NfcAdapter.EXTRA_NDEF_MESSAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.RequiresFeature;
import android.app.AppOpsManager;
import android.app.admin.DevicePolicyManager;
import android.app.admin.FullyManagedDeviceProvisioningParams;
import android.app.admin.ManagedProfileProvisioningParams;
import android.app.admin.PreferentialNetworkServiceConfig;
import android.app.admin.ProvisioningException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.CrossProfileApps;
import android.content.pm.PackageManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.os.BaseBundle;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;

import androidx.test.core.app.ApplicationProvider;

import com.android.bedstead.deviceadminapp.DeviceAdminApp;
import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.AfterClass;
import com.android.bedstead.harrier.annotations.EnsureDoesNotHavePermission;
import com.android.bedstead.harrier.annotations.EnsureHasNoWorkProfile;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireDoesNotHaveFeature;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireNotHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasDeviceOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoDpc;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasNoProfileOwner;
import com.android.bedstead.harrier.annotations.enterprise.EnsureHasProfileOwner;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.devicepolicy.DeviceOwner;
import com.android.bedstead.nene.devicepolicy.ProfileOwner;
import com.android.bedstead.nene.packages.Package;
import com.android.bedstead.nene.permissions.PermissionContext;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.bedstead.testapp.TestAppProvider;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class DevicePolicyManagerTest {
    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final DevicePolicyManager sDevicePolicyManager =
            sContext.getSystemService(DevicePolicyManager.class);
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);
    private static final SharedPreferences sSharedPreferences =
            sContext.getSharedPreferences("required-apps.txt", Context.MODE_PRIVATE);

    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            DeviceAdminApp.deviceAdminComponentName(sContext);

    private static final String MANAGE_PROFILE_AND_DEVICE_OWNERS =
            "android.permission.MANAGE_PROFILE_AND_DEVICE_OWNERS";
    private static final String MANAGE_DEVICE_ADMINS = "android.permission.MANAGE_DEVICE_ADMINS";

    private static final String PROFILE_OWNER_NAME = "testDeviceAdmin";
    private static final String DEVICE_OWNER_NAME = "testDeviceAdmin";

    private static final String ACCOUNT_NAME = "CTS";
    private static final String ACCOUNT_TYPE = "com.android.cts.test";
    private static final Account TEST_ACCOUNT = new Account(ACCOUNT_NAME, ACCOUNT_TYPE);

    private static final String USER_SETUP_COMPLETE_KEY = "user_setup_complete";

    private static final String KEY_PRE_PROVISIONING_SYSTEM_APPS = "pre_provisioning_system_apps";
    private static final String KEY_PRE_PROVISIONING_NON_SYSTEM_APPS =
            "pre_provisioning_non_system_apps";

    private static final String SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND =
            "dpm set-active-admin --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String SET_DEVICE_OWNER_COMMAND =
            "dpm set-device-owner --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String REMOVE_ACTIVE_ADMIN_COMMAND =
            "dpm remove-active-admin --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();
    private static final String SET_PROFILE_OWNER_COMMAND =
            "dpm set-profile-owner --user cur " + DEVICE_ADMIN_COMPONENT_NAME.flattenToString();

    private static final String NFC_INTENT_COMPONENT_NAME =
            "com.test.dpc/com.test.dpc.DeviceAdminReceiver";
    private static final String NFC_INTENT_PACKAGE_NAME =
            "com.test.dpc.DeviceAdminReceiver";
    private static final String NFC_INTENT_LOCALE = "en_US";
    private static final String NFC_INTENT_TIMEZONE = "America/New_York";
    private static final String NFC_INTENT_WIFI_SSID = "\"" + "TestWifiSsid" + "\"";
    private static final String NFC_INTENT_WIFI_SECURITY_TYPE = "";
    private static final String NFC_INTENT_WIFI_PASSWORD = "";
    private static final String NFC_INTENT_BAD_ACTION = "badAction";
    private static final String NFC_INTENT_BAD_MIME = "badMime";
    private static final String NFC_INTENT_PROVISIONING_SAMPLE = "NFC provisioning sample";
    private static final Intent NFC_INTENT_NO_NDEF_RECORD = new Intent(ACTION_NDEF_DISCOVERED);
    private static final HashMap<String, String> NFC_DATA_VALID = createNfcIntentData();
    private static final HashMap<String, String> NFC_DATA_EMPTY = new HashMap();
    private static final Map<String, String> NFC_DATA_WITH_COMPONENT_NAME =
            Map.of(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, NFC_INTENT_COMPONENT_NAME);
    private static final Map<String, String> NFC_DATA_WITH_ADMIN_PACKAGE_NAME =
            Map.of(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, NFC_INTENT_PACKAGE_NAME);

    private static final TestAppProvider sTestAppProvider = new TestAppProvider();
    private static final TestApp sDpcApp = sTestAppProvider.query()
            .whereIsDeviceAdmin().isTrue()
            .whereTestOnly().isFalse()
            .get();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final PersistableBundle ADMIN_EXTRAS_BUNDLE = createAdminExtrasBundle();
    private static final String TEST_KEY = "test_key";
    private static final String TEST_VALUE = "test_value";

    @Before
    public void setUp() {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            sDevicePolicyManager.setDpcDownloaded(false);
        }
    }

    @AfterClass
    public static void tearDown() {
        try (PermissionContext p = TestApis.permissions()
                .withPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)) {
            sDevicePolicyManager.setDpcDownloaded(false);
        }
    }

    @Test
    @EnsureHasNoDpc
    public void setAndRemoveDeviceOwnerRepeatedly_doesNotThrowError() {
        try (TestAppInstance dpcInstance = sDpcApp.install()) {
            ComponentName dpcComponentName = new ComponentName(sDpcApp.packageName(),
                    sDpcApp.packageName() + ".DeviceAdminReceiver");

            for (int i = 0; i < 100; i++) {
                DeviceOwner deviceOwner = TestApis.devicePolicy().setDeviceOwner(dpcComponentName);
                deviceOwner.remove();
            }
        }
    }

    @Test
    @EnsureHasNoDpc
    @EnsureHasNoWorkProfile
    @RequireRunOnPrimaryUser
    public void setAndRemoveProfileOwnerRepeatedly_doesNotThrowError() {
        try (UserReference profile = TestApis.users().createUser().createAndStart()) {
            try (TestAppInstance dpcInstance = sDpcApp.install(profile)) {
                ComponentName dpcComponentName = new ComponentName(sDpcApp.packageName(),
                        sDpcApp.packageName() + ".DeviceAdminReceiver");

                for (int i = 0; i < 100; i++) {
                    ProfileOwner profileOwner = TestApis.devicePolicy().setProfileOwner(
                            profile, dpcComponentName);

                    profileOwner.remove();
                }
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedManagedProfile_createsProfile() throws Exception {
        UserHandle profile = null;
        try {
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            assertThat(profile).isNotNull();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedManagedProfile_createsManagedProfile() throws Exception {
        UserHandle profile = null;
        try {
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            assertThat(sUserManager.isManagedProfile(profile.getIdentifier())).isTrue();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({MANAGE_PROFILE_AND_DEVICE_OWNERS, INTERACT_ACROSS_USERS_FULL})
    @Test
    public void newlyProvisionedManagedProfile_setsActiveAdmin() throws Exception {
        UserHandle profile = null;
        try {
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            assertThat(getDpmForUser(profile).getActiveAdmins()).hasSize(1);
            assertThat(getDpmForUser(profile).getActiveAdmins().get(0))
                    .isEqualTo(DEVICE_ADMIN_COMPONENT_NAME);
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({MANAGE_PROFILE_AND_DEVICE_OWNERS, INTERACT_ACROSS_USERS})
    @Test
    public void newlyProvisionedManagedProfile_setsProfileOwner() throws Exception {
        UserHandle profile = null;
        try {
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            DevicePolicyManager profileDpm = getDpmForUser(profile);
            assertThat(profileDpm.isProfileOwnerApp(sContext.getPackageName())).isTrue();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Ignore
    @Test
    public void newlyProvisionedManagedProfile_copiesAccountToProfile() throws Exception {
        UserHandle profile = null;
        try {
            // TODO(kholoudm): Add account to account manager once the API is ready in Nene
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder()
                            .setAccountToMigrate(TEST_ACCOUNT)
                            .build();
            profile = provisionManagedProfile(params);

            assertThat(hasTestAccount(profile)).isTrue();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedManagedProfile_removesAccountFromParentByDefault()
            throws Exception {
        UserHandle profile = null;
        try {
            // TODO(kholoudm): Add account to account manager once the API is ready in Nene
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder()
                            .setAccountToMigrate(TEST_ACCOUNT)
                            .build();
            profile = provisionManagedProfile(params);

            assertThat(hasTestAccount(sContext.getUser())).isFalse();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Ignore
    @Test
    public void newlyProvisionedManagedProfile_keepsAccountInParentIfRequested() throws Exception {
        UserHandle profile = null;
        try {
            // TODO(kholoudm): Add account to account manager once the API is ready in Nene
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder()
                            .setAccountToMigrate(TEST_ACCOUNT)
                            .setKeepingAccountOnMigration(true)
                            .build();
            profile = provisionManagedProfile(params);

            assertThat(hasTestAccount(sContext.getUser())).isTrue();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedManagedProfile_removesNonRequiredAppsFromProfile()
            throws Exception {
        UserHandle profile = null;
        try {
            Set<String> nonRequiredApps = sDevicePolicyManager.getDisallowedSystemApps(
                    DEVICE_ADMIN_COMPONENT_NAME,
                    sContext.getUserId(),
                    DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE);
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            assertThat(getInstalledPackagesOnUser(nonRequiredApps, profile)).isEmpty();
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedManagedProfile_setsCrossProfilePackages()
            throws Exception {
        UserHandle profile = null;
        try {
            ManagedProfileProvisioningParams params =
                    createManagedProfileProvisioningParamsBuilder().build();
            profile = provisionManagedProfile(params);

            Set<String> crossProfilePackages = getConfigurableDefaultCrossProfilePackages();
            for(String crossProfilePackage : crossProfilePackages) {
                assertIsCrossProfilePackageIfInstalled(crossProfilePackage);
            }
        } finally {
            if (profile != null) {
                TestApis.users().find(profile).remove();
            }
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "new test")
    @Test
    public void createAndProvisionManagedProfile_withExistingProfile_preconditionFails()
            throws Exception {
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder().build();

        ProvisioningException exception = assertThrows(ProvisioningException.class, () ->
                provisionManagedProfile(params));
        assertThat(exception.getProvisioningError()).isEqualTo(ERROR_PRE_CONDITION_FAILED);
    }

    private void assertIsCrossProfilePackageIfInstalled(String packageName) throws Exception {
        if (!isPackageInstalledOnCurrentUser(packageName)) {
            return;
        }
        for (UserHandle profile : sUserManager.getUserProfiles()) {
            assertThat(isCrossProfilePackage(packageName, profile)).isTrue();
        }
    }

    private boolean isCrossProfilePackage(String packageName, UserHandle profile)
            throws Exception {
        return getCrossProfileAppOp(packageName, profile) == MODE_ALLOWED;
    }

    private int getCrossProfileAppOp(String packageName, UserHandle userHandle) throws Exception {
        return sContext.getSystemService(AppOpsManager.class).unsafeCheckOpNoThrow(
                AppOpsManager.permissionToOp(android.Manifest.permission.INTERACT_ACROSS_PROFILES),
                getUidForPackageName(packageName, userHandle),
                packageName);
    }

    private int getUidForPackageName(String packageName, UserHandle userHandle) throws Exception {
        return sContext.createContextAsUser(userHandle, /* flags= */ 0)
                .getPackageManager()
                .getPackageUid(packageName, /* flags= */ 0);
    }

    private UserHandle provisionManagedProfile(ManagedProfileProvisioningParams params)
            throws Exception {
        return sDevicePolicyManager.createAndProvisionManagedProfile(params);
    }

    private ManagedProfileProvisioningParams.Builder
    createManagedProfileProvisioningParamsBuilder() {
        return new ManagedProfileProvisioningParams.Builder(
                        DEVICE_ADMIN_COMPONENT_NAME,
                        PROFILE_OWNER_NAME);
    }

    private boolean hasTestAccount(UserHandle user) {
        AccountManager am = getContextForUser(user).getSystemService(AccountManager.class);
        Account[] userAccounts = am.getAccountsByType(ACCOUNT_TYPE);
        for (Account account : userAccounts) {
            if (TEST_ACCOUNT.equals(account)) {
                return true;
            }
        }
        return false;
    }

    private Set<String> getInstalledPackagesOnUser(Set<String> packages, UserHandle user) {
        Set<String> installedPackagesOnUser = new HashSet<>();

        UserReference userRef = TestApis.users().find(user);
        Collection<Package> packageInUser = TestApis.packages().installedForUser(userRef);
        for (Package pkg : packageInUser) {
            if (packages.contains(pkg.packageName())) {
                installedPackagesOnUser.add(pkg.packageName());
            }
        }

        return installedPackagesOnUser;
    }

    private boolean isPackageInstalledOnCurrentUser(String packageName) {
        return isPackageInstalledOnUser(packageName, sContext.getUser());
    }

    private boolean isPackageInstalledOnUser(String packageName, UserHandle user) {
        return TestApis.packages().find(packageName)
                .installedOnUser(user);
    }

    private Set<String> getConfigurableDefaultCrossProfilePackages() {
        Set<String> defaultPackages = sDevicePolicyManager.getDefaultCrossProfilePackages();
        CrossProfileApps crossProfileApps = sContext.getSystemService(CrossProfileApps.class);
        return defaultPackages.stream().filter(
                crossProfileApps::canConfigureInteractAcrossProfiles).collect(
                Collectors.toSet());
    }

    private DevicePolicyManager getDpmForUser(UserHandle user) {
        return getContextForUser(user).getSystemService(DevicePolicyManager.class);
    }

    private Context getContextForUser(UserHandle user) {
        if (sContext.getUserId() == user.getIdentifier()) {
            return sContext;
        }
        try (PermissionContext p =
                     TestApis.permissions().withPermission(INTERACT_ACROSS_USERS_FULL)) {
            return sContext.createContextAsUser(user, /* flags= */ 0);
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission({MANAGE_PROFILE_AND_DEVICE_OWNERS})
    @Test
    public void newlyProvisionedFullyManagedDevice_setsDeviceOwner() throws Exception {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);
        try {

            FullyManagedDeviceProvisioningParams params =
                    createDefaultManagedDeviceProvisioningParamsBuilder().build();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertThat(sDevicePolicyManager.isDeviceOwnerApp(sContext.getPackageName())).isTrue();

        } finally {
            sDevicePolicyManager.forceRemoveActiveAdmin(
                    DEVICE_ADMIN_COMPONENT_NAME, sContext.getUserId());
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedFullyManagedDevice_doesNotThrowException() throws Exception {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);
        try {

            FullyManagedDeviceProvisioningParams params =
                    createDefaultManagedDeviceProvisioningParamsBuilder().build();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

        } finally {
            sDevicePolicyManager.forceRemoveActiveAdmin(
                    DEVICE_ADMIN_COMPONENT_NAME, sContext.getUserId());
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedFullyManagedDevice_canControlSensorPermissionGrantsByDefault()
            throws Exception {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);
        try {

            FullyManagedDeviceProvisioningParams params =
                    createDefaultManagedDeviceProvisioningParamsBuilder().build();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertThat(sDevicePolicyManager.canAdminGrantSensorsPermissions()).isTrue();

        } finally {
            sDevicePolicyManager.forceRemoveActiveAdmin(
                    DEVICE_ADMIN_COMPONENT_NAME, sContext.getUserId());
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Postsubmit(reason = "b/181993922 automatically marked flaky")
    @Test
    public void newlyProvisionedFullyManagedDevice_canOptOutOfControllingSensorPermissionGrants()
            throws Exception {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);
        try {

            FullyManagedDeviceProvisioningParams params =
                    createDefaultManagedDeviceProvisioningParamsBuilder()
                            .setCanDeviceOwnerGrantSensorsPermissions(false)
                            .build();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            assertThat(sDevicePolicyManager.canAdminGrantSensorsPermissions()).isFalse();

        } finally {
            sDevicePolicyManager.forceRemoveActiveAdmin(
                    DEVICE_ADMIN_COMPONENT_NAME, sContext.getUserId());
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_DEVICE_ADMIN)
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @Test
    public void newlyProvisionedFullyManagedDevice_leavesAllSystemAppsEnabledWhenRequested()
            throws Exception {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);
        try {
            Set<String> systemAppsBeforeProvisioning = findSystemApps();

            FullyManagedDeviceProvisioningParams params =
                    createDefaultManagedDeviceProvisioningParamsBuilder()
                            .setLeaveAllSystemAppsEnabled(true)
                            .build();
            sDevicePolicyManager.provisionFullyManagedDevice(params);

            Set<String> systemAppsAfterProvisioning = findSystemApps();
            assertThat(systemAppsAfterProvisioning).isEqualTo(systemAppsBeforeProvisioning);
        } finally {
            sDevicePolicyManager.forceRemoveActiveAdmin(
                    DEVICE_ADMIN_COMPONENT_NAME, sContext.getUserId());
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }


    @RequireDoesNotHaveFeature(PackageManager.FEATURE_AUTOMOTIVE)
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS)
    @Test
    public void getPolicyExemptAppsCanOnlyBeDefinedOnAutomotiveBuilds() throws Exception {
        assertWithMessage("list of policy-exempt apps")
                .that(sDevicePolicyManager.getPolicyExemptApps())
                .isEmpty();
    }

    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setPreferentialNetworkServiceConfig_withoutRequiredPermission() {
        PreferentialNetworkServiceConfig preferentialNetworkServiceConfigEnabled =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true).build();
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.setPreferentialNetworkServiceConfigs(
                        List.of(preferentialNetworkServiceConfigEnabled)));
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.setPreferentialNetworkServiceConfigs(
                        List.of(PreferentialNetworkServiceConfig.DEFAULT)));
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.getPreferentialNetworkServiceConfigs());
    }

    @Test
    public void setPreferentialNetworkServiceConfig_withRequiredPermission() {
        SystemUtil.runShellCommand(SET_PROFILE_OWNER_COMMAND);
        PreferentialNetworkServiceConfig preferentialNetworkServiceConfigEnabled =
                (new PreferentialNetworkServiceConfig.Builder())
                        .setEnabled(true).build();
        sDevicePolicyManager.setPreferentialNetworkServiceConfigs(
                List.of(preferentialNetworkServiceConfigEnabled));
        assertTrue(sDevicePolicyManager.getPreferentialNetworkServiceConfigs().get(0).isEnabled());
        sDevicePolicyManager.setPreferentialNetworkServiceConfigs(
                List.of(PreferentialNetworkServiceConfig.DEFAULT));
        assertFalse(sDevicePolicyManager.getPreferentialNetworkServiceConfigs().get(0).isEnabled());
        sDevicePolicyManager.clearProfileOwner(DEVICE_ADMIN_COMPONENT_NAME);
        SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
    }

    FullyManagedDeviceProvisioningParams.Builder
            createDefaultManagedDeviceProvisioningParamsBuilder() {
        return new FullyManagedDeviceProvisioningParams.Builder(
                DEVICE_ADMIN_COMPONENT_NAME,
                DEVICE_OWNER_NAME)
                // Don't remove system apps during provisioning until the testing
                // infrastructure supports restoring uninstalled apps.
                .setLeaveAllSystemAppsEnabled(true);
    }

    private Set<String> findSystemApps() {
        return sPackageManager.getInstalledApplications(PackageManager.MATCH_SYSTEM_ONLY)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
    }

    // TODO(b/175380793): Add remaining cts test for DPM#provisionManagedDevice and
    //  DPM#createAndProvisionManagedProfile.
    //  Currently the following methods are not used.
    /**
     * Allows {@link #restorePreProvisioningApps} to be called to restore the pre-provisioning apps
     * that were uninstalled during provisioning.
     */
    private void persistPreProvisioningApps() {
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND);
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_COMMAND);

        Set<String> systemApps = findSystemApps();
        sSharedPreferences.edit()
                .putStringSet(KEY_PRE_PROVISIONING_SYSTEM_APPS, systemApps)
                .commit();
        Set<String> nonSystemApps = findNonSystemApps(systemApps);
        sSharedPreferences.edit()
                .putStringSet(KEY_PRE_PROVISIONING_NON_SYSTEM_APPS, nonSystemApps)
                .commit();
        sDevicePolicyManager.setKeepUninstalledPackages(
                DEVICE_ADMIN_COMPONENT_NAME, new ArrayList<>(nonSystemApps));

        SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
    }

    /**
     * Restores apps that were uninstalled prior to provisioning. No-op if {@link
     * #persistPreProvisioningApps()} was not called prior to provisioning. Subsequent
     * calls will need another prior call to {@link #persistPreProvisioningApps()} to avoid being a
     * no-op.
     */
    public void restorePreProvisioningApps() {
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_ACTIVE_ADMIN_COMMAND);
        SystemUtil.runShellCommand(SET_DEVICE_OWNER_COMMAND);

        Set<String> postProvisioningSystemApps = findSystemApps();
        restorePreProvisioningSystemApps(postProvisioningSystemApps);
        restorePreProvisioningNonSystemApps(postProvisioningSystemApps);
        sSharedPreferences.edit().clear().commit();
        sDevicePolicyManager.setKeepUninstalledPackages(
                DEVICE_ADMIN_COMPONENT_NAME, new ArrayList<>());

        SystemUtil.runShellCommand(REMOVE_ACTIVE_ADMIN_COMMAND);
    }

    private void restorePreProvisioningSystemApps(Set<String> postProvisioningSystemApps) {
        Set<String> preProvisioningSystemApps = sSharedPreferences.getStringSet(
                KEY_PRE_PROVISIONING_SYSTEM_APPS, Collections.emptySet());
        for (String preProvisioningSystemApp : preProvisioningSystemApps) {
            if (postProvisioningSystemApps.contains(preProvisioningSystemApp)) {
                continue;
            }
            sDevicePolicyManager.enableSystemApp(
                    DEVICE_ADMIN_COMPONENT_NAME, preProvisioningSystemApp);
        }
    }

    private void restorePreProvisioningNonSystemApps(Set<String> postProvisioningSystemApps) {
        Set<String> preProvisioningNonSystemApps = sSharedPreferences.getStringSet(
                KEY_PRE_PROVISIONING_NON_SYSTEM_APPS, Collections.emptySet());
        Set<String> postProvisioningNonSystemApps = findNonSystemApps(postProvisioningSystemApps);
        for (String preProvisioningNonSystemApp : preProvisioningNonSystemApps) {
            if (postProvisioningNonSystemApps.contains(preProvisioningNonSystemApp)) {
                continue;
            }
            sDevicePolicyManager.installExistingPackage(
                    DEVICE_ADMIN_COMPONENT_NAME, preProvisioningNonSystemApp);
        }
    }

    private Set<String> findNonSystemApps(Set<String> systemApps) {
        return sPackageManager.getInstalledApplications(PackageManager.MATCH_ALL)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .filter(packageName -> !systemApps.contains(packageName))
                .collect(Collectors.toSet());
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_validNfcIntent_returnsValidIntent()
            throws IOException {
        Intent nfcIntent = createNfcIntentFromMap(NFC_DATA_VALID);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNotNull();
        assertThat(provisioningBundleToMap(provisioningIntent.getExtras()))
                .containsAtLeastEntriesIn(NFC_DATA_VALID);
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_noComponentNorPackage_returnsNull()
            throws IOException {
        Intent nfcIntent = createNfcIntentFromMap(NFC_DATA_EMPTY);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNull();
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_withComponent_returnsValidIntent()
            throws IOException {
        Intent nfcIntent = createNfcIntentFromMap(NFC_DATA_WITH_COMPONENT_NAME);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNotNull();
        assertThat(provisioningBundleToMap(provisioningIntent.getExtras()))
                .containsAtLeastEntriesIn(NFC_DATA_WITH_COMPONENT_NAME);
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_withPackage_returnsValidIntent()
            throws IOException {
        Intent nfcIntent = createNfcIntentFromMap(NFC_DATA_WITH_ADMIN_PACKAGE_NAME);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNotNull();
        assertThat(provisioningBundleToMap(provisioningIntent.getExtras()))
                .containsAtLeastEntriesIn(NFC_DATA_WITH_ADMIN_PACKAGE_NAME);
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_badIntentAction_returnsNull()
            throws IOException {
        Intent nfcIntent = createNfcIntentWithAction(NFC_INTENT_BAD_ACTION);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNull();
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_badMimeType_returnsNull()
            throws IOException {
        Intent nfcIntent = createNfcIntentWithMimeType(NFC_INTENT_BAD_MIME);

        Intent provisioningIntent =
                sDevicePolicyManager.createProvisioningIntentFromNfcIntent(nfcIntent);

        assertThat(provisioningIntent).isNull();
    }

    @Test
    public void createProvisioningIntentFromNfcIntent_doesNotIncludeNdefRecord_returnsNull() {
        Intent provisioningIntent = sDevicePolicyManager
                .createProvisioningIntentFromNfcIntent(NFC_INTENT_NO_NDEF_RECORD);

        assertThat(provisioningIntent).isNull();
    }

    @EnsureHasDeviceOwner
    @Test
    public void getCameraDisabled_adminPassedDoesNotBelongToCaller_throwsException() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.getCameraDisabled(
                sDeviceState.deviceOwner().componentName()));
    }

    @EnsureHasDeviceOwner
    @Test
    public void getKeyguardDisabledFeatures_adminPassedDoesNotBelongToCaller_throwsException() {
        assertThrows(SecurityException.class,
                () -> sDevicePolicyManager.getKeyguardDisabledFeatures(
                        sDeviceState.deviceOwner().componentName()));
    }

    @EnsureHasDeviceOwner
    @EnsureDoesNotHavePermission(MANAGE_DEVICE_ADMINS)
    @Test
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_throwsException() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.removeActiveAdmin(
                sDeviceState.deviceOwner().componentName()));
    }

    @EnsureHasDeviceOwner
    @EnsureHasPermission(MANAGE_DEVICE_ADMINS)
    @Test
    public void removeActiveAdmin_adminPassedDoesNotBelongToCaller_manageDeviceAdminsPermission_noException() {
        sDevicePolicyManager.removeActiveAdmin(
                sDeviceState.deviceOwner().componentName());
    }

    private static HashMap<String, String> createNfcIntentData() {
        HashMap<String, String> nfcIntentInput = new HashMap<String, String>();
        nfcIntentInput.putAll(
                Map.of(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, NFC_INTENT_COMPONENT_NAME,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, NFC_INTENT_PACKAGE_NAME,
                EXTRA_PROVISIONING_LOCALE, NFC_INTENT_LOCALE,
                EXTRA_PROVISIONING_TIME_ZONE, NFC_INTENT_TIMEZONE,
                EXTRA_PROVISIONING_WIFI_SSID, NFC_INTENT_WIFI_SSID,
                EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, NFC_INTENT_WIFI_SECURITY_TYPE,
                EXTRA_PROVISIONING_WIFI_PASSWORD, NFC_INTENT_WIFI_PASSWORD)
        );

        return nfcIntentInput;
    }

    private Intent createNfcIntentWithAction(String action)
            throws IOException {
        return createNfcIntent(NFC_DATA_VALID, action, MIME_TYPE_PROVISIONING_NFC);
    }

    private Intent createNfcIntentWithMimeType(String mime)
            throws IOException {
        return createNfcIntent(NFC_DATA_VALID, ACTION_NDEF_DISCOVERED, mime);
    }

    private Intent createNfcIntentFromMap(Map<String, String> input)
            throws IOException {
        return createNfcIntent(input, ACTION_NDEF_DISCOVERED, MIME_TYPE_PROVISIONING_NFC);
    }

    private Intent createNfcIntent(Map<String, String> input, String action, String mime)
            throws IOException {
        Intent nfcIntent = new Intent(action);
        Parcelable[] nfcMessages =
                new Parcelable[]{createNdefMessage(input, mime)};
        nfcIntent.putExtra(EXTRA_NDEF_MESSAGES, nfcMessages);

        return nfcIntent;
    }

    private Map<String, String> provisioningBundleToMap(Bundle bundle) {
        Map<String, String> map = new HashMap();

        for (String key : bundle.keySet()) {
            if(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME.equals(key)) {
                ComponentName componentName = bundle.getParcelable(key);
                map.put(key, componentName.getPackageName() + "/" + componentName.getClassName());
            }
            else {
                map.put(key, bundle.getString(key));
            }
        }

        return map;
    }

    private NdefMessage createNdefMessage(Map<String, String> provisioningValues, String mime)
            throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        Properties properties = new Properties();
        // Store all the values into the Properties object
        for (Map.Entry<String, String> e : provisioningValues.entrySet()) {
            properties.put(e.getKey(), e.getValue());
        }

        properties.store(stream, NFC_INTENT_PROVISIONING_SAMPLE);
        NdefRecord record = NdefRecord.createMime(mime, stream.toByteArray());

        return new NdefMessage(new NdefRecord[]{record});
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void checkProvisioningPreCondition_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()));

    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void checkProvisioningPreCondition_withRequiredPermission_doesNotThrowSecurityException() {
        sDevicePolicyManager.checkProvisioningPrecondition(
                DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                DEVICE_ADMIN_COMPONENT_NAME.getPackageName());

        // Doesn't throw exception.
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireDoesNotHaveFeature(FEATURE_DEVICE_ADMIN)
    public void checkProvisioningPreCondition_withoutDeviceAdminFeature_returnsDeviceAdminNotSupported() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_DEVICE_ADMIN_NOT_SUPPORTED);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireFeature(FEATURE_MANAGED_USERS)
    public void checkProvisioningPreCondition_actionPO_returnsOk() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_OK);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireDoesNotHaveFeature(FEATURE_MANAGED_USERS)
    public void checkProvisioningPreCondition_actionPO_withoutManagedUserFeature_returnsManagedUsersNotSupported() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_MANAGED_USERS_NOT_SUPPORTED);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasProfileOwner
    @RequireRunOnSecondaryUser
    public void checkProvisioningPreCondition_actionPO_onManagedUser_returnsHasProfileOwner() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnWorkProfile
    public void checkProvisioningPreCondition_actionPO_onManagedProfile_returnsHasProfileOwner() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    @RequireFeature(FEATURE_MANAGED_USERS)
    public void checkProvisioningPreCondition_actionPO_onManagedDevice_returnsCanNotAddManagedProfile() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_CANNOT_ADD_MANAGED_PROFILE);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasWorkProfile
    @RequireRunOnPrimaryUser
    public void checkProvisioningPreCondition_actionPO_withWorkProfile_returnsCanNotAddManagedProfile() {
        assertThat(
                sDevicePolicyManager.checkProvisioningPrecondition(
                        DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE,
                        DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                .isEqualTo(DevicePolicyManager.STATUS_CANNOT_ADD_MANAGED_PROFILE);
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireNotHeadlessSystemUserMode
    public void checkProvisioningPreCondition_actionDO_returnsOk() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_OK);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasNoDpc
    @RequireNotHeadlessSystemUserMode
    public void checkProvisioningPreCondition_actionDO_setupComplete_returnsUserSetupCompleted() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(true);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_USER_SETUP_COMPLETED);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnPrimaryUser
    @EnsureHasDeviceOwner
    @EnsureHasNoWorkProfile
    @RequireNotHeadlessSystemUserMode
    public void checkProvisioningPreCondition_actionDO_onManagedDevice_returnsHasDeviceOwner() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_HAS_DEVICE_OWNER);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnWorkProfile
    @RequireNotHeadlessSystemUserMode
    public void checkProvisioningPreCondition_actionDO_onManagedProfile_returnsHasProfileOwner() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnSecondaryUser
    @EnsureHasProfileOwner
    @RequireNotHeadlessSystemUserMode
    public void checkProvisioningPreCondition_actionDO_onManagedUser_returnsHasProfileOwner() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_USER_HAS_PROFILE_OWNER);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnSecondaryUser
    @EnsureHasNoProfileOwner
    @RequireNotHeadlessSystemUserMode
    @RequiresFeature(FEATURE_DEVICE_ADMIN)
    public void checkProvisioningPreCondition_actionDO_onNonSystemUser_returnsNotSystemUser() {
        boolean setupComplete = TestApis.users().current().getSetupComplete();
        TestApis.users().current().setSetupComplete(false);

        try {
            assertThat(
                    sDevicePolicyManager.checkProvisioningPrecondition(
                            DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE,
                            DEVICE_ADMIN_COMPONENT_NAME.getPackageName()))
                    .isEqualTo(DevicePolicyManager.STATUS_NOT_SYSTEM_USER);

        } finally {
            TestApis.users().current().setSetupComplete(setupComplete);
        }
    }

    // TODO(b/208843126): add more CTS coverage for setUserProvisioningState
    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setUserProvisioningState_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_UNMANAGED,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @RequireRunOnWorkProfile
    public void setUserProvisioningState_withRequiredPermission_doesNotThrowSecurityException() {
        sDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_PROFILE_FINALIZED,
                TestApis.users().current().userHandle());

        // Doesn't throw exception.
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_unmanagedDevice_stateUserSetupIncomplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_SETUP_INCOMPLETE,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_unmanagedDevice_stateUserSetupComplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_SETUP_COMPLETE,
                        TestApis.users().current().userHandle()));

    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_unmanagedDevice_stateUserSetupFinalized_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_SETUP_FINALIZED,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_unmanagedDevice_stateUserProfileComplete_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_PROFILE_COMPLETE,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_unmanagedDevice_stateUserProfileFinalized_throwsIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_PROFILE_FINALIZED,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasNoDpc
    public void setUserProvisioningState_settingToSameState_throwIllegalStateException() {
        assertThrows(IllegalStateException.class, () ->
                sDevicePolicyManager.setUserProvisioningState(
                        DevicePolicyManager.STATE_USER_UNMANAGED,
                        TestApis.users().current().userHandle()));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    @EnsureHasDeviceOwner
    public void setUserProvisioningState_unmanagedDevice_stateUserUnmanaged_doesNotThrowIllegalStateException() {
        sDevicePolicyManager.setUserProvisioningState(
                DevicePolicyManager.STATE_USER_PROFILE_FINALIZED,
                TestApis.users().current().userHandle());

        assertThat(sDevicePolicyManager.getUserProvisioningState())
                .isEqualTo(DevicePolicyManager.STATE_USER_PROFILE_FINALIZED);
    }

    @Test
    public void setAdminExtras_managedProfileParams_works() {
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder()
                        .setAdminExtras(ADMIN_EXTRAS_BUNDLE)
                        .build();

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void setAdminExtras_managedProfileParams_modifyBundle_internalBundleNotModified() {
        PersistableBundle adminExtrasBundle = new PersistableBundle(ADMIN_EXTRAS_BUNDLE);
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder()
                        .setAdminExtras(adminExtrasBundle)
                        .build();

        adminExtrasBundle.putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void getAdminExtras_managedProfileParams_modifyResult_internalBundleNotModified() {
        PersistableBundle adminExtrasBundle = new PersistableBundle(ADMIN_EXTRAS_BUNDLE);
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder()
                        .setAdminExtras(adminExtrasBundle)
                        .build();

        params.getAdminExtras().putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void setAdminExtras_managedProfileParams_emptyBundle_works() {
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder()
                        .setAdminExtras(new PersistableBundle())
                        .build();

        assertThat(params.getAdminExtras().isEmpty()).isTrue();
    }

    @Test
    public void setAdminExtras_managedProfileParams_nullBundle_works() {
        ManagedProfileProvisioningParams params =
                createManagedProfileProvisioningParamsBuilder()
                        .setAdminExtras(null)
                        .build();

        assertThat(params.getAdminExtras().isEmpty()).isTrue();
    }

    @Test
    public void setAdminExtras_fullyManagedParams_works() {
        FullyManagedDeviceProvisioningParams params =
                createDefaultManagedDeviceProvisioningParamsBuilder()
                        .setAdminExtras(ADMIN_EXTRAS_BUNDLE)
                        .build();

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void setAdminExtras_fullyManagedParams_modifyBundle_internalBundleNotModified() {
        PersistableBundle adminExtrasBundle = new PersistableBundle(ADMIN_EXTRAS_BUNDLE);
        FullyManagedDeviceProvisioningParams params =
                createDefaultManagedDeviceProvisioningParamsBuilder()
                        .setAdminExtras(adminExtrasBundle)
                        .build();

        adminExtrasBundle.putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void getAdminExtras_fullyManagedParams_modifyResult_internalBundleNotModified() {
        PersistableBundle adminExtrasBundle = new PersistableBundle(ADMIN_EXTRAS_BUNDLE);
        FullyManagedDeviceProvisioningParams params =
                createDefaultManagedDeviceProvisioningParamsBuilder()
                        .setAdminExtras(adminExtrasBundle)
                        .build();

        params.getAdminExtras().putString(TEST_KEY, TEST_VALUE);

        assertBundlesEqual(params.getAdminExtras(), ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void setAdminExtras_fullyManagedParams_emptyBundle_works() {
        FullyManagedDeviceProvisioningParams params =
                createDefaultManagedDeviceProvisioningParamsBuilder()
                        .setAdminExtras(new PersistableBundle())
                        .build();

        assertThat(params.getAdminExtras().isEmpty()).isTrue();
    }

    @Test
    public void setAdminExtras_fullyManagedParams_nullBundle_works() {
        FullyManagedDeviceProvisioningParams params =
                createDefaultManagedDeviceProvisioningParamsBuilder()
                        .setAdminExtras(null)
                        .build();

        assertThat(params.getAdminExtras().isEmpty()).isTrue();
    }

    @Test
    public void getDeviceManagerRoleHolderPackageName_doesNotCrash() {
        sDevicePolicyManager.getDevicePolicyManagementRoleHolderPackage();
    }

    private static PersistableBundle createAdminExtrasBundle() {
        PersistableBundle result = new PersistableBundle();
        result.putString("key1", "value1");
        result.putInt("key2", 2);
        result.putBoolean("key3", true);
        return result;
    }

    private static void assertBundlesEqual(BaseBundle bundle1, BaseBundle bundle2) {
        if (bundle1 != null) {
            assertWithMessage("Intent bundles are not equal")
                    .that(bundle2).isNotNull();
            assertWithMessage("Intent bundles are not equal")
                    .that(bundle1.keySet().size()).isEqualTo(bundle2.keySet().size());
            for (String key : bundle1.keySet()) {
                assertWithMessage("Intent bundles are not equal")
                        .that(bundle1.get(key))
                        .isEqualTo(bundle2.get(key));
            }
        } else {
            assertWithMessage("Intent bundles are not equal").that(bundle2).isNull();
        }
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureDoesNotHavePermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setDpcDownloaded_withoutRequiredPermission_throwsSecurityException() {
        assertThrows(SecurityException.class, () -> sDevicePolicyManager.setDpcDownloaded(true));
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void setDpcDownloaded_withRequiredPermission_doesNotThrowSecurityException() {
        sDevicePolicyManager.setDpcDownloaded(true);

        // Doesn't throw exception
    }

    @Postsubmit(reason = "New test")
    @Test
    @EnsureHasPermission(MANAGE_PROFILE_AND_DEVICE_OWNERS)
    public void isDpcDownloaded_returnsResultOfSetDpcDownloaded() {
        sDevicePolicyManager.setDpcDownloaded(true);

        assertThat(sDevicePolicyManager.isDpcDownloaded()).isTrue();
    }
}
