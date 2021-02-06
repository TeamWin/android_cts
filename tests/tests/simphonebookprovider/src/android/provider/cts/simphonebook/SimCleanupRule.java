/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.provider.cts.simphonebook;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.SimPhonebookContract;
import android.provider.SimPhonebookContract.ElementaryFiles;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import com.android.compatibility.common.util.SystemUtil;

import org.junit.rules.ExternalResource;

/** Removes all records from the SIM card after tests have run. */
class SimCleanupRule extends ExternalResource {
    private static final String TAG = SimCleanupRule.class.getSimpleName();
    private final ContentResolver mResolver;
    private final int mSubscriptionId;
    private final int mEfType;
    private final Bundle mExtras = new Bundle();

    SimCleanupRule(int efType) {
        this(efType, null);
    }

    SimCleanupRule(int efType, String pin2) {
        this(efType, pin2, SubscriptionManager.getDefaultSubscriptionId());
    }

    SimCleanupRule(int efType, String pin2, int subscriptionId) {
        Context context = ApplicationProvider.getApplicationContext();
        mResolver = context.getContentResolver();
        mEfType = efType;
        mExtras.putString(SimPhonebookContract.SimRecords.QUERY_ARG_PIN2, pin2);
        mSubscriptionId = subscriptionId;
    }

    public static SimCleanupRule forAdnOnSlot(int slotIndex) {
        Context context = ApplicationProvider.getApplicationContext();
        SubscriptionInfo info = null;
        try {
            info = SystemUtil.callWithShellPermissionIdentity(
                    () -> context.getSystemService(SubscriptionManager.class)
                            .getActiveSubscriptionInfoForSimSlotIndex(slotIndex),
                    Manifest.permission.READ_PHONE_STATE);
        } catch (Exception e) {
            Log.e(TAG, "Failed to get subscription", e);
        }
        return new SimCleanupRule(
                ElementaryFiles.EF_ADN, null,
                info != null
                        ? info.getSubscriptionId()
                        : SubscriptionManager.INVALID_SUBSCRIPTION_ID);
    }

    int getSubscriptionId() {
        return mSubscriptionId;
    }

    void setPin2(String pin2) {
        mExtras.putString(SimPhonebookContract.SimRecords.QUERY_ARG_PIN2, pin2);
    }

    @Override
    protected void after() {
        if (mSubscriptionId == SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            Log.w(TAG, "No cleanup for invalid subscription ID");
            return;
        }
        if (mEfType == ElementaryFiles.EF_FDN) {
            clearFdn();
        } else {
            clearEf();
        }
    }

    private void clearFdn() {
        SystemUtil.runWithShellPermissionIdentity(this::clearEf,
                Manifest.permission.MODIFY_PHONE_STATE);
    }

    private void clearEf() {
        try (Cursor cursor = mResolver.query(
                SimPhonebookContract.SimRecords.getContentUri(mSubscriptionId, mEfType),
                new String[]{SimPhonebookContract.SimRecords.RECORD_NUMBER}, null, null)) {
            while (cursor.moveToNext()) {
                mResolver.delete(
                        SimPhonebookContract.SimRecords.getItemUri(mSubscriptionId, mEfType,
                                cursor.getInt(0)), mExtras);
            }
        }
    }
}
