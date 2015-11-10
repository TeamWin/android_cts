package android.accounts.cts.common;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Subclass of {@link TestAccountAuthenticator} with startAddAccountSession(...)
 * and startUpdateCredentialsSession(...) overridden but not finishSession(...)..
 */
public class CustomTestAccountAuthenticator extends TestAccountAuthenticator {

    private final AtomicInteger mTokenCounter = new AtomicInteger(0);
    private final String mAccountType;
    private final Context mContext;

    /**
     * @param context
     * @param accountType
     */
    public CustomTestAccountAuthenticator(Context context, String accountType) {
        super(context, accountType);
        mAccountType = accountType;
        mContext = context;
    }

    /**
     * Starts add account flow of the specified accountType to authenticate user.
     */
    @Override
    public Bundle startAddAccountSession(
            AccountAuthenticatorResponse response,
            String accountType,
            String authTokenType,
            String[] requiredFeatures,
            Bundle options) throws NetworkErrorException {

        if (!mAccountType.equals(accountType)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }

        String accountName = null;
        boolean isCallbackRequired = false;
        Bundle sessionBundle = null;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
            isCallbackRequired = options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED, false);
            sessionBundle = options.getBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE);
        }

        Bundle result = new Bundle();
        String statusToken = Fixtures.PREFIX_STATUS_TOKEN + accountName;
        String password = Fixtures.PREFIX_PASSWORD + accountName;
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
            result.putString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN, statusToken);
            result.putString(AccountManager.KEY_PASSWORD, password);
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    statusToken);
            eventualActivityResultData.putExtra(AccountManager.KEY_PASSWORD, password);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE,
                    sessionBundle);
            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            int errorCode = AccountManager.ERROR_CODE_INVALID_RESPONSE;
            String errorMsg = "Default Error Message";
            if (options != null) {
                errorCode = options.getInt(AccountManager.KEY_ERROR_CODE);
                errorMsg = options.getString(AccountManager.KEY_ERROR_MESSAGE);
            }
            result.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

    /**
     * Prompts user to re-authenticate to a specific account but defers updating local credentials.
     */
    @Override
    public Bundle startUpdateCredentialsSession(
            AccountAuthenticatorResponse response,
            Account account,
            String authTokenType,
            Bundle options) throws NetworkErrorException {

        if (!mAccountType.equals(account.type)) {
            throw new IllegalArgumentException("Request to the wrong authenticator!");
        }

        String accountName = null;
        boolean isCallbackRequired = false;
        Bundle sessionBundle = null;
        if (options != null) {
            accountName = options.getString(Fixtures.KEY_ACCOUNT_NAME);
            isCallbackRequired = options.getBoolean(Fixtures.KEY_CALLBACK_REQUIRED, false);
            sessionBundle = options.getBundle(Fixtures.KEY_ACCOUNT_SESSION_BUNDLE);
        }

        Bundle result = new Bundle();
        String statusToken = Fixtures.PREFIX_STATUS_TOKEN + accountName;
        String password = Fixtures.PREFIX_PASSWORD + accountName;
        if (accountName.startsWith(Fixtures.PREFIX_NAME_SUCCESS)) {
            // fill bundle with a success result.
            result.putBundle(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE, sessionBundle);
            result.putString(AccountManager.KEY_ACCOUNT_STATUS_TOKEN, statusToken);
            result.putString(AccountManager.KEY_PASSWORD, password);
            result.putString(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
        } else if (accountName.startsWith(Fixtures.PREFIX_NAME_INTERVENE)) {
            // Specify data to be returned by the eventual activity.
            Intent eventualActivityResultData = new Intent();
            eventualActivityResultData.putExtra(AccountManager.KEY_AUTHTOKEN,
                    Integer.toString(mTokenCounter.incrementAndGet()));
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_STATUS_TOKEN,
                    statusToken);
            eventualActivityResultData.putExtra(AccountManager.KEY_PASSWORD, password);
            eventualActivityResultData.putExtra(AccountManager.KEY_ACCOUNT_SESSION_BUNDLE,
                    sessionBundle);
            // Fill result with Intent.
            Intent intent = new Intent(mContext, TestAuthenticatorActivity.class);
            intent.putExtra(Fixtures.KEY_RESULT, eventualActivityResultData);
            intent.putExtra(Fixtures.KEY_CALLBACK, response);

            result.putParcelable(AccountManager.KEY_INTENT, intent);
        } else {
            // fill with error
            int errorCode = AccountManager.ERROR_CODE_INVALID_RESPONSE;
            String errorMsg = "Default Error Message";
            if (options != null) {
                errorCode = options.getInt(AccountManager.KEY_ERROR_CODE);
                errorMsg = options.getString(AccountManager.KEY_ERROR_MESSAGE);
            }
            result.putInt(AccountManager.KEY_ERROR_CODE, errorCode);
            result.putString(AccountManager.KEY_ERROR_MESSAGE, errorMsg);
        }

        try {
            return (isCallbackRequired) ? null : result;
        } finally {
            if (isCallbackRequired) {
                response.onResult(result);
            }
        }
    }

}
