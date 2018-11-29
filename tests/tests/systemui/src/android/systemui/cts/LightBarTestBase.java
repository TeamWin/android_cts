/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.systemui.cts;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowInsets;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;

public class LightBarTestBase {

    private static final String TAG = "LightBarTestBase";

    public static final Path DUMP_PATH = FileSystems.getDefault()
            .getPath("/sdcard/LightBarTestBase/");

    protected Bitmap takeStatusBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, 0, 0, activity.getWidth(), activity.getTop());
    }

    protected Bitmap takeNavigationBarScreenshot(LightBarBaseActivity activity) {
        Bitmap fullBitmap = getInstrumentation().getUiAutomation().takeScreenshot();
        return Bitmap.createBitmap(fullBitmap, 0, activity.getBottom(), activity.getWidth(),
                fullBitmap.getHeight() - activity.getBottom());
    }

    protected void dumpBitmap(Bitmap bitmap, String name) {
        File dumpDir = DUMP_PATH.toFile();
        if (!dumpDir.exists()) {
            dumpDir.mkdirs();
        }

        Path filePath = DUMP_PATH.resolve(name + ".png");
        Log.e(TAG, "Dumping failed bitmap to " + filePath);
        FileOutputStream fileStream = null;
        try {
            fileStream = new FileOutputStream(filePath.toFile());
            bitmap.compress(Bitmap.CompressFormat.PNG, 85, fileStream);
            fileStream.flush();
        } catch (Exception e) {
            Log.e(TAG, "Dumping bitmap failed.", e);
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean hasVirtualNavigationBar(ActivityTestRule<? extends LightBarBaseActivity> rule)
            throws Throwable {
        final WindowInsets[] inset = new WindowInsets[1];
        rule.runOnUiThread(()-> {
            inset[0] = rule.getActivity().getRootWindowInsets();
        });
        return inset[0].getStableInsetBottom() > 0;
    }

    private boolean isRunningInVr() {
        final Context context = InstrumentationRegistry.getContext();
        final Configuration config = context.getResources().getConfiguration();
        return (config.uiMode & Configuration.UI_MODE_TYPE_MASK)
                == Configuration.UI_MODE_TYPE_VR_HEADSET;
    }

    private void assumeBasics() {
        final PackageManager pm = getInstrumentation().getContext().getPackageManager();

        // No bars on embedded devices.
        assumeFalse(getInstrumentation().getContext().getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_EMBEDDED));

        // No bars on TVs and watches.
        assumeFalse(pm.hasSystemFeature(PackageManager.FEATURE_WATCH)
                || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK));


        // Non-highEndGfx devices don't do colored system bars.
        assumeTrue(ActivityManager.isHighEndGfx());
    }

    protected void assumeHasColoredStatusBar(ActivityTestRule<? extends LightBarBaseActivity> rule)
            throws Throwable {
        assumeBasics();

        // No status bar when running in Vr
        assumeFalse(isRunningInVr());

        // Status bar exists only when top stable inset is positive
        final WindowInsets[] inset = new WindowInsets[1];
        rule.runOnUiThread(()-> {
            inset[0] = rule.getActivity().getRootWindowInsets();
        });
        assumeTrue("Top stable inset is non-positive.", inset[0].getStableInsetTop() > 0);
    }

    protected void assumeHasColoredNavigationBar(
            ActivityTestRule<? extends LightBarBaseActivity> rule) throws Throwable {
        assumeBasics();

        // No virtual navigation bar, so no effect.
        assumeTrue(hasVirtualNavigationBar(rule));
    }

    protected void checkNavigationBarDivider(LightBarBaseActivity activity, int dividerColor,
            int backgroundColor, String methodName) {
        final Bitmap bitmap = takeNavigationBarScreenshot(activity);
        int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        int backgroundColorPixelCount = 0;
        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] == backgroundColor) {
                backgroundColorPixelCount++;
            }
        }
        assumeNavigationBarChangesColor(backgroundColorPixelCount, pixels.length);

        for (int col = 0; col < bitmap.getWidth(); col++) {
            if (dividerColor != pixels[col]) {
                dumpBitmap(bitmap, methodName);
                fail("Invalid color exptected=" + dividerColor + " actual=" + pixels[col]);
            }
        }
    }

    protected void assumeNavigationBarChangesColor(int backgroundColorPixelCount, int totalPixel) {
        assumeTrue("Not enough background pixels. The navigation bar may not be able to change "
                + "color.", backgroundColorPixelCount > 0.3f * totalPixel);
    }
}
