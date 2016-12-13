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
import android.view.View;
import android.view.ViewGroup;

import com.android.cts.verifier.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class NotificationAssistantVerifierActivity extends InteractiveVerifierActivity
        implements Runnable {
    private static final String TAG = "NoAssistantVerifier";

    protected static final String ASSISTANT_PATH = "com.android.cts.verifier/" +
            "com.android.cts.verifier.notifications.MockAssistant";
    private static final String ENABLED_NOTIFICATION_ASSISTANT_SETTING =
            "enabled_notification_assistant";
    private static final String OTHER_PKG = "android";

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
        tests.add(new AdjustNotificationTest());
        tests.add(new CreateChannelTest());
        tests.add(new UpdateChannelTest());
        tests.add(new DeleteChannelTest());
        tests.add(new IsDisabledTest());
        tests.add(new ServiceStoppedTest());
        tests.add(new NotificationNotEnqueuedTest());
        tests.add(new NotificationNotReceivedTest());
        return tests;
    }

    @SuppressLint("NewApi")
    private void sendNotifications() {
        sendNotifications(null);
    }

    @SuppressLint("NewApi")
    private void sendNotifications(NotificationChannel channel) {
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

        Notification n1 = new Notification.Builder(mContext)
                .setContentTitle("ClearTest 1")
                .setContentText(mTag1.toString())
                .setPriority(Notification.PRIORITY_LOW)
                .setSmallIcon(mIcon1)
                .setWhen(mWhen1)
                .setDeleteIntent(makeIntent(1, mTag1))
                .setOnlyAlertOnce(true)
                .setChannel(channel == null ? NotificationChannel.DEFAULT_CHANNEL_ID
                        : channel.getId())
                .build();
        mNm.notify(mTag1, mId1, n1);
        mFlag1 = Notification.FLAG_ONLY_ALERT_ONCE;

        Notification n2 = new Notification.Builder(mContext)
                .setContentTitle("ClearTest 2")
                .setContentText(mTag2.toString())
                .setPriority(Notification.PRIORITY_HIGH)
                .setSmallIcon(mIcon2)
                .setWhen(mWhen2)
                .setDeleteIntent(makeIntent(2, mTag2))
                .setAutoCancel(true)
                .setChannel(channel == null ? NotificationChannel.DEFAULT_CHANNEL_ID
                        : channel.getId())
                .build();
        mNm.notify(mTag2, mId2, n2);
        mFlag2 = Notification.FLAG_AUTO_CANCEL;

        Notification n3 = new Notification.Builder(mContext)
                .setContentTitle("ClearTest 3")
                .setContentText(mTag3.toString())
                .setPriority(Notification.PRIORITY_LOW)
                .setSmallIcon(mIcon3)
                .setWhen(mWhen3)
                .setDeleteIntent(makeIntent(3, mTag3))
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setChannel(channel == null ? NotificationChannel.DEFAULT_CHANNEL_ID
                        : channel.getId())
                .build();
        mNm.notify(mTag3, mId3, n3);
        mFlag3 = Notification.FLAG_ONLY_ALERT_ONCE | Notification.FLAG_AUTO_CANCEL;
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
            sendNotifications();
            status = READY;
            // wait for notifications to move through the system
            delay();
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
            sendNotifications();
            status = READY;
            // wait for notifications to move through the system
            delay();
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
            sendNotifications();
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
            mNm.cancelAll();
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
            sendNotifications();
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
            mNm.cancelAll();
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
            sendNotifications();
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
            mNm.cancelAll();
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
            sendNotifications();
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
            mNm.cancelAll();
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
            sendNotifications();
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
            mNm.cancelAll();
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class AdjustNotificationTest extends InteractiveTestCase {
        private Bundle bundle1 = new Bundle();
        private Bundle bundle2 = new Bundle();
        private Bundle bundle3 = new Bundle();
        private SnoozeCriterion snooze1 = new SnoozeCriterion("id1", "1", "2");
        private SnoozeCriterion snooze2 = new SnoozeCriterion("id2", "2", "3");
        private String people1 = "people1";
        private String people2 = "people2";
        private ArrayList<String> people;
        private ArrayList<SnoozeCriterion> snooze;
        private NotificationChannel originalChannel = new NotificationChannel("new", "new",
                NotificationManager.IMPORTANCE_LOW);
        private NotificationChannel newChannel = new NotificationChannel("new", "new",
                NotificationManager.IMPORTANCE_LOW);

        @Override
        View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nas_adjustment_payload_intact);
        }

        @Override
        void setUp() {
            try {
                mNm.createNotificationChannel(newChannel, (createdChannel) -> {}, null);
                mNm.createNotificationChannel(originalChannel, (createdChannel) -> {}, null);
            } catch (Exception e) {

            }
            sendNotifications(originalChannel);
            status = READY;
            bundle1.putString(Adjustment.KEY_CHANNEL_ID, newChannel.getId());
            people = new ArrayList<>();
            people.add(people1);
            people.add(people2);
            bundle2.putStringArrayList(Adjustment.KEY_PEOPLE, people);
            snooze = new ArrayList<>();
            snooze.add(snooze1);
            snooze.add(snooze2);
            bundle3.putParcelableArrayList(Adjustment.KEY_SNOOZE_CRITERIA, snooze);
            delay();
        }

        @Override
        void test() {
            if (status == READY) {
                MockAssistant.applyAdjustment(mContext, mTag1, bundle1);
                MockAssistant.applyAdjustment(mContext, mTag2, bundle2);
                MockAssistant.applyAdjustment(mContext, mTag3, bundle3);
                status = RETEST;
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
                                        pass &= checkEquals(newChannel,
                                                payload.getParcelable(KEY_CHANNEL),
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
                                        pass &= checkEquals(originalChannel,
                                                payload.getParcelable(KEY_CHANNEL),
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
                                        pass &= checkEquals(originalChannel,
                                                payload.getParcelable(KEY_CHANNEL),
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
            mNm.cancelAll();
            mNm.deleteNotificationChannel(originalChannel.getId());
            mNm.deleteNotificationChannel(newChannel.getId());
            sleep(1000);
            MockAssistant.resetListenerData(mContext);
            delay();
        }
    }

    private class CreateChannelTest extends InteractiveTestCase {
        private NotificationChannel channel = new NotificationChannel("channelForOtherPkg", "new",
                NotificationManager.IMPORTANCE_LOW);

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
        private NotificationChannel channel = new NotificationChannel("1channelForOtherPkg", "new",
                NotificationManager.IMPORTANCE_LOW);

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
            MockAssistant.deleteChannel(
                    mContext, OTHER_PKG, channel.getId());
            delay();
        }
    }

    private class UpdateChannelTest extends InteractiveTestCase {
        private String id = "channelToUpdate";
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
            updatedChannel.setSound(new Uri.Builder().appendPath("sound").build());
            updatedChannel.setLights(true);
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

    private boolean compareChannels(NotificationChannel expected, NotificationChannel actual) {
        boolean pass = true;
        String msg = "Channel mismatch (%s, %s)";
        if (actual == null || expected == null) {
            logWithStack(String.format("Channel mismatch (%s, %s)", expected, actual));
            return false;
        }
        pass &= checkEquals(expected.getId(), actual.getId(), msg);
        pass &= checkEquals(expected.getName(), actual.getName(), msg);
        pass &= checkEquals(expected.shouldVibrate(), actual.shouldVibrate(), msg);
        pass &= checkEquals(expected.shouldShowLights(), actual.shouldShowLights(), msg);
        pass &= checkEquals(expected.getImportance(), actual.getImportance(), msg);
        pass &= checkEquals(
                expected.getLockscreenVisibility(), actual.getLockscreenVisibility(), msg);
        pass &= checkEquals(expected.getSound(), actual.getSound(), msg);
        pass &= checkEquals(expected.canBypassDnd(), actual.canBypassDnd(), msg);
        pass &= checkEquals(expected.getVibrationPattern(), actual.getVibrationPattern(), msg);
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
