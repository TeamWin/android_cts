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

package com.android.cts.verifier.managedprovisioning;

import android.app.admin.DevicePolicyManager;
import android.content.Intent;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.os.UserManager;
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;
import com.android.cts.verifier.managedprovisioning.DeviceOwnerPositiveTestActivity.CommandReceiver;

/**
 * Test class to verify policy transparency on certain device owner restrictions.
 */
public class PolicyTransparencyActivity extends PassFailButtons.TestListActivity {
    private static final String KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS_ID =
            "KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS";
    private static final String SET_PASSWORD_QUALITY_ID = "SET_PASSWORD_QUALITY";
    private static final String SET_MAXIMUM_TIME_TO_LOCK_ID =
            SetMaximumTimeToLockActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
        setInfoResources(R.string.device_owner_policy_transparency_test,
                R.string.device_owner_policy_transparency_test_instructions, 0);
        setPassFailButtonClickListeners();

        final ArrayTestListAdapter adapter = new ArrayTestListAdapter(this);
        addTestsToAdapter(adapter);
        adapter.registerDataSetObserver(new DataSetObserver() {
            @Override
            public void onChanged() {
                updatePassButton();
            }
        });

        setTestListAdapter(adapter);
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createSetUserRestrictionButtonInteractiveTestItem(UserManager.DISALLOW_ADD_USER,
                R.string.disallow_add_user,
                R.string.disallow_add_user_instructions,
                UserManager.DISALLOW_ADD_USER));
        adapter.add(createSetUserRestrictionButtonInteractiveTestItem(
                UserManager.DISALLOW_ADJUST_VOLUME,
                R.string.disallow_adjust_volume,
                R.string.disallow_adjust_volume_instructions,
                UserManager.DISALLOW_ADJUST_VOLUME));
        adapter.add(createSetAndGoInteractiveTestItem(UserManager.DISALLOW_APPS_CONTROL,
                R.string.disallow_app_control,
                R.string.disallow_app_control_instructions,
                UserManager.DISALLOW_APPS_CONTROL,
                new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        adapter.add(createSetAndGoInteractiveTestItem(UserManager.DISALLOW_CONFIG_WIFI,
                R.string.disallow_config_wifi,
                R.string.disallow_config_wifi_instructions,
                UserManager.DISALLOW_CONFIG_WIFI,
                new Intent(Settings.ACTION_WIFI_SETTINGS)));
        adapter.add(createSetAndGoInteractiveTestItem(UserManager.DISALLOW_FUN,
                R.string.disallow_fun,
                R.string.disallow_fun_instructions,
                UserManager.DISALLOW_FUN,
                new Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)));
        adapter.add(createSetAndGoInteractiveTestItem(UserManager.DISALLOW_MODIFY_ACCOUNTS,
                R.string.disallow_modify_accounts,
                R.string.disallow_modify_accounts_instructions,
                UserManager.DISALLOW_MODIFY_ACCOUNTS,
                new Intent(Settings.ACTION_SYNC_SETTINGS)));
        adapter.add(createSetAndGoInteractiveTestItem(UserManager.DISALLOW_SHARE_LOCATION,
                R.string.disallow_share_location,
                R.string.disallow_share_location_instructions,
                UserManager.DISALLOW_SHARE_LOCATION,
                new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        adapter.add(createSetUserRestrictionButtonInteractiveTestItem(
                UserManager.DISALLOW_USB_FILE_TRANSFER,
                R.string.disallow_usb_file_transfer,
                R.string.disallow_usb_file_transfer_instructions,
                UserManager.DISALLOW_USB_FILE_TRANSFER));
        adapter.add(Utils.createInteractiveTestItem(this,
                KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS_ID,
                R.string.keyguard_disable_unredacted_notifications,
                R.string.keyguard_disable_unredacted_notifications_instructions,
                new ButtonInfo(R.string.device_owner_user_restriction_set,
                        createDeviceOwnerIntentWithIntParameter(
                                DeviceOwnerPositiveTestActivity.
                                        COMMAND_SET_KEYGUARD_DISABLED_FEATURE,
                                DevicePolicyManager.KEYGUARD_DISABLE_UNREDACTED_NOTIFICATIONS))));
        adapter.add(DeviceOwnerPositiveTestActivity.createTestItem(this,
                SET_MAXIMUM_TIME_TO_LOCK_ID,
                R.string.maximum_time_to_lock,
                new Intent(this, SetMaximumTimeToLockActivity.class)));
        adapter.add(Utils.createInteractiveTestItem(this,
                SET_PASSWORD_QUALITY_ID,
                R.string.password_quality,
                R.string.password_quality_instructions,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.device_owner_user_restriction_set,
                                createDeviceOwnerIntentWithIntParameter(
                                        DeviceOwnerPositiveTestActivity.
                                                COMMAND_SET_PASSWORD_QUALITY,
                                        DevicePolicyManager.PASSWORD_QUALITY_COMPLEX)),
                        new ButtonInfo(R.string.device_owner_settings_go,
                                new Intent(Settings.ACTION_SECURITY_SETTINGS))}));
    }

    private Intent createSetUserRestrictionIntent(String restriction) {
        return new Intent(this, CommandReceiver.class)
                .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_COMMAND,
                        DeviceOwnerPositiveTestActivity.COMMAND_ADD_USER_RESTRICTION)
                .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_RESTRICTION, restriction);
    }

    private Intent createDeviceOwnerIntentWithIntParameter(String command, int value) {
        return new Intent(this, CommandReceiver.class)
                .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_COMMAND, command)
                .putExtra(DeviceOwnerPositiveTestActivity.EXTRA_PARAMETER_1, value);
    }

    private TestListItem createSetUserRestrictionButtonInteractiveTestItem(String id, int titleRes,
            int infoRes, String restriction) {
        return Utils.createInteractiveTestItem(this, id, titleRes, infoRes,
                new ButtonInfo(R.string.device_owner_user_restriction_set,
                        createSetUserRestrictionIntent(restriction)));
    }

    private TestListItem createSetAndGoInteractiveTestItem(String id, int titleRes, int infoRes,
            String restriction, Intent goIntent) {
        return Utils.createInteractiveTestItem(this, id, titleRes, infoRes,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.device_owner_user_restriction_set,
                                createSetUserRestrictionIntent(restriction)),
                        new ButtonInfo(R.string.device_owner_settings_go, goIntent)});
    }

}
