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

package com.android.cts.install.lib;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;

import androidx.test.InstrumentationRegistry;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Utilities to facilitate installation in tests.
 */
public class InstallUtils {
    /**
     * Adopts the given shell permissions.
     */
    public static void adoptShellPermissionIdentity(String... permissions) {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .adoptShellPermissionIdentity(permissions);
    }

    /**
     * Drops all shell permissions.
     */
    public static void dropShellPermissionIdentity() {
        InstrumentationRegistry
                .getInstrumentation()
                .getUiAutomation()
                .dropShellPermissionIdentity();
    }
    /**
     * Returns the version of the given package installed on device.
     * Returns -1 if the package is not currently installed.
     */
    public static long getInstalledVersion(String packageName) {
        Context context = InstrumentationRegistry.getContext();
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
            return info.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            return -1;
        }
    }

    /**
     * Waits for the given session to be marked as ready.
     * Throws an assertion if the session fails.
     */
    public static void waitForSessionReady(int sessionId) {
        BlockingQueue<PackageInstaller.SessionInfo> sessionStatus = new LinkedBlockingQueue<>();
        BroadcastReceiver sessionUpdatedReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                PackageInstaller.SessionInfo info =
                        intent.getParcelableExtra(PackageInstaller.EXTRA_SESSION);
                if (info != null && info.getSessionId() == sessionId) {
                    if (info.isStagedSessionReady() || info.isStagedSessionFailed()) {
                        try {
                            sessionStatus.put(info);
                        } catch (InterruptedException e) {
                            throw new AssertionError(e);
                        }
                    }
                }
            }
        };
        IntentFilter sessionUpdatedFilter =
                new IntentFilter(PackageInstaller.ACTION_SESSION_UPDATED);

        Context context = InstrumentationRegistry.getContext();
        context.registerReceiver(sessionUpdatedReceiver, sessionUpdatedFilter);

        PackageInstaller installer = context.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionInfo info = installer.getSessionInfo(sessionId);

        try {
            if (info.isStagedSessionReady() || info.isStagedSessionFailed()) {
                sessionStatus.put(info);
            }

            info = sessionStatus.take();
            context.unregisterReceiver(sessionUpdatedReceiver);
            if (info.isStagedSessionFailed()) {
                throw new AssertionError(info.getStagedSessionErrorMessage());
            }
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Returns the info for the given package name.
     */
    public static PackageInfo getPackageInfo(String packageName) {
        Context context = InstrumentationRegistry.getContext();
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(packageName, PackageManager.MATCH_APEX);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    /**
     * Asserts that {@code result} intent has a success status.
     */
    public static void assertStatusSuccess(Intent result) {
        int status = result.getIntExtra(PackageInstaller.EXTRA_STATUS,
                PackageInstaller.STATUS_FAILURE);
        if (status == -1) {
            throw new AssertionError("PENDING USER ACTION");
        } else if (status > 0) {
            String message = result.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
            throw new AssertionError(message == null ? "UNKNOWN FAILURE" : message);
        }
    }
}
