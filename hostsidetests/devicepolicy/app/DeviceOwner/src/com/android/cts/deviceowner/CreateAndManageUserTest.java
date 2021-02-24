/*
 * Copyright (C) 2016 The Android Open Source Project
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

package com.android.cts.deviceowner;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.testng.Assert.expectThrows;

import android.app.Service;
import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.PersistableBundle;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.DebugUtils;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Test {@link DevicePolicyManager#createAndManageUser}.
 */
public class CreateAndManageUserTest extends BaseDeviceOwnerTest {
    private static final String TAG = "CreateAndManageUserTest";

    private static final int BROADCAST_TIMEOUT = 300_000;

    private static final String AFFILIATION_ID = "affiliation.id";
    private static final String EXTRA_AFFILIATION_ID = "affiliationIdExtra";
    private static final String EXTRA_METHOD_NAME = "methodName";
    private static final long ON_ENABLED_TIMEOUT_SECONDS = 120;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);
        mDevicePolicyManager.clearUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);
        super.tearDown();
    }

    public void testCreateAndManageUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        assertWithMessage("New user").that(userHandle).isNotNull();
    }

    public void testCreateAndManageUser_LowStorage() throws Exception {
        UserManager.UserOperationException e = expectThrows(
                UserManager.UserOperationException.class, () -> createAndManageUser());

        assertUserOperationResult(e.getUserOperationResult(),
                UserManager.USER_OPERATION_ERROR_LOW_STORAGE,
                "user creation on low storage");
    }

    public void testCreateAndManageUser_MaxUsers() throws Exception {
        UserManager.UserOperationException e = expectThrows(
                UserManager.UserOperationException.class, () -> createAndManageUser());

        assertUserOperationResult(e.getUserOperationResult(),
                UserManager.USER_OPERATION_ERROR_MAX_USERS,
                "user creation when max users is reached");
    }

    @SuppressWarnings("unused")
    private static void assertSkipSetupWizard(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) throws Exception {
        assertWithMessage("user setup settings (%s)", Settings.Secure.USER_SETUP_COMPLETE)
                .that(Settings.Secure.getInt(context.getContentResolver(),
                        Settings.Secure.USER_SETUP_COMPLETE))
                .isEqualTo(1);
    }

    public void testCreateAndManageUser_SkipSetupWizard() throws Exception {
        runCrossUserVerification(DevicePolicyManager.SKIP_SETUP_WIZARD, "assertSkipSetupWizard");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    public void testCreateAndManageUser_GetSecondaryUsers() throws Exception {
        UserHandle userHandle = createAndManageUser();

        List<UserHandle> secondaryUsers = mDevicePolicyManager.getSecondaryUsers(getWho());
        assertWithMessage("wrong secondary users").that(secondaryUsers).containsExactly(userHandle);
    }

    public void testCreateAndManageUser_SwitchUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        switchUser(userHandle);
    }

    public void testCreateAndManageUser_CannotStopCurrentUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        switchUser(userHandle);
        stopUserWithError(userHandle, UserManager.USER_OPERATION_ERROR_CURRENT_USER);
    }

    public void testCreateAndManageUser_StartInBackground() throws Exception {
        UserHandle userHandle = createAndManageUser();

        startUserInBackground(userHandle);

    }

    public void testCreateAndManageUser_StartInBackground_MaxRunningUsers() throws Exception {
        UserHandle userHandle = createAndManageUser();

        // Start user in background and should receive max running users error
        startUserInBackgroundWithError(userHandle,
                UserManager.USER_OPERATION_ERROR_MAX_RUNNING_USERS);
    }

    public void testCreateAndManageUser_StopUser() throws Exception {
        UserHandle userHandle = createAndManageUser();

        startUserInBackground(userHandle);

        stopUser(userHandle);
    }

    public void testCreateAndManageUser_StopEphemeralUser_DisallowRemoveUser() throws Exception {
        // Set DISALLOW_REMOVE_USER restriction
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);

        UserHandle userHandle = createAndManageUser(DevicePolicyManager.MAKE_USER_EPHEMERAL);

        startUserInBackground(userHandle);

        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_STOPPED, BasicAdminReceiver.ACTION_USER_REMOVED);
        callback.runAndUnregisterSelf(
                // it's running just one operation (which issues a ACTION_USER_STOPPED), but as the
                // user is ephemeral, it will be automatically removed (which issues a
                // ACTION_USER_REMOVED).
                () -> stopUserWithError(userHandle, UserManager.USER_OPERATION_SUCCESS),
                userHandle, userHandle);
    }

    @SuppressWarnings("unused")
    private static void logoutUser(Context context, DevicePolicyManager devicePolicyManager,
            ComponentName componentName) {
        assertUserOperationResult(devicePolicyManager.logoutUser(componentName),
                UserManager.USER_OPERATION_SUCCESS, "cannot logout user");
    }

    public void testCreateAndManageUser_LogoutUser() throws Exception {

        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_STOPPED);
        callback.runAndUnregisterSelf(() -> {
            UserHandle userHandle = runCrossUserVerification(
                    /* createAndManageUserFlags */ 0, "logoutUser");
            callback.waitForBroadcasts(userHandle);
        });
    }

    @SuppressWarnings("unused")
    private static void assertAffiliatedUser(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) {
        assertWithMessage("affiliated user").that(devicePolicyManager.isAffiliatedUser()).isTrue();
    }

    public void testCreateAndManageUser_Affiliated() throws Exception {
        runCrossUserVerification(/* createAndManageUserFlags */ 0, "assertAffiliatedUser");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    @SuppressWarnings("unused")
    private static void assertEphemeralUser(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) {
        assertWithMessage("ephemeral user").that(devicePolicyManager.isEphemeralUser(componentName))
                .isTrue();
    }

    public void testCreateAndManageUser_Ephemeral() throws Exception {
        runCrossUserVerification(DevicePolicyManager.MAKE_USER_EPHEMERAL, "assertEphemeralUser");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    @SuppressWarnings("unused")
    private static void assertAllSystemAppsInstalled(Context context,
            DevicePolicyManager devicePolicyManager, ComponentName componentName) {
        PackageManager packageManager = context.getPackageManager();
        // First get a set of installed package names
        Set<String> installedPackageNames = packageManager
                .getInstalledApplications(/* flags */ 0)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
        // Then filter all package names by those that are not installed
        Set<String> uninstalledPackageNames = packageManager
                .getInstalledApplications(PackageManager.MATCH_UNINSTALLED_PACKAGES)
                .stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .filter(((Predicate<String>) installedPackageNames::contains).negate())
                .collect(Collectors.toSet());
        // Assert that all apps are installed
        assertTrue("system apps not installed: " + uninstalledPackageNames,
                uninstalledPackageNames.isEmpty());
    }

    public void testCreateAndManageUser_LeaveAllSystemApps() throws Exception {
        runCrossUserVerification(
                DevicePolicyManager.LEAVE_ALL_SYSTEM_APPS_ENABLED, "assertAllSystemAppsInstalled");
        PrimaryUserService.assertCrossUserCallArrived();
    }

    private UserHandle runCrossUserVerification(int createAndManageUserFlags, String methodName)
            throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        // Set affiliation id to allow communication.
        mDevicePolicyManager.setAffiliationIds(getWho(), Collections.singleton(AFFILIATION_ID));

        // Pack the affiliation id in a bundle so the secondary user can get it.
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString(EXTRA_AFFILIATION_ID, AFFILIATION_ID);
        bundle.putString(EXTRA_METHOD_NAME, methodName);

        UserHandle userHandle = mDevicePolicyManager.createAndManageUser(
                getWho(),
                testUserName,
                SecondaryUserAdminReceiver.getComponentName(getContext()),
                bundle,
                createAndManageUserFlags);
        Log.d(TAG, "User created: " + userHandle);
        assertWithMessage("user with name %s", testUserName).that(userHandle).isNotNull();
        startUserInBackground(userHandle);
        return userHandle;
    }

    // createAndManageUser should circumvent the DISALLOW_ADD_USER restriction
    public void testCreateAndManageUser_AddRestrictionSet() throws Exception {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_ADD_USER);

        createAndManageUser();
    }

    public void testCreateAndManageUser_RemoveRestrictionSet() throws Exception {
        mDevicePolicyManager.addUserRestriction(getWho(), UserManager.DISALLOW_REMOVE_USER);

        UserHandle userHandle = createAndManageUser();

        // When the device owner itself has set the user restriction, it should still be allowed
        // to remove a user.
        removeUser(userHandle);
    }

    public void testUserAddedOrRemovedBroadcasts() throws Exception {
        UserHandle userHandle = createAndManageUser();

        removeUser(userHandle);
    }

    private UserHandle createAndManageUser() throws Exception {
        return createAndManageUser(/* flags= */ 0);
    }

    private UserHandle createAndManageUser(int flags) throws Exception {
        String testUserName = "TestUser_" + System.currentTimeMillis();

        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_ADDED);

        return callback.callAndUnregisterSelf(() -> {
            UserHandle userHandle = mDevicePolicyManager.createAndManageUser(
                    /* admin= */ getWho(),
                    testUserName,
                    /* profileOwner= */ getWho(),
                    /* adminExtras= */ null,
                    flags);
            Log.d(TAG, "User '" + testUserName + "' created: " + userHandle);

            callback.waitForBroadcasts(userHandle);
            return userHandle;
        });
    }

    private void switchUser(UserHandle userHandle) throws Exception {
        Log.d(TAG, "Switching to user " + userHandle);

        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_STARTED, BasicAdminReceiver.ACTION_USER_SWITCHED);

        callback.runAndUnregisterSelf(() -> {
            boolean switched = mDevicePolicyManager.switchUser(getWho(), userHandle);
            assertWithMessage("switched to user %s", userHandle).that(switched).isTrue();
        }, userHandle, userHandle);
    }

    private void removeUser(UserHandle userHandle) throws Exception {
        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_REMOVED);

        callback.runAndUnregisterSelf(() -> {
            boolean removed = mDevicePolicyManager.removeUser(getWho(), userHandle);
            assertWithMessage("removed user %s", userHandle).that(removed).isTrue();
        }, userHandle);
    }

    private static String userOperationResultToString(int result) {
        return DebugUtils.constantToString(UserManager.class, "USER_OPERATION_", result);
    }

    private static void assertUserOperationResult(int actualResult, int expectedResult,
            String operationFormat, Object... operationArgs) {
        String operation = String.format(operationFormat, operationArgs);
        assertWithMessage("result for %s (%s instead of %s)", operation,
                userOperationResultToString(actualResult),
                userOperationResultToString(expectedResult))
                        .that(actualResult).isEqualTo(expectedResult);
    }

    private void startUserInBackgroundWithError(UserHandle userHandle, int expectedError) {
        int actualError = mDevicePolicyManager.startUserInBackground(getWho(), userHandle);
        assertUserOperationResult(actualError, expectedError, "starting user %s in background",
                userHandle);
    }

    private void startUserInBackground(UserHandle userHandle) throws Exception {
        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_STARTED);
        callback.runAndUnregisterSelf(() -> startUserInBackgroundWithError(userHandle,
                UserManager.USER_OPERATION_SUCCESS), userHandle);
    }

    private void stopUserWithError(UserHandle userHandle, int expectedError) {
        int actualError = mDevicePolicyManager.stopUser(getWho(), userHandle);
        assertUserOperationResult(actualError, expectedError, "stopping user %s", userHandle);
    }

    private void stopUser(UserHandle userHandle) throws Exception {
        UserActionCallback callback = UserActionCallback.register(getContext(),
                BasicAdminReceiver.ACTION_USER_STOPPED);
        callback.runAndUnregisterSelf(
                () -> stopUserWithError(userHandle, UserManager.USER_OPERATION_SUCCESS),
                userHandle);
    }

    public static final class PrimaryUserService extends Service {
        private static final Semaphore sSemaphore = new Semaphore(0);
        private static String sError = null;

        private final ICrossUserService.Stub mBinder = new ICrossUserService.Stub() {
            public void onEnabledCalled(String error) {
                Log.d(TAG, "onEnabledCalled on primary user");
                sError = error;
                sSemaphore.release();
            }
        };

        @Override
        public IBinder onBind(Intent intent) {
            return mBinder;
        }

        static void assertCrossUserCallArrived() throws Exception {
            assertTrue(sSemaphore.tryAcquire(ON_ENABLED_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            if (sError != null) {
                throw new Exception(sError);
            }
        }
    }

    public static final class SecondaryUserAdminReceiver extends DeviceAdminReceiver {
        @Override
        public void onEnabled(Context context, Intent intent) {
            Log.d(TAG, "onEnabled called");
            DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
            ComponentName who = getComponentName(context);

            // Set affiliation ids
            dpm.setAffiliationIds(
                    who, Collections.singleton(intent.getStringExtra(EXTRA_AFFILIATION_ID)));

            String error = null;
            try {
                Method method = CreateAndManageUserTest.class.getDeclaredMethod(
                        intent.getStringExtra(EXTRA_METHOD_NAME), Context.class,
                        DevicePolicyManager.class, ComponentName.class);
                method.setAccessible(true);
                method.invoke(null, context, dpm, who);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                error = e.toString();
            } catch (InvocationTargetException e) {
                error = e.getCause().toString();
            }

            // Call all affiliated users
            final List<UserHandle> targetUsers = dpm.getBindDeviceAdminTargetUsers(who);
            assertEquals(1, targetUsers.size());
            pingTargetUser(context, dpm, targetUsers.get(0), error);
        }

        private void pingTargetUser(Context context, DevicePolicyManager dpm, UserHandle target,
                String error) {
            Log.d(TAG, "Pinging target: " + target);
            final ServiceConnection serviceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    Log.d(TAG, "onServiceConnected is called in " + Thread.currentThread().getName());
                    ICrossUserService crossUserService = ICrossUserService
                            .Stub.asInterface(service);
                    try {
                        crossUserService.onEnabledCalled(error);
                    } catch (RemoteException re) {
                        Log.e(TAG, "Error when calling primary user", re);
                        // Do nothing, primary user will time out
                    }
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    Log.d(TAG, "onServiceDisconnected is called");
                }
            };
            final Intent serviceIntent = new Intent(context, PrimaryUserService.class);
            assertTrue(dpm.bindDeviceAdminServiceAsUser(
                    getComponentName(context),
                    serviceIntent,
                    serviceConnection,
                    Context.BIND_AUTO_CREATE,
                    target));
        }

        public static ComponentName getComponentName(Context context) {
            return new ComponentName(context, SecondaryUserAdminReceiver.class);
        }
    }
}
