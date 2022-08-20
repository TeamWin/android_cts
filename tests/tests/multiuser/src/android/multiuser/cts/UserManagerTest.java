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
 * limitations under the License
 */

package android.multiuser.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.Manifest.permission.QUERY_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.PermissionHelper.adoptShellPermissionIdentity;
import static android.multiuser.cts.TestingUtils.getBooleanProperty;
import static android.os.UserManager.USER_OPERATION_SUCCESS;
import static android.os.UserManager.USER_TYPE_FULL_SECONDARY;
import static android.os.UserManager.USER_TYPE_PROFILE_MANAGED;

import static com.android.bedstead.harrier.OptionalBoolean.FALSE;
import static com.android.bedstead.harrier.OptionalBoolean.TRUE;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeNoException;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.content.pm.UserProperties;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.SystemUserOnly;
import android.util.Log;
import android.view.Display;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasPermission;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireFeature;
import com.android.bedstead.harrier.annotations.RequireHeadlessSystemUserMode;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.bedstead.nene.users.UserType;
import com.android.bedstead.nene.utils.Poll;
import com.android.bedstead.testapp.TestApp;
import com.android.bedstead.testapp.TestAppInstance;
import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RunWith(BedsteadJUnit4.class)
public final class UserManagerTest {

    private static final String TAG = UserManagerTest.class.getSimpleName();

    @ClassRule
    @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private static final Context sContext = TestApis.context().instrumentedContext();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UserManager mUserManager;

    private final String mAccountName = "test_account_name";
    private final String mAccountType = "test_account_type";

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();
    }

    private void removeUser(UserHandle userHandle) {
        if (userHandle == null) {
            return;
        }

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            assertThat(mUserManager.removeUser(userHandle)).isTrue();
        }
    }

    /**
     * Verify that the isUserAGoat() method always returns false for API level 30. This is
     * because apps targeting R no longer have access to package queries by default.
     */
    @Test
    public void testUserGoat_api30() {
        assertWithMessage("isUserAGoat()").that(mUserManager.isUserAGoat()).isFalse();
    }

    @Test
    public void testIsRemoveResultSuccessful() {
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_REMOVED))
                .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_DEFERRED))
                .isTrue();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ALREADY_BEING_REMOVED))
                        .isTrue();
        assertThat(UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_UNKNOWN))
                .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_RESTRICTION))
                        .isFalse();
        assertThat(UserManager
                .isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_USER_NOT_FOUND))
                        .isFalse();
        assertThat(
                UserManager.isRemoveResultSuccessful(UserManager.REMOVE_RESULT_ERROR_SYSTEM_USER))
                        .isFalse();
    }

    @Test
    public void testIsHeadlessSystemUserMode() throws Exception {
        boolean expected = getBooleanProperty(mInstrumentation,
                "ro.fw.mu.headless_system_user");
        assertWithMessage("isHeadlessSystemUserMode()")
                .that(UserManager.isHeadlessSystemUserMode()).isEqualTo(expected);
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    public void testIsUserForeground_differentContext_noPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserForeground());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserForeground_differentContext_withPermission() throws Exception {
        Context userContext = sContext.createContextAsUser(UserHandle.of(-42), /* flags= */ 0);
        UserManager um = userContext.getSystemService(UserManager.class);

        assertWithMessage("isUserForeground() for unknown user").that(um.isUserForeground())
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    public void testIsUserForeground_currentUser() throws Exception {
        assertWithMessage("isUserForeground() for current user")
                .that(mUserManager.isUserForeground()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserForeground_backgroundUser() throws Exception {
        assertWithMessage("isUserForeground() for bg user (%s)", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserForeground"})
    @RequireRunOnWorkProfile // TODO(b/239961027): should be @RequireRunOnProfile instead
    public void testIsUserForeground_profileOfCurrentUser() throws Exception {
        assertWithMessage("isUserForeground() for profile(%s) of current user", sContext.getUser())
                .that(mUserManager.isUserForeground()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    public void testIsUserVisible_differentContext_noPermission() throws Exception {
        Context context = sContext.createContextAsUser(UserHandle.of(-42), /* flags= */ 0);
        UserManager um = context.getSystemService(UserManager.class);

        assertThrows(SecurityException.class, () -> um.isUserVisible());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void testIsUserVisible_differentContext_withPermission() throws Exception {
        Context context = getContextForOtherUser();
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for unknown user").that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    public void testIsUserVisible_currentUser() throws Exception {
        assertWithMessage("isUserVisible() for current user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isTrue();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireRunOnSecondaryUser(switchedToUser = FALSE)
    public void testIsUserVisible_backgroundUser() throws Exception {
        assertWithMessage("isUserVisible() for background user (id=%s)", sContext.getUser())
                .that(mUserManager.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/239961027): should be @RequireRunOnProfile instead
    @RequireRunOnWorkProfile(switchedToParentUser = TRUE)
    public void testIsUserVisible_startedProfileOfCurrentUser() throws Exception {
        assertWithMessage("isUserVisible() for profile of current user (%s)",
                sContext.getUser()).that(mUserManager.isUserVisible()).isTrue();
    }

    @FlakyTest(bugId = 242364454)
    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    // Cannot use @RunOnProfile as it will stop the profile
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    // TODO(b/239961027): should be @EnsureHasProfile instead of @EnsureHasWorkProfile
    @EnsureHasWorkProfile(installInstrumentedApp = TRUE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // need to call isUserVisible() on other context
    public void testIsUserVisible_stoppedProfileOfCurrentUser() throws Exception {
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        Context context = getContextForUser(profile.userHandle().getIdentifier());
        UserManager um = context.getSystemService(UserManager.class);

        assertWithMessage("isUserVisible() for stopped profile (id=%s) of current user",
                profile.id()).that(um.isUserVisible()).isFalse();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    public void testIsUserVisible_backgroundUserOnSecondaryDisplay() throws Exception {
        UserReference user = sDeviceState.secondaryUser();
        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
        startBackgroundUserOnSecondaryDisplay(user, displayId);
        try {
            TestApp testApp = sDeviceState.testApps().any();
            try (TestAppInstance instance = testApp.install(user)) {
                assertWithMessage("isUserVisible() for background user (id=%s) on display %s",
                        user.id(), displayId).that(instance.userManager().isUserVisible()).isTrue();
            }
        } finally {
            user.stop();
        }
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    public void testIsUserVisible_startedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference user = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "user: id=" + user.id());
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + user.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(user)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
                startBackgroundUserOnSecondaryDisplay(user, displayId);
                startBackgroundUserOnSecondaryDisplay(profile, displayId);

                TestApp testApp = sDeviceState.testApps().any();
                try (TestAppInstance instance = testApp.install(profile)) {
                    assertWithMessage("isUserVisible() for started profile (id=%s) of baground user"
                            + " (id=%s) on display %s", profile.id(), user.id(), displayId)
                                    .that(instance.userManager().isUserVisible()).isTrue();
                } // test instance
            } // new profile
        } // new user
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // need to call isUserVisible() on other context
    public void testIsUserVisible_stoppedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        Log.d(TAG, "Creating bg user and profile");
        try (UserReference user = TestApis.users().createUser().name("parent_user").create()) {
            Log.d(TAG, "user: id=" + user.id());
            try (UserReference profile = TestApis.users().createUser()
                    .name("profile_of_" + user.id())
                    // TODO(b/239961027): type should be just PROFILE_TYPE_NAME
                    .type(TestApis.users().supportedType(UserType.MANAGED_PROFILE_TYPE_NAME))
                    .parent(user)
                    .create()) {
                Log.d(TAG, "profile: id=" + profile.id());

                int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
                startBackgroundUserOnSecondaryDisplay(user, displayId);

                // Make sure it's stopped, as it could have been automatically started with parent
                // user
                Log.d(TAG, "Stopping profile " + profile.id());
                profile.stop();

                TestApis.packages().instrumented().installExisting(profile);

                Context context = getContextForUser(profile.userHandle().getIdentifier());
                UserManager um = context.getSystemService(UserManager.class);

                assertWithMessage("isUserVisible() for stopped profile (id=%s) of background user "
                        + "(id=%s) on secondary display (id=%s)", profile.id(), user.id(),
                        displayId).that(um.isUserVisible()).isFalse();
            } // new profile
        } // new user
    }


    @Test
    public void testCloneProfile() throws Exception {
        UserHandle userHandle = null;

        // Need CREATE_USERS permission to create user in test
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            try {
                userHandle = mUserManager.createProfile(
                    "Clone profile", UserManager.USER_TYPE_PROFILE_CLONE, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create clone profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final Context userContext = sContext.createPackageContextAsUser("system", 0,
                    userHandle);
            final UserManager cloneUserManager = userContext.getSystemService(UserManager.class);
            assertThat(cloneUserManager.isMediaSharedWithParent()).isTrue();
            assertThat(cloneUserManager.isCredentialSharableWithParent()).isTrue();
            assertThat(cloneUserManager.isCloneProfile()).isTrue();
            assertThat(cloneUserManager.isProfile()).isTrue();
            assertThat(cloneUserManager.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isTrue();

            final List<UserInfo> list = mUserManager.getUsers(true, true, true);
            final UserHandle finalUserHandle = userHandle;
            final List<UserInfo> cloneUsers = list.stream().filter(
                    user -> (user.id == finalUserHandle.getIdentifier()
                            && user.isCloneProfile()))
                    .collect(Collectors.toList());
            assertThat(cloneUsers.size()).isEqualTo(1);
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testManagedProfile() throws Exception {
        UserHandle userHandle = null;

        try {
            try {
                userHandle = mUserManager.createProfile(
                    "Managed profile", UserManager.USER_TYPE_PROFILE_MANAGED, new HashSet<>());
            } catch (UserManager.UserOperationException e) {
                // Not all devices and user types support these profiles; skip if this one doesn't.
                assumeNoException("Couldn't create managed profile", e);
                return;
            }
            assertThat(userHandle).isNotNull();

            final UserManager umOfProfile = sContext
                    .createPackageContextAsUser("android", 0, userHandle)
                    .getSystemService(UserManager.class);

            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(umOfProfile.isManagedProfile()).isTrue();
            }
            assertThat(umOfProfile.isManagedProfile(userHandle.getIdentifier())).isTrue();
            assertThat(umOfProfile.isProfile()).isTrue();
            assertThat(umOfProfile.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isTrue();
        } finally {
            removeUser(userHandle);
        }
    }

    @Test
    @EnsureHasPermission({QUERY_USERS})
    public void testSystemUser() throws Exception {
        final UserManager umOfSys = sContext
                .createPackageContextAsUser("android", 0, UserHandle.SYSTEM)
                .getSystemService(UserManager.class);

        // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
            assertThat(umOfSys.isSystemUser()).isTrue();
        }

        // We cannot demand what type of user SYSTEM is, but we can say some things it isn't.
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_CLONE)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_PROFILE_MANAGED)).isFalse();
        assertThat(umOfSys.isUserOfType(UserManager.USER_TYPE_FULL_GUEST)).isFalse();

        assertThat(umOfSys.isProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile()).isFalse();
        assertThat(umOfSys.isManagedProfile(UserHandle.USER_SYSTEM)).isFalse();
        assertThat(umOfSys.isCloneProfile()).isFalse();
    }

    @Test
    @SystemUserOnly(reason = "Restricted users are only supported on system user.")
    public void testRestrictedUser() throws Exception {
        UserHandle user = null;
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            // Check that the SYSTEM user is not restricted.
            assertThat(mUserManager.isRestrictedProfile()).isFalse();
            assertThat(mUserManager.isRestrictedProfile(UserHandle.SYSTEM)).isFalse();
            assertThat(mUserManager.getRestrictedProfileParent()).isNull();

            final UserInfo info = mUserManager.createRestrictedProfile("Restricted user");

            // If the device supports Restricted users, it must report it correctly.
            assumeTrue("Couldn't create a restricted profile", info != null);

            user = UserHandle.of(info.id);
            assertThat(mUserManager.isRestrictedProfile(user)).isTrue();

            final Context userContext = sContext.createPackageContextAsUser("system", 0, user);
            final UserManager userUm = userContext.getSystemService(UserManager.class);
            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(userUm.isRestrictedProfile()).isTrue();
            }
            assertThat(userUm.getRestrictedProfileParent().isSystem()).isTrue();
        } finally {
            removeUser(user);
        }
    }

    private NewUserRequest newUserRequest() {
        final PersistableBundle accountOptions = new PersistableBundle();
        accountOptions.putString("test_account_option_key", "test_account_option_value");

        return new NewUserRequest.Builder()
                .setName("test_user")
                .setUserType(USER_TYPE_FULL_SECONDARY)
                .setUserIcon(Bitmap.createBitmap(32, 32, Bitmap.Config.RGB_565))
                .setAccountName(mAccountName)
                .setAccountType(mAccountType)
                .setAccountOptions(accountOptions)
                .build();
    }

    @Test
    public void testSomeUserHasAccount() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
            user = mUserManager.createUser(newUserRequest()).getUser();
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isTrue();
        } finally {
            removeUser(user);
        }
    }

    @Test
    public void testSomeUserHasAccount_shouldIgnoreToBeRemovedUsers() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserResponse response = mUserManager.createUser(newUserRequest());
            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            mUserManager.removeUser(response.getUser());
            assertThat(mUserManager.someUserHasAccount(mAccountName, mAccountType)).isFalse();
        }
    }

    @Test
    public void testCreateUser_withNewUserRequest_shouldCreateUserWithCorrectProperties()
            throws PackageManager.NameNotFoundException {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserRequest request = newUserRequest();
            final NewUserResponse response = mUserManager.createUser(request);
            user = response.getUser();

            assertThat(response.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response.isSuccessful()).isTrue();
            assertThat(user).isNotNull();

            UserManager userManagerOfNewUser = sContext
                    .createPackageContextAsUser("android", 0, user)
                    .getSystemService(UserManager.class);

            assertThat(userManagerOfNewUser.getUserName()).isEqualTo(request.getName());
            // TODO(b/222584163): Remove the if{} clause after v33 Sdk bump.
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.S_V2) {
                assertThat(userManagerOfNewUser.isUserNameSet()).isTrue();
            }
            assertThat(userManagerOfNewUser.getUserType()).isEqualTo(request.getUserType());
            assertThat(userManagerOfNewUser.isUserOfType(request.getUserType())).isEqualTo(true);
            // We can not test userIcon and accountOptions,
            // because getters require MANAGE_USERS permission.
            // And we are already testing accountName and accountType
            // are set correctly in testSomeUserHasAccount method.
        } finally {
            removeUser(user);
        }
    }

    @Test
    public void testCreateUser_withNewUserRequest_shouldNotAllowDuplicateUserAccounts() {
        // TODO: (b/233197356): Replace with bedstead annotation.
        assumeTrue(mUserManager.supportsMultipleUsers());
        UserHandle user1 = null;
        UserHandle user2 = null;

        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            final NewUserResponse response1 = mUserManager.createUser(newUserRequest());
            user1 = response1.getUser();

            assertThat(response1.getOperationResult()).isEqualTo(USER_OPERATION_SUCCESS);
            assertThat(response1.isSuccessful()).isTrue();
            assertThat(user1).isNotNull();

            final NewUserResponse response2 = mUserManager.createUser(newUserRequest());
            user2 = response2.getUser();

            assertThat(response2.getOperationResult()).isEqualTo(
                    UserManager.USER_OPERATION_ERROR_USER_ACCOUNT_ALREADY_EXISTS);
            assertThat(response2.isSuccessful()).isFalse();
            assertThat(user2).isNull();
        } finally {
            removeUser(user1);
            removeUser(user2);
        }
    }

    @Test
    @AppModeFull
    @EnsureHasWorkProfile
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsParent() {
        final UserReference parent = TestApis.users().instrumented();
        for (UserHandle profile : mUserManager.getUserProfiles()) {
            if (!profile.equals(parent.userHandle())) {
                assertThat(mUserManager.getProfileParent(profile)).isEqualTo(parent.userHandle());
            }
        }
    }

    @Test
    @AppModeFull
    @EnsureHasPermission(INTERACT_ACROSS_USERS)
    public void getProfileParent_returnsNullForNonProfile() {
        assertThat(mUserManager.getProfileParent(TestApis.users().system().userHandle())).isNull();
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableUserCount() {
        final int maxAllowedIterations = 15;
        final String userType = USER_TYPE_FULL_SECONDARY;
        final NewUserRequest request = new NewUserRequest.Builder().build();
        final ArrayDeque<UserHandle> usersCreated = new ArrayDeque<>();

        try {
            final int initialRemainingCount = mUserManager.getRemainingCreatableUserCount(userType);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                usersCreated.push(mUserManager.createUser(request).getUser());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(usersCreated.pop());
                assertThat(mUserManager.getRemainingCreatableUserCount(userType))
                        .isEqualTo(initialRemainingCount - usersCreated.size());
            }
        } finally {
            usersCreated.forEach(this::removeUser);
        }
    }

    @Test
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetRemainingCreatableProfileCount() {
        final int maxAllowedIterations = 15;
        final String type = USER_TYPE_PROFILE_MANAGED;
        final ArrayDeque<UserHandle> profilesCreated = new ArrayDeque<>();
        final Set<String> disallowedPackages = new HashSet<>();
        try {
            final int initialRemainingCount =
                    mUserManager.getRemainingCreatableProfileCount(type);
            assertThat(initialRemainingCount).isAtLeast(0);

            final int numUsersToAdd = Math.min(maxAllowedIterations, initialRemainingCount);

            for (int i = 0; i < numUsersToAdd; i++) {
                profilesCreated.push(mUserManager.createProfile(null, type, disallowedPackages));
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
            for (int i = 0; i < numUsersToAdd; i++) {
                mUserManager.removeUser(profilesCreated.pop());
                assertThat(mUserManager.getRemainingCreatableProfileCount(type))
                        .isEqualTo(initialRemainingCount - profilesCreated.size());
            }
        } finally {
            profilesCreated.forEach(this::removeUser);
        }
    }

    // TODO(b/240736142): tests below should belong to ActivityManagerTest or similar, but it
    // doesn't use bedstead yet

    @FlakyTest(bugId = 242364454)
    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasWorkProfile
    public void testStartUserInBackgroundOnSecondaryDisplay_profileOnDifferentDisplay() {
        // Stop the profile as it was initially running in the same display as parent
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
        int userId = profile.id();
        boolean started = tryTostartBackgroundUserOnSecondaryDisplay(userId, displayId);
        Log.d(TAG, "Started: " + started);

        assertWithMessage("started profile %s on display %s", userId, displayId).that(started)
                .isFalse();
    }

    @FlakyTest(bugId = 242364454)
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @EnsureHasWorkProfile
    @Test
    public void testStartUserInBackgroundOnSecondaryDisplay_displayInUse() {
        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();

        // Start first user on secondary display
        UserReference user = TestApis.users()
                .findUserOfType(TestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME));
        startBackgroundUserOnSecondaryDisplay(user, displayId);

        // Then try second user on same display
        try (UserReference otherUser = TestApis.users().createUser().name("other_user").create()) {
            int otherUserId = otherUser.id();
            Log.d(TAG, "otherUser: id=" + otherUserId);

            boolean started = tryTostartBackgroundUserOnSecondaryDisplay(otherUserId, displayId);
            Log.d(TAG, "Started: " + started);

            assertWithMessage("started user %s on display %s", otherUserId, displayId).that(started)
                    .isFalse();
        }
    }

    @FlakyTest(bugId = 242364454)
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @Test
    public void testStartUserInBackgroundOnSecondaryDisplay_mainDisplay() {
        UserReference user = TestApis.users()
                .findUserOfType(TestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME));

        assertThrows(IllegalArgumentException.class,
                () -> startBackgroundUserOnSecondaryDisplay(user, Display.DEFAULT_DISPLAY));

    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_system() {
        final UserHandle fullUser = TestApis.users().instrumented().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(fullUser);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getUserProperties"})
    @AppModeFull
    @EnsureHasWorkProfile
    @RequireFeature(FEATURE_MANAGED_USERS)
    @EnsureHasPermission({CREATE_USERS, QUERY_USERS})
    public void testGetUserProperties_managedProfile() {
        final UserHandle profile = sDeviceState.workProfile().userHandle();
        final UserProperties properties = mUserManager.getUserProperties(profile);
        assertThat(properties).isNotNull();

        assertThat(properties.getShowInLauncher()).isIn(Arrays.asList(
                UserProperties.SHOW_IN_LAUNCHER_WITH_PARENT,
                UserProperties.SHOW_IN_LAUNCHER_SEPARATE));
    }

    private Context getContextForOtherUser() {
        // TODO(b/240207590): TestApis.context().instrumentedContextForUser(TestApis.users()
        // .nonExisting() doesn't work, it throws:
        // IllegalStateException: Own package not found for user 1: package=android.multiuser.cts
        // There might be some bug (or WAI :-) on ContextImpl that makes it behave different for
        // negative user ids. Anyways, for the purpose of this test, this workaround is fine (i.e.
        // the context user id is passed to the binder call and the service checks if it matches the
        // caller or the caller has the proper permission when it doesn't.
        return getContextForUser(-42);
    }

    private Context getContextForUser(int userId) {
        Log.d(TAG, "Getting context for user " + userId);
        Context context = sContext.createContextAsUser(UserHandle.of(userId), /* flags= */ 0);
        Log.d(TAG, "Got it: " + context);
        return context;
    }

    // TODO(b/240736142): temporary workaround until proper annotation or test API is available
    private int getDisplayForBackgroundUserOnSecondaryDisplay() {
        boolean isSupported = mUserManager.isUsersOnSecondaryDisplaysEnabled();
        Log.v(TAG, "getDisplayForBackgroundUserOnSecondaryDisplay(): "
                + "isUsersOnSecondaryDisplaysEnabled=" + isSupported);
        assumeTrue("device does not support bg users on secondary displays", isSupported);
        // TODO(b/240736142): get display id from DisplayManager
        int displayId = 42;
        Log.d(TAG, "getDisplayForBackgroundUserOnSecondaryDisplay(): returning " + displayId);
        return displayId;
    }

    // TODO(b/240736142): temporary workaround until proper annotation or test API is available
    private void startBackgroundUserOnSecondaryDisplay(UserReference user, int displayId) {
        int userId = user.id();
        boolean started = tryTostartBackgroundUserOnSecondaryDisplay(userId, displayId);
        assertWithMessage("started user %s on display %s", userId, displayId).that(started)
                .isTrue();
        Poll.forValue("User running unlocked", () -> user.isRunning() && user.isUnlocked())
                .toBeEqualTo(true)
                .errorOnFail()
                .timeout(Duration.ofMinutes(1))
                .await();
    }

    // TODO(b/240736142): temporary workaround until proper annotation or test API is available
    private boolean tryTostartBackgroundUserOnSecondaryDisplay(int userId, int displayId) {
        Log.d(TAG, "tryTostartBackgroundUserOnSecondaryDisplay(): user=" + userId + ", display="
                + displayId);
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            return sContext.getSystemService(ActivityManager.class)
                    .startUserInBackgroundOnSecondaryDisplay(userId, displayId);
        }
    }
}
