/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.packageinstaller.admin.cts;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class tests {@link PackageInstaller#ACTION_SESSION_COMMITTED} is properly sent to the
 * launcher app.
 */
public class SessionCommitBroadcastTest extends BasePackageInstallTest {

    private static final long BROADCAST_TIMEOUT_SECS = 10;

    private ComponentName mDefaultLauncher;
    private ComponentName mThisAppLauncher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDefaultLauncher = ComponentName.unflattenFromString(getDefaultLauncher());
        mThisAppLauncher = new ComponentName(mContext, LauncherActivity.class);
    }

    public void testBroadcastReceivedForDifferentLauncher() throws Exception {
        if (!mHasFeature) {
            return;
        }
        assertNotSame(mDefaultLauncher, mThisAppLauncher);

        SessionCommitReceiver receiver = new SessionCommitReceiver();
        // install the app
        assertInstallPackage();
        // Broadcast not received
        assertNull(receiver.blockingGetIntent());

        tryUninstallPackage();
    }

    private void verifySessionIntent(Intent intent) {
        assertNotNull(intent);
        PackageInstaller.SessionInfo info = intent
                .getParcelableExtra(PackageInstaller.EXTRA_SESSION);
        assertEquals(TEST_APP_PKG, info.getAppPackageName());
    }

    public void testBroadcastReceivedForNewInstall() throws Exception {
        if (!mHasFeature) {
            return;
        }
        setLauncher(mThisAppLauncher.flattenToString());

        SessionCommitReceiver receiver = new SessionCommitReceiver();
        // install the app
        assertInstallPackage();

        verifySessionIntent(receiver.blockingGetIntent());

        // Try installing again after uninstall
        tryUninstallPackage();
        receiver = new SessionCommitReceiver();
        assertInstallPackage();
        verifySessionIntent(receiver.blockingGetIntent());

        tryUninstallPackage();
        // Revert to default launcher
        setLauncher(mDefaultLauncher.flattenToString());
    }

    private String getDefaultLauncher() throws Exception {
        final String PREFIX = "Launcher: ComponentInfo{";
        final String POSTFIX = "}";
        for (String s : runShellCommand("cmd shortcut get-default-launcher")) {
            if (s.startsWith(PREFIX) && s.endsWith(POSTFIX)) {
                return s.substring(PREFIX.length(), s.length() - POSTFIX.length());
            }
        }
        throw new Exception("Default launcher not found");
    }

    private void setLauncher(String component) throws Exception {
        runShellCommand("cmd package set-home-activity --user "
                + getInstrumentation().getContext().getUserId() + " " + component);
    }

    public ArrayList<String> runShellCommand(String command) throws Exception {
        ParcelFileDescriptor pfd = getInstrumentation().getUiAutomation()
                .executeShellCommand(command);

        ArrayList<String> ret = new ArrayList<>();
        // Read the input stream fully.
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new ParcelFileDescriptor.AutoCloseInputStream(pfd)))) {
            String line;
            while ((line = r.readLine()) != null) {
                ret.add(line);
            }
        }
        return ret;
    }

    private class SessionCommitReceiver extends BroadcastReceiver {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private Intent mIntent;

        SessionCommitReceiver() {
            mContext.registerReceiver(this,
                    new IntentFilter(PackageInstaller.ACTION_SESSION_COMMITTED));
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            assertNull(mIntent);
            mIntent = intent;
            mLatch.countDown();
        }

        public Intent blockingGetIntent() throws Exception {
            mLatch.await(BROADCAST_TIMEOUT_SECS, TimeUnit.SECONDS);
            mContext.unregisterReceiver(this);
            return mIntent;
        }
    }
}
