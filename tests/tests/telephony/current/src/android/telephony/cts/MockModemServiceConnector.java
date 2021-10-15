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

package android.telephony.cts;

import android.app.Instrumentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Connects Telephony Framework to TestMockModemService. */
class MockModemServiceConnector {

    private static final String TAG = "MockModemServiceConnector";

    private static final String PACKAGE_NAME =
            InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    private static final String SERVICE_NAME = TestMockModemService.class.getClass().getName();

    private static final int BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS = 5000;

    private class MockModemServiceConnection implements ServiceConnection {

        private final CountDownLatch mLatch;

        MockModemServiceConnection(CountDownLatch latch) {
            mLatch = latch;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mMockModemService = ((TestMockModemService.LocalBinder) service).getService();
            mLatch.countDown();
            Log.d(TAG, "MockModemServiceConnection - onServiceConnected");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mMockModemService = null;
            Log.d(TAG, "MockModemServiceConnection - onServiceDisconnected");
        }
    }

    private Instrumentation mInstrumentation;

    private TestMockModemService mMockModemService;
    private MockModemServiceConnection mMockModemServiceConn;

    MockModemServiceConnector(Instrumentation instrumentation) {
        mInstrumentation = instrumentation;
    }

    private boolean setupLocalMockModemService() {
        Log.d(TAG, "setupLocalMockModemService");
        if (mMockModemService != null) {
            return true;
        }

        CountDownLatch latch = new CountDownLatch(1);
        if (mMockModemServiceConn == null) {
            mMockModemServiceConn = new MockModemServiceConnection(latch);
        }

        mInstrumentation
                .getContext()
                .bindService(
                        new Intent(mInstrumentation.getContext(), TestMockModemService.class),
                        mMockModemServiceConn,
                        Context.BIND_AUTO_CREATE);
        try {
            return latch.await(BIND_LOCAL_MOCKMODEM_SERVICE_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            return false;
        }
    }

    /**
     * Binds to the local implementation of TestMockModemService but does not trigger
     * TestMockModemService bind from telephony to allow additional configuration steps if needed.
     *
     * @return true if this request succeeded, false otherwise.
     */
    boolean connectMockModemServiceLocally() {
        if (!setupLocalMockModemService()) {
            Log.w(TAG, "connectMockModemService: couldn't set up service.");
            return false;
        }
        return true;
    }

    boolean connectMockModemService() throws Exception {
        if (!connectMockModemServiceLocally()) return false;

        return true;
    }

    boolean disconnectMockModemService() throws Exception {
        // Remove local connection
        Log.d(TAG, "disconnectMockModemService");
        if (mMockModemServiceConn != null) {
            mInstrumentation.getContext().unbindService(mMockModemServiceConn);
            mMockModemService = null;
        }

        return true;
    }
}
