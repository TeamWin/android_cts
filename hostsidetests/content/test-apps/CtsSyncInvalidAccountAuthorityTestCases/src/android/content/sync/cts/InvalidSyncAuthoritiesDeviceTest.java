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

package android.content.sync.cts;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.content.Context;
import android.test.InstrumentationTestCase;

/**
 * Device side code for {@link android.content.cts.InvalidSyncAuthoritiesHostTest}
 */
public class InvalidSyncAuthoritiesDeviceTest extends InstrumentationTestCase {

    private static final String VALID_TEST_AUTHORITY = "android.content.sync.cts.authority";
    private static final String INVALID_TEST_AUTHORITY = "invalid.authority";
    private static final String VALID_TEST_ACCOUNT_TYPE = "android.content.sync.cts.accounttype";

    private Account mInvalidAccount;
    private Account mValidAccount;
    private AccountManager mAccountManager;

    @Override
    public void setUp() {
        final Context context = getInstrumentation().getTargetContext();
        mAccountManager = (AccountManager) context.getSystemService(Context.ACCOUNT_SERVICE);
        mInvalidAccount = new Account("invalid_test_name", "invalid_test_type");
        final Account[] accounts = mAccountManager.getAccountsByType(VALID_TEST_ACCOUNT_TYPE);
        mValidAccount = (accounts.length == 0) ? createTestAccount() : accounts[0];
    }

    private Account createTestAccount() {
        mValidAccount = new Account("testAccount", VALID_TEST_ACCOUNT_TYPE);
        assertTrue("Failed to create a valid test account",
                mAccountManager.addAccountExplicitly(mValidAccount, "password", null));
        return mValidAccount;
    }

    public void test_populateAndTestSyncAutomaticallyBeforeReboot() {
        ContentResolver.setSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mInvalidAccount, INVALID_TEST_AUTHORITY, true);
        ContentResolver.setSyncAutomatically(mInvalidAccount, VALID_TEST_AUTHORITY, true);

        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY));
        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY));
        // checking for invalid accounts may already return false depending on when the broadcast
        // LOGIN_ACCOUNTS_CHANGED_ACTION was received by SyncManager
    }

    public void testSyncAutomaticallyAfterReboot() {
        assertTrue(ContentResolver.getSyncAutomatically(mValidAccount, VALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mValidAccount, INVALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mInvalidAccount, VALID_TEST_AUTHORITY));
        assertFalse(ContentResolver.getSyncAutomatically(mInvalidAccount, INVALID_TEST_AUTHORITY));
    }
}
