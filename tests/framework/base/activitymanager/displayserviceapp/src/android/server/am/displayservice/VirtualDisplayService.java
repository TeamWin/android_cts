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
 * limitations under the License
 */

package android.server.am.displayservice;

import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
import static android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_CREATE;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_DESTROY;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_OFF;
import static android.server.am.displayservice.Components.VirtualDisplayService.COMMAND_ON;
import static android.server.am.displayservice.Components.VirtualDisplayService.EXTRA_COMMAND;
import static android.server.am.displayservice.Components.VirtualDisplayService
        .EXTRA_SHOW_CONTENT_WHEN_LOCKED;
import static android.server.am.displayservice.Components.VirtualDisplayService
        .VIRTUAL_DISPLAY_NAME;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.ImageReader;
import android.os.IBinder;
import android.util.Log;

public class VirtualDisplayService extends Service {
    private static final String TAG = VirtualDisplayService.class.getSimpleName();

    /** See {@link DisplayManager#VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD}. */
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;

    private static final String NOTIFICATION_CHANNEL_ID = "cts/VirtualDisplayService";
    private static final int FOREGROUND_ID = 1;

    private static final int DENSITY = 160;
    private static final int HEIGHT = 480;
    private static final int WIDTH = 800;

    private ImageReader mReader;
    private VirtualDisplay mVirtualDisplay;

    @Override
    public void onCreate() {
        super.onCreate();

        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        notificationManager.createNotificationChannel(new NotificationChannel(
                NOTIFICATION_CHANNEL_ID, NOTIFICATION_CHANNEL_ID,
                NotificationManager.IMPORTANCE_DEFAULT));
        Notification notif = new Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .build();
        startForeground(FOREGROUND_ID, notif);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String command = intent.getStringExtra(EXTRA_COMMAND);
        Log.d(TAG, "Got command: " + command);

        switch (command) {
            case COMMAND_CREATE:
                createVirtualDisplay(intent);
                break;
            case COMMAND_OFF:
                mVirtualDisplay.setSurface(null);
                break;
            case COMMAND_ON:
                mVirtualDisplay.setSurface(mReader.getSurface());
                break;
            case COMMAND_DESTROY:
                destroyVirtualDisplay();
                stopSelf();
                break;
        }

        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createVirtualDisplay(Intent intent) {
        mReader = ImageReader.newInstance(WIDTH, HEIGHT, PixelFormat.RGBA_8888, 2);

        final DisplayManager displayManager = getSystemService(DisplayManager.class);

        int flags = VIRTUAL_DISPLAY_FLAG_PRESENTATION | VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        if (intent.getBooleanExtra(EXTRA_SHOW_CONTENT_WHEN_LOCKED, false /* defaultValue */)) {
            flags |= VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;
        }
        mVirtualDisplay = displayManager.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME, WIDTH, HEIGHT, DENSITY, mReader.getSurface(), flags);
    }

    private void destroyVirtualDisplay() {
        mVirtualDisplay.release();
        mReader.close();
    }
}
