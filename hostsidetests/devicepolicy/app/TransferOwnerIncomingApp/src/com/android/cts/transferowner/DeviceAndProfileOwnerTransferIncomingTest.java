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
package com.android.cts.transferowner;

import static junit.framework.Assert.assertTrue;

import android.app.admin.DeviceAdminReceiver;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.PersistableBundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;

@SmallTest
public class DeviceAndProfileOwnerTransferIncomingTest {
    public static class BasicAdminReceiver extends DeviceAdminReceiver {
        public BasicAdminReceiver() {}

        @Override
        public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
            putBooleanPref(context, KEY_TRANSFER_COMPLETED_CALLED, true);
        }
    }

    public static class BasicAdminReceiverNoMetadata extends DeviceAdminReceiver {
        public BasicAdminReceiverNoMetadata() {}
    }

    private final static String SHARED_PREFERENCE_NAME = "shared-preference-name";
    private final static String KEY_TRANSFER_COMPLETED_CALLED = "key-transfer-completed-called";

    protected Context mContext;
    protected ComponentName mIncomingComponentName;
    protected DevicePolicyManager mDevicePolicyManager;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mDevicePolicyManager = mContext.getSystemService(DevicePolicyManager.class);
        mIncomingComponentName = new ComponentName(mContext, BasicAdminReceiver.class.getName());
    }

    @Test
    public void testTransferCompleteCallbackIsCalled() {
        assertTrue(getBooleanPref(mContext, KEY_TRANSFER_COMPLETED_CALLED));
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE);
    }

    private static void putBooleanPref(Context context, String key, boolean value) {
        getPrefs(context).edit().putBoolean(key, value).apply();
    }

    private static boolean getBooleanPref(Context context, String key) {
        return getPrefs(context).getBoolean(key, false);
    }
}
