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

import static org.junit.Assume.assumeTrue;
import static org.testng.Assert.assertThrows;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasSecondaryUser;
import com.android.bedstead.harrier.annotations.EnsureHasTvProfile;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.Postsubmit;
import com.android.bedstead.harrier.annotations.RequireFeatures;
import com.android.bedstead.harrier.annotations.RequireRunOnPrimaryUser;
import com.android.bedstead.nene.TestApis;
import com.android.bedstead.nene.users.UserReference;
import com.android.compatibility.common.util.BlockingBroadcastReceiver;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.function.Function;

@RunWith(AndroidJUnit4.class)
public final class StartProfilesTest {

    // We set this to 30 seconds because if the total test time goes over 66 seconds then it causes
    // infrastructure problems
    private static final long PROFILE_ACCESSIBLE_BROADCAST_TIMEOUT = 30 * 1000;

    private static final Context sContext = ApplicationProvider.getApplicationContext();
    private static final UserManager sUserManager = sContext.getSystemService(UserManager.class);
    private static final ActivityManager sActivityManager =
            sContext.getSystemService(ActivityManager.class);

    private UiAutomation mUiAutomation =
            InstrumentationRegistry.getInstrumentation().getUiAutomation();
    private final TestApis mTestApis = new TestApis();

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @After
    public void tearDown() {
        mUiAutomation.dropShellPermissionIdentity();
    }

    private Function<Intent, Boolean> userIsEqual(UserReference user) {
        return (intent) -> user.userHandle().equals(intent.getParcelableExtra(Intent.EXTRA_USER));
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void startProfile_returnsTrue() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().stop();

        assertThat(sActivityManager.startProfile(sDeviceState.workProfile().userHandle())).isTrue();
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void startProfile_broadcastIsReceived_profileIsStarted() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().stop();
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_ACCESSIBLE,
                userIsEqual(sDeviceState.workProfile()));
        sActivityManager.startProfile(sDeviceState.workProfile().userHandle());

        broadcastReceiver.awaitForBroadcastOrFail();

        assertThat(sUserManager.isUserRunning(sDeviceState.workProfile().userHandle())).isTrue();
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void stopProfile_returnsTrue() {
        // TODO(b/171565394): remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().start();

        try {
            assertThat(sActivityManager.stopProfile(
                    sDeviceState.workProfile().userHandle())).isTrue();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void stopProfile_profileIsStopped() {
        // TODO(b/171565394): remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().start();
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(sDeviceState.workProfile()));

        try {
            sActivityManager.stopProfile(sDeviceState.workProfile().userHandle());
            broadcastReceiver.awaitForBroadcastOrFail();

            assertThat(
                    sUserManager.isUserRunning(sDeviceState.workProfile().userHandle())).isFalse();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void startUser_immediatelyAfterStopped_profileIsStarted() {
        // TODO(b/171565394): remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().start();
        BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(sDeviceState.workProfile()));

        sActivityManager.stopProfile(sDeviceState.workProfile().userHandle());
        broadcastReceiver.awaitForBroadcast();

        try {
            // start profile as soon as ACTION_PROFILE_INACCESSIBLE is received
            // verify that ACTION_PROFILE_ACCESSIBLE is received if profile is re-started
            broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                    Intent.ACTION_PROFILE_ACCESSIBLE, userIsEqual(sDeviceState.workProfile()));
            sActivityManager.startProfile(sDeviceState.workProfile().userHandle());
            Intent broadcast = broadcastReceiver.awaitForBroadcast();

            assertWithMessage("Expected to receive ACTION_PROFILE_ACCESSIBLE broadcast").that(
                    broadcast).isNotNull();
            assertThat(
                    sUserManager.isUserRunning(sDeviceState.workProfile().userHandle())).isTrue();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void startUser_userIsStopping_profileIsStarted() {
        // TODO(b/171565394): remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().start();

        // stop and restart profile without waiting for ACTION_PROFILE_INACCESSIBLE broadcast
        sActivityManager.stopProfile(sDeviceState.workProfile().userHandle());
        try {
            sActivityManager.startProfile(sDeviceState.workProfile().userHandle());

            assertThat(sUserManager.isUserRunning(
                    sDeviceState.workProfile().userHandle())).isTrue();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    @Postsubmit(reason="Slow test due to validating a broadcast isn't received")
    public void startUser_userIsStopping_noBroadcastIsReceived() {
        // TODO(b/171565394): remove after infra supports shell permissions annotation
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.workProfile().start();

        // stop and restart profile without waiting for ACTION_PROFILE_INACCESSIBLE broadcast
        // ACTION_PROFILE_ACCESSIBLE should not be received as profile was not fully stopped before
        // restarting
        sActivityManager.stopProfile(sDeviceState.workProfile().userHandle());
        try {
            BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                    Intent.ACTION_PROFILE_ACCESSIBLE, userIsEqual(sDeviceState.workProfile()));
            sActivityManager.startProfile(sDeviceState.workProfile().userHandle());

            Intent broadcast =
                    broadcastReceiver.awaitForBroadcast(PROFILE_ACCESSIBLE_BROADCAST_TIMEOUT);
            assertWithMessage("Expected not to receive ACTION_PROFILE_ACCESSIBLE broadcast").that(
                    broadcast).isNull();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void startProfile_withoutPermission_throwsException() {
        assertThrows(SecurityException.class,
                () -> sActivityManager.startProfile(sDeviceState.workProfile().userHandle()));
    }

    @Test
    @RequireFeatures(PackageManager.FEATURE_MANAGED_USERS)
    @RequireRunOnPrimaryUser
    @EnsureHasWorkProfile
    public void stopProfile_withoutPermission_throwsException() {
        try {
            assertThrows(SecurityException.class,
                    () -> sActivityManager.stopProfile(sDeviceState.workProfile().userHandle()));
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.workProfile().start();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void startProfile_startingFullUser_throwsException() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        assertThrows(IllegalArgumentException.class,
                () -> sActivityManager.startProfile(sDeviceState.secondaryUser().userHandle()));
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasSecondaryUser
    public void stopProfile_stoppingFullUser_throwsException() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");

        try {
            assertThrows(IllegalArgumentException.class,
                    () -> sActivityManager.stopProfile(sDeviceState.secondaryUser().userHandle()));
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.secondaryUser().start();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasTvProfile
    public void startProfile_tvProfile_profileIsStarted() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.tvProfile().stop();

        try {
            BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                    Intent.ACTION_PROFILE_ACCESSIBLE, userIsEqual(sDeviceState.tvProfile()));

            assertThat(
                    sActivityManager.startProfile(sDeviceState.tvProfile().userHandle())).isTrue();
            broadcastReceiver.awaitForBroadcast();

            assertThat(sUserManager.isUserRunning(sDeviceState.tvProfile().userHandle())).isTrue();
        } finally {
            // TODO(b/171565394): Remove once teardown is done for us
            sDeviceState.tvProfile().start();
        }
    }

    @Test
    @RequireRunOnPrimaryUser
    @EnsureHasTvProfile
    public void stopProfile_tvProfile_profileIsStopped() {
        mUiAutomation.adoptShellPermissionIdentity(
                "android.permission.INTERACT_ACROSS_USERS_FULL",
                "android.permission.INTERACT_ACROSS_USERS",
                "android.permission.CREATE_USERS");
        sDeviceState.tvProfile().start();

        try {
            BlockingBroadcastReceiver broadcastReceiver = sDeviceState.registerBroadcastReceiver(
                    Intent.ACTION_PROFILE_INACCESSIBLE, userIsEqual(sDeviceState.tvProfile()));

            assertThat(
                    sActivityManager.stopProfile(sDeviceState.tvProfile().userHandle())).isTrue();
            broadcastReceiver.awaitForBroadcast();

            assertThat(sUserManager.isUserRunning(sDeviceState.tvProfile().userHandle())).isFalse();
        } finally {
            sDeviceState.tvProfile().start();
        }
    }
}