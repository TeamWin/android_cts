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
 * limitations under the License.
 */

package android.devicepolicy.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.enterprise.DeviceState;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasSecondaryUser;
import com.android.compatibility.common.util.enterprise.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.compatibility.common.util.enterprise.annotations.RequireRunOnPrimaryUser;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.NoSuchElementException;
import java.util.Scanner;

@RunWith(AndroidJUnit4.class)
public final class StartProfilesTest {

    private static final int USER_START_TIMEOUT_MILLIS = 30 * 1000; // 30 seconds

    private static final Context CONTEXT = ApplicationProvider.getApplicationContext();
    private static final UserManager USER_MANAGER = CONTEXT.getSystemService(UserManager.class);

    private BroadcastReceiver mBroadcastReceiver;
    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final Object mUserStartStopLock = new Object();
    private boolean mBroadcastReceived = false;

    @ClassRule @Rule
    public static final DeviceState DEVICE_STATE = new DeviceState();

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    //TODO: b/171565394 - use DEVICE_STATE.registerBroadcastReceiver when it supports extra
    // filters (EXTRA_USER)
    private void registerBroadcastReceiver(final UserHandle userHandle) {
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                switch (intent.getAction()) {
                    case Intent.ACTION_PROFILE_ACCESSIBLE:
                    case Intent.ACTION_PROFILE_INACCESSIBLE:
                        if (userHandle.equals(intent.getParcelableExtra(Intent.EXTRA_USER))) {
                            synchronized (mUserStartStopLock) {
                                mBroadcastReceived = true;
                                mUserStartStopLock.notifyAll();
                            }
                        }
                        break;
                }
            }
        };

        IntentFilter filter = new IntentFilter(Intent.ACTION_PROFILE_ACCESSIBLE);
        filter.addAction(Intent.ACTION_PROFILE_INACCESSIBLE);
        CONTEXT.registerReceiver(mBroadcastReceiver, filter);
    }

    @Test
    //TODO: b/171565394 - remove after infra. updates
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStartProfile() throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle workProfile = DEVICE_STATE.getWorkProfile();
        registerBroadcastReceiver(workProfile);

        synchronized (mUserStartStopLock) {
            mUiAutomation.executeShellCommand("am stop-user -f " + workProfile.getIdentifier());
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isFalse();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.startProfile(workProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStopProfile() throws InterruptedException {
        //TODO: b/171565394 - remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle workProfile = DEVICE_STATE.getWorkProfile();
        registerBroadcastReceiver(workProfile);

        //TODO: b/171565394 - remove after infra. guarantees users are started
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.stopProfile(workProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertThat(USER_MANAGER.isUserRunning(workProfile)).isFalse();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);

        //TODO: b/171565394 - move/remove this when DeviceState impl. state restore (reusing users)
        //restore started state
        runCommandWithOutput("am start-user -w " + workProfile.getIdentifier());
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStopAndRestartProfile() throws InterruptedException {
        //TODO: b/171565394 - remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle workProfile = DEVICE_STATE.getWorkProfile();
        registerBroadcastReceiver(workProfile);

        //TODO: b/171565394 - remove after infra. guarantees users are started
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.stopProfile(workProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }
        // start profile as soon as ACTION_PROFILE_INACCESSIBLE is received
        // verify that ACTION_PROFILE_ACCESSIBLE is received if profile is re-started
        mBroadcastReceived = false;
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.startProfile(workProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertWithMessage("Expected to receive ACTION_PROFILE_ACCESSIBLE broadcast").that(
                mBroadcastReceived).isTrue();
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);

        //TODO: b/171565394 - move/remove this when DeviceState impl. state restore (reusing users)
        //restore started state
        runCommandWithOutput("am start-user -w " + workProfile.getIdentifier());
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStopAndRestartProfile_dontWaitForBroadcast() throws InterruptedException {
        //TODO: b/171565394 - remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle workProfile = DEVICE_STATE.getWorkProfile();
        registerBroadcastReceiver(workProfile);

        //TODO: b/171565394 - remove after infra. guarantees users are started
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        // stop and restart profile without waiting for ACTION_PROFILE_INACCESSIBLE broadcast
        // ACTION_PROFILE_ACCESSIBLE should not be received as profile was not fully stopped before
        // restarting
        assertThat(activityManager.stopProfile(workProfile)).isTrue();
        mBroadcastReceived = false;
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.startProfile(workProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertWithMessage("Should have not received ACTION_PROFILE_ACCESSIBLE broadcast").that(
                mBroadcastReceived).isFalse();
        assertThat(USER_MANAGER.isUserRunning(workProfile)).isTrue();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);

        //TODO: b/171565394 - move/remove this when DeviceState impl. state restore (reusing users)
        //restore started state
        runCommandWithOutput("am start-user -w " + workProfile.getIdentifier());
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStartProfileWithoutPermission_throwsException() {
        UserHandle workProfile = DEVICE_STATE.getWorkProfile();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        try {
            activityManager.startProfile(workProfile);
            fail("Should have received an exception");
        } catch (SecurityException expected) {
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void testStopProfileWithoutPermission_throwsException() {
        UserHandle workProfile = DEVICE_STATE.getWorkProfile();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        try {
            activityManager.stopProfile(workProfile);
            fail("Should have received an exception");
        } catch (SecurityException expected) {
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void testStartFullUserAsProfile_throwsException() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle secondaryUser = DEVICE_STATE.getSecondaryUser();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        try {
            activityManager.startProfile(secondaryUser);
            fail("Should have received an exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void testStopFullUserAsProfile_throwsException() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        UserHandle secondaryUser = DEVICE_STATE.getSecondaryUser();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        try {
            activityManager.stopProfile(secondaryUser);
            fail("Should have received an exception");
        } catch (IllegalArgumentException expected) {
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    public void testStartTvProfile() throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        //TODO: b/171565394 - migrate to new test annotation for tv profile tests
        UserHandle tvProfile = createCustomProfile("com.android.tv.profile", false);
        assumeTrue(tvProfile != null);

        registerBroadcastReceiver(tvProfile);

        assertThat(USER_MANAGER.isUserRunning(tvProfile)).isFalse();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.startProfile(tvProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertThat(USER_MANAGER.isUserRunning(tvProfile)).isTrue();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);

        cleanupCustomProfile(tvProfile);
    }

    @Test
    @RequireRunOnPrimaryUser
    public void testStopTvProfile() throws InterruptedException {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        //TODO: b/171565394 - migrate to new test annotation for tv profile tests
        UserHandle tvProfile = createCustomProfile("com.android.tv.profile", true);
        assumeTrue(tvProfile != null);

        registerBroadcastReceiver(tvProfile);

        assertThat(USER_MANAGER.isUserRunning(tvProfile)).isTrue();

        final ActivityManager activityManager = CONTEXT.getSystemService(ActivityManager.class);
        synchronized (mUserStartStopLock) {
            assertThat(activityManager.stopProfile(tvProfile)).isTrue();
            mUserStartStopLock.wait(USER_START_TIMEOUT_MILLIS);
        }

        assertThat(USER_MANAGER.isUserRunning(tvProfile)).isFalse();

        CONTEXT.unregisterReceiver(mBroadcastReceiver);

        cleanupCustomProfile(tvProfile);
    }

    private UserHandle createCustomProfile(String profileType, boolean startAfterCreation) {
        UserHandle userHandle;
        try {
            userHandle = CONTEXT.getSystemService(UserManager.class).createProfile(
                    "testProfile", profileType, new ArraySet<>());
            if (startAfterCreation && userHandle != null) {
                runCommandWithOutput("am start-user -w " + userHandle.getIdentifier());
            }
        } catch (NullPointerException e) {
            userHandle = null;
        }
        return userHandle;
    }

    private void cleanupCustomProfile(UserHandle userHandle) {
        runCommandWithOutput("pm remove-user " + userHandle.getIdentifier());
    }

    private String runCommandWithOutput(String command) {
        ParcelFileDescriptor p =  mUiAutomation.executeShellCommand(command);

        InputStream inputStream = new FileInputStream(p.getFileDescriptor());

        try (Scanner scanner = new Scanner(inputStream, UTF_8.name())) {
            return scanner.useDelimiter("\\A").next();
        } catch (NoSuchElementException e) {
            return "";
        }
    }
}