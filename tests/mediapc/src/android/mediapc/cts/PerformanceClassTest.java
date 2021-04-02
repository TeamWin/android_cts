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

import android.content.pm.PackageManager;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

/**
 * Tests the basic aspects of the media performance class.
 */
public class PerformanceClassTest {
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
}

