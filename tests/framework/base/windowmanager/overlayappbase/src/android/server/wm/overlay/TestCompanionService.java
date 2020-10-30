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

import static android.server.wm.overlay.Components.TestCompanionService.ACTION_SHOW_SYSTEM_ALERT_WINDOW;
import static android.server.wm.overlay.Components.TestCompanionService.ACTION_SHOW_TOAST;
import static android.server.wm.overlay.Components.TestCompanionService.EXTRA_NAME;
import static android.server.wm.overlay.Components.TestCompanionService.EXTRA_OPACITY;
import static android.view.Display.DEFAULT_DISPLAY;

import android.annotation.SuppressLint;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.util.ArrayMap;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.util.Map;


public class TestCompanionService extends Service {
    public static final int BACKGROUND_COLOR = 0xFF00FF00;

    private final Handler mHandler = new ServiceHandler();
    private final Map<Integer, Runnable> mActions = new ArrayMap<>();
    private Messenger mMessenger;
    private Bundle mData;
    private Toast mToast;
    private View mSawView;
    private Context mSawContext;
    private WindowManager mSawWindowManager;

    @Override
    public void onCreate() {
        mMessenger = new Messenger(mHandler);
        mActions.put(ACTION_SHOW_SYSTEM_ALERT_WINDOW, this::onSaw);
        mActions.put(ACTION_SHOW_TOAST, this::onToast);

        mSawContext = getContextForSaw(this);
        mSawWindowManager = mSawContext.getSystemService(WindowManager.class);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        if (mSawView != null) {
            mSawWindowManager.removeView(mSawView);
        }
        if (mToast != null) {
            mToast.cancel();
        }
    }

    protected void onSaw() {
        String name = mData.getString(EXTRA_NAME);
        float opacity = mData.getFloat(EXTRA_OPACITY);

        mSawView = new View(mSawContext);
        mSawView.setBackgroundColor(BACKGROUND_COLOR);
        LayoutParams params =
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.TYPE_APPLICATION_OVERLAY,
                        LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
        params.setTitle(name);
        params.alpha = opacity;
        mSawWindowManager.addView(mSawView, params);
    }

    protected void onToast() {
        mToast = Toast.makeText(this, "Toast from " + getPackageName(), Toast.LENGTH_LONG);
        mToast.show();
    }

    protected void onUnknownAction(int message) {
        throw new IllegalArgumentException("Unknown action " + message);
    }

    private static Context getContextForSaw(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Context displayContext = context.createDisplayContext(display);
        return displayContext.createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null);
    }

    /** Suppressing since all messages are short-lived and we clear the queue on exit. */
    @SuppressLint("HandlerLeak")
    private class ServiceHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            mData = message.getData();
            mActions.getOrDefault(message.what, () -> onUnknownAction(message.what)).run();
        }
    }
}
