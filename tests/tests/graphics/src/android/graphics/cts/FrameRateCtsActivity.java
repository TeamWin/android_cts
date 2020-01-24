/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.graphics.cts;

import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Canvas;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Collections;

/**
 * An Activity to help with frame rate testing.
 */
public class FrameRateCtsActivity extends Activity {
    static {
        System.loadLibrary("ctsgraphics_jni");
    }

    private static String TAG = "FrameRateCtsActivity";
    private static final long WAIT_FOR_SURFACE_TIMEOUT_SECONDS = 3;
    private static final long FRAME_RATE_SWITCH_GRACE_PERIOD_SECONDS = 1;
    private static final long STABLE_FRAME_RATE_WAIT_SECONDS = 1;

    private DisplayManager mDisplayManager;
    private SurfaceView mSurfaceView;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Object mLock = new Object();
    private Surface mSurface = null;
    private float mDeviceFrameRate;
    private ArrayList<Float> mFrameRateChangedEvents = new ArrayList<Float>();

    SurfaceHolder.Callback mSurfaceHolderCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            synchronized (mLock) {
                mSurface = holder.getSurface();
                mLock.notify();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            synchronized (mLock) {
                mSurface = null;
                mLock.notify();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}
    };

    DisplayManager.DisplayListener mDisplayListener = new DisplayManager.DisplayListener() {
        @Override
        public void onDisplayAdded(int displayId) {}

        @Override
        public void onDisplayChanged(int displayId) {
            if (displayId != Display.DEFAULT_DISPLAY) {
                return;
            }
            synchronized (mLock) {
                float frameRate = mDisplayManager.getDisplay(displayId).getMode().getRefreshRate();
                if (frameRate != mDeviceFrameRate) {
                    Log.i(TAG,
                            String.format("Frame rate changed: %.0f --> %.0f", mDeviceFrameRate,
                                    frameRate));
                    mDeviceFrameRate = frameRate;
                    mFrameRateChangedEvents.add(frameRate);
                    mLock.notify();
                }
            }
        }

        @Override
        public void onDisplayRemoved(int displayId) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        synchronized (mLock) {
            mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
            mDeviceFrameRate =
                    mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY).getMode().getRefreshRate();
            mDisplayManager.registerDisplayListener(mDisplayListener, mHandler);
            mSurfaceView = new SurfaceView(this);
            mSurfaceView.setWillNotDraw(false);
            mSurfaceView.setZOrderOnTop(true);
            setContentView(mSurfaceView,
                    new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT));
            mSurfaceView.getHolder().addCallback(mSurfaceHolderCallback);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
    }

    private ArrayList<Float> getFrameRatesToTest() {
        Display display = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        Display.Mode[] modes = display.getSupportedModes();
        Display.Mode currentMode = display.getMode();
        ArrayList<Float> frameRates = new ArrayList<Float>();
        for (Display.Mode mode : modes) {
            if (mode.getPhysicalWidth() == currentMode.getPhysicalWidth()
                    && mode.getPhysicalHeight() == currentMode.getPhysicalHeight()) {
                frameRates.add(mode.getRefreshRate());
            }
        }
        Collections.sort(frameRates);
        ArrayList<Float> uniqueFrameRates = new ArrayList<Float>();
        for (float frameRate : frameRates) {
            if (uniqueFrameRates.isEmpty()
                    || frameRate - uniqueFrameRates.get(uniqueFrameRates.size() - 1) >= 1.0f) {
                uniqueFrameRates.add(frameRate);
            }
        }
        return uniqueFrameRates;
    }

    private void waitForSurface() throws InterruptedException {
        if (mSurface == null) {
            Log.i(TAG, "Waiting for surface");
        }
        long nowNanos = System.nanoTime();
        long endTimeNanos = nowNanos + WAIT_FOR_SURFACE_TIMEOUT_SECONDS * 1_000_000_000L;
        while (mSurface == null) {
            long timeRemainingMillis = (endTimeNanos - nowNanos) / 1_000_000;
            assertTrue("Never got a surface", timeRemainingMillis > 0);
            mLock.wait(timeRemainingMillis);
            nowNanos = System.nanoTime();
        }
    }

    private boolean isDeviceFrameRateCompatibleWithAppRequest(
            float deviceFrameRate, float appRequestedFrameRate) {
        float multiple = deviceFrameRate / appRequestedFrameRate;
        int roundedMultiple = Math.round(multiple);
        return roundedMultiple > 0
                && Math.abs(roundedMultiple * appRequestedFrameRate - deviceFrameRate) <= 0.1f;
    }

    private void postBuffer() {
        Canvas canvas = mSurfaceView.getHolder().lockHardwareCanvas();
        canvas.drawColor(Color.BLUE);
        mSurfaceView.getHolder().unlockCanvasAndPost(canvas);
    }

    private void setFrameRateSurface(float frameRate) {
        Log.i(TAG, String.format("Setting frame rate to %.0f", frameRate));
        mSurface.setFrameRate(frameRate);
        postBuffer();
    }

    private void setFrameRateANativeWindow(float frameRate) {
        Log.i(TAG, String.format("Setting frame rate to %.0f", frameRate));
        nativeWindowSetFrameRate(mSurface, frameRate);
        postBuffer();
    }

    private void setFrameRateSurfaceControl(float frameRate) {
        Log.i(TAG, String.format("Setting frame rate to %.0f", frameRate));
        SurfaceControl.Transaction transaction = new SurfaceControl.Transaction();
        transaction.setFrameRate(mSurfaceView.getSurfaceControl(), frameRate).apply();
        transaction.close();
        postBuffer();
    }

    private void setFrameRateNativeSurfaceControl(long surfaceControl, float frameRate) {
        Log.i(TAG, String.format("Setting frame rate to %.0f", frameRate));
        nativeSurfaceControlSetFrameRate(surfaceControl, frameRate);
    }

    private void verifyCompatibleAndStableFrameRate(float appRequestedFrameRate)
            throws InterruptedException {
        Log.i(TAG, "Verifying compatible and stable frame rate");
        long nowNanos = System.nanoTime();
        long gracePeriodEndTimeNanos =
                nowNanos + FRAME_RATE_SWITCH_GRACE_PERIOD_SECONDS * 1_000_000_000L;
        while (true) {
            // Wait until we switch to a compatible frame rate.
            while (!isDeviceFrameRateCompatibleWithAppRequest(
                           mDeviceFrameRate, appRequestedFrameRate)
                    && gracePeriodEndTimeNanos > nowNanos) {
                mLock.wait((gracePeriodEndTimeNanos - nowNanos) / 1_000_000);
                nowNanos = System.nanoTime();
                assertTrue("Lost the surface", mSurface != null);
            }

            // TODO(b/148033900): Remove the if and error log below, and replace it with this
            // assertTrue() call, once we have a way to ignore display manager policy.
            //
            // assertTrue(
            //         String.format(
            //                 "Timed out waiting for a stable and compatible frame rate."
            //                 + " requested=%.0f current=%.0f.",
            //                 appRequestedFrameRate, mDeviceFrameRate),
            //         gracePeriodEndTimeNanos > nowNanos);
            if (nowNanos >= gracePeriodEndTimeNanos) {
                Log.e(TAG,
                        String.format(
                                "Timed out waiting for a stable and compatible frame rate."
                                + " requested=%.0f current=%.0f.",
                                appRequestedFrameRate, mDeviceFrameRate));
                return;
            }

            // We've switched to a compatible frame rate. Now wait for a while to see if we stay at
            // that frame rate.
            long endTimeNanos = nowNanos + STABLE_FRAME_RATE_WAIT_SECONDS * 1_000_000_000L;
            while (endTimeNanos > nowNanos) {
                mFrameRateChangedEvents.clear();
                mLock.wait((endTimeNanos - nowNanos) / 1_000_000);
                nowNanos = System.nanoTime();
                assertTrue("Lost the surface", mSurface != null);
                if (!mFrameRateChangedEvents.isEmpty()) {
                    break;
                }
                if (nowNanos >= endTimeNanos) {
                    Log.i(TAG, String.format("Stable frame rate %.0f verified", mDeviceFrameRate));
                    return;
                }
            }
        }
    }

    public void testSurfaceSetFrameRate() throws InterruptedException {
        ArrayList<Float> frameRatesToTest = getFrameRatesToTest();
        Log.i(TAG, "Testing Surface.setFrameRate()");
        synchronized (mLock) {
            waitForSurface();
            for (float frameRate : frameRatesToTest) {
                setFrameRateSurface(frameRate);
                verifyCompatibleAndStableFrameRate(frameRate);
            }
            setFrameRateSurface(-100.f);
            Thread.sleep(1000);
            setFrameRateSurface(0.f);
        }
        Log.i(TAG, "Done testing Surface.setFrameRate()");
    }

    public void testANativeWindowSetFrameRate() throws InterruptedException {
        ArrayList<Float> frameRatesToTest = getFrameRatesToTest();
        Log.i(TAG, "Testing ANativeWindow_setFrameRate()");
        synchronized (mLock) {
            waitForSurface();
            for (float frameRate : frameRatesToTest) {
                setFrameRateANativeWindow(frameRate);
                verifyCompatibleAndStableFrameRate(frameRate);
            }
            setFrameRateANativeWindow(-100.f);
            Thread.sleep(1000);
            setFrameRateANativeWindow(0.f);
        }
        Log.i(TAG, "Done testing ANativeWindow_setFrameRate()");
    }

    public void testSurfaceControlSetFrameRate() throws InterruptedException {
        ArrayList<Float> frameRatesToTest = getFrameRatesToTest();
        Log.i(TAG, "Testing SurfaceControl.Transaction.setFrameRate()");
        synchronized (mLock) {
            waitForSurface();
            for (float frameRate : frameRatesToTest) {
                setFrameRateSurfaceControl(frameRate);
                verifyCompatibleAndStableFrameRate(frameRate);
            }
            setFrameRateSurfaceControl(-100.f);
            Thread.sleep(1000);
            setFrameRateSurfaceControl(0.f);
        }
        Log.i(TAG, "Done testing SurfaceControl.Transaction.setFrameRate()");
    }

    public void testNativeSurfaceControlSetFrameRate() throws InterruptedException {
        ArrayList<Float> frameRatesToTest = getFrameRatesToTest();
        Log.i(TAG, "Testing ASurfaceTransaction_setFrameRate()");
        long nativeSurfaceControl = 0;
        try {
            synchronized (mLock) {
                waitForSurface();
                nativeSurfaceControl = nativeSurfaceControlCreate(mSurface);
                assertTrue("Failed to create a native SurfaceControl", nativeSurfaceControl != 0);
                for (float frameRate : frameRatesToTest) {
                    setFrameRateNativeSurfaceControl(nativeSurfaceControl, frameRate);
                    verifyCompatibleAndStableFrameRate(frameRate);
                }
                setFrameRateNativeSurfaceControl(nativeSurfaceControl, -100.f);
                Thread.sleep(1000);
                setFrameRateNativeSurfaceControl(nativeSurfaceControl, 0.f);
            }
        } finally {
            nativeSurfaceControlDestroy(nativeSurfaceControl);
        }
        Log.i(TAG, "Done testing ASurfaceTransaction_setFrameRate()");
    }

    private static native int nativeWindowSetFrameRate(Surface surface, float frameRate);
    private static native long nativeSurfaceControlCreate(Surface parentSurface);
    private static native void nativeSurfaceControlDestroy(long surfaceControl);
    private static native void nativeSurfaceControlSetFrameRate(
            long surfaceControl, float frameRate);
}
