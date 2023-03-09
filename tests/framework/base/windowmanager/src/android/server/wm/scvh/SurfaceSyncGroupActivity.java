/*
 * Copyright (C) 2023 The Android Open Source Project
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

package android.server.wm.scvh;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;
import android.view.Gravity;
import android.view.SurfaceControl;
import android.view.SurfaceControlViewHost;
import android.widget.FrameLayout;
import android.window.SurfaceSyncGroup;

import androidx.annotation.Nullable;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class SurfaceSyncGroupActivity extends Activity {
    private static final String TAG = "SurfaceSyncGroupActivity";

    private SurfaceControlViewHostHelper mSurfaceControlViewHostHelper;
    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private Handler mHandler;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Size size = new Size(500, 500);
        mSurfaceControlViewHostHelper = new SurfaceControlViewHostHelper(TAG, mCountDownLatch, this,
                0, size);

        mSurfaceControlViewHostHelper.bindEmbeddedService();

        FrameLayout frameLayout = new FrameLayout(this);
        setContentView(frameLayout);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(size.getWidth(),
                size.getHeight());
        layoutParams.gravity = Gravity.CENTER;
        mSurfaceControlViewHostHelper.attachSurfaceView(frameLayout, layoutParams);
        mHandler = new Handler(Looper.getMainLooper());

        KeyguardManager km = getSystemService(KeyguardManager.class);
        km.requestDismissKeyguard(this, null);
    }

    public void startTest() {
        boolean ready = false;
        try {
            ready = mCountDownLatch.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for SCVH to attach");
        }

        assertTrue("Failed to attach SCVH", ready);

        SurfaceControlViewHost.SurfacePackage surfacePackage =
                mSurfaceControlViewHostHelper.getSurfacePackage();
        final IAttachEmbeddedWindow iAttachEmbeddedWindow =
                mSurfaceControlViewHostHelper.getAttachedEmbeddedWindow();

        CountDownLatch finishedRunLatch = new CountDownLatch(1);

        mHandler.post(() -> {
            SurfaceSyncGroup surfaceSyncGroup = new SurfaceSyncGroup(TAG);
            surfaceSyncGroup.add(surfacePackage, () -> {
                try {
                    iAttachEmbeddedWindow.sendCrash();
                } catch (RemoteException e) {
                    Log.e(TAG, "Failed to send crash to embedded");
                }
            });
            surfaceSyncGroup.add(getWindow().getRootSurfaceControl(), null /* runnable */);
            // Add a transaction committed listener to make sure the transaction has been applied
            // even though one of the processes involved crashed.
            SurfaceControl.Transaction t = new SurfaceControl.Transaction();
            t.addTransactionCommittedListener(Runnable::run, finishedRunLatch::countDown);
            surfaceSyncGroup.addTransaction(t);
            surfaceSyncGroup.markSyncReady();
        });

        try {
            finishedRunLatch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Log.e(TAG, "Failed to wait for transaction committed callback");
        }

        assertEquals("Failed to apply transaction for SurfaceSyncGroup", 0,
                finishedRunLatch.getCount());
    }
}
