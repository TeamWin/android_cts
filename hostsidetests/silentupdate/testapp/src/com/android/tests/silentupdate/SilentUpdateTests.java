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

package com.android.tests.silentupdate;

import static android.app.PendingIntent.FLAG_MUTABLE;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_REQUIRED;
import static android.content.pm.PackageInstaller.SessionParams.USER_ACTION_UNSPECIFIED;

import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageInstaller.SessionParams;

import androidx.test.platform.app.InstrumentationRegistry;


import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Tests for multi-package (a.k.a. atomic) installs.
 */
@RunWith(JUnit4.class)
public class SilentUpdateTests {
    private static final String CURRENT_APK = "SilentInstallCurrent.apk";
    private static final String P_APK = "SilentInstallP.apk";
    private static final String Q_APK = "SilentInstallQ.apk";

    private static Context getContext() {
        return InstrumentationRegistry.getInstrumentation().getContext();
    }

    @After
    public void cleanUpSessions() {
        final Context context = getContext();
        final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        installer.getMySessions().forEach(
                (session) -> installer.abandonSession(session.getSessionId()));
    }

    @Test
    public void newInstall_RequiresUserAction() throws Exception {
        Assert.assertEquals("New install should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(CURRENT_APK));
    }

    @Test
    public void updateWithUnknownSourcesDisabled_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update with unknown sources disabled should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(CURRENT_APK));
    }

    @Test
    public void updateAsNonInstallerOfRecord_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update when not installer of record should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(CURRENT_APK));
    }

    @Test
    public void updatedInstall_RequiresNoUserAction() throws Exception {
        Assert.assertEquals("Nominal silent update should not require user action",
                PackageInstaller.STATUS_SUCCESS,
                install(CURRENT_APK));
    }

    @Test
    public void updatedInstallWithoutCallSetUserAction_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update should require action when setRequireUserAction not called",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(CURRENT_APK, null /*setRequireUserAction*/));
    }

    @Test
    public void updatedInstallForceUserAction_RequiresUserAction() throws Exception {
        Assert.assertEquals("Update should require action when setRequireUserAction true",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(CURRENT_APK, true /*setRequireUserAction*/));
    }

    @Test
    public void updatePreQApp_RequiresUserAction() throws Exception {
        Assert.assertEquals("Updating to a pre-Q app should require user action",
                PackageInstaller.STATUS_PENDING_USER_ACTION,
                install(P_APK));
    }
    @Test
    public void updateQApp_RequiresNoUserAction() throws Exception {
        Assert.assertEquals("Updating to a Q app should not require user action",
                PackageInstaller.STATUS_SUCCESS,
                install(Q_APK));
    }

    @Test
    public void setRequireUserAction_throwsOnIllegalArgument() {
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        params.setRequireUserAction(USER_ACTION_UNSPECIFIED);
        params.setRequireUserAction(USER_ACTION_REQUIRED);
        params.setRequireUserAction(USER_ACTION_NOT_REQUIRED);
        try {
            params.setRequireUserAction(-1);
            fail("Should not be able to setRequireUserAction to -1");
        } catch (IllegalArgumentException e) {
            // pass!
        }
        try {
            params.setRequireUserAction(3);
            fail("Should not be able to setRequireUserAction to 3");
        } catch (IllegalArgumentException e) {
            // pass!
        }
    }

    private int install(String apkName) throws Exception {
        return install(apkName, false);
    }
    private int install(String apkName, Boolean requireUserAction) throws Exception {
        final Context context = getContext();
        final PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        SessionParams params = new SessionParams(SessionParams.MODE_FULL_INSTALL);
        if (requireUserAction != null) {
            params.setRequireUserAction(requireUserAction
                    ? USER_ACTION_REQUIRED
                    : USER_ACTION_NOT_REQUIRED);
        }
        int sessionId = installer.createSession(params);
        Assert.assertEquals("SessionInfo.getRequireUserAction and "
                        + "SessionParams.setRequireUserAction are not equal",
                installer.getSessionInfo(sessionId).getRequireUserAction(),
                requireUserAction == null
                        ? USER_ACTION_UNSPECIFIED
                        : requireUserAction == Boolean.TRUE
                                ? USER_ACTION_REQUIRED
                                : SessionParams.USER_ACTION_NOT_REQUIRED);
        final PackageInstaller.Session session = installer.openSession(sessionId);
        try(OutputStream os = session.openWrite(apkName, 0, -1)) {
            try (InputStream is = getClass().getClassLoader().getResourceAsStream(apkName)) {
                if (is == null) {
                    throw new IOException("Resource " + apkName + " not found.");
                }
                byte[] buffer = new byte[4096];
                int n;
                while ((n = is.read(buffer)) >= 0) {
                    os.write(buffer, 0, n);
                }
            }
        }
        InstallStatusListener isl = new InstallStatusListener();
        session.commit(isl.getIntentSender());
        final Intent statusUpdate = isl.getResult();
        return statusUpdate.getIntExtra(PackageInstaller.EXTRA_STATUS, Integer.MIN_VALUE);
    }

    public static class InstallStatusListener extends BroadcastReceiver {

        private final BlockingQueue<Intent> mResults = new LinkedBlockingQueue<>();

        @Override
        public void onReceive(Context context, Intent intent) {
            mResults.add(intent);
        }

        public IntentSender getIntentSender() {
            final Context context = getContext();
            final String action = UUID.randomUUID().toString();
            context.registerReceiver(this, new IntentFilter(action));
            Intent intent = new Intent(action);
            PendingIntent pending = PendingIntent.getBroadcast(context, 0, intent, FLAG_MUTABLE);
            return pending.getIntentSender();
        }

        public Intent getResult() throws InterruptedException {
            return mResults.take();
        }
    }
}
