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
import android.provider.Settings;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.IntentDrivenTestActivity.ButtonInfo;
import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListAdapter.TestListItem;

import static com.android.cts.verifier.managedprovisioning.Utils.createInteractiveTestItem;

/**
 * Test class to verify privacy information is shown for devices managed by a Device Owner.
 */
public class EnterprisePrivacyTestListActivity extends PassFailButtons.TestListActivity {

    private static final String ENTERPRISE_PRIVACY_PAGE = "ENTERPRISE_PRIVACY_PAGE";
    private static final String ENTERPRISE_PRIVACY_NETWORK_LOGGING
            = "ENTERPRISE_PRIVACY_NETWORK_LOGGING";
    private static final String ENTERPRISE_PRIVACY_BUG_REPORT = "ENTERPRISE_PRIVACY_BUG_REPORT";
    private static final String ENTERPRISE_PRIVACY_SECURITY_LOGGING
            = "ENTERPRISE_PRIVACY_SECURITY_LOGGING";
    private static final String ENTERPRISE_PRIVACY_QUICK_SETTINGS
            = "ENTERPRISE_PRIVACY_QUICK_SETTINGS";
    private static final String ENTERPRISE_PRIVACY_KEYGUARD = "ENTERPRISE_PRIVACY_KEYGUARD";

    public static final String EXTRA_TEST_ID =
            "com.android.cts.verifier.managedprovisioning.extra.TEST_ID";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.pass_fail_list);
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

    private Intent buildCommandIntent(String command) {
        return new Intent(CommandReceiverActivity.ACTION_EXECUTE_COMMAND)
                .putExtra(CommandReceiverActivity.EXTRA_COMMAND, command);
    }

    private TestListItem buildCommandTest(String id, int titleRes, int infoRes,
            int commandButtonRes, String command) {
        return createInteractiveTestItem(this, id, titleRes, infoRes,
                new ButtonInfo[] {
                        new ButtonInfo(commandButtonRes, buildCommandIntent(command)),
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                               new Intent(Settings.ACTION_ENTERPRISE_PRIVACY_SETTINGS))});
    }

    private void addTestsToAdapter(final ArrayTestListAdapter adapter) {
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_PAGE,
                R.string.enterprise_privacy_page,
                R.string.enterprise_privacy_page_info,
                new ButtonInfo(R.string.go_button_text, new Intent(Settings.ACTION_SETTINGS))));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_NETWORK_LOGGING,
                R.string.enterprise_privacy_network_logging,
                R.string.enterprise_privacy_network_logging_info,
                R.string.enterprise_privacy_retrieve_network_logs,
                CommandReceiverActivity.COMMAND_RETRIEVE_NETWORK_LOGS));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_BUG_REPORT,
                R.string.enterprise_privacy_bug_report,
                R.string.enterprise_privacy_bug_report_info,
                R.string.enterprise_privacy_request_bug_report,
                CommandReceiverActivity.COMMAND_REQUEST_BUGREPORT));
        adapter.add(buildCommandTest(ENTERPRISE_PRIVACY_SECURITY_LOGGING,
                R.string.enterprise_privacy_security_logging,
                R.string.enterprise_privacy_security_logging_info,
                R.string.enterprise_privacy_retrieve_security_logs,
                CommandReceiverActivity.COMMAND_RETRIEVE_SECURITY_LOGS));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_QUICK_SETTINGS,
                R.string.enterprise_privacy_quick_settings,
                R.string.enterprise_privacy_quick_settings_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_clear_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)),
                        new ButtonInfo(R.string.enterprise_privacy_set_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)
                                        .putExtra(CommandReceiverActivity.EXTRA_ORGANIZATION_NAME,
                                                "Foo, Inc."))}));
        adapter.add(createInteractiveTestItem(this, ENTERPRISE_PRIVACY_KEYGUARD,
                R.string.enterprise_privacy_keyguard,
                R.string.enterprise_privacy_keyguard_info,
                new ButtonInfo[] {
                        new ButtonInfo(R.string.enterprise_privacy_open_settings,
                                new Intent(Settings.ACTION_SETTINGS)),
                        new ButtonInfo(R.string.enterprise_privacy_clear_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)),
                        new ButtonInfo(R.string.enterprise_privacy_set_organization,
                                buildCommandIntent(
                                        CommandReceiverActivity.COMMAND_SET_ORGANIZATION_NAME)
                                        .putExtra(CommandReceiverActivity.EXTRA_ORGANIZATION_NAME,
                                                "Foo, Inc."))}));
    }

    @Override
    public String getTestId() {
        return getIntent().getStringExtra(EXTRA_TEST_ID);
    }

    @Override
    public void finish() {
        super.finish();
        startActivity(buildCommandIntent(
                CommandReceiverActivity.COMMAND_DEVICE_OWNER_CLEAR_POLICIES));
    }
}
