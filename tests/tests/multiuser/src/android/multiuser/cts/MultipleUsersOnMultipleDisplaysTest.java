/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.multiuser.cts;


import static android.Manifest.permission.CREATE_USERS;
import static android.Manifest.permission.INTERACT_ACROSS_USERS;
import static android.content.pm.PackageManager.FEATURE_MANAGED_USERS;
import static android.multiuser.cts.PermissionHelper.adoptShellPermissionIdentity;

import static com.android.bedstead.harrier.OptionalBoolean.FALSE;
import static com.android.bedstead.harrier.OptionalBoolean.TRUE;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.Context;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.AppModeFull;
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
import com.android.bedstead.harrier.annotations.RequireMultipleUsersOnMultipleDisplays;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.harrier.annotations.RequireRunOnSecondaryUser;
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
import java.util.List;

/**
 * Tests for user-related APIs that are only available on devices that
 * {@link UserManager#isUsersOnSecondaryDisplaysEnabled() support background users running on
 * secondary displays} (such as cars with passenger displays).
 *
 */
@AppModeFull(reason = "it's testing user features, not related to apps")
@RequireMultipleUsersOnMultipleDisplays(reason = "Because class is testing exactly that")
@RunWith(BedsteadJUnit4.class)
public final class MultipleUsersOnMultipleDisplaysTest {

    private static final String TAG = "MUMDTest";

    private static final Context sContext = TestApis.context().instrumentedContext();

    @Rule
    @ClassRule
    public static final DeviceState sDeviceState = new DeviceState();

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private UserManager mUserManager;

    @Before
    public void setUp() {
        mUserManager = sContext.getSystemService(UserManager.class);
        assertWithMessage("UserManager service").that(mUserManager).isNotNull();
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    public void testIsUserVisible_backgroundUserOnSecondaryDisplay() throws Exception {
        runTestOnSecondaryDisplay((user, displayId, instance) ->
                assertWithMessage("isUserVisible() for background user (id=%s) on display %s",
                    user.id(), displayId)
                            .that(instance.userManager().isUserVisible()).isTrue());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public void testIsUserVisible_startedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        runTestOnSecondaryDisplay(/* startProfile= */ true, (user, profile, displayId, instance) ->
                assertWithMessage("isUserVisible() for started profile (id=%s) of bg user (id=%s) "
                    + "on display %s", profile.id(), user.id(), displayId)
                            .that(instance.userManager().isUserVisible()).isTrue());
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#isUserVisible"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call isUserVisible() on other context
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public void testIsUserVisible_stoppedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {

        runTestOnSecondaryDisplay(/* startProfile= */ false, (user, profile, displayId, instance) ->
                assertWithMessage("isUserVisible() for stoppedprofile (id=%s) of bg user (id=%s) "
                + "on display %s", profile.id(), user.id(), displayId)
                            .that(instance.userManager().isUserVisible()).isFalse()
        );
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_backgroundUserOnSecondaryDisplay() throws Exception {
        // TODO(b/239982558): this test fails when the whole test class is ran and the feature is
        // enabled because it cannot start the user in the background (most likely beucase there's
        // another user started that was not stopped). For now, it's not worth to fix it, as that
        // restriction might go away (if the startBgUser() method automatically stops the previous
        // user)
        runTestOnSecondaryDisplay((user, displayId, instance) ->
                assertWithMessage("getVisibleUsers()")
                        .that(mUserManager.getVisibleUsers())
                        .containsExactly(TestApis.users().current().userHandle(),
                                user.userHandle()));
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public void testGetVisibleUsers_startedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        runTestOnSecondaryDisplay(/* startProfile= */ true,
                (user, profile, display, instance) -> {
                    List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
                    Log.d(TAG, "Visible users: " + visibleUsers);
                    assertWithMessage("getVisibleUsers()").that(visibleUsers)
                            .containsAtLeast(TestApis.users().current().userHandle(),
                                    user.userHandle(), profile.userHandle());
                });
    }

    @Test
    @ApiTest(apis = {"android.os.UserManager#getVisibleUsers"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    @EnsureHasPermission(INTERACT_ACROSS_USERS) // needed to call getVisibleUsers()
    public void testGetVisibleUsers_stoppedProfileOfBackgroundUserOnSecondaryDisplay()
            throws Exception {
        runTestOnSecondaryDisplay(/* startProfile= */ false, (user, profile, display, instance) -> {
            List<UserHandle> visibleUsers = mUserManager.getVisibleUsers();
            Log.d(TAG, "Visible users: " + visibleUsers);

            assertWithMessage("getVisibleUsers()").that(visibleUsers)
                    .containsAtLeast(TestApis.users().current().userHandle(), user.userHandle());
            assertWithMessage("getVisibleUsers()").that(visibleUsers)
                    .doesNotContain(profile.userHandle());
        });
    }

    // TODO(b/240736142): tests below should belong to ActivityManagerTest or similar, but it
    // doesn't use bedstead yet

    @FlakyTest(bugId = 242364454)
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @Test
    public void testStartUserInBackgroundOnSecondaryDisplay() {
        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
        int userId = TestApis.users()
                .findUserOfType(TestApis.users().supportedType(UserType.SECONDARY_USER_TYPE_NAME))
                .id();

        boolean started = tryToStartBackgroundUserOnSecondaryDisplay(userId, displayId);
        assertWithMessage("started user %s on id %s", userId, displayId).that(started)
                .isTrue();

        // Should fail when it's already started
        boolean startedAgain = tryToStartBackgroundUserOnSecondaryDisplay(userId, displayId);
        assertWithMessage("started user %s on id %s again", userId, displayId).that(startedAgain)
                .isFalse();
    }

    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    @RequireHeadlessSystemUserMode// non-HSUM cannot have profiles on secondary users
    @RequireRunOnSecondaryUser
    @RequireFeature(FEATURE_MANAGED_USERS) // TODO(b/239961027): remove if supports other profiles
    public void testStartUserInBackgroundOnSecondaryDisplay_profileOnSameDisplay() {
        runTestOnSecondaryDisplay(/* startProfile= */ true, (user, profile, display, instance) -> {
            Log.d(TAG, "Saul Goodman!");
        });
    }

    @FlakyTest(bugId = 242364454)
    @Test
    @ApiTest(apis = {"android.app.ActivityManager#startUserInBackgroundOnSecondaryDisplay"})
    // TODO(b/240281790): should be @RequireRunOnDefaultUser instead of @RequireRunOnPrimaryUser
    @RequireRunOnPrimaryUser(switchedToUser = TRUE)
    @EnsureHasSecondaryUser(switchedToUser = FALSE)
    @EnsureHasWorkProfile
    public void testStartUserInBackgroundOnSecondaryDisplay_profileOnDifferentDisplay() {
        // Stop the profile as it was initially running in the same display as parent
        UserReference profile = sDeviceState.workProfile();
        Log.d(TAG, "Stopping profile " + profile + " (called from " + sContext.getUser() + ")");
        profile.stop();

        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
        int userId = profile.id();
        boolean started = tryToStartBackgroundUserOnSecondaryDisplay(userId, displayId);
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

            boolean started = tryToStartBackgroundUserOnSecondaryDisplay(otherUserId, displayId);
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

    /**
     * Starts a new user in background in on secondary display and run a test on it.
     *
     * @param test to be run
     */
    private void runTestOnSecondaryDisplay(BackgroundUserOnSecondaryDisplayTester test) {
        UserReference user = sDeviceState.secondaryUser();
        int displayId = getDisplayForBackgroundUserOnSecondaryDisplay();
        startBackgroundUserOnSecondaryDisplay(user, displayId);
        try {
            TestApp testApp = sDeviceState.testApps().any();
            try (TestAppInstance instance = testApp.install(user)) {
                test.run(user, displayId, instance);
            }
        } finally {
            user.stop();
        }
    }

    // TODO(b/245963156): move to Display.java (and @hide) if we decide to support profiles on MUMD
    private static final int PARENT_DISPLAY = -2;

    /**
     * Starts a new user (which has a profile) in background on a secondary display and run a test
     * on it.
     *
     * @param test to be run
     * @param startProfile whether the user profile should be started as well
     */
    private void runTestOnSecondaryDisplay(boolean startProfile,
            BackgroundUserAndProfileOnSecondaryDisplayTester tester) {
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
                try {
                    if (startProfile) {
                        startBackgroundUserOnSecondaryDisplay(profile, PARENT_DISPLAY);
                    } else {
                        // Make sure it's stopped, as it could have been automatically started with
                        // parent user
                        Log.d(TAG, "Stopping profile " + profile.id()); profile.stop();
                    }
                    try {
                        TestApp testApp = sDeviceState.testApps().any();
                        try (TestAppInstance instance = testApp.install(profile)) {
                            tester.run(user, profile, displayId, instance);
                        } // test instance
                    } finally {
                        if (startProfile) {
                            profile.stop();
                        }
                    } // startBackgroundUserOnSecondaryDisplay
                } finally {
                    user.stop();
                } // startBackgroundUserOnSecondaryDisplay
            } // new profile
        } // new user
    }

    private interface BackgroundUserOnSecondaryDisplayTester {
        void run(UserReference user, int displayId, TestAppInstance instance);
    }

    private interface BackgroundUserAndProfileOnSecondaryDisplayTester {
        void run(UserReference user, UserReference profile, int displayId,
                TestAppInstance instance);
    }

    // TODO(b/240736142): methods below are a temporary workaround until proper annotation or test
    // API are available

    private int getDisplayForBackgroundUserOnSecondaryDisplay() {
        // TODO(b/240736142): get display id from DisplayManager
        int displayId = 42;
        Log.d(TAG, "getDisplayForBackgroundUserOnSecondaryDisplay(): returning " + displayId);
        return displayId;
    }

    private void startBackgroundUserOnSecondaryDisplay(UserReference user, int displayId) {
        int userId = user.id();
        boolean started = tryToStartBackgroundUserOnSecondaryDisplay(userId, displayId);
        assertWithMessage("started user %s on display %s", userId, displayId).that(started)
                .isTrue();
        Poll.forValue("User running unlocked", () -> user.isRunning() && user.isUnlocked())
                .toBeEqualTo(true)
                .errorOnFail()
                .timeout(Duration.ofMinutes(1))
                .await();
    }

    private boolean tryToStartBackgroundUserOnSecondaryDisplay(int userId, int displayId) {
        Log.d(TAG, "tryToStartBackgroundUserOnSecondaryDisplay(): user=" + userId + ", display="
                + displayId);
        try (PermissionHelper ph = adoptShellPermissionIdentity(mInstrumentation, CREATE_USERS)) {
            boolean started = sContext.getSystemService(ActivityManager.class)
                    .startUserInBackgroundOnSecondaryDisplay(userId, displayId);
            Log.d(TAG, "Started: " + started);
            return started;
        }
    }
}
