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

import com.android.cts.verifier.wifiaware.BaseTestCase;

/**
 * Test case for data-path, in-band test cases:
 * open/passphrase * solicited/unsolicited * publish/subscribe.
 */
public class DataPathInBandTestCase extends BaseTestCase {
    private static final String TAG = "DataPathInBandTestCase";
    private static final boolean DBG = true;

    private boolean mIsSecurityOpen;
    private boolean mIsPublish;
    private boolean mIsUnsolicited;

    private final Object mLock = new Object();

    private String mFailureReason;

    public DataPathInBandTestCase(Context context, boolean isSecurityOpen, boolean isPublish,
            boolean isUnsolicited) {
        super(context);
        mIsSecurityOpen = isSecurityOpen;
        mIsPublish = isPublish;
        mIsUnsolicited = isUnsolicited;
    }

    @Override
    protected boolean executeTest() throws InterruptedException {
        return false;
    }

    private void setFailureReason(String reason) {
        synchronized (mLock) {
            mFailureReason = reason;
        }
    }

    @Override
    protected String getFailureReason() {
        synchronized (mLock) {
            return mFailureReason;
        }
    }
}