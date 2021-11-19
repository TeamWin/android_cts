/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.mediapc.cts;

import static android.util.DisplayMetrics.DENSITY_400;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Tests the basic aspects of the media performance class.
 */
public class PerformanceClassTest {
    private static final String TAG = "PerformanceClassTest";

    private boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm =
            InstrumentationRegistry.getInstrumentation().getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION)
                && !pm.hasSystemFeature(pm.FEATURE_AUTOMOTIVE);
    }

    @SmallTest
    @Test
    public void testMediaPerformanceClassScope() throws Exception {
        // if device is not of a performance class, we are done.
        Assume.assumeTrue("not a device of a valid media performance class", Utils.isPerfClass());

        if (Utils.isRPerfClass()
                || Utils.isSPerfClass()) {
            assertTrue("performance class is only defined for Handheld devices",
                       isHandheld());
        }
    }

    @Test
    @CddTest(requirement="2.2.7.3/7.1.1.1,7.1.1.3,7.6.1/H-1-1,H-2-1")
    public void testMinimumMemory() {
        Context context = InstrumentationRegistry.getInstrumentation().getContext();

        // Verify minimum screen density and resolution
        assertMinDpiAndPixels(context, DENSITY_400, 1920, 1080);
        // Verify minimum memory
        assertMinMemoryMb(context, 6 * 1024);
    }

    /** Asserts that the given values conform to the specs in CDD */
    private void assertMinDpiAndPixels(Context context, int minDpi, int minLong, int minShort) {
        // Verify display DPI. We only seem to be able to get the primary display.
        DisplayMetrics metrics = new DisplayMetrics();
        WindowManager windowManager =
            (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(metrics);
        int density = metrics.densityDpi;
        int longPix = Math.max(metrics.widthPixels, metrics.heightPixels);
        int shortPix = Math.min(metrics.widthPixels, metrics.heightPixels);

        Log.i(TAG, String.format("minDpi=%d minSize=%dx%dpix", minDpi, minLong, minShort));
        Log.i(TAG, String.format("dpi=%d size=%dx%dpix", density, longPix, shortPix));

        if (Utils.isPerfClass()) {
            assertTrue("Display density " + density + " must be at least " + minDpi + "dpi",
                    density >= minDpi);
            assertTrue("Display resolution " + longPix + "x" + shortPix + "pix must be at least " +
                            minLong + "x" + minShort + "pix",
                    longPix >= minLong && shortPix >= minShort);
        } else {
            int pc = density >= minDpi && longPix >= minLong && shortPix >= minShort
                    ? Build.VERSION_CODES.S : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",  "Display");
            log.addValue("DisplayDensity", density, ResultType.LOWER_BETTER, ResultUnit.NONE);
            log.addValue("ResolutionLong", longPix, ResultType.LOWER_BETTER, ResultUnit.NONE);
            log.addValue("ResolutionShort", shortPix, ResultType.LOWER_BETTER, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.3/7.1.1.1,7.1.1.3/H-1-1,H-2-1 performance_class", pc,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }
    }

    /** Asserts that the given values conform to the specs in CDD 7.6.1 */
    private void assertMinMemoryMb(Context context, long minMb) {
        ActivityManager activityManager =
                    (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        long totalMemoryMb = getTotalMemory(activityManager) / 1024 / 1024;

        Log.i(TAG, String.format("minMb=%,d", minMb));
        Log.i(TAG, String.format("totalMemoryMb=%,d", totalMemoryMb));
        if (Utils.isPerfClass()) {
            assertTrue(String.format("Does not meet minimum memory requirements (CDD 7.6.1)."
                    + "Found = %d, Minimum = %d", totalMemoryMb, minMb), totalMemoryMb >= minMb);
        } else {
            int pc = totalMemoryMb >= minMb ? Build.VERSION_CODES.S : 0;
            DeviceReportLog log = new DeviceReportLog("MediaPerformanceClassLogs",  "MinMemory");
            log.addValue("MemoryMB", totalMemoryMb, ResultType.LOWER_BETTER, ResultUnit.NONE);
            log.setSummary("CDD 2.2.7.3/7.6.1/H-1-1,H-2-1  performance_class", pc,
                    ResultType.NEUTRAL, ResultUnit.NONE);
            log.submit(InstrumentationRegistry.getInstrumentation());
        }

    }

    /**
     * @return the total memory accessible by the kernel as defined by
     * {@code ActivityManager.MemoryInfo}.
     */
    private long getTotalMemory(ActivityManager activityManager) {
        ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(memoryInfo);
        return memoryInfo.totalMem;
    }
}
