/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.media.cts;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.media.AudioManager;
import android.media.AudioPlaybackConfiguration;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.Settings;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import junit.framework.Assert;

public class Utils {
    private static final String TAG = "CtsMediaTestUtil";
    private static final int TEST_TIMING_TOLERANCE_MS = 50;

    public static void enableAppOps(String packageName, String operation,
            Instrumentation instrumentation) {
        setAppOps(packageName, operation, instrumentation, true);
    }

    public static void disableAppOps(String packageName, String operation,
            Instrumentation instrumentation) {
        setAppOps(packageName, operation, instrumentation, false);
    }

    public static String convertStreamToString(InputStream is) {
        try (Scanner scanner = new Scanner(is).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        }
    }

    private static void setAppOps(String packageName, String operation,
            Instrumentation instrumentation, boolean enable) {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(packageName);
        cmd.append(" ");
        cmd.append(operation);
        cmd.append(enable ? " allow" : " deny");
        instrumentation.getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(packageName);
        query.append(" ");
        query.append(operation);
        String queryStr = query.toString();

        String expectedResult = enable ? "allow" : "deny";
        String result = "";
        while(!result.contains(expectedResult)) {
            ParcelFileDescriptor pfd = instrumentation.getUiAutomation().executeShellCommand(
                                                            queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        }
    }

    protected static void toggleNotificationPolicyAccess(String packageName,
            Instrumentation instrumentation, boolean on) throws IOException {
        Context context = instrumentation.getContext();

        // Get permission to enable accessibility
        UiAutomation uiAutomation = instrumentation.getUiAutomation();

        ContentResolver cr = context.getContentResolver();
        String alreadyEnabledServices = Settings.Secure.getString(
                cr, Settings.Secure.ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES);
        ParcelFileDescriptor fd = null;
        if (on) {
            if ((alreadyEnabledServices == null) || !alreadyEnabledServices.contains(packageName)) {
                // Change the settings to enable the media cts package
                final String newEnabledServices = (alreadyEnabledServices == null) ? packageName
                        : alreadyEnabledServices + ":" + packageName;
                fd = uiAutomation.executeShellCommand(
                        "settings --user cur put secure "
                                + Settings.Secure.ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES + " "
                                + newEnabledServices);
            }
        } else if (alreadyEnabledServices != null) {
            int index =  alreadyEnabledServices.indexOf(":" + packageName);
            if (index >= 0) {
                fd = uiAutomation.executeShellCommand(
                        "settings --user cur put secure "
                                + Settings.Secure.ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES + " "
                                + alreadyEnabledServices.substring(0, index));
            } else if (alreadyEnabledServices.equals(packageName)) {
                // "packageName" is the only enabled service
                fd = uiAutomation.executeShellCommand("settings --user cur put secure "
                        + Settings.Secure.ENABLED_NOTIFICATION_POLICY_ACCESS_PACKAGES + "  null");
            }
        }
        if (fd != null) {
            InputStream in = new FileInputStream(fd.getFileDescriptor());
            byte[] buffer = new byte[4096];
            while (in.read(buffer) > 0) ;
        }
        uiAutomation.destroy();
    }

    /**
     * Assert that a media playback is started and an active {@link AudioPlaybackConfiguration}
     * is created once. The playback will be stopped immediately after that.
     * <p>For a media session to receive media button events, an actual playback is needed.
     */
    static void assertMediaPlaybackStarted(Context context) {
        final AudioManager am = new AudioManager(context);
        final HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        final TestAudioPlaybackCallback callback = new TestAudioPlaybackCallback();
        MediaPlayer mediaPlayer = null;

        try {
            final int activeConfigSizeBeforeStart = am.getActivePlaybackConfigurations().size();
            final Handler handler = new Handler(handlerThread.getLooper());

            am.registerAudioPlaybackCallback(callback, handler);
            mediaPlayer = MediaPlayer.create(context, R.raw.sine1khzs40dblong);
            mediaPlayer.start();
            if (!callback.mCountDownLatch.await(TEST_TIMING_TOLERANCE_MS, TimeUnit.MILLISECONDS)
                    || callback.mActiveConfigSize != activeConfigSizeBeforeStart + 1) {
                Assert.fail("Failed to create an active AudioPlaybackConfiguration");
            }
        } catch (InterruptedException e) {
            Assert.fail("Failed to create an active AudioPlaybackConfiguration");
        } finally {
            am.unregisterAudioPlaybackCallback(callback);
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.release();
                mediaPlayer = null;
            }
            handlerThread.quitSafely();
        }
    }

    private static class TestAudioPlaybackCallback extends AudioManager.AudioPlaybackCallback {
        private final CountDownLatch mCountDownLatch = new CountDownLatch(1);
        private int mActiveConfigSize;

        @Override
        public void onPlaybackConfigChanged(List<AudioPlaybackConfiguration> configs) {
            // For non-framework apps, only anonymized active AudioPlaybackCallbacks will be
            // notified.
            mActiveConfigSize = configs.size();
            mCountDownLatch.countDown();
        }
    }
}
