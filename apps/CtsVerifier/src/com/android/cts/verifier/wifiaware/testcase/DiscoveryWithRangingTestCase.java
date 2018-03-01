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

package com.android.cts.verifier.wifiaware.testcase;

import android.content.Context;
import android.util.Log;

import com.android.cts.verifier.R;

/**
 * Test case for Discovery + Ranging:
 *
 * Subscribe test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Subscribe
 *    wait for results (subscribe session)
 * 3. Wait for discovery with ranging
 * 4. Send message
 *    Wait for success
 *
 * Publish test sequence:
 * 1. Attach
 *    wait for results (session)
 * 2. Publish
 *    wait for results (publish session)
 * 3. Wait for rx message
 *
 * Validate that received range is "reasonable" (i.e. 0 <= X <= SPECIFIED_LARGE_RANGE).
 */
public class DiscoveryWithRangingTestCase extends DiscoveryBaseTestCase {
    private static final String TAG = "DiscWithRangingTestCase";
    private static final boolean DBG = true;

    private boolean mIsPublish;

    public DiscoveryWithRangingTestCase(Context context, boolean isPublish) {
        super(context, true, true);

        mIsPublish = isPublish;
    }

    @Override
    protected boolean executeTest() throws InterruptedException {
        if (DBG) Log.d(TAG, "executeTest: mIsPublish=" + mIsPublish);

        boolean success;
        if (mIsPublish) {
            success = executePublish();
        } else {
            success = executeSubscribe();
        }
        if (!success) {
            return false;
        }

        // 7. destroy session
        mWifiAwareDiscoverySession.close();
        mWifiAwareDiscoverySession = null;

        mListener.onTestMsgReceived(mContext.getString(R.string.aware_status_lifecycle_ok));
        return true;
    }
}
