/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.car.cts.builtin.app;

import static android.car.builtin.app.ActivityManagerHelper.ProcessObserverCallback;

import static com.google.common.truth.Truth.assertThat;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.car.builtin.app.ActivityManagerHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class ActivityManagerHelperTest {

    private static final String TAG = ActivityManagerHelperTest.class.getSimpleName();

    private static final String PERMISSION_SET_ACTIVITY_WATCHER =
            "android.permission.SET_ACTIVITY_WATCHER";
    private static final String NOT_REQUESTED_PERMISSION_CAR_MILEAGE =
            "android.car.permission.CAR_MILEAGE";
    private static final String NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY =
            "android.car.permission.READ_CAR_POWER_POLICY";

    private static final String GRANTED_PERMISSION_INTERACT_ACROSS_USERS =
            "android.permission.INTERACT_ACROSS_USERS";

    private static final String SIMPLE_APP_PACKAGE_NAME = "android.car.cts.builtin.apps.simple";
    private static final String SIMPLE_ACTIVITY_NAME = "SimpleActivity";
    private static final String START_SIMPLE_ACTIVITY_COMMAND = "am start -W -n "
            + SIMPLE_APP_PACKAGE_NAME + "/." + SIMPLE_ACTIVITY_NAME;

    private static final int OWNING_UID = -1;
    private static final int MAX_NUM_TASKS = 10_000;
    private static final long TIMEOUT_MS = 20_000;

    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    @Test
    public void testCheckComponentPermission() throws Exception {
        // not requested from Manifest
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_CAR_MILEAGE);
        assertComponentPermissionNotGranted(NOT_REQUESTED_PERMISSION_READ_CAR_POWER_POLICY);

        // requested from Manifest and granted
        assertComponentPermissionGranted(GRANTED_PERMISSION_INTERACT_ACROSS_USERS);
    }

    @Test
    public void testSetFocusedRootTask() throws Exception {
        //TODO(b/201005730): implement the test case to test setFocusedRootTask API.
    }

    @Test
    public void testRemoveTask() throws Exception {
        //TODO(b/201005730): implement the test case to test setRemoveTask API.
    }

    @Test
    public void testProcessObserverCallback() throws Exception {
        // setup
        ProcessObserverCallbackTestImpl callbackImpl = new ProcessObserverCallbackTestImpl();
        launchSimpleActivity();

        // execute
        try {
            mInstrumentation.getUiAutomation().adoptShellPermissionIdentity(
                        PERMISSION_SET_ACTIVITY_WATCHER);

            ActivityManagerHelper.registerProcessObserverCallback(callbackImpl);

            ArrayList<Integer> appTasks = getAppTasks(SIMPLE_APP_PACKAGE_NAME);
            appTasks.forEach((taskId) -> ActivityManagerHelper.removeTask(taskId));

            // assert
            assertThat(callbackImpl.isProcessDiedObserved()).isTrue();
        } finally {
            // teardown
            ActivityManagerHelper.unregisterProcessObserverCallback(callbackImpl);
            mInstrumentation.getUiAutomation().dropShellPermissionIdentity();
        }
    }

    private void assertComponentPermissionGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    private void assertComponentPermissionNotGranted(String permission) throws Exception {
        assertThat(ActivityManagerHelper.checkComponentPermission(permission,
                Process.myUid(), /* owningUid= */ OWNING_UID, /* exported= */ true))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
    }

    private static final class ProcessObserverCallbackTestImpl extends ProcessObserverCallback {
        private final CountDownLatch mLatch = new CountDownLatch(1);

        private int mObservedPid = -1;
        private int mObservedUid = -1;
        private boolean mProcessDiedObserved;

        @Override
        public void onProcessDied(int pid, int uid) {
            mProcessDiedObserved = true;
            Log.d(TAG, "ProcessDied: pid " + pid + " uid " + uid);
            mLatch.countDown();
        }

        public boolean isProcessDiedObserved() throws Exception {
            if (!mLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                throw new Exception("process died is not observed in " + TIMEOUT_MS + " ms)");
            }
            return mProcessDiedObserved;
        }
    }

    private void launchSimpleActivity() {
        SystemUtil.runShellCommand(START_SIMPLE_ACTIVITY_COMMAND);
    }

    private ArrayList<Integer> getAppTasks(String pkgName) {
        ActivityManager am = mContext.getSystemService(ActivityManager.class);

        List<ActivityManager.RunningTaskInfo> runningTasks = am.getRunningTasks(MAX_NUM_TASKS);
        ArrayList<Integer> appTasks = new ArrayList<>();
        for (ActivityManager.RunningTaskInfo taskInfo : runningTasks) {
            String taskPackageName = taskInfo.baseActivity.getPackageName();
            int taskId = taskInfo.taskId;
            Log.d(TAG, "tasks package name: " + taskPackageName);
            if (taskPackageName.equals(pkgName)) {
                Log.d(TAG, "getAppTask(): adding " + SIMPLE_APP_PACKAGE_NAME + " task " + taskId);
                appTasks.add(taskId);
            }
        }

        return appTasks;
    }
}
