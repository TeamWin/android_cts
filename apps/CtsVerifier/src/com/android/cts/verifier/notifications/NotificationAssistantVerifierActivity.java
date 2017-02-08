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
 * limitations under the License.
 */

package com.android.cts.verifier.notifications;

import static android.service.notification.NotificationListenerService.REASON_LISTENER_CANCEL;

import static com.android.cts.verifier.notifications.MockAssistant.EXTRA_PAYLOAD;
import static com.android.cts.verifier.notifications.MockAssistant.EXTRA_TAG;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_CHANNEL;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_FLAGS;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_ICON;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_ID;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_PACKAGE;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_PEOPLE;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_REASON;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_SNOOZE_CRITERIA;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_TAG;
import static com.android.cts.verifier.notifications.MockAssistant.KEY_WHEN;
import static com.android.cts.verifier.notifications.MockAssistant.StatusCatcher;
import static com.android.cts.verifier.notifications.MockAssistant.StringListResultCatcher;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.provider.Settings;
import android.provider.Settings.Secure;
import android.service.notification.Adjustment;
import android.service.notification.SnoozeCriterion;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class NotificationAssistantVerifierActivity extends InteractiveVerifierActivity
        implements Runnable {
    private static final String TAG = "NoAssistantVerifier";

    protected static final String ASSISTANT_PATH = "com.android.cts.verifier/" +
            "com.android.cts.verifier.notifications.MockAssistant";
    private static final String ENABLED_NOTIFICATION_ASSISTANT_SETTING =
            "enabled_notification_assistant";
    private static final String THIS_PKG = "com.android.cts.verifier";
    private static final String OTHER_PKG = "android";

    public static final String ORIGINAL_CHANNEL_ID = TAG + ": original";
    public static final String NEW_CHANNEL_ID = TAG + ": new";

    private String mTag1;
    private String mTag2;
    private String mTag3;
    private int mIcon1;
    private int mIcon2;
    private int mIcon3;
    private int mId1;
    private int mId2;
    private int mId3;
    private long mWhen1;
    private long mWhen2;
    private long mWhen3;
    private int mFlag1;
    private int mFlag2;
    private int mFlag3;

    @Override
    int getTitleResource() {
        return R.string.nas_test;
    }

    @Override
    int getInstructionsResource() {
        return R.string.nas_info;
    }

    // Test Setup

    @Override
    protected List<InteractiveTestCase> createTestItems() {
        List<InteractiveTestCase> tests = new ArrayList<>();
        tests.add(new IsEnabledTest());
        tests.add(new ServiceStartedTest());
        tests.add(new NotificationEnqueuedTest());
        tests.add(new NotificationReceivedTest());
        tests.add(new DataIntactTest());
        tests.add(new DismissOneTest());
        tests.add(new DismissOneWithReasonTest());
        tests.add(new DismissAllTest());
        tests.add(new CreateChannelTest());
        tests.add(new UpdateChannelTest());
        tests.add(new DeleteChannelTest());
        tests.add(new UpdateLiveChannelTest());
        tests.add(new AdjustNotificationTest());
        tests.add(new AdjustEnqueuedNotificationTest());
        tests.add(new SnoozeNotificationUntilContextTest());
        tests.add(new UnsnoozeNotificationTest());
        tests.add(new IsDisabledTest());
        tests.add(new ServiceStoppedTest());
        tests.add(new NotificationNotEnqueuedTest());
        tests.add(new NotificationNotReceivedTest());
        return tests;
    }

    @SuppressLint("NewApi")
    private void sendNotifications(String channelId) {
        mTag1 = UUID.randomUUID().toString();
        mTag2 = UUID.randomUUID().toString();
        mTag3 = UUID.randomUUID().toString();

        mNm.cancelAll();

        mWhen1 = System.currentTimeMillis() + 1;
        mWhen2 = System.currentTimeMillis() + 2;
        mWhen3 = System.currentTimeMillis() + 3;

        mIcon1 = R.drawable.ic_stat_alice;
        mIcon2 = R.drawable.ic_stat_bob;
        mIcon3 = R.drawable.ic_stat_charlie;

        mId1 = NOTIFICATION_ID + 1;
        mId2 = NOTIFICATION_ID + 2;
        mId3 = NOTIFICATION_ID + 3;

        mPackageString = "com.android.cts.verifier";

        Notification n1 = new Notification.Builder(mContext, channelId)
                .setContentTitle("One")
                .setSortKey(Adjustment.KEY_CHANNEL_ID)
                .setContentText(mTag1.toString())
                .setPriority(Notification.PRIORITY_LOW)
                .setSmallIcon(mIcon1)
                .setWhen(mWhen1)
                .setDeleteIntent(makeIntent(1, mTag1))
                .setOnlyAlertOnce(true)
                .build();
        mNm.notify(mTag1, mId1, n1);
        mFlag1 = Notification.FLAG_ONLY_ALERT_ONCE;

        Notification n2 = new Notification.Builder(mContext, channelId)
                .setContentTitle("Two")
                .setSortKey(Adjustment.KEY_PEOPLE)
                .setContentText(mTag2.toString())
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(mIcon2)
                .setWhen(mWhen2)
                .setDeleteIntent(makeIntent(2, mTag2))
                .setAutoCancel(true)
                .build();
        mNm.notify(mTag2, mId2, n2);
        mFlag2 = Notification.FLAG_AUTO_CANCEL;

        Notification n3 = new Notification.Builder(mContext, channelId)
                .setContentTitle("Three")
                .setSortKey(Adjustment.KEY_SNOOZE_CRITERIA)
                .setContentText(mTag3.toString())
                .setPriority(Notification.PRIORITY_LOW)
                .setSmallIcon(mIcon3)
                .setWhen(mWhen3)
                .setDeleteIntent(makeIntent(3, mTag3))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build();
        mNm.notify(mTag3, mId3, n3);
        mFlag3 = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
    }

    private void createChannels() {
        try {
            NotificationChannel newChannel = new NotificationChannel(
                    NEW_CHANNEL_ID, NEW_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            mNm.createNotificationChannel(newChannel);
        } catch (Exception e) {
            Log.e(TAG, "failed to create channel", e);
        }
        try {
            NotificationChannel originalChannel = new NotificationChannel(ORIGINAL_CHANNEL_ID,
                    ORIGINAL_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            mNm.createNotificationChannel(originalChannel);
        } catch (Exception e) {
            Log.e(TAG, "failed to create channel", e);
        }
    }

    private void deleteChannels() {
        mNm.cancelAll();
        mNm.deleteNotificationChannel(ORIGINAL_CHANNEL_ID);
        mNm.deleteNotificationChannel(NEW_CHANNEL_ID);
    }

    // Tests

    protected class IsEnabledTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createSettingsItem(parent, R.string.nas_enable_service);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        void test() {
            mNm.cancelAll();
            Intent settings = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
            if (settings.resolveActivity(mPackageManager) == null) {
                logFail("no settings activity");
                status = FAIL;
            } else {
                String listeners = Secure.getString(getContentResolver(),
                        ENABLED_NOTIFICATION_ASSISTANT_SETTING);
                if (listeners != null && listeners.equals(ASSISTANT_PATH)) {
                    status = PASS;
                } else {
                    status = WAIT_FOR_USER;
                }
                next();
            }
        }

        void tearDown() {
            // wait for the service to start
            delay();
        }
    }

    protected class ServiceStartedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_service_started);
        }

        @Override
        void test() {
            MockAssistant.probeListenerStatus(mContext,
                    new MockAssistant.StatusCatcher() {
                        @Override
                        public void accept(int result) {
                            if (result == Activity.RESULT_OK) {
                                status = PASS;
                                next();
                            } else {
                                logFail();
                                status = RETEST;
                                delay();
                            }
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockListener.resetListenerData(mContext);
            delay();
        }
    }

    private class NotificationEnqueuedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_note_enqueued_received);

        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void tearDown() {
            deleteChannels();
        }

        @Override
        void test() {
            MockAssistant.probeListenerEnqueued(mContext,
                    new StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            if (result != null && result.size() > 0 && result.contains(mTag1)) {
                                status = PASS;
                            } else {
                                logFail();
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }
    }

    private class NotificationReceivedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_note_received);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            // wait for notifications to move through the system
            delay();
        }

        @Override
        void tearDown() {
            deleteChannels();
        }

        @Override
        void test() {
            MockAssistant.probeListenerPosted(mContext,
                    new StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            if (result != null && result.size() > 0 && result.contains(mTag1)) {
                                status = PASS;
                            } else {
                                logFail();
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }
    }

    private class DataIntactTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_payload_intact);
        }

        @Override
        void test() {
            MockAssistant.probeListenerPayloads(mContext,
                    new MockAssistant.BundleListResultCatcher() {
                        @Override
                        public void accept(ArrayList<Parcelable> result) {
                            Set<String> found = new HashSet<String>();
                            if (result == null || result.size() == 0) {
                                status = FAIL;
                                return;
                            }
                            boolean pass = true;
                            for (Parcelable payloadData : result) {
                                Bundle payload = (Bundle) payloadData;
                                pass &= checkEquals(mPackageString,
                                        payload.getString(KEY_PACKAGE),
                                        "data integrity test: notification package (%s, %s)");
                                String tag = payload.getString(KEY_TAG);
                                if (mTag1.equals(tag)) {
                                    found.add(mTag1);
                                    pass &= checkEquals(mIcon1, payload.getInt(KEY_ICON),
                                            "data integrity test: notification icon (%d, %d)");
                                    pass &= checkFlagSet(mFlag1, payload.getInt(KEY_FLAGS),
                                            "data integrity test: notification flags (%d, %d)");
                                    pass &= checkEquals(mId1, payload.getInt(KEY_ID),
                                            "data integrity test: notification ID (%d, %d)");
                                    pass &= checkEquals(mWhen1, payload.getLong(KEY_WHEN),
                                            "data integrity test: notification when (%d, %d)");
                                } else if (mTag2.equals(tag)) {
                                    found.add(mTag2);
                                    pass &= checkEquals(mIcon2, payload.getInt(KEY_ICON),
                                            "data integrity test: notification icon (%d, %d)");
                                    pass &= checkFlagSet(mFlag2, payload.getInt(KEY_FLAGS),
                                            "data integrity test: notification flags (%d, %d)");
                                    pass &= checkEquals(mId2, payload.getInt(KEY_ID),
                                            "data integrity test: notification ID (%d, %d)");
                                    pass &= checkEquals(mWhen2, payload.getLong(KEY_WHEN),
                                            "data integrity test: notification when (%d, %d)");
                                } else if (mTag3.equals(tag)) {
                                    found.add(mTag3);
                                    pass &= checkEquals(mIcon3, payload.getInt(KEY_ICON),
                                            "data integrity test: notification icon (%d, %d)");
                                    pass &= checkFlagSet(mFlag3, payload.getInt(KEY_FLAGS),
                                            "data integrity test: notification flags (%d, %d)");
                                    pass &= checkEquals(mId3, payload.getInt(KEY_ID),
                                            "data integrity test: notification ID (%d, %d)");
                                    pass &= checkEquals(mWhen3, payload.getLong(KEY_WHEN),
                                            "data integrity test: notification when (%d, %d)");
                                } else {
                                    pass = false;
                                    logFail("unexpected notification tag: " + tag);
                                }
                            }

                            pass &= found.size() == 3;
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            mNm.cancelAll();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class DismissOneTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_clear_one);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.clearOne(mContext, mTag1, mId1);
                status = RETEST;
            } else {
                MockAssistant.probeListenerRemoved(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() != 0
                                        && result.contains(mTag1)
                                        && !result.contains(mTag2)
                                        && !result.contains(mTag3)) {
                                    status = PASS;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                next();
                            }
                        });
            }
            delay();
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class DismissOneWithReasonTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_clear_one_reason);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.clearOne(mContext, mTag2, mId2);
                status = RETEST;
            } else {
                MockAssistant.probeListenerRemovedWithReason(mContext,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    return;
                                }
                                boolean pass = true;
                                for (Parcelable payloadData : result) {
                                    Bundle payload = (Bundle) payloadData;
                                    pass &= checkEquals(mTag2,
                                            payload.getString(KEY_TAG),
                                            "data dismissal test: notification tag (%s, %s)");
                                    pass &= checkEquals(REASON_LISTENER_CANCEL,
                                            payload.getInt(KEY_REASON),
                                            "data dismissal test: reason (%d, %d)");
                                }
                                status = pass ? PASS : FAIL;
                                next();
                            }
                        });
            }
            delay();
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class DismissAllTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_clear_all);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.clearAll(mContext);
                status = RETEST;
            } else {
                MockAssistant.probeListenerRemoved(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() != 0
                                        && result.contains(mTag1)
                                        && result.contains(mTag2)
                                        && result.contains(mTag3)) {
                                    status = PASS;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                next();
                            }
                        });
            }
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class IsDisabledTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createSettingsItem(parent, R.string.nas_disable_service);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        void test() {
            String listeners = Secure.getString(getContentResolver(),
                    ENABLED_NOTIFICATION_ASSISTANT_SETTING);
            if (listeners == null || !listeners.equals(ASSISTANT_PATH)) {
                status = PASS;
            } else {
                status = WAIT_FOR_USER;
            }
            next();
        }

        @Override
        void tearDown() {
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class ServiceStoppedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_service_stopped);
        }

        @Override
        void test() {
            MockAssistant.probeListenerStatus(mContext,
                    new StatusCatcher() {
                        @Override
                        public void accept(int result) {
                            if (result == Activity.RESULT_OK) {
                                logFail();
                                status = FAIL;
                            } else {
                                status = PASS;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            // wait for intent to move through the system
            delay();
        }
    }

    private class NotificationNotEnqueuedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_note_missed_enqueued);

        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            MockAssistant.probeListenerEnqueued(mContext,
                    new StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            if (result == null || result.size() == 0) {
                                status = PASS;
                            } else {
                                logFail();
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class NotificationNotReceivedTest extends InteractiveTestCase {
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_note_missed);

        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            MockAssistant.probeListenerPosted(mContext,
                    new StringListResultCatcher() {
                        @Override
                        public void accept(List<String> result) {
                            if (result == null || result.size() == 0) {
                                status = PASS;
                            } else {
                                logFail();
                                status = FAIL;
                            }
                            next();
                        }
                    });
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class AdjustNotificationTest extends InteractiveTestCase {
        private ArrayList<String> people;
        private ArrayList<SnoozeCriterion> snooze;
        private Map<String, Bundle> adjustments;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_adjustment_payload_intact);
        }

        @Override
        void setUp() {
            createChannels();
            adjustments = getAdjustments();
            snooze = adjustments.get(Adjustment.KEY_SNOOZE_CRITERIA).getParcelableArrayList(
                    Adjustment.KEY_SNOOZE_CRITERIA);
            people = adjustments.get(Adjustment.KEY_PEOPLE).getStringArrayList(
                    Adjustment.KEY_PEOPLE);
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.probeListenerPosted(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() > 0 && result.contains(mTag1)) {
                                    MockAssistant.applyAdjustment(mContext, mTag1,
                                            adjustments.get(Adjustment.KEY_CHANNEL_ID));
                                    MockAssistant.applyAdjustment(mContext, mTag2,
                                            adjustments.get(Adjustment.KEY_PEOPLE));
                                    MockAssistant.applyAdjustment(mContext, mTag3,
                                            adjustments.get(Adjustment.KEY_SNOOZE_CRITERIA));
                                    status = RETEST;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                delay(3000);
                            }
                        });
            } else {
                MockAssistant.probeListenerPayloads(mContext,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                Set<String> found = new HashSet<>();
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    return;
                                }
                                boolean pass = true;
                                for (Parcelable payloadData : result) {
                                    Bundle payload = (Bundle) payloadData;
                                    pass &= checkEquals(mPackageString,
                                            payload.getString(KEY_PACKAGE),
                                            "data integrity test: notification package (%s, %s)");

                                    String tag = payload.getString(KEY_TAG);
                                    if (mTag1.equals(tag)) {
                                        found.add(mTag1);
                                        pass &= checkEquals(NEW_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else if (mTag2.equals(tag)) {
                                        found.add(mTag2);
                                        pass &= checkEquals(ORIGINAL_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(people.toArray(new String[]{}),
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else if (mTag3.equals(tag)) {
                                        found.add(mTag3);
                                        pass &= checkEquals(ORIGINAL_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");;
                                        pass &= checkEquals(snooze.toArray(new SnoozeCriterion[]{}),
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else {
                                        pass = false;
                                        logFail("unexpected notification tag: " + tag);
                                    }
                                }

                                pass &= found.size() == 3;
                                status = pass ? PASS : FAIL;
                                next();
                            }
                        });
            }
            delay(6000);  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class AdjustEnqueuedNotificationTest extends InteractiveTestCase {
        private ArrayList<String> people;
        private ArrayList<SnoozeCriterion> snooze;
        private Map<String, Bundle> adjustments;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_adjustment_enqueue_payload_intact);
        }

        @Override
        void setUp() {
            MockAssistant.adjustEnqueue(mContext);
            createChannels();
            adjustments = getAdjustments();
            snooze = adjustments.get(Adjustment.KEY_SNOOZE_CRITERIA).getParcelableArrayList(
                    Adjustment.KEY_SNOOZE_CRITERIA);
            people = adjustments.get(Adjustment.KEY_PEOPLE).getStringArrayList(
                    Adjustment.KEY_PEOPLE);
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.probeListenerEnqueued(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() > 0 && result.contains(mTag1)) {
                                    status = RETEST;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                next();
                            }
                        });
            } else {
                MockAssistant.probeListenerPayloads(mContext,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                Set<String> found = new HashSet<>();
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    return;
                                }
                                boolean pass = true;
                                for (Parcelable payloadData : result) {
                                    Bundle payload = (Bundle) payloadData;
                                    pass &= checkEquals(mPackageString,
                                            payload.getString(KEY_PACKAGE),
                                            "data integrity test: notification package (%s, %s)");

                                    String tag = payload.getString(KEY_TAG);
                                    if (mTag1.equals(tag)) {
                                        found.add(mTag1);
                                        pass &= checkEquals(NEW_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else if (mTag2.equals(tag)) {
                                        found.add(mTag2);
                                        pass &= checkEquals(ORIGINAL_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(people.toArray(new String[]{}),
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else if (mTag3.equals(tag)) {
                                        found.add(mTag3);
                                        pass &= checkEquals(ORIGINAL_CHANNEL_ID,
                                                ((NotificationChannel) payload.getParcelable(
                                                        KEY_CHANNEL)).getId(),
                                                "data integrity test: notification channel ("
                                                        + "%s, %s)");
                                        pass &= checkEquals(null,
                                                payload.getStringArray(KEY_PEOPLE),
                                                "data integrity test, notification people ("
                                                        + "%s, %s)");;
                                        pass &= checkEquals(snooze.toArray(new SnoozeCriterion[]{}),
                                                payload.getParcelableArray(KEY_SNOOZE_CRITERIA),
                                                "data integrity test, notification snooze ("
                                                        + "%s, %s)");
                                    } else {
                                        pass = false;
                                        logFail("unexpected notification tag: " + tag);
                                    }
                                }

                                pass &= found.size() == 3;
                                status = pass ? PASS : FAIL;
                                next();
                            }
                        });
            }
            delay(6000);  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            deleteChannels();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    public static Map<String, Bundle> getAdjustments() {
        Map<String, Bundle> adjustments = new ArrayMap<>();
        Bundle bundle1 = new Bundle();
        Bundle bundle2 = new Bundle();
        Bundle bundle3 = new Bundle();
        SnoozeCriterion snooze1 = new SnoozeCriterion("id1", "1", "2");
        SnoozeCriterion snooze2 = new SnoozeCriterion("id2", "2", "3");
        String people1 = "people1";
        String people2 = "people2";
        ArrayList<String> people = new ArrayList<>();
        ArrayList<SnoozeCriterion> snooze = new ArrayList<>();
        NotificationChannel newChannel = new NotificationChannel("new", "new",
                NotificationManager.IMPORTANCE_LOW);

        bundle1.putString(Adjustment.KEY_CHANNEL_ID, newChannel.getId());
        adjustments.put(Adjustment.KEY_CHANNEL_ID, bundle1);

        people.add(people1);
        people.add(people2);
        bundle2.putStringArrayList(Adjustment.KEY_PEOPLE, people);
        adjustments.put(Adjustment.KEY_PEOPLE, bundle2);

        snooze.add(snooze1);
        snooze.add(snooze2);
        bundle3.putParcelableArrayList(Adjustment.KEY_SNOOZE_CRITERIA, snooze);
        adjustments.put(Adjustment.KEY_SNOOZE_CRITERIA, bundle3);
        return adjustments;
    }

    private class CreateChannelTest extends InteractiveTestCase {
        private NotificationChannel channel = new NotificationChannel(UUID.randomUUID().toString(),
                "new", NotificationManager.IMPORTANCE_LOW);

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_create_channel);
        }

        @Override
        void setUp() {
            MockAssistant.createChannel(mContext, OTHER_PKG, channel);
            status = READY;
            delay();
        }

        @Override
        void test() {
            MockAssistant.probeChannels(mContext, OTHER_PKG,
                    new MockAssistant.BundleListResultCatcher() {
                        @Override
                        public void accept(ArrayList<Parcelable> result) {
                            if (result == null || result.size() == 0) {
                                logFail(result == null ? "no results"
                                        : String.format("%d results returned", result.size()));
                                status = FAIL;
                                return;
                            }
                            boolean pass = false;
                            for (Parcelable payloadData : result) {
                                NotificationChannel payload = (NotificationChannel) payloadData;
                                pass |= compareChannels(payload, channel);
                            }
                            status = pass ? PASS : FAIL;
                            next();
                        }
                    });

            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockAssistant.deleteChannel(
                    mContext, OTHER_PKG, channel.getId());
            delay();
        }
    }

    private class DeleteChannelTest extends InteractiveTestCase {
        private NotificationChannel channel = new NotificationChannel(UUID.randomUUID().toString(),
                "new", NotificationManager.IMPORTANCE_LOW);

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_delete_channel);
        }

        @Override
        void setUp() {
            MockAssistant.createChannel(mContext, OTHER_PKG, channel);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.probeChannels(mContext, OTHER_PKG,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    logFail();
                                    return;
                                }
                                boolean pass = false;
                                for (Parcelable payloadData : result) {
                                    NotificationChannel payload = (NotificationChannel) payloadData;
                                    pass |= compareChannels(channel, payload);
                                }
                                if (pass) {
                                    MockAssistant.deleteChannel(
                                            mContext, OTHER_PKG, channel.getId());
                                    status = RETEST;
                                }
                                delay();
                            }
                        });
            } else if (status == RETEST) {
                MockAssistant.probeChannels(mContext, OTHER_PKG,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() <= 1) {
                                    status = PASS;
                                } else {
                                    boolean fail = false;
                                    for (Parcelable payloadData : result) {
                                        NotificationChannel payload =
                                                (NotificationChannel) payloadData;
                                        fail |= compareChannels(channel, payload);
                                    }
                                    if (fail) {
                                        logFail();
                                        status = FAIL;
                                    } else {
                                        status = PASS;
                                    }
                                }
                                next();
                            }
                        });
            }

            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockAssistant.deleteChannel(
                    mContext, OTHER_PKG, channel.getId());
            delay();
        }
    }

    private class UpdateChannelTest extends InteractiveTestCase {
        private String id = UUID.randomUUID().toString();
        private NotificationChannel channel = new NotificationChannel(id, "new",
                NotificationManager.IMPORTANCE_LOW);
        private NotificationChannel updatedChannel = new NotificationChannel(id, "new",
                NotificationManager.IMPORTANCE_MIN);

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_update_channel);
        }

        @Override
        void setUp() {
            updatedChannel.setVibrationPattern(new long[] {467, 2478, 24738});
            updatedChannel.setSound(new Uri.Builder().appendPath("sound").build(), null);
            updatedChannel.enableLights(true);
            updatedChannel.enableVibration(true);
            updatedChannel.setBypassDnd(true);
            updatedChannel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            MockAssistant.createChannel(mContext, OTHER_PKG, channel);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.probeChannels(mContext, OTHER_PKG,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    next();
                                    return;
                                }
                                boolean pass = false;
                                for (Parcelable payloadData : result) {
                                    NotificationChannel payload = (NotificationChannel) payloadData;
                                    pass |= compareChannels(channel, payload);
                                }
                                if (pass) {
                                    MockAssistant.updateChannel(
                                            mContext, OTHER_PKG, updatedChannel);
                                    status = RETEST;
                                } else {
                                    status = FAIL;
                                    next();
                                }
                            }
                        });
            } else if (status == RETEST) {
                MockAssistant.probeChannels(mContext, OTHER_PKG,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    next();
                                    return;
                                }
                                boolean pass = false;
                                for (Parcelable payloadData : result) {
                                    NotificationChannel payload = (NotificationChannel) payloadData;
                                    pass |= compareChannels(updatedChannel, payload);
                                }
                                status = pass ? PASS : FAIL;
                                next();
                            }
                        });
            }

            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockAssistant.deleteChannel(
                    mContext, OTHER_PKG, channel.getId());
            delay();
        }
    }

    private class UpdateLiveChannelTest extends InteractiveTestCase {
        private String id = "channelToBlock-" + System.currentTimeMillis();
        private NotificationChannel channel = new NotificationChannel(id, "new",
                NotificationManager.IMPORTANCE_LOW);

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_block_channel);
        }

        @Override
        void setUp() {
            try {
                mNm.createNotificationChannel(channel);
            } catch (Exception e) {
                Log.e(TAG, "failed to create channel", e);
            }
            sendNotifications(channel.getId());
            status = READY;
            delay(6000);
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.probeListenerPosted(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() > 0 && result.contains(mTag1)) {
                                    channel.setImportance(NotificationManager.IMPORTANCE_NONE);
                                    MockAssistant.updateChannel(mContext, THIS_PKG, channel);
                                    status = RETEST;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                delay(3000);
                            }
                        });
            } else if (status == RETEST) {
                MockAssistant.probeListenerRemoved(mContext,
                        new StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() == 3) {
                                    status = PASS;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                next();
                            }
                        });
            }
            delay();  // in case the catcher never returns
        }

        @Override
        void tearDown() {
            MockAssistant.deleteChannel(mContext, THIS_PKG, channel.getId());
            mNm.cancel(mTag1, mId1);
            mNm.cancel(mTag2, mId2);
            mNm.cancel(mTag2, mId3);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class SnoozeNotificationUntilContextTest extends InteractiveTestCase {
        String snoozeContextId1 = "hello1";
        String snoozeContextId3 = "hello3";

        boolean queryIds = false;

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_snooze_context);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.snoozeUntilContext(mContext, mTag1, snoozeContextId1);
                MockAssistant.snoozeUntilContext(mContext, mTag3, snoozeContextId3);
                status = RETEST;
            } else {
                if (queryIds) {
                    MockAssistant.probeAssistantSnoozeContext(mContext,
                            new MockAssistant.BundleListResultCatcher() {
                                @Override
                                public void accept(ArrayList<Parcelable> result) {
                                    boolean foundOne = false;
                                    boolean foundTwo = false;
                                    boolean foundThree = false;
                                    for (Parcelable payloadData : result) {
                                        Bundle payload = (Bundle) payloadData;
                                        String tag = payload.getString(EXTRA_TAG);
                                        String snoozeCriterionId = payload.getString(EXTRA_PAYLOAD);
                                        if (mTag1.equals(tag)
                                                && snoozeContextId1.equals(snoozeCriterionId)) {
                                            foundOne = true;
                                        }
                                        if (mTag2.equals(tag)) {
                                            foundTwo = true;
                                        }
                                        if (mTag3.equals(tag)
                                                && snoozeContextId3.equals(snoozeCriterionId)) {
                                            foundThree = true;
                                        }
                                    }
                                    if (foundOne && foundThree && !foundTwo) {
                                        status = PASS;
                                    } else {
                                        logFail();
                                        status = FAIL;
                                    }
                                    delay();
                                }
                            });
                } else {
                    MockAssistant.probeListenerRemoved(mContext,
                            new StringListResultCatcher() {
                                @Override
                                public void accept(List<String> result) {
                                    if (result != null && result.size() != 0
                                            && result.contains(mTag1)
                                            && !result.contains(mTag2)
                                            && result.contains(mTag3)) {
                                        status = RETEST;
                                        queryIds = true;
                                    } else {
                                        logFail();
                                        status = FAIL;
                                    }
                                    delay();
                                }
                            });
                }
            }
            delay();
        }

        @Override
        void tearDown() {
            mNm.cancel(mTag1, mId1);
            mNm.cancel(mTag2, mId2);
            mNm.cancel(mTag2, mId3);
            deleteChannels();
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class UnsnoozeNotificationTest extends InteractiveTestCase {
        final static int READY_TO_SNOOZE = 0;
        final static int SNOOZED = 1;
        final static int READY_TO_UNSNOOZE = 2;
        final static int UNSNOOZED = 3;
        int state = -1;
        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_unsnooze_one);
        }

        @Override
        void setUp() {
            createChannels();
            sendNotifications(ORIGINAL_CHANNEL_ID);
            status = READY;
            delay();
        }

        @Override
        void test() {
            status = RETEST;

            if (state == READY_TO_SNOOZE) {
                MockAssistant.snoozeUntilContext(mContext, mTag1, "hello");
                state = SNOOZED;
            } else if (state == SNOOZED) {
                MockAssistant.probeListenerRemovedWithReason(mContext,
                        new MockAssistant.BundleListResultCatcher() {
                            @Override
                            public void accept(ArrayList<Parcelable> result) {
                                if (result == null || result.size() == 0) {
                                    status = FAIL;
                                    return;
                                }
                                boolean pass = true;
                                for (Parcelable payloadData : result) {
                                    Bundle payload = (Bundle) payloadData;
                                    pass &= checkEquals(mTag1,
                                            payload.getString(KEY_TAG),
                                            "data dismissal test: notification tag (%s, %s)");
                                    pass &= checkEquals(MockAssistant.REASON_SNOOZED,
                                            payload.getInt(KEY_REASON),
                                            "data dismissal test: reason (%d, %d)");
                                }
                                status = pass ? PASS : FAIL;
                                next();
                            }
                        });
            } else if (state == READY_TO_UNSNOOZE) {
                MockAssistant.unsnoozeOne(mContext, mTag1);
                state = UNSNOOZED;
            } else {
                MockAssistant.probeListenerPosted(mContext,
                        new MockAssistant.StringListResultCatcher() {
                            @Override
                            public void accept(List<String> result) {
                                if (result != null && result.size() != 0
                                        && result.contains(mTag1)) {
                                    status = PASS;
                                } else {
                                    logFail();
                                    status = FAIL;
                                }
                                next();
                            }
                        });
            }
        }

        @Override
        void tearDown() {
            deleteChannels();
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private boolean compareChannels(NotificationChannel expected, NotificationChannel actual) {
        boolean pass = true;
        String msg = "Channel mismatch (%s, %s)";
        if (actual == null || expected == null) {
            logWithStack(String.format("Channel mismatch (%s, %s)", expected, actual));
            return false;
        }
        pass &= checkEquals(expected.getId(), actual.getId(), msg);
        pass &= checkEquals(expected.getName(), actual.getName(), msg);
        pass &= checkEquals(expected.canShowBadge(), actual.canShowBadge(), msg);
        pass &= checkEquals(expected.shouldVibrate(), actual.shouldVibrate(), msg);
        pass &= checkEquals(expected.shouldShowLights(), actual.shouldShowLights(), msg);
        pass &= checkEquals(expected.getImportance(), actual.getImportance(), msg);
        pass &= checkEquals(
                expected.getLockscreenVisibility(), actual.getLockscreenVisibility(), msg);
        pass &= checkEquals(expected.getSound(), actual.getSound(), msg);
        pass &= checkEquals(expected.canBypassDnd(), actual.canBypassDnd(), msg);
        pass &= checkEquals(expected.getVibrationPattern(), actual.getVibrationPattern(), msg);
        pass &= checkEquals(expected.getAudioAttributes(), actual.getAudioAttributes(), msg);
        return pass;
    }

    protected View createSettingsItem(ViewGroup parent, int messageId) {
        return createUserItem(parent, R.string.nls_start_settings, messageId);
    }

    @Override
    public void launchSettings() {
        startActivity(new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
    }

    @Override
    public void actionPressed(View v) {
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            int id = ((Integer) tag).intValue();
            if (id == R.string.nls_start_settings) {
                launchSettings();
            } else if (id == R.string.attention_ready) {
                mCurrentTest.status = READY;
                next();
            }
        }
    }
}
