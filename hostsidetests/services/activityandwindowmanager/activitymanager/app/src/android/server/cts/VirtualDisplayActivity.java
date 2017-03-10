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

import static android.content.Context.DISPLAY_SERVICE;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC;
import static android.hardware.display.DisplayManager
        .VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
import android.app.Activity;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Bundle;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Activity that is able to create and destroy a virtual display.
 */
public class VirtualDisplayActivity extends Activity implements SurfaceHolder.Callback {
    private static final String TAG = "VirtualDisplayActivity";

    private static final int DEFAULT_DENSITY_DPI = 160;
    private static final String KEY_DENSITY_DPI = "density_dpi";
    private static final String KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD
            = "can_show_with_insecure_keyguard";
    private static final String KEY_PUBLIC_DISPLAY = "public_display";
    private static final String KEY_RESIZE_DISPLAY = "resize_display";

    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay;
    private int mDensityDpi = DEFAULT_DENSITY_DPI;
    private boolean mResizeDisplay;

    private Surface mSurface;
    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.virtual_display_layout);

        mSurfaceView = (SurfaceView) findViewById(R.id.surfaceView);
        mSurfaceView.getHolder().addCallback(this);
        mSurface = mSurfaceView.getHolder().getSurface();

        mDisplayManager = (DisplayManager) getSystemService(DISPLAY_SERVICE);
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
            mDensityDpi = extras.getInt(KEY_DENSITY_DPI, DEFAULT_DENSITY_DPI);
            mResizeDisplay = extras.getBoolean(KEY_RESIZE_DISPLAY);
            int flags = 0;

            final boolean canShowWithInsecureKeyguard
                    = extras.getBoolean(KEY_CAN_SHOW_WITH_INSECURE_KEYGUARD);
            if (canShowWithInsecureKeyguard) {
                flags |= VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
            }

            final boolean publicDisplay = extras.getBoolean(KEY_PUBLIC_DISPLAY);
            if (publicDisplay) {
                flags |= VIRTUAL_DISPLAY_FLAG_PUBLIC | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
            }

            Log.d(TAG, "createVirtualDisplay: " + width + "x" + height + ", dpi: "
                    + mDensityDpi + ", canShowWithInsecureKeyguard=" + canShowWithInsecureKeyguard
                    + ", publicDisplay=" + publicDisplay);
            try {
                mVirtualDisplay = mDisplayManager.createVirtualDisplay("VirtualDisplay", width,
                        height, mDensityDpi, mSurface, flags);
            } catch (IllegalArgumentException e) {
                // This is expected when trying to create show-when-locked public display.
            }
        }
    }

    private void destroyVirtualDisplay() {
        if (mVirtualDisplay != null) {
            Log.d(TAG, "destroyVirtualDisplay");
            mVirtualDisplay.release();
            mVirtualDisplay = null;
        }
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        if (mResizeDisplay && mVirtualDisplay != null) {
            mVirtualDisplay.resize(width, height, mDensityDpi);
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
    }
}
