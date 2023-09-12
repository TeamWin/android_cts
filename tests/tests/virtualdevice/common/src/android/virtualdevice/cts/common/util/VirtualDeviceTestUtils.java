/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.virtualdevice.cts.common.util;

import static com.google.common.util.concurrent.Uninterruptibles.tryAcquireUninterruptibly;

import android.app.ActivityOptions;
import android.content.ComponentName;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.os.ResultReceiver;
import android.view.Surface;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.app.BlockedAppStreamingActivity;

import java.util.ArrayList;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Test utilities for Virtual Device tests.
 */
public final class VirtualDeviceTestUtils {

    public static final ComponentName BLOCKED_ACTIVITY_COMPONENT =
            new ComponentName("android", BlockedAppStreamingActivity.class.getName());

    public static VirtualDisplayConfig.Builder createDefaultVirtualDisplayConfigBuilder() {
        return new VirtualDisplayConfig.Builder("testDisplay", 100, 100, 240)
                .setSurface(new Surface());
    }

    public static ResultReceiver createResultReceiver(OnReceiveResultListener listener) {
        ResultReceiver receiver = new ResultReceiver(new Handler(Looper.getMainLooper())) {
            @Override
            protected void onReceiveResult(int resultCode, Bundle resultData) {
                listener.onReceiveResult(resultCode, resultData);
            }
        };
        // Erase the subclass to make the given result receiver safe to include inside Bundles
        // (See b/177985835).
        Parcel parcel = Parcel.obtain();
        receiver.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        receiver = ResultReceiver.CREATOR.createFromParcel(parcel);
        parcel.recycle();
        return receiver;
    }

    /**
     * Returns if VirtualDeviceManager is enabled on the device or not.
     */
    public static boolean isVirtualDeviceManagerConfigEnabled(Context context) {
        return context.getResources().getBoolean(
                Resources.getSystem().getIdentifier(
                        "config_enableVirtualDeviceManager",
                        "bool",
                        "android"));
    }

    /**
     * Interface mimicking {@link ResultReceiver}, allowing it to be mocked.
     */
    public interface OnReceiveResultListener {
        void onReceiveResult(int resultCode, Bundle resultData);
    }

    public static Bundle createActivityOptions(VirtualDisplay virtualDisplay) {
        return createActivityOptions(virtualDisplay.getDisplay().getDisplayId());
    }

    public static Bundle createActivityOptions(int displayId) {
        return ActivityOptions.makeBasic()
                .setLaunchDisplayId(displayId)
                .toBundle();
    }

    private VirtualDeviceTestUtils() {}

    public static class DisplayListenerForTest implements DisplayManager.DisplayListener {

        private static final int TIMEOUT_MILLIS = 1000;

        private final Semaphore mDisplayAddedSemaphore = new Semaphore(0);
        private final Semaphore mDisplayRemovedSemaphore = new Semaphore(0);
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private final ArrayList<Integer> mAddedDisplays = new ArrayList<>();
        @GuardedBy("mLock")
        private final ArrayList<Integer> mRemovedDisplays = new ArrayList<>();

        /**
         * Wait until {@code OnDisplayAddedCallback} is invoked at least numDisplays or timeout
         * expires.
         *
         * @param numDisplays - how many invocations of {@code OnDisplayAddedCallback} to wait for.
         * @return true iff the {@code OnDisplayAddedCallback} was invoked at least numDisplays
         * before
         * timeout expiration.
         */
        public boolean waitForOnDisplayAddedCallback(int numDisplays) {
            return tryAcquireUninterruptibly(mDisplayAddedSemaphore, numDisplays, TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
        }

        /**
         * Wait until {@code OnDisplayAddedCallback} is invoked once or timeout expires.
         *
         * @return true iff the {@code OnDisplayAddedCallback} was invoked at least once before
         * timeout expiration.
         */
        public boolean waitForOnDisplayAddedCallback() {
            return waitForOnDisplayAddedCallback(/*numDisplays=*/1);
        }


        /**
         * Wait until {@code OnDisplayRemovedCallback} is invoked at least numDisplays or timeout
         * expires.
         *
         * @param numDisplays - how many invocations of {@code OnDisplayRemovedCallback} to wait
         *                    for.
         * @return true iff the {@code OnDisplayRemovedCallback} was invoked at least numDisplays
         * before
         * timeout expiration.
         */
        public boolean waitForOnDisplayRemovedCallback(int numDisplays) {
            return tryAcquireUninterruptibly(mDisplayRemovedSemaphore, numDisplays, TIMEOUT_MILLIS,
                    TimeUnit.MILLISECONDS);
        }

        /**
         * Wait until {@code OnDisplayRemovedCallback} is invoked once or timeout expires.
         *
         * @return true iff the {@code OnDisplayRemovedCallback} was invoked at least once before
         * timeout expiration.
         */
        public boolean waitForOnDisplayRemovedCallback() {
            return waitForOnDisplayRemovedCallback(/*numDisplays=*/1);
        }

        @Override
        public void onDisplayAdded(int displayId) {
            synchronized (mLock) {
                mAddedDisplays.add(displayId);
                mDisplayAddedSemaphore.release();
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {
            synchronized (mLock) {
                mRemovedDisplays.add(displayId);
                mDisplayRemovedSemaphore.release();
            }
        }


        @Override
        public void onDisplayChanged(int displayId) {
            //Do nothing.
        }

        /**
         * Get list of recently added display ids.
         *
         * @return List of recently added display ids.
         */
        public ArrayList<Integer> getObservedAddedDisplays() {
            synchronized (mLock) {
                synchronized (mDisplayAddedSemaphore) {
                    return new ArrayList<>(mAddedDisplays);
                }
            }
        }

        /**
         * Get list of recently removed display ids.
         *
         * @return List of recently removed display ids.
         */
        public ArrayList<Integer> getObservedRemovedDisplays() {
            synchronized (mLock) {
                synchronized (mDisplayRemovedSemaphore) {
                    return new ArrayList<>(mRemovedDisplays);
                }
            }
        }
    }
}
