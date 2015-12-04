
package android.accounts.cts;

import android.accounts.AccountManager;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.accounts.cts.common.Fixtures;
import android.os.Bundle;
import android.os.RemoteException;
import android.test.AndroidTestCase;

import java.io.IOException;

/**
 * Tests for AccountManager and AbstractAccountAuthenticator related behavior
 * using {@link android.accounts.cts.common.CustomTestAccountAuthenticator}
 * instances signed with different keys than the caller. This is important to
 * test that portion of the default implementation of the
 * {@link AccountManager#finishSession} API when implementers of
 * {@link android.accounts.AbstractAccountAuthenticator} override only
 * {@link AccountManager#startAddAccountSession} and/or
 * {@link AccountManager#startUpdateCredentialsSession} but not
 * {@link AccountManager#finishSession}.
 * <p>
 * You can run those unit tests with the following command line:
 * <p>
 * adb shell am instrument -e debug false -w -e class
 * android.accounts.cts.AccountManagerUnaffiliatedCustomAuthenticatorTests
 * android.accounts.cts/android.support.test.runner.AndroidJUnitRunner
 */
public class AccountManagerUnaffliatedCustomAuthenticatorTests extends AndroidTestCase {

    private AccountManager mAccountManager;

    @Override
    public void setUp() throws Exception {
        // bind to the diagnostic service and set it up.
        mAccountManager = AccountManager.get(getContext());
    }

    /**
     * Tests finishSession default implementation with custom
     * startAddAccountSession implementation. AuthenticatorException is expected
     * because default implementation cannot understand custom session bundle.
     */
    public void testFinishSessiontWithCustomStartAddAccountSessionImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        // Creates session bundle to be returned by custom implementation of
        // startAddAccountSession of authenticator.
        Bundle sessionBundle = new Bundle();
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, Fixtures.TYPE_CUSTOM_UNAFFILIATED);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);

        // First get an encrypted session bundle from custom startAddAccountSession implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startAddAccountSession(
                Fixtures.TYPE_CUSTOM_UNAFFILIATED,
                null /* authTokenType */,
                null /* requiredFeatures */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        Bundle decryptedBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(decryptedBundle);

        try {
            // Call default implementation of finishSession of authenticator
            // with encrypted session bundle.
            future = mAccountManager.finishSession(
                    decryptedBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            future.getResult();

            fail("Should have thrown AuthenticatorException if finishSession is not overridden.");
        } catch (AuthenticatorException e) {
        }
    }

    /**
     * Tests finishSession default implementation with custom
     * startUpdateCredentialsSession implementation. AuthenticatorException is expected
     * because default implementation cannot understand custom session bundle.
     */
    public void testFinishSessionWithCustomStartUpdateCredentialsSessionImpl()
            throws OperationCanceledException, AuthenticatorException, IOException {
        String accountName = Fixtures.PREFIX_NAME_SUCCESS + "@" + Fixtures.SUFFIX_NAME_FIXTURE;
        // Creates session bundle to be returned by custom implementation of
        // startUpdateCredentialsSession of authenticator.
        Bundle sessionBundle = new Bundle();
        sessionBundle.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        sessionBundle.putString(AccountManager.KEY_ACCOUNT_TYPE, Fixtures.TYPE_CUSTOM_UNAFFILIATED);
        Bundle options = new Bundle();
        options.putString(Fixtures.KEY_ACCOUNT_NAME, accountName);
        options.putBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);

        // First get an encrypted session bundle from custom
        // startUpdateCredentialsSession implementation.
        AccountManagerFuture<Bundle> future = mAccountManager.startUpdateCredentialsSession(
                Fixtures.ACCOUNT_CUSTOM_UNAFFILIATED_FIXTURE_SUCCESS,
                null /* authTokenType */,
                options,
                null /* activity */,
                null /* callback */,
                null /* handler */);

        Bundle result = future.getResult();
        assertTrue(future.isDone());
        assertNotNull(result);

        Bundle decryptedBundle = result.getBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE);
        assertNotNull(decryptedBundle);

        try {
            // Call default implementation of finishSession of authenticator
            // with encrypted session bundle.
            future = mAccountManager.finishSession(
                    decryptedBundle,
                    null /* activity */,
                    null /* callback */,
                    null /* handler */);
            future.getResult();

            fail("Should have thrown AuthenticatorException if finishSession is not overridden.");
        } catch (AuthenticatorException e) {
        }
    }
}
