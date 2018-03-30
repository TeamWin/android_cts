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

package com.android.cts;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;

public class BinderPermissionTestService extends Service {

    // Note: keep these constants in sync with GRANTED_PERMISSION and NOT_GRANTED_PERMISSION in
    // ContextTest.
    //
    // A permission that's granted to the content test package (ContextTest).
    public static final String CALLER_GRANTED_PERMISSION = "android.permission.USE_CREDENTIALS";
    // A permission that's not granted to the content test package (ContextTest).
    public static final String CALLER_NOT_GRANTED_PERMISSION = "android.permission.HARDWARE_TEST";

    private static String TEST_NOT_ALLOWED_MESSAGE = "Test: you're not allowed to do this.";

    private final IBinder mBinder = new IBinderPermissionTestService.Stub() {
        @Override
        public void callEnforceCallingPermissionGranted() {
            enforceCallingPermission(CALLER_GRANTED_PERMISSION, TEST_NOT_ALLOWED_MESSAGE);
        }

        @Override
        public void callEnforceCallingPermissionNotGranted() {
            enforceCallingPermission(CALLER_NOT_GRANTED_PERMISSION, TEST_NOT_ALLOWED_MESSAGE);
        }

        @Override
        public int callCheckCallingPermissionGranted() {
            return checkCallingPermission(CALLER_GRANTED_PERMISSION);
        }

        @Override
        public int callCheckCallingPermissionNotGranted() {
            return checkCallingPermission(CALLER_NOT_GRANTED_PERMISSION);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }
}
