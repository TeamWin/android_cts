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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.ActivityManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.IBinder;
import android.os.Process;
import android.os.UserHandle;
import android.platform.test.annotations.AsbSecurityTest;
import android.util.Log;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ShellUtils;
import com.android.sts.common.util.StsExtraBusinessLogicTestCase;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.InvocationTargetException;

@RunWith(AndroidJUnit4.class)
public class ActivityManagerTest extends StsExtraBusinessLogicTestCase {

    private boolean canSupportMultiuser() {
        String output = ShellUtils.runShellCommand("pm get-max-users");
        if (output.contains("Maximum supported users:")) {
            return Integer.parseInt(output.split(": ", 2)[1].trim()) > 1;
        }
        return false;
    }

    @AsbSecurityTest(cveBugId = 217934898)
    @Test
    public void testActivityManager_registerUidChangeObserver_onlyNoInteractAcrossPermission()
            throws Exception {
        if (!canSupportMultiuser()) {
            return;
        }
        String out = "";
        final UidImportanceObserver observer = new UidImportanceObserver();
        final ActivityManager mActMan = (ActivityManager) getContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int currentUser = mActMan.getCurrentUser();
        try {

            mActMan.addOnUidImportanceListener(observer,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            out = ShellUtils.runShellCommand("pm create-user testUser");
            out = out.length() > 2 ? out.substring(out.length() - 2) : out;

            ShellUtils.runShellCommand("am switch-user " + out);

            Thread.sleep(5000);
            assertFalse(observer.didObserverOtherUser());
        } finally {
            ShellUtils.runShellCommand("am switch-user " + currentUser);
            ShellUtils.runShellCommand("pm remove-user " + out);
            mActMan.removeOnUidImportanceListener(observer);
        }
    }

    @AsbSecurityTest(cveBugId = 217934898)
    @Test
    public void testActivityManager_registerUidChangeObserver_allPermission()
            throws Exception {
        if (!canSupportMultiuser()) {
            return;
        }
        String out = "";
        final UidImportanceObserver observer = new UidImportanceObserver();
        final ActivityManager mActMan = (ActivityManager) getContext()
                .getSystemService(Context.ACTIVITY_SERVICE);
        final int currentUser = mActMan.getCurrentUser();
        final UiAutomation uiAutomation =
                InstrumentationRegistry.getInstrumentation().getUiAutomation();
        uiAutomation
                .adoptShellPermissionIdentity("android.permission.INTERACT_ACROSS_USERS_FULL");
        try {
            mActMan.addOnUidImportanceListener(observer,
                    ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND);

            out = ShellUtils.runShellCommand("pm create-user testUser");
            out = out.length() > 2 ? out.substring(out.length() - 2) : out;

            ShellUtils.runShellCommand("am switch-user " + out);

            Thread.sleep(5000);
            assertTrue(observer.didObserverOtherUser());
        } finally {
            uiAutomation.dropShellPermissionIdentity();
            ShellUtils.runShellCommand("am switch-user " + currentUser);
            ShellUtils.runShellCommand("pm remove-user " + out);
            mActMan.removeOnUidImportanceListener(observer);
        }
    }

    @AsbSecurityTest(cveBugId = 19394591)
    @Test
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

    // b/144285917
    @AsbSecurityTest(cveBugId = 144285917)
    @Test
    public void testActivityManager_attachNullApplication() {
        SecurityException securityException = null;
        Exception unexpectedException = null;
        try {
            final Object iam = ActivityManager.class.getDeclaredMethod("getService").invoke(null);
            Class.forName("android.app.IActivityManager").getDeclaredMethod("attachApplication",
                    Class.forName("android.app.IApplicationThread"), long.class)
                    .invoke(iam, null /* thread */, 0 /* startSeq */);
        } catch (SecurityException e) {
            securityException = e;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof SecurityException) {
                securityException = (SecurityException) e.getCause();
            } else {
                unexpectedException = e;
            }
        } catch (Exception e) {
            unexpectedException = e;
        }
        if (unexpectedException != null) {
            Log.w("ActivityManagerTest", "Unexpected exception", unexpectedException);
        }

        assertNotNull("Expect SecurityException by attaching null application", securityException);
    }

    // b/166667403
    @AsbSecurityTest(cveBugId = 166667403)
    @Test
    public void testActivityManager_appExitReasonPackageNames() {
        final String mockPackage = "com.foo.bar";
        final String realPackage = "com.android.compatibility.common.deviceinfo";
        final Context context = InstrumentationRegistry.getTargetContext();
        final ActivityManager am = context.getSystemService(ActivityManager.class);
        try {
            am.getHistoricalProcessExitReasons(mockPackage, 0, 0);
            fail("Expecting SecurityException");
        } catch (SecurityException e) {
            // expected
        }

        final int totalLoops = 10000;
        int mockPackagescores = 0;
        final double tolerance = 0.2d;
        for (int i = 0; i < totalLoops; i++) {
            final long realPackageTiming = measureGetHistoricalProcessExitReasons(am, realPackage);
            final long mockPackageTiming = measureGetHistoricalProcessExitReasons(am, mockPackage);
            mockPackagescores += mockPackageTiming < realPackageTiming ? 1 : 0;
        }

        assertTrue(Math.abs((double) mockPackagescores / totalLoops - 0.5d) < tolerance);
    }

    /**
     * Run ActivityManager.getHistoricalProcessExitReasons once, return the time spent on it.
     */
    private long measureGetHistoricalProcessExitReasons(ActivityManager am, String pkg) {
        final long start = System.nanoTime();
        try {
            am.getHistoricalProcessExitReasons(pkg, 0, 0);
        } catch (Exception e) {
        }
        return System.nanoTime() - start;
    }

    static final class UidImportanceObserver implements ActivityManager.OnUidImportanceListener {

        private boolean mObservedNonOwned = false;
        private int mMyUid;

        UidImportanceObserver() {
            mMyUid = UserHandle.getUserId(Process.myUid());
        }

        public void onUidImportance(int uid, int importance) {
            Log.i("ActivityManagerTestObserver", "Observing change for "
                    + uid + " by user " + UserHandle.getUserId(uid));
            if (UserHandle.getUserId(uid) != mMyUid) {
                mObservedNonOwned = true;
            }
        }

        public boolean didObserverOtherUser() {
            return this.mObservedNonOwned;
        }
    }
}
