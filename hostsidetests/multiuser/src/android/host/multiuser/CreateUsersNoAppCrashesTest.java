/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.host.multiuser;

import android.platform.test.annotations.Presubmit;

import com.android.tradefed.device.DeviceNotAvailableException;

import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;

/**
 * Test verifies that users can be created/switched to without error dialogs shown to the user
 * Run: atest CreateUsersNoAppCrashesTest
 */
public class CreateUsersNoAppCrashesTest extends BaseMultiUserTest {
    private int mInitialUserId;
    private static final long LOGCAT_POLL_INTERVAL_MS = 5000;
    private static final long USER_SWITCH_COMPLETE_TIMEOUT_MS = 120000;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mInitialUserId = getDevice().getCurrentUser();
    }

    @Presubmit
    public void testCanCreateGuestUser() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                true /* guest */,
                false /* ephemeral */);
        assertSwitchToNewUser(userId);
        assertSwitchToUser(userId, mInitialUserId);
    }

    @Presubmit
    public void testCanCreateSecondaryUser() throws Exception {
        if (!mSupportsMultiUser) {
            return;
        }
        int userId = getDevice().createUser(
                "TestUser_" + System.currentTimeMillis() /* name */,
                false /* guest */,
                false /* ephemeral */);
        assertSwitchToNewUser(userId);
        assertSwitchToUser(userId, mInitialUserId);
    }

    private void assertSwitchToNewUser(int toUserId) throws Exception {
        final String exitString = "Finished processing BOOT_COMPLETED for u" + toUserId;
        final Set<String> appErrors = new LinkedHashSet<>();
        getDevice().executeAdbCommand("logcat", "-c"); // Reset log
        assertTrue("Couldn't switch to user " + toUserId, getDevice().switchUser(toUserId));
        final boolean result = waitForUserSwitchComplete(appErrors, toUserId, exitString);
        assertTrue("Didn't receive BOOT_COMPLETED delivered notification. appErrors="
                + appErrors, result);
        assertTrue("App error dialog(s) are present: " + appErrors, appErrors.isEmpty());
    }

    private void assertSwitchToUser(int fromUserId, int toUserId) throws Exception {
        final String exitString = "Continue user switch oldUser #" + fromUserId + ", newUser #"
                + toUserId;
        final Set<String> appErrors = new LinkedHashSet<>();
        getDevice().executeAdbCommand("logcat", "-c"); // Reset log
        assertTrue("Couldn't switch to user " + toUserId, getDevice().switchUser(toUserId));
        final boolean result = waitForUserSwitchComplete(appErrors, toUserId, exitString);
        assertTrue("Didn't reach \"Continue user switch\" stage. appErrors=" + appErrors, result);
        assertTrue("App error dialog(s) are present: " + appErrors, appErrors.isEmpty());
    }

    private boolean waitForUserSwitchComplete(Set<String> appErrors, int targetUserId,
            String exitString) throws DeviceNotAvailableException, InterruptedException {
        boolean mExitFound = false;
        long ti = System.currentTimeMillis();
        while (System.currentTimeMillis() - ti < USER_SWITCH_COMPLETE_TIMEOUT_MS) {
            String logs = getDevice().executeAdbCommand("logcat", "-v", "brief", "-d",
                    "ActivityManager:D", "AndroidRuntime:E", "*:S");
            Scanner in = new Scanner(logs);
            while (in.hasNextLine()) {
                String line = in.nextLine();
                if (line.contains("Showing crash dialog for package")) {
                    appErrors.add(line);
                } else if (line.contains(exitString)) {
                    // Parse all logs in case crashes occur as a result of onUserChange callbacks
                    mExitFound = true;
                } else if (line.contains("FATAL EXCEPTION IN SYSTEM PROCESS")) {
                    throw new IllegalStateException("System process crashed - " + line);
                }
            }
            in.close();
            if (mExitFound) {
                return true;
            }
            Thread.sleep(LOGCAT_POLL_INTERVAL_MS);
        }
        return false;
    }
}
