/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.cts;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;

/**
 * Activity that is able to create and destroy a virtual display.
 */
public class VirtualDisplayActivity extends Activity {
    private static final String TAG = "VirtualDisplayActivity";

    private static final int DEFAULT_DENSITY_DPI = 160;
    private static final String KEY_DENSITY_DPI = "densityDpi";

    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay;

    private Surface mSurface;
    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.virtual_display_layout);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurface = mSurfaceView.getHolder().getSurface();

        mDisplayManager = (DisplayManager) getSystemService(Context.DISPLAY_SERVICE);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Bundle extras = intent.getExtras();
        if (extras == null) {
            return;
        }

        String command = extras.getString("command");
        switch (command) {
            case "create_display":
                createVirtualDisplay(extras);
                break;
            case "destroy_display":
                destroyVirtualDisplay();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        destroyVirtualDisplay();
    }

    private void createVirtualDisplay(Bundle extras) {
        if (mVirtualDisplay == null) {
            final int width = mSurfaceView.getWidth();
            final int height = mSurfaceView.getHeight();
            final int densityDpi = extras.getInt(KEY_DENSITY_DPI, DEFAULT_DENSITY_DPI);
            Log.d(TAG, "createVirtualDisplay: " + width + "x" + height + ", dpi: "
                    + densityDpi);
            final int flags = 0;
            mVirtualDisplay = mDisplayManager.createVirtualDisplay("VirtualDisplay", width, height,
                    densityDpi, mSurface, flags);
        }
    }

    private void destroyVirtualDisplay() {
        if (mVirtualDisplay != null) {
            Log.d(TAG, "destroyVirtualDisplay");
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }
}
