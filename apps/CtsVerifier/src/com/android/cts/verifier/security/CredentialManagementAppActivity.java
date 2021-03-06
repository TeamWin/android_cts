/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.cts.verifier.security;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.security.AppUriAuthenticationPolicy;
import android.security.KeyChain;

import com.android.cts.verifier.ArrayTestListAdapter;
import com.android.cts.verifier.DialogTestListActivity;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestResult;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * CTS verifier test for credential management on unmanaged device.
 *
 * This activity is responsible for starting the credential management app flow. It performs the
 * following verifications:
 *  Can successfully request to become the Credential management app
 *  The credential management app is correctly set
 *  The authentication policy is correctly set
 */
public class CredentialManagementAppActivity extends DialogTestListActivity {

    private static final String TAG = "CredentialManagementAppActivity";

    private static final int REQUEST_MANAGE_CREDENTIALS_STATUS = 0;

    private static final String TEST_APP_PACKAGE_NAME = "com.test.pkg";
    private static final Uri TEST_URI = Uri.parse("https://test.com");
    private static final String TEST_ALIAS = "testAlias";
    private static final AppUriAuthenticationPolicy AUTHENTICATION_POLICY =
            new AppUriAuthenticationPolicy.Builder()
                    .addAppAndUriMapping(TEST_APP_PACKAGE_NAME, TEST_URI, TEST_ALIAS)
                    .build();

    private DialogTestListItem mRequestManageCredentials;
    private DialogTestListItem mCheckIsCredentialManagementApp;
    private DialogTestListItem mCheckAuthenticationPolicy;

    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    public CredentialManagementAppActivity() {
        super(R.layout.credential_management_app_test,
                R.string.credential_management_app_test,
                R.string.credential_management_app_info,
                R.string.credential_management_app_info);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void setupTests(final ArrayTestListAdapter testAdapter) {
        mRequestManageCredentials = new DialogTestListItem(this,
                R.string.request_manage_credentials,
                "request_manage_credentials") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                Intent intent = KeyChain.createManageCredentialsIntent(AUTHENTICATION_POLICY);
                startActivityForResult(intent, REQUEST_MANAGE_CREDENTIALS_STATUS);
            }
        };
        testAdapter.add(mRequestManageCredentials);
        mCheckIsCredentialManagementApp = new DialogTestListItem(this,
                R.string.is_credential_management_app,
                "is_credential_management_app") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkIsCredentialManagementApp();
            }
        };
        testAdapter.add(mCheckIsCredentialManagementApp);
        mCheckAuthenticationPolicy = new DialogTestListItem(this,
                R.string.credential_management_app_policy,
                "credential_management_app_policy") {
            @Override
            public void performTest(DialogTestListActivity activity) {
                checkAuthenticationPolicy();
            }
        };
        testAdapter.add(mCheckAuthenticationPolicy);
    }

    private void checkIsCredentialManagementApp() {
        mExecutor.execute(() -> {
            final boolean isCredMngApp =
                    KeyChain.isCredentialManagementApp(getApplicationContext());
            mHandler.post(() -> setResult(mCheckIsCredentialManagementApp, isCredMngApp));
        });
    }

    private void checkAuthenticationPolicy() {
        mExecutor.execute(() -> {
            final AppUriAuthenticationPolicy authenticationPolicy =
                    KeyChain.getCredentialManagementAppPolicy(getApplicationContext());
            mHandler.post(() -> setResult(mCheckAuthenticationPolicy,
                    authenticationPolicy.equals(AUTHENTICATION_POLICY)));
        });
    }

    private void setResult(DialogTestListItem testListItem, boolean passed) {
        if (passed) {
            setTestResult(testListItem, TestResult.TEST_RESULT_PASSED);
        } else {
            setTestResult(testListItem, TestResult.TEST_RESULT_FAILED);
        }
    }

    @Override
    protected void handleActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_MANAGE_CREDENTIALS_STATUS:
                setResult(mRequestManageCredentials, resultCode == RESULT_OK);
                break;
            default:
                super.handleActivityResult(requestCode, resultCode, data);
        }
    }
}
