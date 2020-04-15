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

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.assertEquals;

import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import androidx.test.filters.FlakyTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

@Presubmit
public class WindowContextTests extends MultiDisplayTestBase {
    private Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private Context mContext = mInstrumentation.getTargetContext();
    private DisplayManager mDisplayManager = mContext.getSystemService(DisplayManager.class);

    @Test
    @FlakyTest(bugId = 150251036)
    @AppModeFull
    public void testWindowContextConfigChanges() {
        final WindowManagerState.DisplayContent display =  createManagedVirtualDisplaySession()
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

    private Context createWindowContext(int displayId) {
        final Display display = mDisplayManager.getDisplay(displayId);
        return mContext.createDisplayContext(display).createWindowContext(TYPE_APPLICATION_OVERLAY,
                null /* options */);
    }

    private void assertBoundsEquals(ReportedDisplayMetrics expectedMetrics,
            Rect bounds) {
        assertEquals(expectedMetrics.getSize().getWidth(), bounds.width());
        assertEquals(expectedMetrics.getSize().getHeight(), bounds.height());
    }
}
