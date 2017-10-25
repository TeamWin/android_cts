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
 * limitations under the License.
 */

package com.android.cts.verifier.managedprovisioning;

import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_HOME;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NONE;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_RECENTS;
import static android.app.admin.DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO;

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.Toast;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

/**
 * Tests for {@link DevicePolicyManager#setLockTaskFeatures(ComponentName, int)}.
 */
public class LockTaskUiTestActivity extends PassFailButtons.TestListActivity {

    public static final String EXTRA_TEST_ID =
            "com.android.cts.verifier.managedprovisioning.extra.TEST_ID";

    private static final String TAG = LockTaskUiTestActivity.class.getSimpleName();

    private static final ComponentName ADMIN_RECEIVER =
            DeviceAdminTestReceiver.getReceiverComponentName();
    private static final String TEST_PACKAGE_NAME = "com.android.cts.verifier";
    private static final String ACTION_STOP_LOCK_TASK =
            "com.android.cts.verifier.managedprovisioning.action.STOP_LOCK_TASK";

    private static final String TEST_ID_DEFAULT = "lock-task-ui-default";
    private static final String TEST_ID_SYSTEM_INFO = "lock-task-ui-system-info";
    private static final String TEST_ID_NOTIFICATIONS = "lock-task-ui-notifications";
    private static final String TEST_ID_HOME = "lock-task-ui-home";
    private static final String TEST_ID_RECENTS = "lock-task-ui-recents";
    private static final String TEST_ID_GLOBAL_ACTIONS = "lock-task-ui-global-actions";
    private static final String TEST_ID_KEYGUARD = "lock-task-ui-keyguard";
    private static final String TEST_ID_STOP_LOCK_TASK = "lock-task-ui-stop-lock-task";

    private DevicePolicyManager mDpm;
    private ActivityManager mAm;
    private NotificationManager mNotifyMgr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.device_owner_lock_task_ui);
        setPassFailButtonClickListeners();

        mDpm = getSystemService(DevicePolicyManager.class);
        mAm = getSystemService(ActivityManager.class);
        mNotifyMgr = getSystemService(NotificationManager.class);

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        addTestsToAdapter(adapter);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });
        setTestListAdapter(adapter);

        Button startLockTaskButton = findViewById(R.id.start_lock_task_button);
        startLockTaskButton.setOnClickListener((view) -> startLockTaskMode());
    }

    @Override
    protected void onStart() {
        super.onStart();
        final String action = getIntent().getAction();
        if (ACTION_STOP_LOCK_TASK.equals(action)) {
            stopLockTaskMode();
            finish();
        }
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createInteractiveTestItem(this,
                TEST_ID_DEFAULT,
                R.string.device_owner_lock_task_ui_default_test,
                R.string.device_owner_lock_task_ui_default_test_info,
                new ButtonInfo[]{}));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_SYSTEM_INFO,
                LOCK_TASK_FEATURE_SYSTEM_INFO,
                R.string.device_owner_lock_task_ui_system_info_test,
                R.string.device_owner_lock_task_ui_system_info_test_info));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_NOTIFICATIONS,
                LOCK_TASK_FEATURE_NOTIFICATIONS,
                R.string.device_owner_lock_task_ui_notifications_test,
                R.string.device_owner_lock_task_ui_notifications_test_info));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_HOME,
                LOCK_TASK_FEATURE_HOME,
                R.string.device_owner_lock_task_ui_home_test,
                R.string.device_owner_lock_task_ui_home_test_info));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_RECENTS,
                LOCK_TASK_FEATURE_RECENTS,
                R.string.device_owner_lock_task_ui_recents_test,
                R.string.device_owner_lock_task_ui_recents_test_info));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_GLOBAL_ACTIONS,
                LOCK_TASK_FEATURE_GLOBAL_ACTIONS,
                R.string.device_owner_lock_task_ui_global_actions_test,
                R.string.device_owner_lock_task_ui_global_actions_test_info));

        adapter.add(createSetLockTaskFeaturesTest(
                TEST_ID_KEYGUARD,
                LOCK_TASK_FEATURE_KEYGUARD,
                R.string.device_owner_lock_task_ui_keyguard_test,
                R.string.device_owner_lock_task_ui_keyguard_test_info));

        final Intent stopLockTaskIntent = new Intent(this, LockTaskUiTestActivity.class);
        stopLockTaskIntent.setAction(ACTION_STOP_LOCK_TASK);
        adapter.add(createInteractiveTestItem(this,
                TEST_ID_STOP_LOCK_TASK,
                R.string.device_owner_lock_task_ui_stop_lock_task_test,
                R.string.device_owner_lock_task_ui_stop_lock_task_test_info,
                new ButtonInfo(
                        R.string.device_owner_lock_task_ui_stop_lock_task_test,
                        stopLockTaskIntent
                )));
    }

    private void startLockTaskMode() {
        mDpm.setLockTaskPackages(ADMIN_RECEIVER, new String[] {TEST_PACKAGE_NAME});
        mDpm.setLockTaskFeatures(ADMIN_RECEIVER, LOCK_TASK_FEATURE_NONE);
        if (mAm.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_LOCKED) {
            startLockTask();
            if (mAm.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_LOCKED) {
                Log.e(TAG, "Failed to start LockTask mode");
                Toast.makeText(this, "Failed to start LockTask mode", Toast.LENGTH_SHORT).show();
            }
        }
        issueTestNotification();
    }

    private void stopLockTaskMode() {
        cancelTestNotification();
        if (mAm.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
            stopLockTask();
            if (mAm.getLockTaskModeState() != ActivityManager.LOCK_TASK_MODE_NONE) {
                Log.e(TAG, "Failed to stop LockTask mode");
                Toast.makeText(this, "Failed to stop LockTask mode", Toast.LENGTH_SHORT).show();
            }
        }
        mDpm.setLockTaskFeatures(ADMIN_RECEIVER, LOCK_TASK_FEATURE_NONE);
        mDpm.setLockTaskPackages(ADMIN_RECEIVER, new String[] {});
    }

    private void issueTestNotification() {
        String channelId = getTestId();
        if (mNotifyMgr.getNotificationChannel(channelId) == null) {
            NotificationChannel channel = new NotificationChannel(
                    channelId, getTestId(), NotificationManager.IMPORTANCE_HIGH);
            mNotifyMgr.createNotificationChannel(channel);
        }

        Notification note = new Notification.Builder(this, channelId)
                .setContentTitle(getString(R.string.device_owner_lock_task_ui_test))
                .setSmallIcon(android.R.drawable.sym_def_app_icon)
                .setOngoing(true)
                .build();

        mNotifyMgr.notify(0, note);
    }

    private void cancelTestNotification() {
        mNotifyMgr.cancelAll();
    }

    private TestListItem createSetLockTaskFeaturesTest(String testId, int featureFlags,
            int titleResId, int detailResId) {
        final Intent commandIntent = new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND);
        commandIntent.putExtra(CommandReceiverActivity.EXTRA_COMMAND,
                CommandReceiverActivity.COMMAND_SET_LOCK_TASK_FEATURES);
        commandIntent.putExtra(CommandReceiverActivity.EXTRA_VALUE, featureFlags);

        return createInteractiveTestItem(this, testId, titleResId, detailResId,
                new ButtonInfo(titleResId, commandIntent));
    }

    @Override
    public String getTestId() {
        return getIntent().getStringExtra(EXTRA_TEST_ID);
    }
}
