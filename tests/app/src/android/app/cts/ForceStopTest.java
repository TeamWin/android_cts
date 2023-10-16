/*
 * Copyright (C) 2023 The Android Open Source Project
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

import static android.content.pm.Flags.FLAG_STAY_STOPPED;

import static com.android.compatibility.common.util.SystemUtil.runWithShellPermissionIdentity;

import static junit.framework.Assert.fail;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.ActivityManager;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.Flags;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.AmUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@Presubmit
public final class ForceStopTest {

    private static final String TAG = ForceStopTest.class.getSimpleName();
    // A secondary test activity from another APK.
    private static final String SIMPLE_PACKAGE_NAME = "com.android.cts.launcherapps.simpleapp";
    private static final String SIMPLE_ACTIVITY = ".SimpleActivity";
    // The action sent back by the SIMPLE_APP after a restart.
    private static final String ACTIVITY_LAUNCHED_ACTION =
            "com.android.cts.launchertests.LauncherAppsTests.LAUNCHED_ACTION";

    // Return states of the ActivityReceiverFilter.
    public static final int RESULT_PASS = 1;
    public static final int RESULT_TIMEOUT = 3;

    private static final long DELAY_MILLIS = 10_000;

    private Context mTargetContext;
    private ActivityManager mActivityManager;
    private PackageManager mPackageManager;
    private Instrumentation mInstrumentation;

    private long mTimestampMs;

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Exception {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mTargetContext = mInstrumentation.getTargetContext();
        mActivityManager = mInstrumentation.getContext().getSystemService(ActivityManager.class);
        mPackageManager = mInstrumentation.getContext().getPackageManager();

        AmUtils.waitForBroadcastBarrier();
    }

    private Intent createSimpleActivityIntent() {
        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_MAIN);
        intent.setPackage(SIMPLE_PACKAGE_NAME);
        intent.setClassName(SIMPLE_PACKAGE_NAME, SIMPLE_PACKAGE_NAME + SIMPLE_ACTIVITY);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return intent;
    }

    private ActivityReceiverFilter forceStopAndStartSimpleActivity(Intent intent) throws Exception {
        // Ensure that there are no remaining component records of the test app package.
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(intent.getPackage()));
        ActivityReceiverFilter appStartedReceiver = new ActivityReceiverFilter(
                ACTIVITY_LAUNCHED_ACTION);
        // Start an activity of another APK.
        mTargetContext.startActivity(intent);
        assertEquals(RESULT_PASS, appStartedReceiver.waitForActivity());
        return appStartedReceiver;
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageStoppedState() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();
        forceStopAndStartSimpleActivity(intent);

        assertFalse("Package " + packageName + " shouldn't be in the stopped state",
                mPackageManager.isPackageStopped(packageName));

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
        assertTrue("Package " + packageName + " should be in the stopped state",
                mPackageManager.isPackageStopped(packageName));
    }

    @Test
    public void testPackageRestartedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotRestarted = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_RESTARTED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotRestarted.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preStopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        // Force-stop it again
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));

        if (!gotRestarted.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_RESTARTED");
        }
        if (Flags.stayStopped()) {
            assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preStopTimestampMs,
                    mTimestampMs >= preStopTimestampMs);
        }
    }

    @Test
    @RequiresFlagsEnabled(FLAG_STAY_STOPPED)
    public void testPackageUnstoppedBroadcast() throws Exception {
        final Intent intent = createSimpleActivityIntent();
        final String packageName = intent.getPackage();

        // Setup to receive broadcasts about stopped state
        final ConditionVariable gotUnstopped = new ConditionVariable();
        final BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final String action = intent.getAction();
                final Uri uri = intent.getData();
                final String pkg = uri != null ? uri.getSchemeSpecificPart() : null;
                if (Intent.ACTION_PACKAGE_UNSTOPPED.equals(action)
                        && packageName.equals(pkg)) {
                    mTimestampMs = intent.getLongExtra(Intent.EXTRA_TIME, 0L);
                    gotUnstopped.open();
                }
            }
        };

        mTimestampMs = 0;
        final long preUnstopTimestampMs = SystemClock.elapsedRealtime();

        final IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_UNSTOPPED);
        mTargetContext.registerReceiver(receiver, filter);

        forceStopAndStartSimpleActivity(intent);

        assertTrue("EXTRA_TIME " + mTimestampMs + " not after " + preUnstopTimestampMs,
                mTimestampMs >= preUnstopTimestampMs);

        if (!gotUnstopped.block(DELAY_MILLIS)) {
            fail("Didn't get ACTION_PACKAGE_UNSTOPPED");
        }

        // Force-stop it again to clean up
        runWithShellPermissionIdentity(
                () -> mActivityManager.forceStopPackage(packageName));
    }

    // The receiver filter needs to be instantiated with the command to filter for before calling
    // startActivity.
    private class ActivityReceiverFilter extends BroadcastReceiver {
        // The activity we want to filter for.
        private String mActivityToFilter;
        private int mResult = RESULT_TIMEOUT;
        private static final int TIMEOUT_IN_MS = 5000;
        private ConditionVariable mBroadcastCondition = new ConditionVariable();

        // Create the filter with the intent to look for.
        ActivityReceiverFilter(String activityToFilter) {
            mActivityToFilter = activityToFilter;
            final IntentFilter filter = new IntentFilter();
            filter.addAction(mActivityToFilter);
            mTargetContext.registerReceiver(this, filter,
                    Context.RECEIVER_EXPORTED);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(mActivityToFilter)) {
                mResult = RESULT_PASS;
                mBroadcastCondition.open();
            }
        }

        public int waitForActivity() throws Exception {
            AmUtils.waitForBroadcastBarrier();
            if (mResult == RESULT_PASS) return mResult;
            // Wait for the broadcast
            mBroadcastCondition.block(TIMEOUT_IN_MS);
            return mResult;
        }
    }
}
