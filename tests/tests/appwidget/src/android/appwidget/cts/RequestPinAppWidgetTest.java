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

package android.appwidget.cts;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.cts.common.Constants;
import android.appwidget.cts.provider.FirstAppWidgetProvider;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.test.InstrumentationTestCase;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class RequestPinAppWidgetTest extends InstrumentationTestCase {

    private static final String LAUNCHER_CLASS = "android.appwidget.cts.packages.Launcher";
    private static final String ACTION_PIN_RESULT = "android.appwidget.cts.ACTION_PIN_RESULT";

    private String mDefaultLauncher;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mDefaultLauncher = getDefaultLauncher();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // Set the launcher back
        setLauncher(mDefaultLauncher);
    }

    private void runPinWidgetTest(final String launcherPkg) throws Exception {
        Context context = getInstrumentation().getContext();
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)) {
            // System does not support app widgets.
            return;
        }

        setLauncher(launcherPkg + "/" + LAUNCHER_CLASS);

        // Request to pin widget
        BlockingReceiver setupReceiver = new BlockingReceiver()
                .register(Constants.ACTION_SETUP_REPLY);

        PendingIntent pinResult = PendingIntent.getBroadcast(context, 0,
                new Intent(ACTION_PIN_RESULT), PendingIntent.FLAG_ONE_SHOT);
        AppWidgetManager.getInstance(context).requestPinAppWidget(
                new ComponentName(context, FirstAppWidgetProvider.class), pinResult);

        setupReceiver.await();
        // Verify that the confirmation dialog was opened
        assertTrue(setupReceiver.mResult.getBooleanExtra(Constants.EXTRA_SUCCESS, false));
        assertEquals(launcherPkg, setupReceiver.mResult.getStringExtra(Constants.EXTRA_PACKAGE));
        setupReceiver.unregister();

        // Accept the request
        BlockingReceiver resultReceiver = new BlockingReceiver().register(ACTION_PIN_RESULT);
        context.sendBroadcast(new Intent(Constants.ACTION_CONFIRM_PIN)
                .setPackage(launcherPkg)
                .putExtra("dummy", "dummy-2"));
        resultReceiver.await();

        // Verify that the result contain the extras
        assertEquals("dummy-2", resultReceiver.mResult.getStringExtra("dummy"));
        resultReceiver.unregister();
    }

    public void testPinWidget_launcher1() throws Exception {
        runPinWidgetTest("android.appwidget.cts.packages.launcher1");
    }

    public void testPinWidget_launcher2() throws Exception {
        runPinWidgetTest("android.appwidget.cts.packages.launcher2");
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

    private ArrayList<String> runShellCommand(String command) throws Exception {
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

    private class BlockingReceiver extends BroadcastReceiver {
        private final CountDownLatch notifier = new CountDownLatch(1);

        private Intent mResult;

        @Override
        public void onReceive(Context context, Intent intent) {
            mResult = new Intent(intent);
            notifier.countDown();
        }

        public BlockingReceiver register(String action) {
            Context context = getInstrumentation().getContext();
            context.registerReceiver(this, new IntentFilter(action),
                    null, new Handler(context.getMainLooper()));
            return this;
        }

        public void await() throws Exception {
            assertTrue(notifier.await(20, TimeUnit.SECONDS));
        }

        public void unregister() {
            getInstrumentation().getContext().unregisterReceiver(this);
        }
    }
}
