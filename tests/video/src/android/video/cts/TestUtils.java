/*
 * Copyright 2018 The Android Open Source Project
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

package android.video.cts;

import static android.content.pm.PackageManager.MATCH_APEX;

import static org.junit.Assume.assumeTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;

import androidx.test.core.app.ApplicationProvider;

import org.junit.Assert;
import org.junit.AssumptionViolatedException;

import java.util.Objects;

/**
 * Utilities for tests.
 */
public final class TestUtils {
    private static String TAG = "TestUtils";

    /**
     * Reports whether {@code module} is the version shipped with the original system image
     * or if it has been updated via a mainline update.
     *
     * @param module     the apex module name
     * @return {@code true} if the apex module is the original version shipped with the device.
     */
    public static boolean isMainlineModuleFactoryVersion(String module)
            throws PackageManager.NameNotFoundException {
        Context context = ApplicationProvider.getApplicationContext();
        PackageInfo info = context.getPackageManager().getPackageInfo(module,
                MATCH_APEX);
        if (info == null) {
            return true;
        }
        return (info.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }
}
