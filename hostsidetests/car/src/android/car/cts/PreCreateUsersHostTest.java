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

package android.car.cts;

import static com.android.tradefed.device.NativeDevice.INVALID_USER_ID;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assume.assumeTrue;


import android.platform.test.annotations.Presubmit;

import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Tests for pre-created users.
 */
@RunWith(DeviceJUnit4ClassRunner.class)
public final class PreCreateUsersHostTest extends CarHostJUnit4TestCase {

    /**
     * Uninstalls the test app.
     */
    @Before
    @After
    public void uninstallTestApp() throws Exception {
        // TODO (b/167698977): Remove assumption after the user build has proper permissions.
        assumeTrue("Temporarily Skipping on non-root device", getDevice().isAdbRoot());
        assumeSupportsMultipleUsers();
        getDevice().uninstallPackage(APP_PKG);
    }

    /**
     * Makes sure an app installed for a regular user is not visible to a pre-created user.
     */
    @Presubmit
    @Test
    public void testAppsAreNotInstalledOnPreCreatedUser() throws Exception {
        appsAreNotInstalledOnPreCreatedUserTest(/* isGuest= */ false, /* afterReboot= */ false);
    }

    /**
     * Same as {@link #testAppsAreNotInstalledOnPreCreatedUser()}, but for a guest user.
     */
    @Presubmit
    @Test
    public void testAppsAreNotInstalledOnPreCreatedGuest() throws Exception {
        appsAreNotInstalledOnPreCreatedUserTest(/* isGuest= */ true, /* afterReboot= */ false);
    }

    /**
     * Makes sure an app installed for a regular user is not visible to a pre-created user, even
     * after the system restarts
     */
    @Presubmit
    @Test
    public void testAppsAreNotInstalledOnPreCreatedUserAfterReboot() throws Exception {
        appsAreNotInstalledOnPreCreatedUserTest(/* isGuest= */ false, /* afterReboot= */ true);
    }

    /**
     * Same as {@link #testAppsAreNotInstalledOnPreCreatedUserAfterReboot()}, but for a guest
     * user.
     */
    @Presubmit
    @Test
    public void testAppsAreNotInstalledOnPreCreatedGuestAfterReboot() throws Exception {
        appsAreNotInstalledOnPreCreatedUserTest(/* isGuest= */ true, /* afterReboot= */ true);
    }

    private void appsAreNotInstalledOnPreCreatedUserTest(boolean isGuest,
            boolean afterReboot) throws Exception {
        deletePreCreatedUsers();

        requiresExtraUsers(1);

        int initialUserId = getCurrentUserId();

        int preCreatedUserId = preCreateUser(isGuest);

        installPackageAsUser(APP_APK, /* grantPermission= */ false, initialUserId);

        assertAppInstalledForUser(APP_PKG, initialUserId);
        assertAppNotInstalledForUser(APP_PKG, preCreatedUserId);

        if (afterReboot) {
            // CarUserService creates / remove pre-created users on boot to keep the pool constant,
            // based on system properties. We need to tune then so the pre-created users set by this
            // test are not changed when the system restarts.
            if (isGuest) {
                setPreCreatedGuestsProperties(1);
                setPreCreatedUsersProperties(0);
            } else {
                setPreCreatedUsersProperties(1);
                setPreCreatedGuestsProperties(0);
            }

            // Restart the system to make sure PackageManager preserves the installed bit
            restartSystemServer();

            // Checks again
            assertAppInstalledForUser(APP_PKG, initialUserId);
            assertAppNotInstalledForUser(APP_PKG, preCreatedUserId);
        }

        // Convert the user as a "full" user
        int convertedUserId = isGuest
                ? createGuestUser("PreCreatedUsersTest_Guest")
                : createFullUser("PreCreatedUsersTest_User");
        assertWithMessage("Id of converted user doesn't match").that(convertedUserId)
                .isEqualTo(preCreatedUserId);

        assertAppNotInstalledForUser(APP_PKG, preCreatedUserId);
    }

    private List<Integer> getPreCreatedUsers() throws Exception {
        return onAllUsers((allUsers) -> allUsers.stream()
                    .filter((u) -> u.otherState.contains("(pre-created)"))
                    .map((u) -> u.id).collect(Collectors.toList()));
    }

    private int preCreateUser(boolean isGuest) throws Exception {
        return executeAndParseCommand((output) -> {
            int userId = INVALID_USER_ID;
            if (output.startsWith("Success")) {
                try {
                    userId = Integer.parseInt(output.substring(output.lastIndexOf(" ")).trim());
                    CLog.i("Pre-created user with id %d; waiting until it's initialized", userId);
                    waitForUserInitialized(userId);
                    markUserForRemovalAfterTest(userId);
                } catch (Exception e) {
                    CLog.e("Exception pre-creating %s: %s", (isGuest ? "guest" : "user"), e);
                }
            }
            if (userId == INVALID_USER_ID) {
                throw new IllegalStateException("failed to pre-create user");
            }
            return userId;
        }, "pm create-user --pre-create-only%s", (isGuest ? " --guest" : ""));
    }

    private void deletePreCreatedUsers() throws Exception {
        List<Integer> userIds = getPreCreatedUsers();
        for (int userId : userIds) {
            getDevice().removeUser(userId);
        }
    }

    private void setPreCreatedUsersProperties(int value) throws DeviceNotAvailableException {
        getDevice().setProperty("android.car.number_pre_created_users", Integer.toString(value));
    }

    private void setPreCreatedGuestsProperties(int value) throws DeviceNotAvailableException {
        getDevice().setProperty("android.car.number_pre_created_guests", Integer.toString(value));
    }
}
