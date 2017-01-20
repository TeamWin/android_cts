/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.app.cts;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.stubs.R;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Telephony.Threads;
import android.service.notification.StatusBarNotification;
import android.test.AndroidTestCase;
import android.util.Log;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import java.util.Arrays;

public class NotificationManagerTest extends AndroidTestCase {
    final String TAG = NotificationManagerTest.class.getSimpleName();
    final boolean DEBUG = false;

    private NotificationManager mNotificationManager;
    private String mId;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // This will leave a set of channels on the device with each test run.
        mId = UUID.randomUUID().toString();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        // clear the deck so that our getActiveNotifications results are predictable
        mNotificationManager.cancelAll();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mNotificationManager.cancelAll();
    }

    public void testCreateChannel() throws InterruptedException {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.enableVibration(true);
        channel.setVibrationPattern(new long[] {5, 8, 2, 1});
        channel.setSound(new Uri.Builder().scheme("test").build());
        channel.setLights(true);
        channel.setBypassDnd(true);
        channel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
        try {
            mNotificationManager.createNotificationChannel(channel);
            final NotificationChannel createdChannel =
                    mNotificationManager.getNotificationChannel("id");
            compareChannels(channel, createdChannel);
            // Lockscreen Visibility and canBypassDnd no longer settable.
            assertTrue(createdChannel.getLockscreenVisibility() != Notification.VISIBILITY_SECRET);
            assertFalse(createdChannel.canBypassDnd());
        } finally {
            mNotificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    public void testCreateSameChannelDoesNotUpdate() throws InterruptedException {
        final NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        try {
            mNotificationManager.createNotificationChannel(channel);
            final NotificationChannel channelDupe =
                    new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channelDupe);
            final NotificationChannel createdChannel =
                    mNotificationManager.getNotificationChannel(mId);
            compareChannels(channel, createdChannel);
        } finally {
            mNotificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    public void testCreateChannelAlreadyExistsNoOp() {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        try {
            mNotificationManager.createNotificationChannel(channel);
            NotificationChannel channelDupe =
                    new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_HIGH);
            mNotificationManager.createNotificationChannel(channelDupe);
            compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        } finally {
            mNotificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    public void testCreateChannelInvalidImportance() {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_UNSPECIFIED);
        try {
            mNotificationManager.createNotificationChannel(channel);
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testDeleteChannel() {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_LOW);
        mNotificationManager.createNotificationChannel(channel);
        compareChannels(channel, mNotificationManager.getNotificationChannel(channel.getId()));
        mNotificationManager.deleteNotificationChannel(channel.getId());
        assertNull(mNotificationManager.getNotificationChannel(channel.getId()));
    }

    public void testCannotDeleteDefaultChannel() {
        try {
            mNotificationManager.deleteNotificationChannel(NotificationChannel.DEFAULT_CHANNEL_ID);
            fail("Deleted default channel");
        } catch (IllegalArgumentException e) {
            //success
        }
    }

    public void testGetChannel() {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", NotificationManager.IMPORTANCE_MIN);
        try {
            mNotificationManager.createNotificationChannel(channel1);
            mNotificationManager.createNotificationChannel(channel2);
            mNotificationManager.createNotificationChannel(channel3);
            mNotificationManager.createNotificationChannel(channel4);

            compareChannels(channel2,
                    mNotificationManager.getNotificationChannel(channel2.getId()));
            compareChannels(channel3,
                    mNotificationManager.getNotificationChannel(channel3.getId()));
            compareChannels(channel1,
                    mNotificationManager.getNotificationChannel(channel1.getId()));
            compareChannels(channel4,
                    mNotificationManager.getNotificationChannel(channel4.getId()));
        } finally {
            mNotificationManager.deleteNotificationChannel(channel1.getId());
            mNotificationManager.deleteNotificationChannel(channel2.getId());
            mNotificationManager.deleteNotificationChannel(channel3.getId());
            mNotificationManager.deleteNotificationChannel(channel4.getId());
        }
    }

    public void testGetChannels() {
        NotificationChannel channel1 =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        NotificationChannel channel2 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name2", NotificationManager.IMPORTANCE_HIGH);
        NotificationChannel channel3 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name3", NotificationManager.IMPORTANCE_LOW);
        NotificationChannel channel4 =
                new NotificationChannel(
                        UUID.randomUUID().toString(), "name4", NotificationManager.IMPORTANCE_MIN);

        Map<String, NotificationChannel> channelMap = new HashMap<>();
        channelMap.put(channel1.getId(), channel1);
        channelMap.put(channel2.getId(), channel2);
        channelMap.put(channel3.getId(), channel3);
        channelMap.put(channel4.getId(), channel4);
        try {
            mNotificationManager.createNotificationChannel(channel1);
            mNotificationManager.createNotificationChannel(channel2);
            mNotificationManager.createNotificationChannel(channel3);
            mNotificationManager.createNotificationChannel(channel4);

            mNotificationManager.deleteNotificationChannel(channel3.getId());

            List<NotificationChannel> channels = mNotificationManager.getNotificationChannels();
            for (NotificationChannel nc : channels) {
                if (NotificationChannel.DEFAULT_CHANNEL_ID.equals(nc.getId())) {
                    continue;
                }
                assertFalse(channel3.getId().equals(nc.getId()));
                compareChannels(channelMap.get(nc.getId()), nc);
            }
        } finally {
            mNotificationManager.deleteNotificationChannel(channel1.getId());
            mNotificationManager.deleteNotificationChannel(channel2.getId());
            mNotificationManager.deleteNotificationChannel(channel3.getId());
            mNotificationManager.deleteNotificationChannel(channel4.getId());
        }
    }

    public void testRecreateDeletedChannel() {
        NotificationChannel channel =
                new NotificationChannel(mId, "name", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setShowBadge(true);
        NotificationChannel newChannel = new NotificationChannel(
                channel.getId(), channel.getName(), NotificationManager.IMPORTANCE_HIGH);
        try {
            mNotificationManager.createNotificationChannel(channel);
            mNotificationManager.deleteNotificationChannel(channel.getId());

            mNotificationManager.createNotificationChannel(newChannel);

            compareChannels(channel,
                    mNotificationManager.getNotificationChannel(newChannel.getId()));

        } finally {
            mNotificationManager.deleteNotificationChannel(channel.getId());
        }
    }

    public void testNotify() {
        mNotificationManager.cancelAll();

        final int id = 1;
        sendNotification(id, R.drawable.black);
        // test updating the same notification
        sendNotification(id, R.drawable.blue);
        sendNotification(id, R.drawable.yellow);

        // assume that sendNotification tested to make sure individual notifications were present
        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() != id) {
                fail("we got back other notifications besides the one we posted: "
                        + sbn.getKey());
            }
        }
    }

    public void testCancel() {
        final int id = 9;
        sendNotification(id, R.drawable.black);
        mNotificationManager.cancel(id);

        if (!checkNotificationExistence(id, /*shouldExist=*/ false)) {
            fail("canceled notification was still alive, id=" + id);
        }
    }

    public void testCancelAll() {
        sendNotification(1, R.drawable.black);
        sendNotification(2, R.drawable.blue);
        sendNotification(3, R.drawable.yellow);

        if (DEBUG) {
            Log.d(TAG, "posted 3 notifications, here they are: ");
            StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
            for (StatusBarNotification sbn : sbns) {
                Log.d(TAG, "  " + sbn);
            }
            Log.d(TAG, "about to cancel...");
        }
        mNotificationManager.cancelAll();

        StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        assertTrue("notification list was not empty after cancelAll", sbns.length == 0);
    }

    private void sendNotification(final int id, final int icon) {
        final Intent intent = new Intent(Intent.ACTION_MAIN, Threads.CONTENT_URI);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);

        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent, 0);
        final Notification notification = new Notification.Builder(mContext)
                .setSmallIcon(icon)
                .setWhen(System.currentTimeMillis())
                .setContentTitle("notify#" + id)
                .setContentText("This is #" + id + "notification  ")
                .setContentIntent(pendingIntent)
                .build();
        mNotificationManager.notify(id, notification);


        if (!checkNotificationExistence(id, /*shouldExist=*/ true)) {
            fail("couldn't find posted notification id=" + id);
        }
    }

    private boolean checkNotificationExistence(int id, boolean shouldExist) {
        boolean found = false;
        final StatusBarNotification[] sbns = mNotificationManager.getActiveNotifications();
        for (StatusBarNotification sbn : sbns) {
            if (sbn.getId() == id) {
                found = true;
                break;
            }
        }
        return found == shouldExist;
    }

    private void compareChannels(NotificationChannel expected, NotificationChannel actual) {
        if (actual == null) {
            fail("actual channel is null");
            return;
        }
        if (expected == null) {
            fail("expected channel is null");
            return;
        }
        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getName(), actual.getName());
        assertEquals(expected.shouldVibrate(), actual.shouldVibrate());
        assertEquals(expected.shouldShowLights(), actual.shouldShowLights());
        assertEquals(expected.getImportance(), actual.getImportance());
        assertEquals(expected.getSound(), actual.getSound());
        assertTrue(Arrays.equals(expected.getVibrationPattern(), actual.getVibrationPattern()));
    }
}
