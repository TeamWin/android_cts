/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.AuthenticatorContentProvider;
import android.accounts.cts.common.Fixtures;
import android.accounts.cts.common.tx.AddAccountTx;
import android.accounts.cts.common.tx.UpdateCredentialsTx;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.os.Bundle;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import java.io.IOException;

/**
 * Tests for AccountManager and AbstractAccountAuthenticator related behavior using {@link
 * android.accounts.cts.common.TestAccountAuthenticator} instances signed with different keys than
 * the caller. This is important to test that portion of the {@link AccountManager} API intended
 * for {@link android.accounts.AbstractAccountAuthenticator} implementers.
 * <p>
 * You can run those unit tests with the following command line:
 * <p>
 *  adb shell am instrument
 *   -e debug false -w
 *   -e class android.accounts.cts.AccountManagerUnaffiliatedAuthenticatorTests
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AccountManagerUnaffiliatedAuthenticatorTests extends AndroidTestCase {

    private AccountManager mAccountManager;
    private ContentProviderClient mProviderClient;

    @Override
    public void setUp() throws Exception {
        // bind to the diagnostic service and set it up.
        mAccountManager = AccountManager.get(getContext());
        ContentResolver resolver = getContext().getContentResolver();
        mProviderClient = resolver.acquireContentProviderClient(
                AuthenticatorContentProvider.AUTHORITY);
        /*
         * This will install a bunch of accounts on the device
         * (see Fixtures.getFixtureAccountNames()).
         */
        mProviderClient.call(AuthenticatorContentProvider.METHOD_SETUP, null, null);
    }

    @Override
    public void tearDown() throws RemoteException {
        try {
            mProviderClient.call(AuthenticatorContentProvider.METHOD_TEARDOWN, null, null);
        } finally {
            mProviderClient.release();
        }
    }

    public void testNotifyAccountAuthenticated() {
        try {
            mAccountManager.notifyAccountAuthenticated(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("Expected to just barf if the caller doesn't share a signature.");
        } catch (SecurityException expected) {}
    }

    public void testEditProperties()  {
        try {
            mAccountManager.editProperties(
                    Fixtures.TYPE_STANDARD_UNAFFILIATED,
                    null, // activity
                    null, // callback
                    null); // handler
            fail("Expecting a OperationCanceledException.");
        } catch (SecurityException expected) {
            
        }
    }

    public void testAddAccountExplicitly() {
        try {
            mAccountManager.addAccountExplicitly(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "shouldn't matter", // password
                    null); // bundle
            fail("addAccountExplicitly should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccount_withBooleanResult() {
        try {
            mAccountManager.removeAccount(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    null,
                    null);
            fail("removeAccount should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccount_withBundleResult() {
        try {
            mAccountManager.removeAccount(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    null, // Activity
                    null,
                    null);
            fail("removeAccount should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testRemoveAccountExplicitly() {
        try {
            mAccountManager.removeAccountExplicitly(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("removeAccountExplicitly should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testGetPassword() {
        try {
            mAccountManager.getPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("getPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testSetPassword() {
        try {
            mAccountManager.setPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "Doesn't matter");
            fail("setPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testClearPassword() {
        try {
            mAccountManager.clearPassword(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS);
            fail("clearPassword should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testGetUserData() {
        try {
            mAccountManager.getUserData(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "key");
            fail("getUserData should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testSetUserData() {
        try {
            mAccountManager.setUserData(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "key",
                    "value");
            fail("setUserData should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void setAuthToken() {
        try {
            mAccountManager.setAuthToken(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "tokenType",
                    "token");
            fail("setAuthToken should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testPeekAuthToken() {
        try {
            mAccountManager.peekAuthToken(
                    Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                    "tokenType");
            fail("peekAuthToken should just barf if the caller isn't permitted.");
        } catch (SecurityException expected) {}
    }

    public void testGetAccounts() {
        Account[] accounts = mAccountManager.getAccounts();
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByType() {
        Account[] accounts = mAccountManager.getAccountsByType(
                Fixtures.TYPE_STANDARD_UNAFFILIATED);
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByTypeAndFeatures()
            throws OperationCanceledException, AuthenticatorException, IOException {
        AccountManagerFuture<Account[]> future = mAccountManager.getAccountsByTypeAndFeatures(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                new String[] { "doesn't matter" },
                null,  // Callback
                null);  // Handler
        Account[] accounts = future.getResult();
        assertEquals(0, accounts.length);
    }

    public void testGetAccountsByTypeForPackage() {
        Account[] accounts = mAccountManager.getAccountsByTypeForPackage(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                getContext().getPackageName());
        assertEquals(0, accounts.length);
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
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

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

    private void validateNullPasswordAndStatusToken(Bundle result) {
        assertNull(result.getString(AccountManager.KEY_PASSWORD));
        assertNull(result.getString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN));
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
                Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
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

    /**
     * Tests finishSession default implementation with default startAddAccountSession.
     * Only account name and account type should be returned as a bundle.
     */
    public void testFinishSessionAndStartAddAccountSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException,
            RemoteException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        // First obtain an encrypted session bundle from startAddAccountSession(...) default
        // implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_STANDARD_UNAFFILIATED,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Assert that result contains a non-null session bundle.
        Bundle escrowBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(escrowBundle);

        // Now call finishSession(...) with the session bundle we just obtained.
        future = mAccountManager.finishSession(
                escrowBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate that parameters are passed to addAccount(...) correctly in default finishSession
        // implementation.
        Bundle providerBundle = mProviderClient.call(
                AuthenticatorContentProvider.METHOD_GET,
                null /* arg */,
                null /* extras */);
        providerBundle.setClassLoader(AddAccountTx.class.getClassLoader());
        AddAccountTx addAccountTx = providerBundle
                .getParcelable(AuthenticatorContentProvider.KEY_TX);
        assertNotNull(addAccountTx);

        // Assert parameters has been passed to addAccount(...) correctly
        assertEquals(Fixtures.TYPE_STANDARD_UNAFFILIATED, addAccountTx.accountType);
        assertNull(addAccountTx.authTokenType);

        validateSystemOptions(addAccountTx.options);
        // Validate options
        assertNotNull(addAccountTx.options);
        assertEquals(accountName, addAccountTx.options.getString(Fixtures.KEY_ACCOUNT_NAME));
        // Validate features.
        assertEquals(0, addAccountTx.requiredFeatures.size());

        // Assert returned result contains correct account name, account type and null auth token.
        assertEquals(accountName, result.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(Fixtures.TYPE_STANDARD_UNAFFILIATED,
                result.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));
    }

    /**
     * Tests finishSession default implementation with default startAddAccountSession.
     * Only account name and account type should be returned as a bundle.
     */
    public void testFinishSessionAndStartUpdateCredentialsSessionDefaultImpl()
            throws OperationCanceledException, AuthenticatorException, IOException,
            RemoteException {
        Bundle options = new Bundle();
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);

        // First obtain an encrypted session bundle from startUpdateCredentialsSession(...) default
        // implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS,
                null /* authTokenTYpe */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Assert that result contains a non-null session bundle.
        Bundle escrowBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(escrowBundle);

        // Now call finishSession(...) with the session bundle we just obtained.
        future = mAccountManager.finishSession(
                escrowBundle,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        // Validate that parameters are passed to updateCredentials(...) correctly in default
        // finishSession implementation.
        Bundle providerBundle = mProviderClient.call(
                AuthenticatorContentProvider.METHOD_GET,
                null /* arg */,
                null /* extras */);
        providerBundle.setClassLoader(UpdateCredentialsTx.class.getClassLoader());
        UpdateCredentialsTx updateCredentialsTx = providerBundle
                .getParcelable(AuthenticatorContentProvider.KEY_TX);
        assertNotNull(updateCredentialsTx);

        // Assert parameters has been passed to updateCredentials(...) correctly
        assertEquals(Fixtures.ACCOUNT_UNAFFILIATED_FIXTURE_SUCCESS, updateCredentialsTx.account);
        assertNull(updateCredentialsTx.authTokenType);

        validateSystemOptions(updateCredentialsTx.options);
        // Validate options
        assertNotNull(updateCredentialsTx.options);
        assertEquals(accountName, updateCredentialsTx.options.getString(Fixtures.KEY_ACCOUNT_NAME));

        // Assert returned result contains correct account name, account type and null auth token.
        assertEquals(accountName, result.get(AccountManager.KEY_ACCOUNT_NAME));
        assertEquals(Fixtures.TYPE_STANDARD_UNAFFILIATED,
                result.get(AccountManager.KEY_ACCOUNT_TYPE));
        assertNull(result.get(AccountManager.KEY_AUTHTOKEN));
    }

    private void validateSystemOptions(Bundle options) {
        assertNotNull(options.getString(AccountManager.KEY_ANDROID_PACKAGE_NAME));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_UID));
        assertTrue(options.containsKey(AccountManager.KEY_CALLER_PID));
    }
}

