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

package android.server.wm.overlay;

import static android.view.Display.DEFAULT_DISPLAY;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.server.wm.app.IUntrustedTouchTestService;
import android.util.ArraySet;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;


public class UntrustedTouchTestService extends Service {
    public static final int BACKGROUND_COLOR = 0xFF00FF00;

    private final IUntrustedTouchTestService mBinder = new Binder();
    private final Set<View> mSawViews = Collections.synchronizedSet(new ArraySet<>());

    /** Can only be accessed from the main thread. */
    private Toast mToast;

    private volatile Handler mMainHandler;
    private volatile Context mSawContext;
    private volatile WindowManager mSawWindowManager;

    @Override
    public void onCreate() {
        mMainHandler = new Handler(Looper.getMainLooper());
        mSawContext = getContextForSaw(this);
        mSawWindowManager = mSawContext.getSystemService(WindowManager.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder.asBinder();
    }

    @Override
    public void onDestroy() {
        removeOverlays();
    }

    private class Binder extends IUntrustedTouchTestService.Stub {
        private final UntrustedTouchTestService mService = UntrustedTouchTestService.this;

        @Override
        public void showToast() {
            mMainHandler.post(() -> {
                mToast = Toast.makeText(mService, "Toast " + getPackageName(), Toast.LENGTH_LONG);
                mToast.show();
            });
        }

        @Override
        public void showSystemAlertWindow(String windowName, float opacity) {
            View view = new View(mSawContext);
            view.setBackgroundColor(BACKGROUND_COLOR);
            LayoutParams params =
                    new LayoutParams(
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.MATCH_PARENT,
                            LayoutParams.TYPE_APPLICATION_OVERLAY,
                            LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_NOT_FOCUSABLE,
                            PixelFormat.TRANSLUCENT);
            params.setTitle(windowName);
            params.alpha = opacity;
            mMainHandler.post(() -> mSawWindowManager.addView(view, params));
            mSawViews.add(view);
        }

        public void removeOverlays() {
            mService.removeOverlays();
        }
    }

    private void removeOverlays() {
        synchronized (mSawViews) {
            for (View view : mSawViews) {
                mSawWindowManager.removeView(view);
            }
            mSawViews.clear();
        }
        mMainHandler.post(() -> {
            if (mToast != null) {
                mToast.cancel();
            }
        });
    }

    private static Context getContextForSaw(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Context displayContext = context.createDisplayContext(display);
        return displayContext.createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null);
    }
}
