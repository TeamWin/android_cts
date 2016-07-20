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

package android.accounts.cts;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.Fixtures;
import android.os.Bundle;
import android.test.AndroidTestCase;

import java.io.IOException;

/**
 * Tests for AccountManager and AbstractAccountAuthenticator. This is to test
 * default implementation of account session api in
 * {@link android.accounts.AbstractAccountAuthenticator}.
 * <p>
 * You can run those unit tests with the following command line:
 * <p>
 *  adb shell am instrument
 *   -e debug false -w
 *   -e class android.accounts.cts.AbstractAuthenticatorTests
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AbstractAuthenticatorTests extends AndroidTestCase {

    private AccountManager mAccountManager;

    @Override
    public void setUp() throws Exception {
        // bind to the diagnostic service and set it up.
        mAccountManager = AccountManager.get(getContext());
    }

    /**
     * Tests startAddAccountSession default implementation. An encrypted session
     * bundle should always be returned without password or status token.
     */
    public void testStartAddAccountSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_DEFAULT,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();

        // Validate that auth token was stripped from result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate that no password nor status token is returned in the result
        // for default implementation.
        validateNullPasswordAndStatusToken(result);

        Bundle sessionBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        // Validate session bundle is returned but data in the bundle is
        // encrypted and hence not visible.
        assertNotNull(sessionBundle);
        assertNull(sessionBundle.getString(AccountManager.KEY_ACCOUNT_TYPE));
    }


    /**
     * Tests startUpdateCredentialsSession default implementation. An encrypted session
     * bundle should always be returned without password or status token.
     */
    public void testStartUpdateCredentialsSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_DEFAULT,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate no auth token in result.
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));

        // Validate that no password nor status token is returned in the result
        // for default implementation.
        validateNullPasswordAndStatusToken(result);

        Bundle sessionBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        // Validate session bundle is returned but data in the bundle is
        // encrypted and hence not visible.
        assertNotNull(sessionBundle);
        assertNull(sessionBundle.getString(Fixtures.KEY_ACCOUNT_NAME));
    }

    private void validateNullPasswordAndStatusToken(Bundle result) {
        assertNull(result.getString(AccountManager.KEY_PASSWORD));
        assertNull(result.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
    }
}
