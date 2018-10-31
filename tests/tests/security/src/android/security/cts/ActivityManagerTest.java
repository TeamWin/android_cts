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
package android.security.cts;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.platform.test.annotations.SecurityTest;
import android.support.test.InstrumentationRegistry;
import android.view.WindowManager;

import com.android.compatibility.common.util.SystemUtil;

import junit.framework.TestCase;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;

@SecurityTest
public class ActivityManagerTest extends TestCase {

    private static final String SECURITY_CTS_PACKAGE_NAME = "android.security.cts";
    private static final CountDownLatch normalLatch = new CountDownLatch(2);
    private static final CountDownLatch maliciousLatch = new CountDownLatch(2);
    private static int normalActivityUserId = -1;
    private static boolean isAppForegroundCheck = false;
    private static boolean gotHijacked = false;

    private String mTargetPackage;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mTargetPackage = InstrumentationRegistry.getContext().getPackageName();
    }

    public void testActivityManager_injectInputEvents() throws ClassNotFoundException {
        try {
            /*
             * Should throw NoSuchMethodException. getEnclosingActivityContainer() has been
             * removed/renamed.
             * Patch:  https://android.googlesource.com/platform/frameworks/base/+/aa7e3ed%5E!/
             */
            Class.forName("android.app.ActivityManagerNative").getMethod(
                    "getEnclosingActivityContainer", IBinder.class);
            fail("ActivityManagerNative.getEnclosingActivityContainer() API should not be" +
                    "available in patched devices: Device is vulnerable to CVE-2015-1533");
        } catch (NoSuchMethodException e) {
            // Patched devices should throw this exception
        }
    }

    public void testActivityManager_checkAppInForegroundCall() throws Exception {
        /* Verify that isAppForeground can be called by the caller on itself. */
        launchActivity(NormalActivity.class);
        String packageInfo = executeShellCommand("dumpsys package " + SECURITY_CTS_PACKAGE_NAME);
        int userIdStartIndex = packageInfo.indexOf("userId=") + 7;
        int userIdEndIndex = packageInfo.indexOf("\n", userIdStartIndex);
        normalActivityUserId = Integer.valueOf(
                packageInfo.substring(userIdStartIndex, userIdEndIndex));
        normalLatch.await(); // Ensure the service has ran at least twice.
        assertTrue(isAppForegroundCheck);

        /* Verify that isAppForeground cannot be called by another app on a known uid. */
        launchActivity(MaliciousActivity.class);
        launchSettingsActivity();
        maliciousLatch.await(); // Ensure the service has ran at least twice.
        assertFalse(gotHijacked);
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(mTargetPackage, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void launchSettingsActivity() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private static String executeShellCommand(String command) {
        try {
            return SystemUtil.runShellCommand(InstrumentationRegistry.getInstrumentation(),
                    command);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static class NormalActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            Intent intent = new Intent(this, NormalAppMonitoring.class);
            startService(intent);
        }
    }

    public static class NormalAppMonitoring extends Service {

        public NormalAppMonitoring() {
            super.onCreate();
            try {

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            ActivityManager activityManager = (ActivityManager) getSystemService(
                                    ACTIVITY_SERVICE);
                            assert activityManager != null;
                            Field field = activityManager.getClass().getDeclaredField(
                                    "IActivityManagerSingleton");
                            field.setAccessible(true);
                            Object fieldValue = field.get(activityManager);
                            Method method = fieldValue.getClass().getDeclaredMethod("create");
                            method.setAccessible(true);
                            Object IActivityInstance = method.invoke(fieldValue);
                            Method isAppForeground = IActivityInstance.getClass().getDeclaredMethod(
                                    "isAppForeground", int.class);
                            isAppForeground.setAccessible(true);
                            boolean res = (boolean) isAppForeground.invoke(IActivityInstance,
                                    normalActivityUserId);
                            if (res) {
                                isAppForegroundCheck = true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        normalLatch.countDown();
                        handler.postDelayed(this, 200);

                    }
                }, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }

    public static class MaliciousActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            Intent intent = new Intent(this, MaliciousAppMonitoring.class);
            startService(intent);
            finish();
        }
    }

    public static class MaliciousAppMonitoring extends Service {

        public MaliciousAppMonitoring() {
            super.onCreate();
            try {

                final Handler handler = new Handler();
                handler.postDelayed(new Runnable() {
                    public void run() {
                        try {
                            ActivityManager activityManager = (ActivityManager) getSystemService(
                                    ACTIVITY_SERVICE);
                            assert activityManager != null;
                            Field field = activityManager.getClass().getDeclaredField(
                                    "IActivityManagerSingleton");
                            field.setAccessible(true);
                            Object fieldValue = field.get(activityManager);
                            Method method = fieldValue.getClass().getDeclaredMethod("create");
                            method.setAccessible(true);
                            Object IActivityInstance = method.invoke(fieldValue);
                            Method isAppForeground = IActivityInstance.getClass().getDeclaredMethod(
                                    "isAppForeground", int.class);
                            isAppForeground.setAccessible(true);
                            // Assumption: Settings uid is always 1000.
                            boolean res = (boolean) isAppForeground.invoke(IActivityInstance,
                                    1000);
                            if (res) {
                                gotHijacked = true;
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                        maliciousLatch.countDown();
                        handler.postDelayed(this, 200);

                    }
                }, 0);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }
}