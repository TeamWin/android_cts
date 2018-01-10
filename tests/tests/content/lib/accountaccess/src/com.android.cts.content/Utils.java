package com.android.cts.content;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SyncRequest;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.uiautomator.UiDevice;
import android.util.Log;

import com.android.compatibility.common.util.SystemUtil;

import java.io.IOException;

public class Utils {
    public static final long SYNC_TIMEOUT_MILLIS = 20000; // 20 sec
    public static final String TOKEN_TYPE_REMOVE_ACCOUNTS = "TOKEN_TYPE_REMOVE_ACCOUNTS";
    private static final String LOG_TAG = Utils.class.getSimpleName();

    public static boolean hasDataConnection() {
        ConnectivityManager connectivityManager = getContext().getSystemService(
                ConnectivityManager.class);
        NetworkInfo activeNetwork = connectivityManager.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    public static boolean hasNotificationSupport() {
        return !getContext().getPackageManager()
                .hasSystemFeature(PackageManager.FEATURE_LEANBACK);
    }

    public static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    public static boolean isWatch() {
        return (getContext().getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_TYPE_MASK) == Configuration.UI_MODE_TYPE_WATCH;
    }

    public static UiDevice getUiDevice() {
        return UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
    }

    public static void waitForSyncManagerAccountChangeUpdate() {
        // Wait for the sync manager to be notified for the new account.
        // Unfortunately, there is no way to detect this event, sigh...
        SystemClock.sleep(SYNC_TIMEOUT_MILLIS);
    }

    public static void allowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist +" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy add restrict-background-whitelist " + Process.myUid());
    }

    public static  void disallowSyncAdapterRunInBackgroundAndDataInBackground() throws IOException {
        // Allow us to run in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd deviceidle whitelist -" + getContext().getPackageName());
        // Allow us to use data in the background
        SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                "cmd netpolicy remove restrict-background-whitelist " + Process.myUid());
    }

    public static AutoCloseable withAccount(@NonNull Activity activity)
            throws AuthenticatorException, OperationCanceledException, IOException {
        AccountManager accountManager = getContext().getSystemService(AccountManager.class);
        Bundle result = accountManager.addAccount("com.stub", null, null, null,
                activity, null, null).getResult();
        Account addedAccount = new Account(
                result.getString(AccountManager.KEY_ACCOUNT_NAME),
                result.getString(AccountManager.KEY_ACCOUNT_TYPE));
        Log.i(LOG_TAG, "Added account " + addedAccount);

        waitForSyncManagerAccountChangeUpdate();

        return () -> {
            // Ask the differently signed authenticator to drop all accounts
            accountManager.getAuthToken(addedAccount, TOKEN_TYPE_REMOVE_ACCOUNTS, null, false, null,
                    null);
        };
    }

    public static SyncRequest requestSync() {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_DO_NOT_RETRY, true);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_PRIORITY, true);
        extras.getBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
        SyncRequest request = new SyncRequest.Builder()
                .setSyncAdapter(null, "com.android.cts.stub.provider")
                .syncOnce()
                .setExtras(extras)
                .setExpedited(true)
                .setManual(true)
                .build();
        ContentResolver.requestSync(request);

        return request;
    }
}
