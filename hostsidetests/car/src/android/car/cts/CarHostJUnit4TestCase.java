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

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.compatibility.common.util.CommonTestUtils;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.log.LogUtil.CLog;
import com.android.tradefed.testtype.ITestInformationReceiver;
import com.android.tradefed.testtype.junit4.BaseHostJUnit4Test;

import org.junit.After;
import org.junit.AssumptionViolatedException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import java.util.ArrayList;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Base class for all test cases.
 */
// NOTE: must be public because of @Rules
public abstract class CarHostJUnit4TestCase extends BaseHostJUnit4Test {

    private static final int DEFAULT_TIMEOUT_SEC = 10;

    private static final Pattern CREATE_USER_OUTPUT_REGEX = Pattern.compile("id=(\\d+)");

    @Rule
    public final RequiredFeatureRule mHasAutomotiveRule = new RequiredFeatureRule(this,
            "android.hardware.type.automotive");

    private final ArrayList<Integer> mCreatedUserIds = new ArrayList<>();
    private int mInitialUserId;
    private Integer mInitialMaximumNumberOfUsers;

    /**
     * Saves multi-user state so it can be restored after the test.
     */
    @Before
    public void saveUserState() throws Exception {
        mInitialUserId = getCurrentUserId();
    }

    /**
     * Restores multi-user state from before the test.
     */
    @After
    public void restoreUsersState() throws Exception {
        int currentUserId = getCurrentUserId();
        CLog.d("restoreUsersState(): initial user: %d, current user: %d, created users: %s "
                + "max number of users: %d",
                mInitialUserId, currentUserId, mCreatedUserIds, mInitialMaximumNumberOfUsers);
        if (currentUserId != mInitialUserId) {
            CLog.i("Switching back from %d to %d", currentUserId, mInitialUserId);
            switchUser(mInitialUserId);
        }
        if (!mCreatedUserIds.isEmpty()) {
            CLog.i("Removing users %s", mCreatedUserIds);
            for (int userId : mCreatedUserIds) {
                removeUser(userId);
            }
        }
        if (mInitialMaximumNumberOfUsers != null) {
            CLog.i("Restoring max number of users to %d", mInitialMaximumNumberOfUsers);
            setMaxNumberUsers(mInitialMaximumNumberOfUsers);
        }
    }

    /**
     * Makes sure the device supports multiple users, throwing {@link AssumptionViolatedException}
     * if it doesn't.
     */
    protected void assumeSupportsMultipleUsers() throws Exception {
        assumeTrue("device does not support multi-user",
                getDevice().getMaxNumberOfUsersSupported() > 1);
    }

    /**
     * Makes sure the device can add {@code numberOfUsers} new users, increasing limit if needed or
     * failing if not possible.
     */
    protected void requiresExtraUsers(int numberOfUsers) throws Exception {
        assumeSupportsMultipleUsers();

        int maxNumber = getDevice().getMaxNumberOfUsersSupported();
        int currentNumber = getDevice().listUsers().size();

        if (currentNumber + numberOfUsers <= maxNumber) return;

        if (!getDevice().isAdbRoot()) {
            failCannotCreateUsers(numberOfUsers, currentNumber, maxNumber, /* isAdbRoot= */ false);
        }

        // Increase limit...
        mInitialMaximumNumberOfUsers = maxNumber;
        setMaxNumberUsers(maxNumber + numberOfUsers);

        // ...and try again
        maxNumber = getDevice().getMaxNumberOfUsersSupported();
        if (currentNumber + numberOfUsers > maxNumber) {
            failCannotCreateUsers(numberOfUsers, currentNumber, maxNumber, /* isAdbRoot= */ true);
        }
    }

    private void failCannotCreateUsers(int numberOfUsers, int currentNumber, int maxNumber,
            boolean isAdbRoot) {
        String reason = isAdbRoot ? "failed to increase it"
                : "cannot be increased without adb root";
        String existingUsers = "";
        try {
            existingUsers = "Existing users: " + executeCommand("cmd user list --all -v");
        } catch (Exception e) {
            // ignore
        }
        fail("Cannot create " + numberOfUsers + " users: current number is " + currentNumber
                + ", limit is " + maxNumber + " and could not be increased (" + reason + "). "
                + existingUsers);
    }

    /**
     * Executes the adb shell command and returns the output.
     */
    protected String executeCommand(String command, Object...args) throws Exception {
        String fullCommand = String.format(command, args);
        return getDevice().executeShellCommand(fullCommand);
    }

    /**
     * Executes the adb shell command and parses the Matcher output with {@code resultParser}.
     */
    protected <T> T executeAndParseCommand(Pattern regex, String matchNotFoundErrorMessage,
            Function<Matcher, T> resultParser,
            String command, Object...args) throws Exception {
        String output = executeCommand(command, args);
        Matcher matcher = regex.matcher(output);
        if (!matcher.find()) {
            fail(matchNotFoundErrorMessage);
        }
        return resultParser.apply(matcher);
    }

    /**
     * Sets the maximum number of users that can be created for this car.
     *
     * @throws IllegalStateException if adb is not running as root
     */
    protected void setMaxNumberUsers(int numUsers) throws Exception {
        if (!getDevice().isAdbRoot()) {
            throw new IllegalStateException("must be running adb root");
        }
        executeCommand("setprop fw.max_users %d", numUsers);
    }

    /**
     * Gets the current user's id.
     */
    protected int getCurrentUserId() throws DeviceNotAvailableException {
        return getDevice().getCurrentUser();
    }

    /**
     * Creates a full user with car service shell command.
     */
    protected int createFullUser(String name) throws Exception {
        int userId = executeAndParseCommand(CREATE_USER_OUTPUT_REGEX,
                "Could not create user with name " + name,
                matcher -> Integer.parseInt(matcher.group(1)),
                "cmd car_service create-user %s", name);

        mCreatedUserIds.add(userId);
        return userId;
    }

    /**
     * Switches the current user.
     */
    protected void switchUser(int userId) throws Exception {
        String output = executeCommand("cmd car_service switch-user %d", userId);
        if (!output.contains("STATUS_SUCCESSFUL")) {
            throw new IllegalStateException("Failed to switch to user " + userId + ": " + output);
        }
        waitUntilCurrentUser(userId);
    }

    protected void waitUntilCurrentUser(int userId) throws Exception {
        CommonTestUtils.waitUntil("timed out (" + DEFAULT_TIMEOUT_SEC
                + "s) waiting for current user to be " + userId
                + " (it is " + getCurrentUserId() + ")",
                DEFAULT_TIMEOUT_SEC,
                () -> (getCurrentUserId() == userId));
    }

    /**
     * Removes a user by user ID.
     */
    protected void removeUser(int userId) throws Exception {
        executeCommand("cmd car_service remove-user %d", userId);
    }

    // TODO: move to common infra code
    private static final class RequiredFeatureRule implements TestRule {

        private final ITestInformationReceiver mReceiver;
        private final String mFeature;

        RequiredFeatureRule(ITestInformationReceiver receiver, String feature) {
            mReceiver = receiver;
            mFeature = feature;
        }

        @Override
        public Statement apply(Statement base, Description description) {
            return new Statement() {

                @Override
                public void evaluate() throws Throwable {
                    boolean hasFeature = false;
                    try {
                        hasFeature = mReceiver.getTestInformation().getDevice()
                                .hasFeature(mFeature);
                    } catch (DeviceNotAvailableException e) {
                        CLog.e("Could not check if device has feature %s: %e", mFeature, e);
                        return;
                    }

                    if (!hasFeature) {
                        CLog.d("skipping %s#%s"
                                + " because device does not have feature '%s'",
                                description.getClassName(), description.getMethodName(), mFeature);
                        assumeTrue("Device does not have feature '" + mFeature + "'",
                                hasFeature);
                        return;
                    }
                    base.evaluate();
                }
            };
        }

        @Override
        public String toString() {
            return "RequiredFeatureRule[" + mFeature + "]";
        }
    }
}
