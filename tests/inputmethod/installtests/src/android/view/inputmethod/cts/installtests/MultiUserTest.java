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

package android.view.inputmethod.cts.installtests;

import static com.android.compatibility.common.util.SystemUtil.runShellCommandOrThrow;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.Manifest;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.InstantAppInfo;
import android.content.pm.PackageManager;
import android.content.pm.UserInfo;
import android.os.NewUserRequest;
import android.os.Process;
import android.os.RemoteCallback;
import android.os.UserHandle;
import android.os.UserManager;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.Flags;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.inputmethod.cts.installtests.common.Ime1Constants;
import android.view.inputmethod.cts.installtests.common.Ime2Constants;
import android.view.inputmethod.cts.installtests.common.ShellCommandUtils;
import android.view.inputmethod.cts.util.MockTestActivityUtil;

import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@LargeTest
@RunWith(JUnit4.class)
public class MultiUserTest {
    private static final String TAG = "MultiUserTest";
    private static final long TIMEOUT = TimeUnit.SECONDS.toMillis(15);
    private static final long USER_SWITCH_TIMEOUT = TimeUnit.SECONDS.toMillis(60);
    private static final long IME_COMMAND_TIMEOUT = TimeUnit.SECONDS.toMillis(20);

    /**
     * A sleep time after calling {@link com.android.tradefed.device.ITestDevice#switchUser(int)} to
     * see if the flakiness comes from race condition in UserManagerService#removeUser() or not.
     *
     * <p>TODO(Bug 122609784): Remove this once we figure out what is the root cause of flakiness.
     */
    private static final long WAIT_AFTER_USER_SWITCH = TimeUnit.SECONDS.toMillis(10);

    public ErrorCollector mErrorCollector = new ErrorCollector();

    private Context mContext;
    private InputMethodManager mImm;
    private UserManager mUserManager;

    private boolean mNeedsTearDown = false;

    /**
     * {@code true} if {@link #tearDown()} needs to be fully executed.
     *
     * <p>When {@link #setUp()} is interrupted by {@link org.junit.AssumptionViolatedException}
     * before
     * the actual setup tasks are executed, all the corresponding cleanup tasks should also be
     * skipped.
     *
     * <p>Once JUnit 5 becomes available in Android, we can remove this by moving the assumption
     * checks into a non-static {@link org.junit.BeforeClass} method.
     */
    private List<Integer> mOriginalUsers;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        mContext = instrumentation.getContext();
        mImm = mContext.getSystemService(InputMethodManager.class);
        mUserManager = mContext.getSystemService(UserManager.class);

        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_INPUT_METHODS));
        assumeTrue(UserManager.supportsMultipleUsers());

        mNeedsTearDown = true;

        mOriginalUsers = listUsers();
        mOriginalUsers.forEach(userId -> uninstallImeAsUser(Ime1Constants.PACKAGE, userId));
    }

    @After
    public void tearDown() throws Exception {
        if (!mNeedsTearDown) {
            return;
        }
        switchUser(Process.myUserHandle().getIdentifier());
        // We suspect that the optimization made for Bug 38143512 was a bit unstable.  Let's see
        // if adding a sleep improves the stability or not.
        Thread.sleep(WAIT_AFTER_USER_SWITCH);

        final List<Integer> newUsers = listUsers();
        for (int userId : newUsers) {
            if (!mOriginalUsers.contains(userId)) {
                uninstallImeAsUser(Ime1Constants.PACKAGE, userId);
                uninstallImeAsUser(Ime2Constants.PACKAGE, userId);
                removeUser(userId);
            }
        }

        runShellCommandOrThrow(ShellCommandUtils.resetImesForAllUsers());

        runShellCommandOrThrow(ShellCommandUtils.wakeUp());
        runShellCommandOrThrow(ShellCommandUtils.dismissKeyguard());
        runShellCommandOrThrow(ShellCommandUtils.closeSystemDialog());
    }

    /**
     * Make sure that InputMethodManagerService automatically updates its internal IME list upon IME
     * APK installation
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMM_USERHANDLE_HOSTSIDETESTS)
    public void testSecondaryUser() throws Exception {
        final int currentUserId = Process.myUserHandle().getIdentifier();
        final int secondaryUserId = createNewUser();

        startUser(secondaryUserId);

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(secondaryUserId);

        installPackageAsUser(Ime1Constants.APK_PATH, secondaryUserId);

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, secondaryUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, secondaryUserId));
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);

        switchUser(secondaryUserId);

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);

        switchUser(currentUserId);

        // For devices that have config_multiuserDelayUserDataLocking set to true, the
        // secondaryUserId will be stopped after switching to the currentUserId. This means that
        // the InputMethodManager can no longer query for the Input Method Services since they have
        // all been stopped.
        startUser(secondaryUserId /* waitFlag */);

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeExistsInApiResult(Ime1Constants.IME_ID, secondaryUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(secondaryUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, secondaryUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, secondaryUserId);
    }

    /**
     * Make sure that InputMethodManagerService automatically updates its internal IME list upon IME
     * APK installation
     */
    @Test
    @RequiresFlagsEnabled(Flags.FLAG_IMM_USERHANDLE_HOSTSIDETESTS)
    public void testProfileUser() throws Exception {
        assumeTrue(mContext.getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_MANAGED_USERS));

        final int currentUserId = Process.myUserHandle().getIdentifier();
        final int profileUserId = createProfile(currentUserId);

        startUser(profileUserId /* waitFlag */);

        assertImeNotExistInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);

        runShellCommandOrThrow(ShellCommandUtils.waitForBroadcastBarrier());

        // Install IME1 then enable/set it as the current IME for the main user.
        installPackageAsUser(Ime1Constants.APK_PATH, currentUserId);
        waitUntilImeIsInShellCommandResult(Ime1Constants.IME_ID, currentUserId);
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime1Constants.IME_ID, currentUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime1Constants.IME_ID, currentUserId));

        // Install IME2 then enable/set it as the current IME for the profile user.
        installPackageAsUser(Ime2Constants.APK_PATH, profileUserId);
        waitUntilImeIsInShellCommandResult(Ime2Constants.IME_ID, profileUserId);
        runShellCommandOrThrow(ShellCommandUtils.enableIme(Ime2Constants.IME_ID, profileUserId));
        runShellCommandOrThrow(
                ShellCommandUtils.setCurrentImeSync(Ime2Constants.IME_ID, profileUserId));

        // Main User: IME1:enabled, IME2:N/A
        assertImeExistsInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeEnabledInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime2Constants.IME_ID, currentUserId);
        assertImeNotEnabledInApiResult(Ime2Constants.IME_ID, currentUserId);
        assertImeSelected(Ime1Constants.IME_ID, currentUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime2Constants.IME_ID, currentUserId);

        // Profile User: IME1:N/A, IME2:enabled
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertImeNotEnabledInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertImeExistsInApiResult(Ime2Constants.IME_ID, profileUserId);
        assertImeEnabledInApiResult(Ime2Constants.IME_ID, profileUserId);
        assertImeSelected(Ime2Constants.IME_ID, profileUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, profileUserId);
        assertImeInCurrentInputMethodInfo(Ime2Constants.IME_ID, profileUserId);
        // Check isStylusHandwritingAvailable() for profile user.
        assertIsStylusHandwritingAvailable(profileUserId, currentUserId);

        // Install Test App for the profile user and make sure it is available as it is used next
        installExistingPackageAsUser(MockTestActivityUtil.TEST_ACTIVITY.getPackageName(),
                profileUserId);
        assertPackageExistsInApiResult(MockTestActivityUtil.TEST_ACTIVITY.getPackageName(),
                profileUserId);

        // Make sure that IME switches depending on the target user.
        assertConnectingToTheSameUserIme(currentUserId);
        assertConnectingToTheSameUserIme(profileUserId);
        assertConnectingToTheSameUserIme(currentUserId);

        assertIme1ImplicitlyEnabledSubtypeExists(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);

        assertImeExistsInApiResult(Ime1Constants.IME_ID, currentUserId);
        assertImeNotExistInApiResult(Ime1Constants.IME_ID, profileUserId);
        assertIme1ImplicitlyEnabledSubtypeExists(currentUserId);
        assertIme1ImplicitlyEnabledSubtypeNotExist(profileUserId);
        // check getCurrentInputMethodInfoAsUser(userId)
        assertImeInCurrentInputMethodInfo(Ime1Constants.IME_ID, currentUserId);
        assertImeNotCurrentInputMethodInfo(Ime1Constants.IME_ID, profileUserId);
    }

    private void assertPackageExistsInApiResult(String packageName, int userId) {
        PackageManager packageManager = mContext.getPackageManager();
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check(
                        "Package " + packageName + " must exist for user " + userId, TIMEOUT,
                        () -> packageManager.getInstalledPackagesAsUser(
                                PackageManager.MATCH_INSTANT, userId).stream().anyMatch(
                                    packageInfo -> TextUtils.equals(packageInfo.packageName,
                                        packageName))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                Manifest.permission.ACCESS_INSTANT_APPS);
    }

    private void assertImeExistsInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must exist for user " + userId, TIMEOUT,
                        () -> mImm.getInputMethodListAsUser(userId).stream().anyMatch(
                                imi -> TextUtils.equals(imi.getId(), imeId))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotExistInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> assertFalse(mImm.getInputMethodListAsUser(
                        userId).stream().anyMatch(imi -> TextUtils.equals(imi.getId(), imeId))),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertIme1ImplicitlyEnabledSubtypeExists(int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                PollingCheck.check(
                        "Implicitly enabled Subtype must exist for Ime " + Ime1Constants.IME_ID,
                        TIMEOUT, () -> mImm.getInputMethodListAsUser(userId).stream().filter(
                                imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)).flatMap(
                                    imi -> mImm.getEnabledInputMethodSubtypeListAsUser(imi.getId(),
                                        true, UserHandle.of(userId)).stream()).anyMatch(
                                InputMethodSubtype::overridesImplicitlyEnabledSubtype));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertIme1ImplicitlyEnabledSubtypeNotExist(int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> assertFalse(mImm.getInputMethodListAsUser(
                        userId).stream().filter(
                            imi -> TextUtils.equals(imi.getId(), Ime1Constants.IME_ID)).flatMap(
                                imi -> mImm.getEnabledInputMethodSubtypeList(imi, true)
                                        .stream()).anyMatch(
                                            InputMethodSubtype::overridesImplicitlyEnabledSubtype)),
                Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeInCurrentInputMethodInfo(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> PollingCheck.check(
                String.format("Ime %s must be the current IME. Found %s", imeId,
                        mImm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId()),
                TIMEOUT, () -> TextUtils.equals(
                        mImm.getCurrentInputMethodInfoAsUser(UserHandle.of(userId)).getId(),
                        imeId)), Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotCurrentInputMethodInfo(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(
                () -> PollingCheck.check("Ime " + imeId + " must not be the current IME.", TIMEOUT,
                        () -> {
                            final InputMethodInfo info = mImm.getCurrentInputMethodInfoAsUser(
                                    UserHandle.of(userId));
                            if (info == null) {
                                return true;
                            }
                            return !TextUtils.equals(info.getId(), imeId);
                        }), Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeEnabledInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                PollingCheck.check("Ime " + imeId + " must be enabled.", TIMEOUT,
                        () -> mImm.getEnabledInputMethodListAsUser(
                                UserHandle.of(userId)).stream().anyMatch(
                                    imi -> TextUtils.equals(imi.getId(), imeId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeNotEnabledInApiResult(String imeId, int userId) {
        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                assertFalse(mImm.getEnabledInputMethodListAsUser(
                        UserHandle.of(userId)).stream().anyMatch(
                            imi -> TextUtils.equals(imi.getId(), imeId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    private void assertImeSelected(String imeId, int userId) {
        assertEquals(imeId, runShellCommandOrThrow(ShellCommandUtils.getCurrentIme(userId)).trim());
    }

    private void assertIsStylusHandwritingAvailable(int profileUserId, int currentUserId) {
        // Turn stylus handwriting pref ON for current user and OFF for profile user.
        SystemUtil.runWithShellPermissionIdentity(() -> {
            runShellCommandOrThrow(
                    ShellCommandUtils.setStylusHandwritingEnabled(currentUserId, true));
            runShellCommandOrThrow(
                    ShellCommandUtils.setStylusHandwritingEnabled(profileUserId, false));
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);

        SystemUtil.runWithShellPermissionIdentity(() -> {
            try {
                // Stylus pref should still be picked from parent profile i.e. default true.
                PollingCheck.check(
                        "Handwriting should be enabled on profile user as primary user has it "
                                + "enabled",
                        TIMEOUT, () -> mImm.isStylusHandwritingAvailableAsUser(
                                UserHandle.of(profileUserId)));
            } catch (NoSuchMethodError error) {
                Log.w(TAG, "Caught NoSuchMethodError due to not available TestApi", error);
            }
        }, Manifest.permission.INTERACT_ACROSS_USERS_FULL);
    }

    /**
     * check if the app is connecting to the IME that runs under the same user ID.
     */
    public void assertConnectingToTheSameUserIme(int userId) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        RemoteCallback onCreateInputConnectionCallback = new RemoteCallback(bundle -> {
            Log.i(TAG, "RemoteCallback was invoked for user #" + bundle.getInt(
                    MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE));
            mErrorCollector.checkThat(userId,
                    equalTo(bundle.getInt(MockTestActivityUtil.ACTION_KEY_REPLY_USER_HANDLE)));
            latch.countDown();
        });
        try (AutoCloseable ignored = MockTestActivityUtil.launchSyncAsUser(userId, isInstantApp(),
                null, onCreateInputConnectionCallback)) {
            if (!latch.await(TIMEOUT, TimeUnit.MILLISECONDS)) {
                fail(String.format("IME not connected to the same user #%s within timeout",
                        userId));
            }
        }
    }

    private void waitUntilImeIsInShellCommandResult(String imeId, int userId) throws Exception {
        final String command = ShellCommandUtils.getAvailableImes(userId);
        PollingCheck.check(imeId + " is not found for user #" + userId + " within timeout.",
                IME_COMMAND_TIMEOUT,
                () -> Arrays.asList(runShellCommandOrThrow(command)
                        .split("\n")).contains(imeId));
    }

    private int createNewUser() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            final NewUserRequest newUserRequest = new NewUserRequest.Builder().setName(
                    "test_user" + System.currentTimeMillis()).setUserType(
                    UserManager.USER_TYPE_FULL_SECONDARY).build();
            final UserHandle newUser = mUserManager.createUser(newUserRequest).getUser();
            if (newUser == null) {
                fail("Error while creating a new user");
            }
            return newUser.getIdentifier();
        });
    }

    private void removeUser(int userId) {
        final String output = runShellCommandOrThrow(ShellCommandUtils.removeUser(userId));
        if (output.startsWith("Error")) {
            fail("Error removing the user #" + userId + ": " + output);
        }
    }

    private void startUser(int userId) {
        Log.i(TAG, "Starting user " + userId);
        String output = runShellCommandOrThrow(ShellCommandUtils.startUser(userId));
        if (output.startsWith("Error")) {
            fail(String.format("Failed to start user %d: %s", userId, output));
        }
    }

    private void switchUser(int userId) throws Exception {
        runShellCommandOrThrow(ShellCommandUtils.switchToUserId(userId));

        // TODO(b/282196632): Implement cmd input_method get-last-switch-user-id in
        // Android Auto IMMS
        if (isMultiUserMultiDisplayIme()) {
            return;
        }

        PollingCheck.check("Failed to get last SwitchUser ID from InputMethodManagerService.",
                USER_SWITCH_TIMEOUT, () -> {
                    String result = runShellCommandOrThrow(ShellCommandUtils.getLastSwitchUserId());
                    final String[] lines = result.split("\\r?\\n");
                    if (lines.length < 1) {
                        throw new IllegalStateException(
                                "Failed to get last SwitchUser ID from InputMethodManagerService."
                                        + " result=" + result);
                    }
                    final int lastSwitchUserId = Integer.parseInt(lines[0], 10);
                    return userId == lastSwitchUserId;
            });
    }

    private int createProfile(int parentUserId) {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            final UserInfo newUser = mUserManager.createProfileForUser(
                    "profile_user" + System.currentTimeMillis(),
                    UserManager.USER_TYPE_PROFILE_MANAGED, 0, parentUserId, new String[]{});
            if (newUser == null) {
                fail("Error while creating a new profile");
            }
            return newUser.getUserHandle().getIdentifier();
        });
    }

    private List<Integer> listUsers() {
        return SystemUtil.runWithShellPermissionIdentity(() -> mUserManager.getUsers().stream().map(
                userInfo -> userInfo.getUserHandle().getIdentifier()).toList());
    }

    // TODO(b/282196632): remove this method once b/282196632) is fixed
    private boolean isMultiUserMultiDisplayIme() {
        String result = runShellCommandOrThrow("dumpsys input_method");
        return result.contains("InputMethodManagerServiceProxy");
    }

    private void installPackageAsUser(String apkPath, int userId) {
        Log.v(TAG, "Installing apk: " + apkPath + " for user " + userId);
        runShellCommandOrThrow(ShellCommandUtils.installPackageAsUser(apkPath, userId));
        runShellCommandOrThrow(ShellCommandUtils.waitForBroadcastBarrier());
    }

    private void installExistingPackageAsUser(String packageName, int userId) {
        Log.v(TAG, "Installing existing package: " + packageName + " for user " + userId);
        runShellCommandOrThrow(ShellCommandUtils.installExistingPackageAsUser(packageName, userId,
                isInstantApp()));
        runShellCommandOrThrow(ShellCommandUtils.waitForBroadcastBarrier());
    }

    private boolean isInstantApp() {
        return SystemUtil.runWithShellPermissionIdentity(() -> {
            // as this test app itself is always running as a full app, we can check if the
            // CtsInputMethodStandaloneTestApp was installed as an instant app
            boolean instant = false;
            Optional<InstantAppInfo> instantAppInfo =
                    mContext.getPackageManager().getInstantApps().stream()
                            .filter(packageInfo -> TextUtils.equals(packageInfo.getPackageName(),
                                    MockTestActivityUtil.TEST_ACTIVITY.getPackageName()))
                            .findFirst();
            if (instantAppInfo.isPresent()
                    && instantAppInfo.get().getApplicationInfo().isInstantApp()) {
                instant = true;
            }
            return instant;
        }, Manifest.permission.ACCESS_INSTANT_APPS);
    }

    private static void uninstallImeAsUser(String packageName, int userId) {
        Log.v(TAG, "Uninstalling package: " + packageName + " for user " + userId);
        runShellCommandOrThrow(ShellCommandUtils.uninstallPackage(packageName, userId));
        runShellCommandOrThrow(ShellCommandUtils.resetImes());
    }
}
