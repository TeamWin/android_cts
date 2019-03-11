/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.role.cts;

import static com.android.compatibility.common.util.SystemUtil.callWithShellPermissionIdentity;
import static com.android.compatibility.common.util.SystemUtil.runShellCommand;
import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;
import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.role.OnRoleHoldersChangedListener;
import android.app.role.RoleManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Process;
import android.os.UserHandle;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.UiObject2;
import android.support.test.uiautomator.Until;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.FlakyTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AppOpsUtils;
import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * Tests {@link RoleManager}.
 */
@RunWith(AndroidJUnit4.class)
public class RoleManagerTest {

    private static final String LOG_TAG = "RoleManagerTest";

    private static final long TIMEOUT_MILLIS = 15 * 1000;

    private static final long UNEXPECTED_TIMEOUT_MILLIS = 1000;

    private static final String ROLE_NAME = RoleManager.ROLE_MUSIC;

    private static final String APP_PACKAGE_NAME = "android.app.role.cts.app";
    private static final String APP_IS_ROLE_HELD_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".IsRoleHeldActivity";
    private static final String APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD = APP_PACKAGE_NAME
            + ".extra.IS_ROLE_HELD";
    private static final String APP_REQUEST_ROLE_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".RequestRoleActivity";

    private static final String PERMISSION_MANAGE_ROLES_FROM_CONTROLLER =
            "com.android.permissioncontroller.permission.MANAGE_ROLES_FROM_CONTROLLER";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final PackageManager sPackageManager = sContext.getPackageManager();
    private static final RoleManager sRoleManager = sContext.getSystemService(RoleManager.class);
    private static final UiDevice sUiDevice = UiDevice.getInstance(sInstrumentation);

    @Rule
    public ActivityTestRule<WaitForResultActivity> mActivityRule =
            new ActivityTestRule<>(WaitForResultActivity.class);

    private String mRoleHolder;

    @Before
    public void saveRoleHolder() throws Exception {
        List<String> roleHolders = getRoleHolders(ROLE_NAME);
        mRoleHolder = !roleHolders.isEmpty() ? roleHolders.get(0) : null;

        if (Objects.equals(mRoleHolder, APP_PACKAGE_NAME)) {
            removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
            mRoleHolder = null;
        }
    }

    @After
    public void restoreRoleHolder() throws Exception {
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        if (mRoleHolder != null) {
            addRoleHolder(ROLE_NAME, mRoleHolder);
        }

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void requestRoleIntentHasPermissionControllerPackage() throws Exception {
        Intent intent = sRoleManager.createRequestRoleIntent(ROLE_NAME);

        assertThat(intent.getPackage()).isEqualTo(
                sPackageManager.getPermissionControllerPackageName());
    }

    @Test
    public void requestRoleIntentHasExtraRoleName() throws Exception {
        Intent intent = sRoleManager.createRequestRoleIntent(ROLE_NAME);

        assertThat(intent.getStringExtra(Intent.EXTRA_ROLE_NAME)).isEqualTo(ROLE_NAME);
    }

    @FlakyTest
    @Test
    public void requestRoleAndRejectThenIsNotRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @FlakyTest
    @Test
    public void requestRoleAndApproveThenIsRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(true);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);
    }

    private void requestRole(@NonNull String roleName) {
        Intent intent = new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME, APP_REQUEST_ROLE_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_ROLE_NAME, roleName);
        mActivityRule.getActivity().startActivityToWaitForResult(intent);
    }

    private void respondToRoleRequest(boolean ok, boolean expectResultOk)
            throws InterruptedException, IOException {
        wakeUpScreen();
        String buttonId = ok ? "android:id/button1" : "android:id/button2";
        UiObject2 button = sUiDevice.wait(Until.findObject(By.res(buttonId)), TIMEOUT_MILLIS);
        if (button == null) {
            dumpWindowHierarchy();
            fail("Cannot find button to click");
        }
        button.click();
        Pair<Integer, Intent> result = mActivityRule.getActivity().waitForActivityResult(
                TIMEOUT_MILLIS);
        int expectedResult = expectResultOk ? Activity.RESULT_OK : Activity.RESULT_CANCELED;

        assertThat(result.first).isEqualTo(expectedResult);
    }

    private void respondToRoleRequest(boolean ok) throws InterruptedException, IOException {
        respondToRoleRequest(ok, ok);
    }

    private void wakeUpScreen() throws IOException {
        runShellCommand(sInstrumentation, "input keyevent KEYCODE_WAKEUP");
    }

    private void dumpWindowHierarchy() throws InterruptedException, IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sUiDevice.dumpWindowHierarchy(outputStream);
        String windowHierarchy = outputStream.toString(StandardCharsets.UTF_8.name());

        Log.w(LOG_TAG, "Window hierarchy:");
        for (String line : windowHierarchy.split("\n")) {
            Thread.sleep(10);
            Log.w(LOG_TAG, line);
        }
    }

    @Test
    public void roleIsAvailable() {
        assertThat(sRoleManager.isRoleAvailable(ROLE_NAME)).isTrue();
    }

    @Test
    public void dontAddRoleHolderThenRoleIsNotHeld() throws Exception {
        assertRoleIsHeld(ROLE_NAME, false);
    }

    @Test
    public void addRoleHolderThenRoleIsHeld() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertRoleIsHeld(ROLE_NAME, true);
    }

    @Test
    public void addAndRemoveRoleHolderThenRoleIsNotHeld() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertRoleIsHeld(ROLE_NAME, false);
    }

    private void assertRoleIsHeld(@NonNull String roleName, boolean isHeld)
            throws InterruptedException {
        Intent intent = new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME, APP_IS_ROLE_HELD_ACTIVITY_NAME))
                .putExtra(Intent.EXTRA_ROLE_NAME, roleName);
        WaitForResultActivity activity = mActivityRule.getActivity();
        activity.startActivityToWaitForResult(intent);
        Pair<Integer, Intent> result = activity.waitForActivityResult(TIMEOUT_MILLIS);

        assertThat(result.first).isEqualTo(Activity.RESULT_OK);
        assertThat(result.second).isNotNull();
        assertThat(result.second.hasExtra(APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD)).isTrue();
        assertThat(result.second.getBooleanExtra(APP_IS_ROLE_HELD_EXTRA_IS_ROLE_HELD, false))
                .isEqualTo(isHeld);
    }

    @Test
    public void dontAddRoleHolderThenIsNotRoleHolder() throws Exception {
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addRoleHolderThenIsRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);
    }

    @Test
    public void addAndRemoveRoleHolderThenIsNotRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addAndClearRoleHoldersThenIsNotRoleHolder() throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        clearRoleHolders(ROLE_NAME);

        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndAddRoleHolderThenIsNotified() throws Exception {
        assertOnRoleHoldersChangedListenerIsNotified(() -> addRoleHolder(ROLE_NAME,
                APP_PACKAGE_NAME));
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndRemoveRoleHolderThenIsNotified()
            throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertOnRoleHoldersChangedListenerIsNotified(() -> removeRoleHolder(ROLE_NAME,
                APP_PACKAGE_NAME));
    }

    @Test
    public void addOnRoleHoldersChangedListenerAndClearRoleHoldersThenIsNotified()
            throws Exception {
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        assertOnRoleHoldersChangedListenerIsNotified(() -> clearRoleHolders(ROLE_NAME));
    }

    private void assertOnRoleHoldersChangedListenerIsNotified(@NonNull ThrowingRunnable runnable)
            throws Exception {
        ListenerFuture future = new ListenerFuture();
        UserHandle user = Process.myUserHandle();
        runWithShellPermissionIdentity(() -> sRoleManager.addOnRoleHoldersChangedListenerAsUser(
                sContext.getMainExecutor(), future, user));
        Pair<String, UserHandle> result;
        try {
            runnable.run();
            result = future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } finally {
            runWithShellPermissionIdentity(() ->
                    sRoleManager.removeOnRoleHoldersChangedListenerAsUser(future, user));
        }

        assertThat(result.first).isEqualTo(ROLE_NAME);
        assertThat(result.second).isEqualTo(user);
    }

    @Test
    public void addAndRemoveOnRoleHoldersChangedListenerAndAddRoleHolderThenIsNotNotified()
            throws Exception {
        ListenerFuture future = new ListenerFuture();
        UserHandle user = Process.myUserHandle();
        runWithShellPermissionIdentity(() -> {
            sRoleManager.addOnRoleHoldersChangedListenerAsUser(sContext.getMainExecutor(), future,
                    user);
            sRoleManager.removeOnRoleHoldersChangedListenerAsUser(future, user);
        });
        addRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);

        try {
            future.get(UNEXPECTED_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // Expected
            return;
        }
        throw new AssertionError("OnRoleHoldersChangedListener was notified after removal");
    }

    @Test
    public void setRoleNamesFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.setRoleNamesFromController(Collections.emptyList()),
                "setRoleNamesFromController");
    }

    @Test
    public void addRoleHolderFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.addRoleHolderFromController(ROLE_NAME, APP_PACKAGE_NAME),
                "addRoleHolderFromController");
    }

    @Test
    public void removeRoleHolderFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.removeRoleHolderFromController(ROLE_NAME, APP_PACKAGE_NAME),
                "removeRoleHolderFromController");
    }

    @Test
    public void getHeldRolesFromControllerShouldRequireManageRolesFromControllerPermission() {
        assertRequiresManageRolesFromControllerPermission(
                () -> sRoleManager.getHeldRolesFromController(APP_PACKAGE_NAME),
                "getHeldRolesFromController");
    }

    private void assertRequiresManageRolesFromControllerPermission(@NonNull Runnable runnable,
            @NonNull String methodName) {
        try {
            runnable.run();
        } catch (SecurityException e) {
            if (e.getMessage().contains(PERMISSION_MANAGE_ROLES_FROM_CONTROLLER)) {
                // Expected
                return;
            }
            throw e;
        }
        fail("RoleManager." + methodName + "() should require "
                + PERMISSION_MANAGE_ROLES_FROM_CONTROLLER);
    }

    @Test
    public void manageRoleFromsFromControllerPermissionShouldBeDeclaredByPermissionController()
            throws PackageManager.NameNotFoundException {
        PermissionInfo permissionInfo = sPackageManager.getPermissionInfo(
                PERMISSION_MANAGE_ROLES_FROM_CONTROLLER, 0);

        assertThat(permissionInfo.packageName).isEqualTo(
                sPackageManager.getPermissionControllerPackageName());
        assertThat(permissionInfo.getProtection()).isEqualTo(PermissionInfo.PROTECTION_SIGNATURE);
        assertThat(permissionInfo.getProtectionFlags()).isEqualTo(0);
    }

    @Test
    public void removeSmsRoleHolderThenDialerRoleAppOpIsNotDenied() throws Exception {
        if (!(sRoleManager.isRoleAvailable(RoleManager.ROLE_DIALER)
                && sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS))) {
            return;
        }

        addRoleHolder(RoleManager.ROLE_DIALER, APP_PACKAGE_NAME);
        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        removeRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);

        assertThat(AppOpsUtils.getOpMode(APP_PACKAGE_NAME, AppOpsManager.OPSTR_SEND_SMS))
                .isEqualTo(AppOpsManager.MODE_ALLOWED);
    }

    @Test
    public void smsRoleHasHolder() throws Exception {
        if (!sRoleManager.isRoleAvailable(RoleManager.ROLE_SMS)) {
            return;
        }

        assertThat(getRoleHolders(RoleManager.ROLE_SMS)).isNotEmpty();
    }

    private List<String> getRoleHolders(@NonNull String roleName) throws Exception {
        return callWithShellPermissionIdentity(() -> sRoleManager.getRoleHolders(roleName));
    }

    private void assertIsRoleHolder(@NonNull String roleName, @NonNull String packageName,
            boolean shouldBeRoleHolder) throws Exception {
        List<String> packageNames = getRoleHolders(roleName);

        if (shouldBeRoleHolder) {
            assertThat(packageNames).contains(packageName);
        } else {
            assertThat(packageNames).doesNotContain(packageName);
        }
     }

    private void addRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.addRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), sContext.getMainExecutor(), future));
        future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void removeRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.removeRoleHolderAsUser(roleName,
                packageName, 0, Process.myUserHandle(), sContext.getMainExecutor(), future));
        future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void clearRoleHolders(@NonNull String roleName) throws Exception {
        CallbackFuture future = new CallbackFuture();
        runWithShellPermissionIdentity(() -> sRoleManager.clearRoleHoldersAsUser(roleName, 0,
                Process.myUserHandle(), sContext.getMainExecutor(), future));
        future.get(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static class ListenerFuture extends CompletableFuture<Pair<String, UserHandle>>
            implements OnRoleHoldersChangedListener {

        @Override
        public void onRoleHoldersChanged(@NonNull String roleName, @NonNull UserHandle user) {
            complete(new Pair<>(roleName, user));
        }
    }

    private static class CallbackFuture extends CompletableFuture<Void>
            implements Consumer<Boolean> {

        @Override
        public void accept(Boolean successful) {
            if (successful) {
                complete(null);
            } else {
                completeExceptionally(new RuntimeException());
            }
        }
    }
}
