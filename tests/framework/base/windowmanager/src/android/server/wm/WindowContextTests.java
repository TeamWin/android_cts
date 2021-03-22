/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests that verify the behavior of window context
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceTestCases:WindowContextTests
 */
@Presubmit
public class WindowContextTests extends WindowContextTestBase {
    @Test
    @AppModeFull
    public void testWindowContextConfigChanges() {
        createAllowSystemAlertWindowAppOpSession();
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        mInstrumentation.runOnMainSync(() -> {
            final View view = new View(windowContext);
            WindowManager wm = windowContext.getSystemService(WindowManager.class);
            wm.addView(view, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY));
        });
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(display.mId);

        mWmState.computeState();

        Rect bounds = windowContext.getSystemService(WindowManager.class).getCurrentWindowMetrics()
                .getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);

        displayMetricsSession.changeDisplayMetrics(1.2 /* sizeRatio */, 1.1 /* densityRatio */);

        mWmState.computeState();

        bounds = windowContext.getSystemService(WindowManager.class).getCurrentWindowMetrics()
                .getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);
    }

    private void assertBoundsEquals(ReportedDisplayMetrics expectedMetrics,
            Rect bounds) {
        assertEquals(expectedMetrics.getSize().getWidth(), bounds.width());
        assertEquals(expectedMetrics.getSize().getHeight(), bounds.height());
    }

    @Test
    @AppModeFull
    public void testWindowContextBindService() {
        createAllowSystemAlertWindowAppOpSession();
        final Context windowContext = createWindowContext(DEFAULT_DISPLAY);
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {}

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        try {
            assertTrue("WindowContext must bind service successfully.",
                    windowContext.bindService(new Intent(windowContext, TestLogService.class),
                            serviceConnection, Context.BIND_AUTO_CREATE));
        } finally {
            windowContext.unbindService(serviceConnection);
        }
    }

    /**
     * Verify if the {@link ComponentCallbacks#onConfigurationChanged(Configuration)} callback
     * is received when the window context configuration changes.
     */
    @Test
    public void testWindowContextRegisterComponentCallbacks() throws Exception {
        final TestComponentCallbacks callbacks = new TestComponentCallbacks();
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(display.mId);

        windowContext.registerComponentCallbacks(callbacks);

        callbacks.mLatch = new CountDownLatch(1);

        displayMetricsSession.changeDisplayMetrics(1.2 /* sizeRatio */, 1.1 /* densityRatio */);

        // verify if there is a gicallback from the window context configuration change.
        assertTrue(callbacks.mLatch.await(4, TimeUnit.SECONDS));
        Rect bounds = callbacks.mConfiguration.windowConfiguration.getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);

        windowContext.unregisterComponentCallbacks(callbacks);
    }

    private static class TestComponentCallbacks implements ComponentCallbacks {
        private Configuration mConfiguration;
        private CountDownLatch mLatch = new CountDownLatch(1);

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            mConfiguration = newConfig;
            mLatch.countDown();
        }

        @Override
        public void onLowMemory() {}
    }
}
