/*
 * Copyright 2017, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.cts.deviceandprofileowner;

import android.os.Handler;
import android.os.HandlerThread;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test class that calls DPM.clearApplicationUserData and verifies that it doesn't time out.
 */
public class ClearApplicationDataTest extends BaseDeviceAdminTest {
    private static final String TEST_PKG = "com.android.cts.intent.receiver";
    private static final Semaphore mSemaphore = new Semaphore(0);
    private static final long CLEAR_APPLICATION_DATA_TIMEOUT_S = 10;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mHandlerThread = new HandlerThread("ClearApplicationData");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
    }

    @Override
    public void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        super.tearDown();
    }

    public void testClearApplicationData() throws Exception {
        mDevicePolicyManager.clearApplicationUserData(ADMIN_RECEIVER_COMPONENT, TEST_PKG,
                (String pkg, boolean succeeded) -> {
                    assertEquals(TEST_PKG, pkg);
                    assertTrue(succeeded);
                    mSemaphore.release();
                }, mHandler);

        assertTrue("Clearing application data took too long",
                mSemaphore.tryAcquire(CLEAR_APPLICATION_DATA_TIMEOUT_S, TimeUnit.SECONDS));
    }
}
