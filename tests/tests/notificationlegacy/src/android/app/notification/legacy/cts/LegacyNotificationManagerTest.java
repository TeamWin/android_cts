/*
 * Copyright (C) 2018 The Android Open Source Project
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

package android.app.notification.legacy.cts;

import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_AMBIENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_LIGHTS;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_NOTIFICATION_LIST;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_PEEK;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_OFF;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_SCREEN_ON;
import static android.app.NotificationManager.Policy.SUPPRESSED_EFFECT_STATUS_BAR;

import static junit.framework.Assert.assertEquals;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.ParcelFileDescriptor;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Home for tests that need to verify behavior for apps that target old sdk versions.
 */
@RunWith(AndroidJUnit4.class)
public class LegacyNotificationManagerTest {

    private NotificationManager mNotificationManager;
    private ActivityManager mActivityManager;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getContext();
        mNotificationManager = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Test
    public void testPrePCannotToggleAlarmsAndMediaTest() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            return;
        }
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        // Pre-P cannot toggle alarms and media
        NotificationManager.Policy origPolicy = mNotificationManager.getNotificationPolicy();
        int alarmBit = origPolicy.priorityCategories & NotificationManager.Policy
                .PRIORITY_CATEGORY_ALARMS;
        int mediaBit = origPolicy.priorityCategories & NotificationManager.Policy
                .PRIORITY_CATEGORY_MEDIA;
        int systemBit = origPolicy.priorityCategories & NotificationManager.Policy
                .PRIORITY_CATEGORY_SYSTEM;

        // attempt to toggle off alarms, media, system:
        mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(0, 0, 0));
        NotificationManager.Policy policy = mNotificationManager.getNotificationPolicy();
        assertEquals(alarmBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS);
        assertEquals(mediaBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA);
        assertEquals(systemBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM);

        // attempt to toggle on alarms, media, system:
        mNotificationManager.setNotificationPolicy(new NotificationManager.Policy(
                NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
                        | NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA, 0, 0));
        policy = mNotificationManager.getNotificationPolicy();
        assertEquals(alarmBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS);
        assertEquals(mediaBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_MEDIA);
        assertEquals(systemBit, policy.priorityCategories
                & NotificationManager.Policy.PRIORITY_CATEGORY_SYSTEM);

        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), false);
    }

    @Test
    public void testSetNotificationPolicy_preP_setOldFields() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            return;
        }
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy userPolicy = mNotificationManager.getNotificationPolicy();

        NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF);
        mNotificationManager.setNotificationPolicy(appPolicy);

        int expected = userPolicy.suppressedVisualEffects
                | SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_SCREEN_OFF
                | SUPPRESSED_EFFECT_PEEK | SUPPRESSED_EFFECT_AMBIENT
                | SUPPRESSED_EFFECT_LIGHTS | SUPPRESSED_EFFECT_FULL_SCREEN_INTENT;

        assertEquals(expected,
                mNotificationManager.getNotificationPolicy().suppressedVisualEffects);
    }

    @Test
    public void testSetNotificationPolicy_preP_setNewFields() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            return;
        }
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy userPolicy = mNotificationManager.getNotificationPolicy();

        NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_NOTIFICATION_LIST);
        mNotificationManager.setNotificationPolicy(appPolicy);

        int expected = userPolicy.suppressedVisualEffects;
        expected &= ~ SUPPRESSED_EFFECT_SCREEN_OFF & ~ SUPPRESSED_EFFECT_SCREEN_ON;
        assertEquals(expected,
                mNotificationManager.getNotificationPolicy().suppressedVisualEffects);

        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), false);
    }

    @Test
    public void testSetNotificationPolicy_preP_setOldNewFields() throws Exception {
        if (mActivityManager.isLowRamDevice()) {
            return;
        }
        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), true);

        NotificationManager.Policy userPolicy = mNotificationManager.getNotificationPolicy();

        NotificationManager.Policy appPolicy = new NotificationManager.Policy(0, 0, 0,
                SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_STATUS_BAR);
        mNotificationManager.setNotificationPolicy(appPolicy);

        int expected = userPolicy.suppressedVisualEffects
                | SUPPRESSED_EFFECT_SCREEN_ON | SUPPRESSED_EFFECT_PEEK;
        expected &= ~ SUPPRESSED_EFFECT_SCREEN_OFF;
        assertEquals(expected,
                mNotificationManager.getNotificationPolicy().suppressedVisualEffects);

        toggleNotificationPolicyAccess(mContext.getPackageName(),
                InstrumentationRegistry.getInstrumentation(), false);
    }

    private void toggleNotificationPolicyAccess(String packageName,
            Instrumentation instrumentation, boolean on) throws IOException {

        String command = " cmd notification " + (on ? "allow_dnd " : "disallow_dnd ") + packageName;

        // Get permission to change dnd policy
        UiAutomation uiAutomation = instrumentation.getUiAutomation();
        // Execute command
        try (ParcelFileDescriptor fd = uiAutomation.executeShellCommand(command)) {
            Assert.assertNotNull("Failed to execute shell command: " + command, fd);
            // Wait for the command to finish by reading until EOF
            try (InputStream in = new FileInputStream(fd.getFileDescriptor())) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) > 0) {}
            } catch (IOException e) {
                throw new IOException("Could not read stdout of command: " + command, e);
            }
        } finally {
            uiAutomation.destroy();
        }

        NotificationManager nm = (NotificationManager) mContext.getSystemService(
                Context.NOTIFICATION_SERVICE);
        assertEquals("Notification Policy Access Grant is " +
                        nm.isNotificationPolicyAccessGranted() + " not " + on, on,
                nm.isNotificationPolicyAccessGranted());
    }
}
