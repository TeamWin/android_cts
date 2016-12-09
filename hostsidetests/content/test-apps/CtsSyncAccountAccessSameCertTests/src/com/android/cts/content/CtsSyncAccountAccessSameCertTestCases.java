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

package com.android.cts.content;

import static junit.framework.Assert.assertTrue;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentProviderClient;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SyncRequest;
import android.content.SyncResult;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests whether a sync adapter can access accounts.
 */
@RunWith(AndroidJUnit4.class)
public class CtsSyncAccountAccessSameCertTestCases {
    private static final long SYNC_TIMEOUT_MILLIS = 10000; // 20 sec

    @Test
    public void testAccountAccess_sameCertAsAuthenticatorCanSeeAccount() throws Exception {
        Intent intent = new Intent(getContext(), StubActivity.class);
        Activity activity = InstrumentationRegistry.getInstrumentation().startActivitySync(intent);

        AccountManager accountManager = getContext().getSystemService(AccountManager.class);
        Bundle result = accountManager.addAccount("com.stub", null, null, null, activity,
                null, null).getResult();

        Account addedAccount = new Account(
                result.getString(AccountManager.KEY_ACCOUNT_NAME),
                        result.getString(AccountManager.KEY_ACCOUNT_TYPE));

        try {
            CountDownLatch latch = new CountDownLatch(1);

            SyncAdapter.setOnPerformSyncDelegate((Account account, Bundle extras,
                    String authority, ContentProviderClient provider, SyncResult syncResult)
                    -> latch.countDown());

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

            assertTrue(latch.await(SYNC_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS));
        } finally {
            accountManager.removeAccount(addedAccount, activity, null, null);
        }
    }

    private Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }
}
