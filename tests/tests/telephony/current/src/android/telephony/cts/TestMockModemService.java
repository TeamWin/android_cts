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

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class TestMockModemService extends Service {
    private static final String TAG = "MockModemService";

    public static final int TEST_TIMEOUT_MS = 30000;
    public static final int LATCH_MOCK_MODEM_SERVICE_READY = 0;
    private static final int LATCH_MAX = 1;

    private Object mLock;
    protected static CountDownLatch[] sLatches;
    private LocalBinder mBinder;

    // For local access of this Service.
    class LocalBinder extends Binder {
        TestMockModemService getService() {
            return TestMockModemService.this;
        }
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "Mock Modem Service Created");

        mLock = new Object();

        sLatches = new CountDownLatch[LATCH_MAX];
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }

        mBinder = new LocalBinder();
    }

    @Override
    public IBinder onBind(Intent intent) {
        countDownLatch(LATCH_MOCK_MODEM_SERVICE_READY);
        Log.i(TAG, "onBind-Local");
        return mBinder;
    }

    public void resetState() {
        synchronized (mLock) {
            for (int i = 0; i < LATCH_MAX; i++) {
                sLatches[i] = new CountDownLatch(1);
            }
        }
    }

    public boolean waitForLatchCountdown(int latchIndex) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(TEST_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public boolean waitForLatchCountdown(int latchIndex, long waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }
}
