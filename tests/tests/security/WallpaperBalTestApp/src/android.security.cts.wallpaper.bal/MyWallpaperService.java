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
package android.security.cts.wallpaper.bal;
import android.content.Intent;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

public class MyWallpaperService extends WallpaperService {
    private static final String TAG = "jji";
    private static final long INTERVAL_MS = 15_000L;
    private final MyEngine mEngine = new MyEngine();
    private final Handler mHandler = new Handler();
    private volatile boolean mStarted = false;
    private final Runnable mBALRunnable = this::doBALOnce;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i(TAG, "MyWallpaperService.onCreate");
        mStarted = true;
        mHandler.postDelayed(mBALRunnable, INTERVAL_MS);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.i(TAG, "MyWallpaperService.onDestroy");
        mStarted = false;
        mHandler.removeCallbacks(mBALRunnable);
    }

    private void doBALOnce() {
        if (mStarted) {
            final Intent intent = new Intent();
            intent.setClass(this, SpammyActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            Log.i(TAG, "MyWallpaperService trying to start spammy activity");
            startActivity(intent);
            mHandler.postDelayed(mBALRunnable, INTERVAL_MS);
        }
    }

    @Override
    public Engine onCreateEngine() {
        return mEngine;
    }

    private class MyEngine extends Engine {
        @Override // android.service.wallpaper.WallpaperService.Engine
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
        }

        @Override // android.service.wallpaper.WallpaperService.Engine
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
        }

        @Override // android.service.wallpaper.WallpaperService.Engine
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
        }

        @Override // android.service.wallpaper.WallpaperService.Engine
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            super.onSurfaceDestroyed(holder);
        }
    }
}
