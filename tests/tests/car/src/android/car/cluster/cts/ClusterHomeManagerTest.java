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
package android.car.cluster.cts;

import static android.car.feature.Flags.FLAG_CLUSTER_HEALTH_MONITORING;

import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.car.Car;
import android.car.cluster.ClusterHomeManager;
import android.car.cts.utils.DumpUtils;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class ClusterHomeManagerTest {
    private static final long TIMEOUT_MS = 5_000;
    private static final String CLUSTER_HOME_SERVICE = "ClusterHomeService";
    private static final String DUMP_TPL_COUNT = "mTrustedPresentationListenerCount";
    private static final String DUMP_CLUSTER_VISIBLE = "mClusterActivityVisible";

    private final Instrumentation mInstrumentation =
            InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();
    private final Context mTargetContext = mInstrumentation.getTargetContext();
    private final UiAutomation mUiAutomation = mInstrumentation.getUiAutomation();
    private final ComponentName mTestActivityName =
            new ComponentName(mTargetContext, ClusterHomeManagerTest.TestActivity.class);
    private TestActivity mTestActivity;

    @Before
    public void setUp() {
        Car car = Car.createCar(mContext);
        ClusterHomeManager clusterHomeManager = car.getCarManager(ClusterHomeManager.class);
        assumeTrue(clusterHomeManager != null);
        mUiAutomation.adoptShellPermissionIdentity(Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    @After
    public void tearDown() throws Exception {
        if (mTestActivity != null) {
            mTestActivity.finishAndRemoveTask();
            mTestActivity.waitForDestroyed();
            mTestActivity = null;
        }
        mUiAutomation.dropShellPermissionIdentity();
    }

    @Test
    @RequiresFlagsEnabled(FLAG_CLUSTER_HEALTH_MONITORING)
    @ApiTest(apis = {"android.car.cluster.ClusterHomeManager#startVisibilityMonitoring(Activity)"})
    public void testStartVisibilityMonitoring() throws Exception {
        var oldDump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
        int oldCount = Integer.valueOf(oldDump.get(DUMP_TPL_COUNT));

        mTestActivity = (TestActivity) mInstrumentation.startActivitySync(
                Intent.makeMainActivity(mTestActivityName)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));

        // The callback will be called with 'true' as soon as the system bars disappear.
        PollingCheck.waitFor(TIMEOUT_MS, () -> {
            var dump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
            int count = Integer.valueOf(dump.get(DUMP_TPL_COUNT));
            boolean visible = Boolean.parseBoolean(dump.get(DUMP_CLUSTER_VISIBLE));
            return count > oldCount && visible;
        });

        var oldDump2 = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
        int oldCount2 = Integer.valueOf(oldDump2.get(DUMP_TPL_COUNT));

        // Insets can be accessible only in the Activity's thread.
        mTestActivity.getMainExecutor().execute(() -> {
                    WindowInsetsController insets =
                            mTestActivity.getWindow().getDecorView().getWindowInsetsController();
                    insets.show(WindowInsets.Type.systemBars());
                }
        );

        // The callback will be called with 'false' as soon as the system bars appear.
        PollingCheck.waitFor(TIMEOUT_MS, () -> {
            var dump = DumpUtils.executeDumpShellCommand(CLUSTER_HOME_SERVICE);
            int count = Integer.valueOf(dump.get(DUMP_TPL_COUNT));
            boolean visible = Boolean.parseBoolean(dump.get(DUMP_CLUSTER_VISIBLE));
            return count > oldCount2 && !visible;
        });
    }

    public static final class TestActivity extends Activity {
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onStart() {
            super.onStart();
            Car car = Car.createCar(this);
            ClusterHomeManager clusterHomeManager = car.getCarManager(ClusterHomeManager.class);
            clusterHomeManager.startVisibilityMonitoring(this);

            // SystemBar also hides ActivitySurface, so hide the system bars.
            WindowInsetsController insets = getWindow().getDecorView().getWindowInsetsController();
            insets.hide(WindowInsets.Type.systemBars());
        }

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        private boolean waitForDestroyed() throws InterruptedException {
            return mDestroyed.await(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
