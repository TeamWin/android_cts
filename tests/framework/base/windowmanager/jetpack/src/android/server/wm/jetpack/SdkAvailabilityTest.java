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

package android.server.wm.jetpack;

import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.hardware.display.DisplayManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.ExtensionUtil;
import android.server.wm.jetpack.utils.SidecarUtil;
import android.server.wm.jetpack.utils.WindowManagerJetpackTestBase;
import android.view.Display;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.WindowExtensions;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;

import com.android.compatibility.common.util.CddTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for devices implementations include an Android-compatible display(s)
 * that has a minimum screen dimension greater than or equal to 600dp.
 *
 * Build/Install/Run:
 * atest CtsWindowManagerJetpackTestCases:SdkAvailabilityTest
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"7.1.1.1/C-2-1,C-2-2"})
public class SdkAvailabilityTest extends WindowManagerJetpackTestBase {

    private static final int DISPLAY_MIN_WIDTH = 600;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        assumeScreenWidth600dp();
    }

    /**
     * MUST implement the latest available stable version of the extensions API
     * to be used by Window Manager Jetpack library.
     */
    @Test
    public void testWindowExtensionsAvailability() {
        assertTrue("WindowExtension version is not latest",
                ExtensionUtil.isExtensionVersionLatest());
    }

    /**
     * MUST support Activity Embedding APIs and make ActivityEmbeddingComponent available via
     * WindowExtensions interface.
     */
    @Test
    public void testActivityEmbeddingAvailability() {
        WindowExtensions windowExtensions = ExtensionUtil.getWindowExtensions();
        assertNotNull("WindowExtensions is not available", windowExtensions);
        ActivityEmbeddingComponent activityEmbeddingComponent =
                windowExtensions.getActivityEmbeddingComponent();
        assertNotNull("ActivityEmbeddingComponent is not available", activityEmbeddingComponent);
    }


    /**
     * MUST also implement the stable version of sidecar API for compatibility with older
     * applications.
     */
    @Test
    public void testSidecarAvailability() {
        assertTrue("Sidecar is not available", SidecarUtil.isSidecarVersionValid());
    }

    private void assumeScreenWidth600dp() {
        // Use WindowContext with type application overlay to prevent the metrics overridden by
        // activity bounds. Note that process configuration may still be overridden by
        // foreground Activity.
        final Context appContext = ApplicationProvider.getApplicationContext();
        final Display defaultDisplay = appContext.getSystemService(DisplayManager.class)
                .getDisplay(DEFAULT_DISPLAY);
        final Context windowContext = appContext.createWindowContext(defaultDisplay,
                TYPE_APPLICATION_OVERLAY, null /* options */);
        assumeTrue("Device does not has a minimum screen dimension greater than or equal to 600dp",
                windowContext.getResources().getConfiguration().smallestScreenWidthDp
                        >= DISPLAY_MIN_WIDTH);
    }
}
