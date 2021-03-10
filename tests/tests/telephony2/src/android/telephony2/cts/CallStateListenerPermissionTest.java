/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.telephony2.cts;

import static android.content.pm.PackageManager.FEATURE_TELEPHONY;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.telephony.TelephonyCallback;
import android.telephony.TelephonyManager;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class CallStateListenerPermissionTest {
    private Context mContext;
    private CountDownLatch mCallStateReceivedLatch = new CountDownLatch(1);

    private boolean mReceivedCallback = false;
    private Executor mSimpleExecutor = r -> r.run();

    private class MyTelephonyCallback extends TelephonyCallback
            implements TelephonyCallback.CallStateListener {

        @Override
        public void onCallStateChanged(int state) {
            mReceivedCallback = true;
            mCallStateReceivedLatch.countDown();
        }
    }

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
    }

    /**
     * Ensures we get a valid callback on registration even though we don't have
     * {@link android.Manifest.permission#READ_CALL_LOG} permission.
     */
    @Test
    public void testRegisterWithNoCallLogPermission() {
        if (!mContext.getPackageManager().hasSystemFeature(FEATURE_TELEPHONY)) {
            return;
        }

        TelephonyManager telephonyManager = mContext.getSystemService(TelephonyManager.class);
        assertNotNull(telephonyManager);

        MyTelephonyCallback callback = new MyTelephonyCallback();
        telephonyManager.registerTelephonyCallback(mSimpleExecutor, callback);

        try {
            mCallStateReceivedLatch.await(10000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            fail("Expected to receive call state callback");
        }

        assertTrue(mReceivedCallback);
    }
}
