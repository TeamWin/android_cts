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
package com.android.cts.comp.provisioning;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class SilentProvisioningTestManager {

    private static final long TIMEOUT_SECONDS = 60;
    private static final String TAG = "SilentProvisioningTestManager";

    private static final SilentProvisioningTestManager sInstance
            = new SilentProvisioningTestManager();

    private final LinkedBlockingQueue<Boolean> mProvisioningResults = new LinkedBlockingQueue(1);

    public static SilentProvisioningTestManager getInstance() {
        return sInstance;
    }

    private SilentProvisioningTestManager() {}

    public void reset() {
        mProvisioningResults.clear();
    }

    public void startProvisioning(Context context) {
        context.startActivity(new Intent(context, StartProvisioningActivity.class));
    }

    public void notifyProvisioningResult(boolean result) throws InterruptedException {
        mProvisioningResults.put(result);
    }

    public boolean waitForProvisioningResult(Context context) throws InterruptedException {
        Boolean result = mProvisioningResults.poll(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (result == null) {
            Log.i(TAG, "ManagedProvisioning doesn't return result within "
                    + TIMEOUT_SECONDS + " seconds ");
            return false;
        }

        if (!result) {
            Log.i(TAG, "Failed to provision");
            return false;
        }

        // Wait for profile enabled
        ManagedProfileAddedReceiver receiver = new ManagedProfileAddedReceiver(context);
        receiver.register();
        return receiver.await();
    }

    private static class ManagedProfileAddedReceiver extends BroadcastReceiver {

        private static final CountDownLatch mLatch = new CountDownLatch(1);

        private final Context mContext;

        private ManagedProfileAddedReceiver(Context context) {
            mContext = context;
        }

        private void register() {
            mContext.registerReceiver(this, new IntentFilter(Intent.ACTION_MANAGED_PROFILE_ADDED));
        }

        private boolean await() throws InterruptedException {
            return mLatch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mLatch.countDown();
        }
    }
}


