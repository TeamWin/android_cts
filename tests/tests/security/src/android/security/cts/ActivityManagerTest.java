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
import android.os.Process;
import android.platform.test.annotations.SecurityTest;
import android.support.test.InstrumentationRegistry;
import android.util.Log;
import android.view.WindowManager;

import junit.framework.TestCase;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@SecurityTest
public class ActivityManagerTest extends TestCase {

    private static final String SECURITY_CTS_PACKAGE_NAME = "android.security.cts";
    private static CountDownLatch sLatch;
    private static volatile int sNormalActivityUserId;
    private static volatile boolean sCannotReflect;
    private static volatile boolean sIsAppForeground;

    private static final String TAG = "ActivityManagerTest";

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        sLatch = new CountDownLatch(2);
        sNormalActivityUserId = -1;
        sCannotReflect = false;
        sIsAppForeground = false;
    }

    @SecurityTest(minPatchLevel = "2015-03")
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

    @SecurityTest(minPatchLevel = "2019-01")
    public void testIsAppInForegroundNormal() throws Exception {
        /* Verify that isAppForeground can be called by the caller on itself. */
        launchActivity(NormalActivity.class);
        sNormalActivityUserId = InstrumentationRegistry.getTargetContext().getPackageManager()
                .getPackageUid(SECURITY_CTS_PACKAGE_NAME, 0);
        sLatch.await(5, TimeUnit.SECONDS); // Ensure the service has ran at least twice.
        if (sCannotReflect) return; // If reflection is not possible, pass the test.
        assertTrue("isAppForeground failed to query for uid on itself.", sIsAppForeground);
    }

    @SecurityTest(minPatchLevel = "2019-01")
    public void testIsAppInForegroundMalicious() throws Exception {
        /* Verify that isAppForeground cannot be called by another app on a known uid. */
        launchActivity(MaliciousActivity.class);
        launchSettingsActivity();
        sLatch.await(5, TimeUnit.SECONDS); // Ensure the service has ran at least twice.
        if (sCannotReflect) return; // If reflection is not possible, pass the test.
        assertFalse("isAppForeground successfully queried for a uid other than itself.",
                sIsAppForeground);
    }

    private void launchActivity(Class<? extends Activity> clazz) {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClassName(SECURITY_CTS_PACKAGE_NAME, clazz.getName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    private void launchSettingsActivity() {
        final Context context = InstrumentationRegistry.getInstrumentation().getContext();
        final Intent intent = new Intent(android.provider.Settings.ACTION_SETTINGS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    public static class NormalActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            Intent intent = new Intent(this, AppMonitoringService.class);
            intent.putExtra(AppMonitoringService.EXTRA_UID, sNormalActivityUserId);
            startService(intent);
        }
    }

    public static class MaliciousActivity extends Activity {

        @Override
        protected void onCreate(@Nullable Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

            Intent intent = new Intent(this, AppMonitoringService.class);
            intent.putExtra(AppMonitoringService.EXTRA_UID, Process.SYSTEM_UID);
            startService(intent);
            finish();
        }
    }

    public static class AppMonitoringService extends Service {

        private static final String EXTRA_UID = "android.security.cts.extra.UID";
        private int uid;

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            uid = intent.getIntExtra(EXTRA_UID, -1);
            return super.onStartCommand(intent, flags, startId);
        }

        public AppMonitoringService() {
            super.onCreate();

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    try {
                        ActivityManager activityManager = (ActivityManager) getSystemService(
                                ACTIVITY_SERVICE);
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
                        boolean res = (boolean) isAppForeground.invoke(IActivityInstance, uid);
                        if (res) {
                            sIsAppForeground = true;
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to fetch/invoke field/method via reflection.", e);
                        sCannotReflect = true;
                    }
                    sLatch.countDown();
                    handler.postDelayed(this, 200);

                }
            }, 0);
        }

        @Override
        public IBinder onBind(Intent intent) {
            throw new UnsupportedOperationException("Not yet implemented");
        }
    }
}
