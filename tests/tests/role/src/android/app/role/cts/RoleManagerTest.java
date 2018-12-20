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

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.Instrumentation;
import android.app.role.RoleManager;
import android.app.role.RoleManagerCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.os.UserHandle;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiDevice;
import android.support.test.uiautomator.Until;
import android.util.Pair;

import androidx.annotation.NonNull;

import com.android.compatibility.common.util.AppOpsUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link RoleManager}.
 */
@RunWith(AndroidJUnit4.class)
public class RoleManagerTest {

    private static final long TIMEOUT_MILLIS = 15 * 1000;

    private static final String ROLE_NAME = RoleManager.ROLE_DIALER;

    private static final String APP_PACKAGE_NAME = "android.app.role.cts.app";
    private static final String APP_REQUEST_ROLE_ACTIVITY_NAME = APP_PACKAGE_NAME
            + ".RequestRoleActivity";
    private static final String APP_REQUEST_ROLE_EXTRA_ROLE_NAME = APP_PACKAGE_NAME
            + ".extra.ROLE_NAME";

    private static final Instrumentation sInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private static final Context sContext = InstrumentationRegistry.getTargetContext();
    private static final RoleManager sRoleManager = sContext.getSystemService(RoleManager.class);
    private static final UiDevice sUiDevice = UiDevice.getInstance(sInstrumentation);

    @Rule
    public ActivityTestRule<WaitForResultActivity> mActivityRule =
            new ActivityTestRule<>(WaitForResultActivity.class);

    // TODO: STOPSHIP: Remove once we automatically revoke role upon uninstallation.
    @Before
    @After
    public void removeRoleHolder() throws Exception {
        removeRoleHolder(ROLE_NAME, APP_PACKAGE_NAME);
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void roleIsAvailable() {
        assertThat(sRoleManager.isRoleAvailable(ROLE_NAME)).isTrue();
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
    public void requestRoleAndRejectThenIsNotRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(false);
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, false);
    }

    @Test
    public void requestRoleAndApproveThenIsRoleHolder() throws Exception {
        requestRole(ROLE_NAME);
        respondToRoleRequest(true);
        assertIsRoleHolder(ROLE_NAME, APP_PACKAGE_NAME, true);
    }

    @Test
    public void revokeSingleRoleThenEnsureOtherRolesAppopsIntact() throws Exception {
        addRoleHolder(RoleManager.ROLE_DIALER, APP_PACKAGE_NAME);
        addRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        removeRoleHolder(RoleManager.ROLE_SMS, APP_PACKAGE_NAME);
        assertEquals(AppOpsManager.MODE_ALLOWED,
                AppOpsUtils.getOpMode(APP_PACKAGE_NAME, AppOpsManager.OPSTR_SEND_SMS));
    }

    @Test
    public void migratedRoleHoldersNotEmpty() throws Exception {
        assertThat(getRoleHolders(RoleManager.ROLE_SMS)).isNotEmpty();
    }

    private void requestRole(@NonNull String roleName) {
        Intent intent = new Intent()
                .setComponent(new ComponentName(APP_PACKAGE_NAME, APP_REQUEST_ROLE_ACTIVITY_NAME))
                .putExtra(APP_REQUEST_ROLE_EXTRA_ROLE_NAME, roleName);
        mActivityRule.getActivity().startActivityToWaitForResult(intent);
    }

    private void respondToRoleRequest(boolean ok, boolean expectResultOk)
            throws InterruptedException, IOException {
        wakeUpScreen();
        String buttonId = ok ? "android:id/button1" : "android:id/button2";
        sUiDevice.wait(Until.findObject(By.res(buttonId)), TIMEOUT_MILLIS).click();
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

    private void assertIsRoleHolder(@NonNull String roleName, @NonNull String packageName,
            boolean shouldBeRoleHolder) throws Exception {
        List<String> packageNames = getRoleHolders(roleName);
        if (shouldBeRoleHolder) {
            assertThat(packageNames).contains(packageName);
        } else {
            assertThat(packageNames).doesNotContain(packageName);
        }
     }

    private List<String> getRoleHolders(@NonNull String roleName) throws Exception {
        return callWithShellPermissionIdentity(() -> sRoleManager.getRoleHolders(roleName));
    }

    private void addRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = sContext.getMainExecutor();
        boolean[] successful = new boolean[1];
        CountDownLatch latch = new CountDownLatch(1);
        runWithShellPermissionIdentity(() -> sRoleManager.addRoleHolderAsUser(roleName,
                packageName, user, executor, new RoleManagerCallback() {
                    @Override
                    public void onSuccess() {
                        successful[0] = true;
                        latch.countDown();
                    }
                    @Override
                    public void onFailure() {
                        successful[0] = false;
                        latch.countDown();
                    }
                }));
        latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(successful[0]).isTrue();
    }

    private void removeRoleHolder(@NonNull String roleName, @NonNull String packageName)
            throws Exception {
        UserHandle user = Process.myUserHandle();
        Executor executor = sContext.getMainExecutor();
        boolean[] successful = new boolean[1];
        CountDownLatch latch = new CountDownLatch(1);
        runWithShellPermissionIdentity(() -> sRoleManager.removeRoleHolderAsUser(roleName,
                packageName, user, executor, new RoleManagerCallback() {
                    @Override
                    public void onSuccess() {
                        successful[0] = true;
                        latch.countDown();
                    }
                    @Override
                    public void onFailure() {
                        successful[0] = false;
                        latch.countDown();
                    }
                }));
        latch.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        assertThat(successful[0]).isTrue();
    }
}
