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

package android.app.usage.cts.test1;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStatsManager;
import android.app.usage.cts.ITestReceiver;
import android.content.Intent;
import android.os.IBinder;
import android.provider.Settings;

public class TestService extends Service {
    private static final String TEST_CHANNEL_ID = "test_channel_id";
    private static final String TEST_CHANNEL_NAME = "test_channel_name";
    private static final String TEST_CHANNEL_DESC = "Test channel";
    private static final int TEST_NOTIFICATION_ID = 11;

    @Override
    public IBinder onBind(Intent intent) {
        return new TestReceiver();
    }

    private class TestReceiver extends ITestReceiver.Stub {
        @Override
        public boolean isAppInactive(String pkg) {
            UsageStatsManager usm = getSystemService(UsageStatsManager.class);
            return usm.isAppInactive(pkg);
        }

        @Override
        public void generateAndSendNotification() {
            final NotificationManager notificationManager =
                    getSystemService(NotificationManager.class);
            final NotificationChannel mChannel = new NotificationChannel(TEST_CHANNEL_ID,
                    TEST_CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            // Configure the notification channel.
            mChannel.setDescription(TEST_CHANNEL_DESC);
            notificationManager.createNotificationChannel(mChannel);
            final Notification.Builder mBuilder =
                    new Notification.Builder(getApplicationContext(), TEST_CHANNEL_ID)
                            .setSmallIcon(android.R.drawable.ic_info)
                            .setContentTitle("My notification")
                            .setContentText("Hello World!");
            final PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 1,
                    new Intent(Settings.ACTION_SETTINGS), PendingIntent.FLAG_IMMUTABLE);
            mBuilder.setContentIntent(pi);
            notificationManager.notify(TEST_NOTIFICATION_ID, mBuilder.build());
        }
    }
}
