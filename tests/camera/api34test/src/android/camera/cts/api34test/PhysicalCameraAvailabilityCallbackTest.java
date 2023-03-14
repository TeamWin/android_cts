/*
 * Copyright 2023 The Android Open Source Project
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

package android.camera.cts.api34test;

import android.content.Context;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.helpers.CameraErrorCollector;
import android.os.Handler;
import android.os.HandlerThread;
import android.test.AndroidTestCase;

public class PhysicalCameraAvailabilityCallbackTest extends AndroidTestCase {
    private static final String TAG = "PhysicalCameraAvailabilityCallbackTest";

    private CameraManager mCameraManager;
    private String[] mCameraIds;
    private Handler mHandler;
    private HandlerThread mHandlerThread;
    private CameraErrorCollector mCollector;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mCameraManager = context.getSystemService(CameraManager.class);
        assertNotNull("Can't connect to camera manager!", mCameraManager);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mCameraIds = mCameraManager.getCameraIdList();
        assertNotNull("Camera ids shouldn't be null", mCameraIds);

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mCollector = new CameraErrorCollector();
    }

    @Override
    protected void tearDown() throws Exception {
        mHandlerThread.quitSafely();
        mHandler = null;

        try {
            mCollector.verify();
        } catch (Throwable e) {
            // When new Exception(e) is used, exception info will be printed twice.
            throw new Exception(e.getMessage());
        } finally {
            super.tearDown();
        }
    }

    /**
     * Test that the physical camera available/unavailable callback behavior is consistent
     * between:
     *
     * - No camera is open,
     * - After camera is opened, and
     * - After camera is closed,
     */
    public void testApi34PhysicalCameraAvailabilityConsistency() throws Throwable {
        CameraTestUtils.testPhysicalCameraAvailabilityConsistencyHelper(mCameraIds,
                mCameraManager, mHandler, true /*expectInitialCallbackAfterOpen*/);
    }
}
