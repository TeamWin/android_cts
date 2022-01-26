/*
 * Copyright (C) 2022 The Android Open Source Project
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

import static android.app.Notification.FLAG_BUBBLE;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_ALL;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_NONE;
import static android.app.NotificationManager.BUBBLE_PREFERENCE_SELECTED;
import static android.app.stubs.BubblesTestService.EXTRA_TEST_CASE;
import static android.app.stubs.BubblesTestService.TEST_CALL;
import static android.app.stubs.BubblesTestService.TEST_MESSAGING;
import static android.app.stubs.SendBubbleActivity.BUBBLE_NOTIF_ID;
import static android.content.Intent.FLAG_ACTIVITY_MULTIPLE_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_DOCUMENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static org.junit.Assert.assertThrows;

import android.app.ActivityOptions;
import android.app.Instrumentation;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.stubs.BubbledActivity;
import android.app.stubs.BubblesTestService;
import android.app.stubs.R;
import android.app.stubs.SendBubbleActivity;
import android.app.stubs.TestNotificationListener;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.LocusId;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.FeatureUtil;
import com.android.compatibility.common.util.SystemUtil;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests bubbles related logic in NotificationManager.
 */
public class NotificationManagerBubbleTest extends BaseNotificationManagerTest {

    // use a value of 10000 for consistency with other CTS tests (see
    // android.server.wm.intentLaunchRunner#ACTIVITY_LAUNCH_TIMEOUT)
    private static final int ACTIVITY_LAUNCH_TIMEOUT = 10000;

    private BroadcastReceiver mBubbleBroadcastReceiver;
    private boolean mBubblesEnabledSettingToRestore;

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        // This setting is forced on / off for certain tests, save it & restore what's on the
        // device after tests are run
        mBubblesEnabledSettingToRestore = Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.NOTIFICATION_BUBBLES) == 1;

        // ensure listener access isn't allowed before test runs (other tests could put
        // TestListener in an unexpected state)
        toggleListenerAccess(false);

        // delay between tests so notifications aren't dropped by the rate limiter
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
        }
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();

        // Restore bubbles setting
        setBubblesGlobal(mBubblesEnabledSettingToRestore);
    }

    private void sendAndVerifyBubble(final int id, Notification.Builder builder,
            Notification.BubbleMetadata data, boolean shouldBeBubble) {
        setUpNotifListener();

        final Intent intent = new Intent(mContext, BubbledActivity.class);

        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP
                | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.setAction(Intent.ACTION_MAIN);
        final PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_MUTABLE_UNAUDITED);

        if (data == null) {
            data = new Notification.BubbleMetadata.Builder(pendingIntent,
                    Icon.createWithResource(mContext, R.drawable.black))
                    .build();
        }
        if (builder == null) {
            builder = new Notification.Builder(mContext, NOTIFICATION_CHANNEL_ID)
                    .setSmallIcon(R.drawable.black)
                    .setWhen(System.currentTimeMillis())
                    .setContentTitle("notify#" + id)
                    .setContentText("This is #" + id + "notification  ")
                    .setContentIntent(pendingIntent);
        }
        builder.setBubbleMetadata(data);

        Notification notif = builder.build();
        mNotificationManager.notify(id, notif);

        verifyNotificationBubbleState(id, shouldBeBubble);
    }

    /**
     * Make sure {@link #setUpNotifListener()} is called prior to sending the notif and verifying
     * in this method.
     */
    private void verifyNotificationBubbleState(int id, boolean shouldBeBubble) {
        try {
            // FLAG_BUBBLE relies on notification being posted, wait for notification listener
            Thread.sleep(500);
        } catch (InterruptedException ex) {
        }

        for (StatusBarNotification sbn : mListener.mPosted) {
            if (sbn.getId() == id) {
                boolean isBubble = (sbn.getNotification().flags & FLAG_BUBBLE) != 0;
                if (isBubble != shouldBeBubble) {
                    final String failure = shouldBeBubble
                            ? "Notification with id= " + id + " wasn't a bubble"
                            : "Notification with id= " + id + " was a bubble and shouldn't be";
                    fail(failure);
                } else {
                    // pass
                    return;
                }
            }
        }
        fail("Couldn't find posted notification with id= " + id);
    }

    private void setBubblesGlobal(boolean enabled)
            throws InterruptedException {
        SystemUtil.runWithShellPermissionIdentity(() ->
                Settings.Secure.putInt(mContext.getContentResolver(),
                        Settings.Secure.NOTIFICATION_BUBBLES, enabled ? 1 : 0));
        Thread.sleep(500); // wait for ranking update
    }

    private void setBubblesAppPref(int pref) throws Exception {
        int userId = mContext.getUser().getIdentifier();
        String pkg = mContext.getPackageName();
        String command = " cmd notification set_bubbles " + pkg
                + " " + Integer.toString(pref)
                + " " + userId;
        runCommand(command, InstrumentationRegistry.getInstrumentation());
        Thread.sleep(500); // wait for ranking update
    }

    private void setBubblesChannelAllowed(boolean allowed) throws Exception {
        int userId = mContext.getUser().getIdentifier();
        String pkg = mContext.getPackageName();
        String command = " cmd notification set_bubbles_channel " + pkg
                + " " + NOTIFICATION_CHANNEL_ID
                + " " + Boolean.toString(allowed)
                + " " + userId;
        runCommand(command, InstrumentationRegistry.getInstrumentation());
        Thread.sleep(500); // wait for ranking update
    }

    /**
     * Starts an activity that is able to send a bubble; also handles unlocking the device.
     * Any tests that use this method should be sure to call {@link #cleanupSendBubbleActivity()}
     * to unregister the related broadcast receiver.
     *
     * @return the SendBubbleActivity that was opened.
     */
    private SendBubbleActivity startSendBubbleActivity() {
        final CountDownLatch latch = new CountDownLatch(2);
        mBubbleBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                latch.countDown();
            }
        };
        IntentFilter filter = new IntentFilter(SendBubbleActivity.BUBBLE_ACTIVITY_OPENED);
        mContext.registerReceiver(mBubbleBroadcastReceiver, filter);

        // Start & get the activity
        Class clazz = SendBubbleActivity.class;

        Instrumentation.ActivityResult result =
                new Instrumentation.ActivityResult(0, new Intent());
        Instrumentation.ActivityMonitor monitor =
                new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
        InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

        Intent i = new Intent(mContext, SendBubbleActivity.class);
        i.setFlags(FLAG_ACTIVITY_NEW_TASK);
        InstrumentationRegistry.getInstrumentation().startActivitySync(i);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        SendBubbleActivity sendBubbleActivity = (SendBubbleActivity) monitor.waitForActivity();

        // Make sure device is unlocked
        KeyguardManager keyguardManager = mContext.getSystemService(KeyguardManager.class);
        keyguardManager.requestDismissKeyguard(sendBubbleActivity,
                new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissSucceeded() {
                        latch.countDown();
                    }
                });
        try {
            latch.await(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return sendBubbleActivity;
    }

    private void cleanupSendBubbleActivity() {
        mContext.unregisterReceiver(mBubbleBroadcastReceiver);
    }

    public void testCanBubble_ranking() throws Exception {
        if ((mActivityManager.isLowRamDevice() && !FeatureUtil.isWatch())
                || FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            return;
        }

        // turn on bubbles globally
        setBubblesGlobal(true);

        assertEquals(1, Settings.Secure.getInt(
                mContext.getContentResolver(), Settings.Secure.NOTIFICATION_BUBBLES));

        toggleListenerAccess(true);
        Thread.sleep(500); // wait for listener to be allowed

        mListener = TestNotificationListener.getInstance();
        assertNotNull(mListener);

        sendNotification(1, R.drawable.black);
        Thread.sleep(500); // wait for notification listener to receive notification
        NotificationListenerService.RankingMap rankingMap = mListener.mRankingMap;
        NotificationListenerService.Ranking outRanking =
                new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                // by default nothing can bubble
                assertFalse(outRanking.canBubble());
            }
        }

        // turn off bubbles globally
        setBubblesGlobal(false);

        rankingMap = mListener.mRankingMap;
        outRanking = new NotificationListenerService.Ranking();
        for (String key : rankingMap.getOrderedKeys()) {
            if (key.contains(mListener.getPackageName())) {
                rankingMap.getRanking(key, outRanking);
                assertFalse(outRanking.canBubble());
            }
        }

        mListener.resetData();
    }

    public void testAreBubblesAllowed_appNone() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_NONE);
        assertFalse(mNotificationManager.areBubblesAllowed());
    }

    public void testAreBubblesAllowed_appSelected() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_SELECTED);
        assertFalse(mNotificationManager.areBubblesAllowed());
    }

    public void testAreBubblesAllowed_appAll() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_ALL);
        assertTrue(mNotificationManager.areBubblesAllowed());
    }

    public void testGetBubblePreference_appNone() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_NONE);
        assertEquals(BUBBLE_PREFERENCE_NONE, mNotificationManager.getBubblePreference());
    }

    public void testGetBubblePreference_appSelected() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_SELECTED);
        assertEquals(BUBBLE_PREFERENCE_SELECTED, mNotificationManager.getBubblePreference());
    }

    public void testGetBubblePreference_appAll() throws Exception {
        setBubblesAppPref(BUBBLE_PREFERENCE_ALL);
        assertEquals(BUBBLE_PREFERENCE_ALL, mNotificationManager.getBubblePreference());
    }

    public void testAreBubblesEnabled() throws Exception {
        setBubblesGlobal(true);
        assertTrue(mNotificationManager.areBubblesEnabled());
    }

    public void testAreBubblesEnabled_false() throws Exception {
        setBubblesGlobal(false);
        assertFalse(mNotificationManager.areBubblesEnabled());
    }

    public void testNotificationManagerBubblePolicy_flag_intentBubble()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);
            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, null /* use default metadata */, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_service()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        Intent serviceIntent = new Intent(mContext, BubblesTestService.class);
        serviceIntent.putExtra(EXTRA_TEST_CASE, TEST_MESSAGING);
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            mContext.startService(serviceIntent);

            // No services in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            mContext.stopService(serviceIntent);
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_phonecall()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        Intent serviceIntent = new Intent(mContext, BubblesTestService.class);
        serviceIntent.putExtra(EXTRA_TEST_CASE, TEST_CALL);

        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            mContext.startService(serviceIntent);

            // No phonecalls in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            mContext.stopService(serviceIntent);
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_foreground() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // Start & get the activity
            SendBubbleActivity a = startSendBubbleActivity();
            // Send a bubble that doesn't fulfill policy from foreground
            a.sendInvalidBubble(BUBBLE_NOTIF_ID, false /* autoExpand */);

            // No foreground bubbles that don't fulfill policy in R (allowed in Q)
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    public void testNotificationManagerBubble_checkActivityFlagsDocumentLaunchMode()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // make ourselves foreground so we can auto-expand the bubble & check the intent flags
            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */, false /* suppressNotif */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivityWithTimeout(
                    ACTIVITY_LAUNCH_TIMEOUT);
            assertNotNull(String.format(
                    "Failed to detect BubbleActivity after %d ms", ACTIVITY_LAUNCH_TIMEOUT),
                    activity);
            assertTrue((activity.getIntent().getFlags() & FLAG_ACTIVITY_NEW_DOCUMENT) != 0);
            assertTrue((activity.getIntent().getFlags() & FLAG_ACTIVITY_MULTIPLE_TASK) != 0);
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    public void testNotificationManagerBubblePolicy_flag_shortcutBubble()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, data, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_invalidShortcut()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();

            Notification.Builder nb = getConversationNotification();
            nb.setShortcutId("invalid");
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder("invalid")
                            .build();

            sendAndVerifyBubble(1, nb, data, false);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_invalidNotif()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();

            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();

            sendAndVerifyBubble(1, null /* use default notif builder */, data,
                    false /* shouldBeBubble */);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appAll_globalOn() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, data, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appAll_globalOff() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(false);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            sendAndVerifyBubble(1, nb, data, false);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appAll_channelNo() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(false);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            sendAndVerifyBubble(1, nb, data, false);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appSelected_channelNo() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(2 /* selected */);
            setBubblesChannelAllowed(false);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            sendAndVerifyBubble(1, nb, data, false);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appSelected_channelYes() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(2 /* selected */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            boolean shouldBeBubble = !mActivityManager.isLowRamDevice();
            sendAndVerifyBubble(1, nb, data, shouldBeBubble);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_appNone_channelNo() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(0 /* none */);
            setBubblesChannelAllowed(false);

            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            sendAndVerifyBubble(1, nb, data, false);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubblePolicy_noFlag_shortcutRemoved()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }

        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);
            createDynamicShortcut();
            Notification.BubbleMetadata data =
                    new Notification.BubbleMetadata.Builder(SHARE_SHORTCUT_ID)
                            .build();
            Notification.Builder nb = getConversationNotification();

            sendAndVerifyBubble(42, nb, data, true /* shouldBeBubble */);
            mListener.resetData();

            deleteShortcuts();
            verifyNotificationBubbleState(42, false /* should be bubble */);
        } finally {
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubbleNotificationSuppression() throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // make ourselves foreground so we can specify suppress notification flag
            SendBubbleActivity a = startSendBubbleActivity();

            // send the bubble with notification suppressed
            a.sendBubble(BUBBLE_NOTIF_ID, false /* autoExpand */, true /* suppressNotif */);
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            // check for the notification
            StatusBarNotification sbnSuppressed = mListener.mPosted.get(0);
            assertNotNull(sbnSuppressed);
            // check for suppression state
            Notification.BubbleMetadata metadata =
                    sbnSuppressed.getNotification().getBubbleMetadata();
            assertNotNull(metadata);
            assertTrue(metadata.isNotificationSuppressed());

            mListener.resetData();

            // send the bubble with notification NOT suppressed
            a.sendBubble(BUBBLE_NOTIF_ID, false /* autoExpand */, false /* suppressNotif */);
            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBubble */);

            // check for the notification
            StatusBarNotification sbnNotSuppressed = mListener.mPosted.get(0);
            assertNotNull(sbnNotSuppressed);
            // check for suppression state
            metadata = sbnNotSuppressed.getNotification().getBubbleMetadata();
            assertNotNull(metadata);
            assertFalse(metadata.isNotificationSuppressed());
        } finally {
            cleanupSendBubbleActivity();
            deleteShortcuts();
        }
    }

    public void testNotificationManagerBubble_checkIsBubbled_pendingIntent()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */, false /* suppressNotif */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            assertTrue(activity.isLaunchedFromBubble());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    public void testNotificationManagerBubble_checkIsBubbled_shortcut()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            SendBubbleActivity a = startSendBubbleActivity();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            a.sendBubble(BUBBLE_NOTIF_ID, true /* autoExpand */,
                    false /* suppressNotif */,
                    false /* suppressBubble */,
                    true /* useShortcut */,
                    true /* setLocus */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);

            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            assertTrue(activity.isLaunchedFromBubble());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is suppressed when it should be. */
    public void testNotificationManagerBubble_setSuppressBubble()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            final int notifId = 3;

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(notifId,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            mListener.resetData();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            // Launch same activity as whats in the bubble
            a.startBubbleActivity(notifId);
            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(notifId)),
                    activity.getLocusId());

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            mListener.resetData();

            // Bubble should have suppressed flag
            StatusBarNotification sbn = findPostedNotification(notifId, true);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if dev didn't specify suppressable */
    public void testNotificationManagerBubble_setSuppressBubble_notSuppressable()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    false /* suppressBubble */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            // Launch same activity as whats in the bubble
            a.startBubbleActivity(BUBBLE_NOTIF_ID);
            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(BUBBLE_NOTIF_ID)),
                    activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
            }
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed
            StatusBarNotification sbn = findPostedNotification(BUBBLE_NOTIF_ID, true);
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if the activity doesn't have a locusId. */
    public void testNotificationManagerBubble_setSuppressBubble_activityNoLocusId()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            // Launch same activity as whats in the bubble
            a.startBubbleActivity(BUBBLE_NOTIF_ID, false /* addLocusId */);
            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // It shouldn't have the locusId
            assertNull(activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
            }
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed
            StatusBarNotification sbn = findPostedNotification(BUBBLE_NOTIF_ID, true);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is not suppressed if the notification doesn't have a locusId. */
    public void testNotificationManagerBubble_setSuppressBubble_notificationNoLocusId()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(BUBBLE_NOTIF_ID,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */,
                    false /* useShortcut */,
                    false /* setLocusId */);

            verifyNotificationBubbleState(BUBBLE_NOTIF_ID, true /* shouldBeBubble */);
            mListener.resetData();

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            // Launch same activity as whats in the bubble
            a.startBubbleActivity(BUBBLE_NOTIF_ID, true /* addLocusId */);
            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // Activity has the locus
            assertNotNull(activity.getLocusId());

            // Wait a little (if it wrongly updates it'd be a new post so wait for that))
            try {
                Thread.sleep(500);
            } catch (InterruptedException ex) {
            }
            assertTrue(mListener.mPosted.isEmpty());

            // Bubble should not be suppressed & not have a locusId
            StatusBarNotification sbn = findPostedNotification(BUBBLE_NOTIF_ID, true);
            assertNull(sbn.getNotification().getLocusId());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies the bubble is unsuppressed when the locus activity is hidden. */
    public void testNotificationManagerBubble_setSuppressBubble_dismissLocusActivity()
            throws Exception {
        if (FeatureUtil.isAutomotive() || FeatureUtil.isTV()
                || mActivityManager.isLowRamDevice()) {
            // These do not support bubbles.
            return;
        }
        try {
            setBubblesGlobal(true);
            setBubblesAppPref(1 /* all */);
            setBubblesChannelAllowed(true);

            createDynamicShortcut();
            setUpNotifListener();

            final int notifId = 2;

            // Make a bubble
            SendBubbleActivity a = startSendBubbleActivity();
            a.sendBubble(notifId,
                    false /* autoExpand */,
                    false /* suppressNotif */,
                    true /* suppressBubble */);

            verifyNotificationBubbleState(notifId, true);
            mListener.resetData();

            StatusBarNotification sbn = findPostedNotification(notifId, true);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());

            // Prep to find bubbled activity
            Class clazz = BubbledActivity.class;
            Instrumentation.ActivityResult result =
                    new Instrumentation.ActivityResult(0, new Intent());
            Instrumentation.ActivityMonitor monitor =
                    new Instrumentation.ActivityMonitor(clazz.getName(), result, false);
            InstrumentationRegistry.getInstrumentation().addMonitor(monitor);

            // Launch same activity as whats in the bubble
            a.startBubbleActivity(notifId);
            BubbledActivity activity = (BubbledActivity) monitor.waitForActivity();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            // It should have the locusId
            assertEquals(new LocusId(String.valueOf(notifId)),
                    activity.getLocusId());

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            mListener.resetData();

            // Bubble should have suppressed flag
            sbn = findPostedNotification(notifId, true);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());

            activity.finish();

            // notif gets posted with update, so wait
            verifyNotificationBubbleState(notifId, true /* shouldBeBubble */);
            mListener.resetData();

            sbn = findPostedNotification(notifId, true);
            assertTrue(sbn.getNotification().getBubbleMetadata().isBubbleSuppressable());
            assertFalse(sbn.getNotification().getBubbleMetadata().isBubbleSuppressed());
        } finally {
            deleteShortcuts();
            cleanupSendBubbleActivity();
        }
    }

    /** Verifies that a regular activity can't specify a bubble in ActivityOptions */
    public void testNotificationManagerBubble_launchBubble_activityOptions_fails()
            throws Exception {
        try {
            // Start test activity
            SendBubbleActivity activity = startSendBubbleActivity();
            assertFalse(activity.isLaunchedFromBubble());

            // Should have exception
            assertThrows(SecurityException.class, () -> {
                Intent i = new Intent(mContext, BubbledActivity.class);
                ActivityOptions options = ActivityOptions.makeBasic();
                Bundle b = options.toBundle();
                b.putBoolean("android.activity.launchTypeBubble", true);
                activity.startActivity(i, b);
            });
        } finally {
            cleanupSendBubbleActivity();
        }
    }
}
