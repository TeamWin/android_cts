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

package android.app.cts.android.app.cts.tools;

import android.app.NotificationManager;
import android.app.PendingIntent.CanceledException;
import android.app.stubs.TestNotificationListener;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

public class NotificationHelper {

    private static final String TAG = NotificationHelper.class.getSimpleName();
    public static final long SHORT_WAIT_TIME = 100;
    public static final long MAX_WAIT_TIME = 2000;

    public enum SEARCH_TYPE {
        /**
         * Search for the notification only within the posted app. This returns enqueued
         * as well as posted notifications, so use with caution.
         */
        APP,
        /**
         * Search for the notification across all apps. Makes a binder call from the NLS to 
         * check currently posted notifications for all apps, which means it can return
         * notifications the NLS hasn't been informed about yet. 
         */
        LISTENER,
        /**
         * Search for the notification across all apps. Looks only in the list of notifications
         * that the listener has been informed about via onNotificationPosted.
         */
        POSTED
    }

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private Supplier<TestNotificationListener> mNotificationListener;

    public NotificationHelper(Context context, Supplier<TestNotificationListener> listener) {
        mContext = context;
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        mNotificationListener = listener;
    }

    public void clickNotification(int notificationId, boolean searchAll) throws CanceledException {
        findPostedNotification(null, notificationId,
                searchAll ? SEARCH_TYPE.LISTENER : SEARCH_TYPE.APP)
                .getNotification().contentIntent.send();
    }

    public StatusBarNotification findPostedNotification(String tag, int id,
            SEARCH_TYPE searchType) {
        // notification posting is asynchronous so it may take a few hundred ms to appear.
        // we will check for it for up to MAX_WAIT_TIME ms before giving up.
        for (long totalWait = 0; totalWait < MAX_WAIT_TIME; totalWait += SHORT_WAIT_TIME) {
            StatusBarNotification n = findNotificationNoWait(tag, id, searchType);
            if (n != null) {
                return n;
            }
            try {
                Thread.sleep(SHORT_WAIT_TIME);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return findNotificationNoWait(null, id, searchType);
    }

    /**
     * Returns true if the notification cannot be found. Polls for the notification to account for
     * delays in posting
     */
    public boolean isNotificationGone(int id, SEARCH_TYPE searchType) {
        // notification is a bit asynchronous so it may take a few ms to appear in
        // getActiveNotifications()
        // we will check for it for up to 300ms before giving up
        boolean found = false;
        for (int tries = 3; tries-- > 0; ) {
            // Need reset flag.
            found = false;
            for (StatusBarNotification sbn : getActiveNotifications(searchType)) {
                Log.d(TAG, "Found " + sbn.getKey());
                if (sbn.getId() == id) {
                    found = true;
                    break;
                }
            }
            if (!found) break;
            try {
                Thread.sleep(100);
            } catch (InterruptedException ex) {
                // pass
            }
        }
        return !found;
    }

    private StatusBarNotification findNotificationNoWait(String tag, int id,
            SEARCH_TYPE searchType) {
        for (StatusBarNotification sbn : getActiveNotifications(searchType)) {
            if (sbn.getId() == id && Objects.equal(sbn.getTag(), tag)) {
                return sbn;
            }
        }
        return null;
    }

    private ArrayList<StatusBarNotification> getActiveNotifications(SEARCH_TYPE searchType) {
        switch (searchType) {
            case APP:
                return new ArrayList<>(
                        Arrays.asList(mNotificationManager.getActiveNotifications()));
            case POSTED:
                return new ArrayList(mNotificationListener.get().mPosted);
            case LISTENER:
            default:
                return new ArrayList<>(
                        Arrays.asList(mNotificationListener.get().getActiveNotifications()));
        }
    }
}
