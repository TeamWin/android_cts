/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.stubs;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.app.stubs.R;

import com.android.compatibility.common.util.IBinderParcelable;

/**
 * Foreground Service with location type.
 */
public class LocalForegroundServiceLocation extends LocalForegroundService {

    private static final String TAG = "LocalForegroundServiceLocation";
    private static final String EXTRA_COMMAND = "LocalForegroundServiceLocation.command";
    private static final String NOTIFICATION_CHANNEL_ID = "cts/" + TAG;

    /** Returns the channel id for this service */
    @Override
    protected String getNotificationChannelId() {
        return NOTIFICATION_CHANNEL_ID;
    }
}
