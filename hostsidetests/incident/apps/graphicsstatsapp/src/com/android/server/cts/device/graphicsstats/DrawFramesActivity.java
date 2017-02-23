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

package com.android.server.cts.device.graphicsstats;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class DrawFramesActivity extends Activity {

    private static final String TAG = "GraphicsStatsDeviceTest";

    private static final int[] COLORS = new int[] {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
    };

    private View mColorView;
    private int mColorIndex;
    private final CountDownLatch mReady = new CountDownLatch(1);
    private CountDownLatch mFramesFinishedFence = mReady;
    private int mFramesRemaining = 1;

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        mColorView = new View(this);
        updateColor();
        mColorView.getViewTreeObserver().addOnDrawListener(this::onDraw);
        setContentView(mColorView);
    }

    private void updateColor() {
        mColorView.setBackgroundColor(COLORS[mColorIndex]);
        // allow COLORs to be length == 1 or have duplicates without breaking the test
        mColorView.invalidate();
        mColorIndex = (mColorIndex + 1) % COLORS.length;
    }

    private void onDraw() {
        if (mFramesFinishedFence != null && --mFramesRemaining >= 0) {
            if (mFramesRemaining == 0) {
                mFramesFinishedFence.countDown();
                mFramesFinishedFence = null;
            } else {
                mColorView.post(this::updateColor);
            }
        }
    }

    public void drawFrames(final int frameCount) throws InterruptedException, TimeoutException {
        if (!mReady.await(4, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
        final CountDownLatch fence = new CountDownLatch(1);
        runOnUiThread(() -> {
            mFramesRemaining = frameCount;
            mFramesFinishedFence = fence;
            mColorView.invalidate();
        });
        // Set an upper-bound at 100ms/frame, nothing should ever come close to this for a simple
        // color fill.
        if (!fence.await(frameCount / 10, TimeUnit.SECONDS)) {
            throw new TimeoutException();
        }
    }
}
