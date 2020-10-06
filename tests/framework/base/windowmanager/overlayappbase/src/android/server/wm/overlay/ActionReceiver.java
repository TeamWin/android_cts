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

import static android.server.wm.overlay.Components.ActionReceiver.ACTION_OVERLAY;
import static android.server.wm.overlay.Components.ActionReceiver.ACTION_PING;
import static android.server.wm.overlay.Components.ActionReceiver.CALLBACK_PONG;
import static android.server.wm.overlay.Components.ActionReceiver.EXTRA_CALLBACK;
import static android.server.wm.overlay.Components.ActionReceiver.EXTRA_NAME;
import static android.server.wm.overlay.Components.ActionReceiver.EXTRA_OPACITY;
import static android.view.Display.DEFAULT_DISPLAY;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.os.IBinder;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;


public class ActionReceiver extends BroadcastReceiver {
    private static final String TAG = "ActionReceiver";
    public static final int BACKGROUND_COLOR = 0xFF00FF00;

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        switch (action) {
            case ACTION_OVERLAY:
                String name = intent.getStringExtra(EXTRA_NAME);
                float opacity = intent.getFloatExtra(EXTRA_OPACITY, 1f);
                overlay(context, name, opacity);
                break;
            case ACTION_PING:
                IBinder callback = intent.getExtras().getBinder(EXTRA_CALLBACK);
                try {
                    callback.transact(CALLBACK_PONG, Parcel.obtain(), null, IBinder.FLAG_ONEWAY);
                } catch (RemoteException e) {
                    Log.e(TAG, "Caller (test) died while sending response to ping", e);
                }
                break;
            default:
                Log.e(TAG, "Unknown action " + action);
                break;
        }
    }

    private void overlay(Context context, String name, float opacity) {
        Context windowContext = getContextForOverlay(context);
        View view = new View(windowContext);
        view.setBackgroundColor(BACKGROUND_COLOR);
        LayoutParams params =
                new LayoutParams(
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.MATCH_PARENT,
                        LayoutParams.TYPE_APPLICATION_OVERLAY,
                        LayoutParams.FLAG_NOT_TOUCHABLE | LayoutParams.FLAG_NOT_FOCUSABLE,
                        PixelFormat.TRANSLUCENT);
        params.setTitle(name);
        params.alpha = opacity;
        WindowManager windowManager = windowContext.getSystemService(WindowManager.class);
        windowManager.addView(view, params);
    }

    private Context getContextForOverlay(Context context) {
        DisplayManager displayManager = context.getSystemService(DisplayManager.class);
        Display display = displayManager.getDisplay(DEFAULT_DISPLAY);
        Context displayContext = context.createDisplayContext(display);
        return displayContext.createWindowContext(LayoutParams.TYPE_APPLICATION_OVERLAY, null);
    }
}
