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

import android.graphics.Bitmap;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;

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

    protected boolean hasVirtualNavigationBar() {
        boolean hasBackKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_BACK);
        boolean hasHomeKey = KeyCharacterMap.deviceHasKey(KeyEvent.KEYCODE_HOME);
        return !hasBackKey || !hasHomeKey;
    }
}
