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

package android.car.cts;

import static android.Manifest.permission.CREATE_USERS;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_REMOVED;
import static android.car.user.CarUserManager.UserLifecycleEvent;
import static android.car.user.CarUserManager.UserLifecycleListener;

import static com.google.common.truth.Truth.assertWithMessage;

import android.car.test.ApiCheckerRule;
import android.car.test.PermissionsCheckerRule.EnsureHasPermission;
import android.car.test.util.AndroidHelper;
import android.car.testapi.BlockingUserLifecycleListener;
import android.car.user.CarUserManager;
import android.os.Bundle;
import android.os.NewUserRequest;
import android.os.NewUserResponse;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class CarUserManagerTest extends AbstractCarTestCase {
    private static final String TAG = AbstractCarTestCase.class.getSimpleName();
    private static final String NEW_USER_NAME_PREFIX = "CarCTSTest.";
    private static final int START_TIMEOUT_MS = 20_000;

    private final List<UserHandle> mUsersToRemove = new ArrayList<>();
    private CarUserManager mCarUserManager;
    private UserManager mUserManager;

    @Before
    public void setUp() {
        mUserManager = mContext.getSystemService(UserManager.class);
        mCarUserManager = getCar().getCarManager(CarUserManager.class);
    }

    @After
    public void cleanupUserState() {
        try {
            if (!mUsersToRemove.isEmpty()) {
                Log.i(TAG, "removing users at end of " + getTestName() + ": " + mUsersToRemove);
                for (UserHandle userHandle : mUsersToRemove) {
                    removeUser(userHandle);
                }
            } else {
                Log.i(TAG, "no user to remove at end of " + getTestName());
            }
        } catch (Exception e) {
            // Must catch otherwise it would be the test failure, which could hide the real issue
            Log.e(TAG, "Caught exception on " + getTestName()
                    + " cleanupUserState()", e);
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @ApiCheckerRule.SupportedVersionTest(unsupportedVersionTest =
            "testLifecycleUserCreatedListener_unsupportedVersion")
    @EnsureHasPermission(CREATE_USERS)
    public void testLifecycleUserCreatedListener_supportedVersion() throws Exception {

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_CREATED)
                .build();

        UserHandle newUser = null;
        try {
            Log.d(TAG, "registering listener: " + listener);
            mCarUserManager.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            newUser = createUser("TestUserToCreate", false);

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_CREATED);
            assertWithMessage("user id on %s", event).that(event.getUserHandle().getIdentifier())
                    .isEqualTo(newUser.getIdentifier());
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mCarUserManager.removeListener(listener);
            Log.v(TAG, "ok");

            if (newUser != null) {
                removeUser(newUser);
            }
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_CREATED"})
    @ApiCheckerRule.UnsupportedVersionTest(behavior =
            ApiCheckerRule.UnsupportedVersionTest.Behavior.EXPECT_PASS,
            supportedVersionTest = "testLifecycleUserCreatedListener_supportedVersion")
    @EnsureHasPermission(CREATE_USERS)
    public void testLifecycleUserCreatedListener_unsupportedVersion() throws Exception {

        LifecycleListener listener = new LifecycleListener();

        UserHandle newUser = null;
        try {
            mCarUserManager.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            newUser = createUser("TestUserToCreate", false);

            Log.d(TAG, "Waiting for events");
            listener.assertEventNotReceived(
                    newUser.getIdentifier(), CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mCarUserManager.removeListener(listener);
            Log.v(TAG, "ok");

            if (newUser != null) {
                removeUser(newUser);
            }
        }
    }

    @Test
    @ApiCheckerRule.SupportedVersionTest(unsupportedVersionTest =
            "testLifecycleUserRemovedListener_unsupportedVersion")
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    @EnsureHasPermission(CREATE_USERS)
    public void testLifecycleUserRemovedListener_supportedVersion() throws Exception {

        UserHandle newUser = createUser("TestUserToRemove", false);

        BlockingUserLifecycleListener listener = BlockingUserLifecycleListener
                .forSpecificEvents()
                .forUser(newUser.getIdentifier())
                .setTimeout(START_TIMEOUT_MS)
                .addExpectedEvent(USER_LIFECYCLE_EVENT_TYPE_REMOVED)
                .build();

        try {
            Log.d(TAG, "registering listener: " + listener);
            mCarUserManager.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            removeUser(newUser);

            Log.d(TAG, "Waiting for events");
            List<UserLifecycleEvent> events = listener.waitForEvents();
            Log.d(TAG, "events: " + events);
            assertWithMessage("events").that(events).hasSize(1);
            UserLifecycleEvent event = events.get(0);
            assertWithMessage("type of event %s", event).that(event.getEventType())
                    .isEqualTo(USER_LIFECYCLE_EVENT_TYPE_REMOVED);
            assertWithMessage("user id on %s", event).that(event.getUserHandle().getIdentifier())
                    .isEqualTo(newUser.getIdentifier());
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mCarUserManager.removeListener(listener);
            Log.v(TAG, "ok");
        }
    }

    @Test
    @ApiTest(apis = {"android.car.user.CarUserManager#USER_LIFECYCLE_EVENT_TYPE_REMOVED"})
    @ApiCheckerRule.UnsupportedVersionTest(behavior =
            ApiCheckerRule.UnsupportedVersionTest.Behavior.EXPECT_PASS,
            supportedVersionTest = "testLifecycleUserRemovedListener_supportedVersion")
    @EnsureHasPermission(CREATE_USERS)
    public void testLifecycleUserRemovedListener_unsupportedVersion() throws Exception {
        UserHandle newUser = createUser("TestUserToRemove", false);

        LifecycleListener listener = new LifecycleListener();

        try {
            Log.d(TAG, "registering listener: " + listener);
            mCarUserManager.addListener(Runnable::run, listener);
            Log.v(TAG, "ok");

            removeUser(newUser);

            Log.d(TAG, "Waiting for events");
            listener.assertEventNotReceived(
                    newUser.getIdentifier(), CarUserManager.USER_LIFECYCLE_EVENT_TYPE_CREATED);
        } finally {
            Log.d(TAG, "unregistering listener: " + listener);
            mCarUserManager.removeListener(listener);
            Log.v(TAG, "ok");
        }
    }

    @NonNull
    private UserHandle createUser(@Nullable String name, boolean isGuest) {
        name = getNewUserName(name);
        Log.d(TAG, "Creating new " + (isGuest ? "guest" : "user") + " with name '" + name
                + "' using CarUserManager");

        assertCanAddUser();

        // TODO(b/235994008): Update this to use CarUserManager.createUser when it is unhidden.
        NewUserResponse result = mUserManager.createUser(new NewUserRequest.Builder().build());

        Log.d(TAG, "result: " + result);
        assertWithMessage("user creation result (waited for %sms)", DEFAULT_WAIT_TIMEOUT_MS)
                .that(result).isNotNull();
        assertWithMessage("user creation result (%s) success for user named %s", result, name)
                .that(result.isSuccessful()).isTrue();
        UserHandle user = result.getUser();
        assertWithMessage("user on result %s", result).that(user).isNotNull();
        mUsersToRemove.add(user);
        return user;
    }

    protected void removeUser(UserHandle userHandle) {
        Log.d(TAG, "Removing user " + userHandle.getIdentifier());

        // TODO(b/235994008): Update this to use CarUserManager.createUser when it is unhidden.
        boolean result = mUserManager.removeUser(userHandle);

        assertWithMessage("User %s removed. Result: %s", userHandle.getIdentifier(), result)
                .that(result).isTrue();

        mUsersToRemove.remove(userHandle);
    }

    protected void assertCanAddUser() {
        Bundle restrictions = mUserManager.getUserRestrictions();
        Log.d(TAG, "Restrictions for user " + mContext.getUser() + ": "
                + AndroidHelper.toString(restrictions));
        assertWithMessage("%s restriction", UserManager.DISALLOW_ADD_USER)
                .that(restrictions.getBoolean(UserManager.DISALLOW_ADD_USER, false)).isFalse();
    }

    private String getNewUserName(String name) {
        StringBuilder newName = new StringBuilder(NEW_USER_NAME_PREFIX).append(getTestName());
        if (name != null) {
            newName.append('.').append(name);
        }
        return newName.toString();
    }

    protected String getTestName() {
        return getClass().getSimpleName() + "." + mApiCheckerRule.getTestMethodName();
    }

    protected static void fail(String format, Object... args) {
        String message = String.format(format, args);
        Log.e(TAG, "test failed: " + message);
        org.junit.Assert.fail(message);
    }

    // TODO(b/244594590): Clean this listener up once BlockingUserLifecycleListener supports
    // no events received.
    private static final class LifecycleListener implements UserLifecycleListener {
        private static final int TIMEOUT_MS = 60_000;

        private final List<UserLifecycleEvent> mEvents = new ArrayList<>();

        private final Object mLock = new Object();

        private final CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onEvent(UserLifecycleEvent event) {
            Log.d(TAG, "Event received: " + event);
            synchronized (mLock) {
                mEvents.add(event);
            }
        }

        public void assertEventNotReceived(int userId, int eventType)
                throws InterruptedException {
            if (!mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                String errorMessage = "Interrupted while While waiting for " + eventType;
                Log.e(TAG, errorMessage);
                throw new IllegalStateException(errorMessage);
            }

            boolean result = checkEvent(userId, eventType);
            if (result) {
                fail("Event" + eventType
                        + " was not expected but was received within timeoutMs: " + TIMEOUT_MS);
            }
        }

        private boolean checkEvent(int userId, int eventType) {
            synchronized (mLock) {
                for (UserLifecycleEvent event : mEvents) {
                    if (event.getUserHandle().getIdentifier() == userId
                            && event.getEventType() == eventType) {
                        return true;
                    }
                }
            }
            return false;
        }
    }
}
